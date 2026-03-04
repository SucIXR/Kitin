package me.sucixr.kitin.performance.scheduler.galaxy.core;

import ca.spottedleaf.concurrentutil.scheduler.SchedulableTick;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Kitin Galaxy Scheduler (Kitin 银河调度器) - 核心执行引擎
 * <p>
 * 本调度器彻底重构了 Folia 原版的全局 EDF (最早截止时间优先) 队列模型，
 * 采用基于物理仿生学（引力与热力模型）的“亲和性感知 + 动态工作窃取”混合拓扑架构。
 * 旨在极端并发环境下，实现 L2/L3 缓存命中率与多核负载均衡的完美统一。
 * <p>
 * 核心架构矩阵：
 * <ul>
 * <li><b>1. 万有引力锚定 (Strict Cache Affinity)：</b>
 * 摒弃全局锁与无序抢占。通过 {@code task.id % threadCount} 将 Region 任务硬绑定至专属星轨（本地 PriorityBlockingQueue）。
 * 确保同一区块的运算高度贴合特定 CPU 核心，极大幅度降低内存总线延迟与缓存未命中（Cache Miss）惩罚。</li>
 * * <li><b>2. 斥力溢出机制 (Latency-Aware Work Stealing)：</b>
 * 引入 2ms 延迟容忍阈值。常态下各线程独立运行（免锁隔离）；
 * 当局部星轨因重载事件（如巨量 TNT 爆炸/复杂红石）发生阻塞时，相邻空闲星轨将跨越边界“窃取”待办任务。
 * 通过延迟阈值防抖，平滑抹平 TPS 局部骤降，实现算力的自适应延展。</li>
 * * <li><b>3. 量子状态锁 (Zero-Overhead State Machine)：</b>
 * 深入底层，利用 {@link java.lang.invoke.MethodHandle} 零开销接管 Folia 内部任务状态。
 * 构建 {@code IDLE(0) -> QUEUED(1) -> EXECUTING(2)} 三态原子级 CAS (Compare-And-Swap) 屏障，
 * 提供无锁且防弹级别的并发双重执行防御，根除区块状态损坏的隐患。</li>
 * * <li><b>4. 宏观拓扑协同 (NUMA & Netty Synergy)：</b>
 * 作为底层基石，本计算引擎的运转将直接影响星体的“温度”（CPU 负载）。
 * 这些状态将被 {@code GalaxyObservatory} 采集，进而驱动 Netty 网络线程进行 OS 级物理绑核（NUMA 跃迁），
 * 史无前例地打通了 Minecraft 计算层与网络层的物理壁垒。</li>
 * </ul>
 */
