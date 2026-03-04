package me.sucixr.kitin.performance.scheduler.galaxy;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kitin Galaxy Scheduler - 热力传感器
 * 精准测量各恒星(Region线程)的真实 CPU 负载率。
 */
public class ThermalSensor {
    private static final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    private static final ConcurrentHashMap<Thread, Long> lastCpuTime = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Thread, Long> lastWallTime = new ConcurrentHashMap<>();

    public static double getTemperature(Thread t) {
        if (!threadBean.isThreadCpuTimeSupported() || t == null) return 0.0;

        long currentCpuTime = threadBean.getThreadCpuTime(t.getId());
        long currentWallTime = System.nanoTime();

        long prevCpuTime = lastCpuTime.getOrDefault(t, currentCpuTime);
        long prevWallTime = lastWallTime.getOrDefault(t, currentWallTime);

        lastCpuTime.put(t, currentCpuTime);
        lastWallTime.put(t, currentWallTime);

        long wallDelta = currentWallTime - prevWallTime;
        if (wallDelta <= 0) return 0.0;

        double load = (double) (currentCpuTime - prevCpuTime) / wallDelta;
        return Math.min(Math.max(load, 0.0), 1.0);
    }
}