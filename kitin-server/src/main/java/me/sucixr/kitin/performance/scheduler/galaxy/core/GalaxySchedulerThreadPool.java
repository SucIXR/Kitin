package me.sucixr.kitin.performance.scheduler.galaxy.core;

import ca.spottedleaf.concurrentutil.scheduler.SchedulableTick;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
// 导入我们的跳板类！
import ca.spottedleaf.concurrentutil.scheduler.GalaxyTaskAccessor;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Kitin GALAXY Scheduler
 * <p>
 * 遵循天体物理与计算机底层的绝对同构：
 * 1. 万有引力 (Cache Affinity): O(1) 哈希绑核，L3 缓存命中率极值化。
 * 2. 波粒二象 (Wave-Particle): runTick 与 runTasks 的无锁时空分离，光速发包。
 * 3. 洛希跃迁 (Roche Stealing): 基于 64位掩码矩阵 (Bit-Scan) 的 O(1) 极速工作窃取。
 */
public class GalaxySchedulerThreadPool extends Scheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int threadCount;
    private final Thread[] threads;
    private final GalaxyTickRunner[] runners;

    private final OverloadMatrix matrix;
    private volatile boolean halted = false;

    public GalaxySchedulerThreadPool(int threads, ThreadFactory threadFactory) {
        this.threadCount = Math.max(1, threads);
        this.threads = new Thread[this.threadCount];
        this.runners = new GalaxyTickRunner[this.threadCount];

        if (this.threadCount <= 64) {
            this.matrix = new SingleLongMatrix();
        } else {
            this.matrix = new MultiLongMatrix(this.threadCount);
        }

        for (int i = 0; i < this.threadCount; i++) {
            this.runners[i] = new GalaxyTickRunner(i, this);
            this.threads[i] = threadFactory.newThread(this.runners[i]);
            this.threads[i].setPriority(Thread.NORM_PRIORITY + 2);
        }
    }

    public void start() {
        for (Thread t : this.threads) {
            t.start();
        }
    }

    @Override
    public void schedule(SchedulableTick task) {
        if (this.halted) return;

        GalaxyTaskState state = getOrInitializeState(task);

        while (true) {
            int currentStatus = state.state.get();
            if (currentStatus == GalaxyTaskState.STATE_SCHEDULED) return;
            if (currentStatus == GalaxyTaskState.STATE_EXECUTING) {
                state.pendingNotify = true;
                if (state.state.get() == GalaxyTaskState.STATE_EXECUTING) return;
                continue;
            }

            if (state.state.compareAndSet(currentStatus, GalaxyTaskState.STATE_SCHEDULED)) {
                int targetOrbit = (int) ((task.id & Long.MAX_VALUE) % this.threadCount);
                this.runners[targetOrbit].localOrbit.offer(task);
                LockSupport.unpark(this.threads[targetOrbit]);
                return;
            }
        }
    }

    @Override
    public void notifyTasks(SchedulableTick task) {
        GalaxyTaskState state = getOrInitializeState(task);
        if (state.state.get() == GalaxyTaskState.STATE_SCHEDULED) {
            int targetOrbit = (int) ((task.id & Long.MAX_VALUE) % this.threadCount);
            LockSupport.unpark(this.threads[targetOrbit]);
        } else if (state.state.get() == GalaxyTaskState.STATE_EXECUTING) {
            state.pendingNotify = true;
        } else {
            this.schedule(task);
        }
    }

    @Override
    public boolean cancel(SchedulableTick task) {
        GalaxyTaskState state = getOrInitializeState(task);
        if (state.state.compareAndSet(GalaxyTaskState.STATE_SCHEDULED, GalaxyTaskState.STATE_IDLE)) {
            int targetOrbit = (int) ((task.id & Long.MAX_VALUE) % this.threadCount);
            if (this.runners[targetOrbit].localOrbit.remove(task)) return true;
        }
        return false;
    }

    public static class GalaxyTickRunner implements Runnable {
        private final int id;
        private final GalaxySchedulerThreadPool pool;

        public final PriorityBlockingQueue<SchedulableTick> localOrbit = new PriorityBlockingQueue<>(11, (t1, t2) -> {
            return Long.compare(GalaxyTaskAccessor.getScheduledStart(t1), GalaxyTaskAccessor.getScheduledStart(t2));
        });

        public GalaxyTickRunner(int id, GalaxySchedulerThreadPool pool) {
            this.id = id;
            this.pool = pool;
        }

        @Override
        public void run() {
            while (!this.pool.halted) {
                long now = System.nanoTime();
                SchedulableTick task = this.localOrbit.peek();

                if (task != null) {
                    long sched = GalaxyTaskAccessor.getScheduledStart(task);
                    long delay = now - sched;

                    if (delay > 2_000_000L) {
                        this.pool.matrix.markOverloaded(this.id);
                    } else {
                        this.pool.matrix.clearOverloaded(this.id);
                    }

                    if (now >= sched) {
                        if (this.localOrbit.remove(task)) this.executeTask(task, true);
                    } else if (task.hasTasks()) {
                        if (this.localOrbit.remove(task)) this.executeTask(task, false);
                    } else {
                        SchedulableTick stolen = this.stealFromMatrix();
                        if (stolen != null) {
                            this.executeTask(stolen, true);
                        } else {
                            long parkTime = sched - System.nanoTime();
                            if (parkTime > 0) LockSupport.parkNanos(Math.min(parkTime, 1_000_000L));
                        }
                    }
                } else {
                    this.pool.matrix.clearOverloaded(this.id);
                    SchedulableTick stolen = this.stealFromMatrix();
                    if (stolen != null) {
                        this.executeTask(stolen, true);
                    } else {
                        LockSupport.parkNanos(1_000_000L);
                    }
                }
            }
        }

        private void executeTask(SchedulableTick task, boolean isMacroTick) {
            GalaxyTaskState state = getOrInitializeState(task);
            if (!state.state.compareAndSet(GalaxyTaskState.STATE_SCHEDULED, GalaxyTaskState.STATE_EXECUTING)) return;
            state.pendingNotify = false;

            try {
                boolean reschedule;
                if (isMacroTick) {
                    reschedule = task.runTick();
                } else {
                    long start = System.nanoTime();
                    reschedule = task.runTasks(() -> (System.nanoTime() - start) < 2_000_000L);
                }
                state.state.set(GalaxyTaskState.STATE_IDLE);

                if (reschedule || state.pendingNotify) {
                    this.pool.schedule(task);
                }
            } catch (Throwable t) {
                state.state.set(GalaxyTaskState.STATE_IDLE);
                LOGGER.error("[Kitin Galaxy Scheduler] Unhandled exception during region task execution", t);
            }
        }

        private SchedulableTick stealFromMatrix() {
            int targetId = this.pool.matrix.findTarget(this.id, this.pool.threadCount);
            if (targetId == -1) return null;

            GalaxyTickRunner target = this.pool.runners[targetId];
            SchedulableTick stolen = target.localOrbit.peek();

            if (stolen != null) {
                if (System.nanoTime() - GalaxyTaskAccessor.getScheduledStart(stolen) > 2_000_000L) {
                    if (target.localOrbit.remove(stolen)) {
                        return stolen;
                    }
                }
            } else {
                this.pool.matrix.clearOverloaded(targetId);
            }
            return null;
        }
    }

    public static class GalaxyTaskState {
        public static final int STATE_IDLE = 0;
        public static final int STATE_SCHEDULED = 1;
        public static final int STATE_EXECUTING = 2;

        public final AtomicInteger state = new AtomicInteger(STATE_IDLE);
        public volatile boolean pendingNotify = false;
    }

    private static GalaxyTaskState getOrInitializeState(SchedulableTick task) {
        Object rawState = GalaxyTaskAccessor.getState(task);
        if (rawState instanceof GalaxyTaskState) {
            return (GalaxyTaskState) rawState;
        }
        synchronized (task) {
            rawState = GalaxyTaskAccessor.getState(task);
            if (rawState instanceof GalaxyTaskState) {
                return (GalaxyTaskState) rawState;
            }
            GalaxyTaskState newState = new GalaxyTaskState();
            GalaxyTaskAccessor.setState(task, newState);
            return newState;
        }
    }

    @Override public void halt() { this.halted = true; for (Thread t : this.threads) LockSupport.unpark(t); }
    @Override public boolean join(long ms) { return true; }
    @Override public boolean joinInterruptable(long ms) { return true; }
    @Override public Thread[] getCoreThreads() { return this.threads.clone(); }
    @Override public Thread[] getAliveThreads() { return getCoreThreads(); }

    // ==========================================
    // 自适应矩阵引擎 (支持 64+ 核心无限扩展)
    // ==========================================
    private interface OverloadMatrix {
        void markOverloaded(int id);
        void clearOverloaded(int id);
        int findTarget(int currentId, int maxThreads);
    }

    private static final class SingleLongMatrix implements OverloadMatrix {
        private final AtomicLong mask = new AtomicLong(0);

        @Override public void markOverloaded(int id) { this.mask.updateAndGet(m -> m | (1L << id)); }
        @Override public void clearOverloaded(int id) { this.mask.updateAndGet(m -> m & ~(1L << id)); }
        @Override public int findTarget(int currentId, int maxThreads) {
            long m = this.mask.get();
            if (m == 0) return -1;
            int targetId = Long.numberOfTrailingZeros(m);
            return (targetId < maxThreads && targetId != currentId) ? targetId : -1;
        }
    }

    private static final class MultiLongMatrix implements OverloadMatrix {
        private final java.util.concurrent.atomic.AtomicLongArray masks;

        public MultiLongMatrix(int threadCount) {
            this.masks = new java.util.concurrent.atomic.AtomicLongArray((threadCount + 63) >>> 6);
        }

        @Override public void markOverloaded(int id) {
            this.masks.updateAndGet(id >>> 6, m -> m | (1L << (id & 63)));
        }
        @Override public void clearOverloaded(int id) {
            this.masks.updateAndGet(id >>> 6, m -> m & ~(1L << (id & 63)));
        }
        @Override public int findTarget(int currentId, int maxThreads) {
            for (int i = 0; i < this.masks.length(); i++) {
                long m = this.masks.get(i);
                if (m != 0) {
                    int targetId = (i << 6) + Long.numberOfTrailingZeros(m);
                    if (targetId < maxThreads && targetId != currentId) return targetId;
                }
            }
            return -1;
        }
    }
}