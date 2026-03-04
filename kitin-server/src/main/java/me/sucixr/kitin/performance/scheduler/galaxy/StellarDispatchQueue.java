package me.sucixr.kitin.performance.scheduler.galaxy;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * Kitin Galaxy Scheduler - 纯净版统一发包队列
 * 采用单轨 FIFO 保证 Minecraft 原版严格时序，同时采集引力数据支撑宏观调度。
 */
public final class StellarDispatchQueue<T> {

    private final ConcurrentLinkedQueue<T> unifiedOrbit;
    private final LongAdder totalMass = new LongAdder();

    public StellarDispatchQueue() {
        this.unifiedOrbit = new ConcurrentLinkedQueue<>();
    }

    public void offer(T action, StellarOrbit orbit, io.netty.channel.EventLoop targetEventLoop) {
        // 采集质量并上传给观测站
        int packetMass = (orbit == StellarOrbit.GAS_GIANT_ORBIT) ? 10 : 1;
        GalaxyObservatory.recordTraffic(Thread.currentThread(), targetEventLoop, packetMass);

        this.unifiedOrbit.offer(action);
        this.totalMass.increment();
    }

    public T pollAny() {
        T task = this.unifiedOrbit.poll();
        if (task != null) {
            this.totalMass.decrement();
        }
        return task;
    }

    public boolean isEmpty() { return this.getTotalMass() <= 0; }
    public boolean add(T action) { this.offer(action, StellarOrbit.ASTEROID_ORBIT, null); return true; }
    public boolean addAll(java.util.Collection<? extends T> actions) { for (T action : actions) this.add(action); return true; }
    public T poll() { return this.pollAny(); }
    public T peek() { return this.unifiedOrbit.peek(); }
    public void resetBudget() {}
    public long getTotalMass() { return this.totalMass.sum(); }

    public T pollIf(java.util.function.Predicate<T> predicate) {
        T task = this.unifiedOrbit.peek();
        if (task != null && predicate.test(task)) {
            this.unifiedOrbit.poll();
            this.totalMass.decrement();
            return task;
        }
        return null;
    }
}