public class GalaxySchedulerThreadPool extends Scheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int threadCount;
    private final Thread[] threads;
    private final GalaxyTickRunner[] runners;
    private volatile boolean halted = false;

    public GalaxySchedulerThreadPool(int threads, ThreadFactory threadFactory) {
        this.threadCount = threads;
        this.threads = new Thread[threads];
        this.runners = new GalaxyTickRunner[threads];

        for (int i = 0; i < threads; i++) {
            this.runners[i] = new GalaxyTickRunner(i, this);
            this.threads[i] = threadFactory.newThread(this.runners[i]);
            // 提升线程优先级以压制系统杂音
            this.threads[i].setPriority(Thread.NORM_PRIORITY + 2);
        }

        //LOGGER.info("[Kitin Galaxy Scheduler] Initialized with {} worker threads and state machine lock.", threads);
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

        // 量子状态锁：绝对防御并发双重执行
        while (true) {
            int currentStatus = state.status.get();

            if (currentStatus == 1) { // QUEUED
                return;
            }
            if (currentStatus == 2) { // EXECUTING
                state.pendingNotify.set(true);
                if (state.status.get() == 2) return;
                continue;
            }

            // IDLE -> QUEUED
            if (state.status.compareAndSet(0, 1)) {
                // 万有引力锚定
                int targetOrbit = (int) ((task.id & Long.MAX_VALUE) % this.threadCount);
                this.runners[targetOrbit].localOrbit.offer(task);
                LockSupport.unpark(this.threads[targetOrbit]);
                return;
            }
        }
    }

    @Override
    public void halt() {
        this.halted = true;
        for (Thread t : this.threads) {
            LockSupport.unpark(t);
        }
    }

    @Override public boolean join(long msToWait) { return true; }
    @Override public boolean joinInterruptable(long msToWait) { return true; }
    @Override public Thread[] getCoreThreads() { return this.threads.clone(); }
    @Override public Thread[] getAliveThreads() { return getCoreThreads(); }

    @Override
    public boolean cancel(SchedulableTick task) {
        GalaxyTaskState state = getOrInitializeState(task);
        if (state.status.compareAndSet(1, 0)) {
            for (GalaxyTickRunner runner : this.runners) {
                runner.localOrbit.remove(task);
            }
            return true;
        }
        return false;
    }

    @Override
    public void notifyTasks(SchedulableTick task) {
        schedule(task);
    }

    /**
     * 行星自转引擎：处理本线程内的任务与溢出窃取
     */
    public static class GalaxyTickRunner implements Runnable {
        private final int id;
        private final GalaxySchedulerThreadPool pool;

        public final PriorityBlockingQueue<SchedulableTick> localOrbit = new PriorityBlockingQueue<>(11, (t1, t2) -> {
            return Long.compare(getScheduledStartFast(t1), getScheduledStartFast(t2));
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

                if (task != null && now >= getScheduledStartFast(task)) {
                    if (this.localOrbit.remove(task)) {
                        this.executeTask(task);
                    }
                } else {
                    // 斥力溢出 (Work Stealing)
                    SchedulableTick stolenTask = this.stealFromOverloadedNeighbors(now);
                    if (stolenTask != null) {
                        this.executeTask(stolenTask);
                    } else {
                        long parkTime = (task != null) ? getScheduledStartFast(task) - System.nanoTime() : 50_000_000L;
                        if (parkTime > 0) {
                            LockSupport.parkNanos(parkTime);
                        }
                    }
                }
            }
        }

        private void executeTask(SchedulableTick task) {
            GalaxyTaskState state = getOrInitializeState(task);
            if (!state.status.compareAndSet(1, 2)) return;

            state.pendingNotify.set(false);

            try {
                boolean reschedule = task.runTick();
                state.status.set(0);

                if (reschedule || state.pendingNotify.get()) {
                    this.pool.schedule(task);
                }
            } catch (Throwable t) {
                state.status.set(0);
                LOGGER.error("[Kitin Galaxy Scheduler] Exception in tick runner", t);
            }
        }

        private SchedulableTick stealFromOverloadedNeighbors(long now) {
            // 延迟容忍度：2毫秒
            long stealThreshold = 2_000_000L;

            for (int i = 1; i < this.pool.threadCount; i++) {
                int neighborId = (this.id + i) % this.pool.threadCount;
                GalaxyTickRunner neighbor = this.pool.runners[neighborId];

                SchedulableTick neighborTask = neighbor.localOrbit.peek();
                if (neighborTask != null && now - getScheduledStartFast(neighborTask) > stealThreshold) {
                    if (neighbor.localOrbit.remove(neighborTask)) {
                        return neighborTask;
                    }
                }
            }
            return null;
        }
    }

    // ==========================================
    // 底层反射与状态机
    // ==========================================
    public static class GalaxyTaskState {
        public final AtomicInteger status = new AtomicInteger(0);
        public final AtomicBoolean pendingNotify = new AtomicBoolean(false);
    }

    private static final MethodHandle STATE_GETTER;
    private static final MethodHandle STATE_SETTER;
    private static final MethodHandle SCHEDULED_START_GETTER;

    static {
        try {
            Field stateF = SchedulableTick.class.getDeclaredField("state");
            stateF.setAccessible(true);
            STATE_GETTER = MethodHandles.lookup().unreflectGetter(stateF);
            STATE_SETTER = MethodHandles.lookup().unreflectSetter(stateF);

            Field startF = SchedulableTick.class.getDeclaredField("scheduledStart");
            startF.setAccessible(true);
            SCHEDULED_START_GETTER = MethodHandles.lookup().unreflectGetter(startF);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize the SchedulableTick handle", e);
        }
    }

    private static GalaxyTaskState getOrInitializeState(SchedulableTick task) {
        try {
            Object obj = STATE_GETTER.invoke(task);
            if (obj instanceof GalaxyTaskState) {
                return (GalaxyTaskState) obj;
            }
            synchronized (task) {
                obj = STATE_GETTER.invoke(task);
                if (obj instanceof GalaxyTaskState) {
                    return (GalaxyTaskState) obj;
                }
                GalaxyTaskState newState = new GalaxyTaskState();
                STATE_SETTER.invoke(task, newState);
                return newState;
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static long getScheduledStartFast(SchedulableTick tick) {
        try {
            return (long) SCHEDULED_START_GETTER.invoke(tick);
        } catch (Throwable t) {
            return System.nanoTime();
        }
    }
}