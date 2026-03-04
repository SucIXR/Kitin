package me.sucixr.kitin.performance.scheduler.galaxy;

import io.netty.channel.EventLoop;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Kitin Galaxy Scheduler - 天文观测站
 * 负责收集网路层(Netty)与计算层(Region)之间的流量数据，并结合热力传感器(CPU温度)，
 * 使用 Lennard-Jones 势能方程下达跨界绑核(跃迁)指令。
 */
public class GalaxyObservatory {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Debug 模式开关，用于控制是否输出详细的物理引擎计算报告
    public static boolean DEBUG_MODE = false;

    // [目标 Netty 线程] -> [源 Region 线程名 -> 流量]
    private static final ConcurrentHashMap<EventLoop, ConcurrentHashMap<String, LongAdder>> universeMatrix = new ConcurrentHashMap<>();
    // [源 Region 线程名] -> [Thread 对象]
    private static final ConcurrentHashMap<String, Thread> starRegistry = new ConcurrentHashMap<>();
    // [Netty 线程] -> [当前所在的 Region 线程名]
    private static final ConcurrentHashMap<EventLoop, String> currentOrbitMatrix = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService physicsEngine = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread t = new Thread(runnable, "Kitin-Physics-Engine");
        t.setDaemon(true);
        return t;
    });

    public static void recordTraffic(Thread sourceThread, EventLoop targetNettyLoop, int mass) {
        if (targetNettyLoop == null) return;
        String sourceName = sourceThread.getName();

        starRegistry.putIfAbsent(sourceName, sourceThread);

        universeMatrix
                .computeIfAbsent(targetNettyLoop, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(sourceName, k -> new LongAdder())
                .add(mass);
    }

    public static void ignitePhysicsEngine() {
        //LOGGER.info("[Kitin Galaxy Scheduler] Thermodynamics-based netty balancing engine started");

        physicsEngine.scheduleAtFixedRate(() -> {
            try {
                observeAndCalculateNetForce();
            } catch (Throwable e) {
                LOGGER.error("[Kitin Galaxy Scheduler] Physics engine calculation encountered an exception", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static void observeAndCalculateNetForce() {
        if (universeMatrix.isEmpty()) return;

        boolean hasActivity = false;
        StringBuilder report = new StringBuilder();
        if (DEBUG_MODE) {
            report.append("\n[Kitin Galaxy Scheduler] Netty & Region Affinity Report (5s period)\n");
            report.append("--------------------------------------------------\n");
        }

        // 1. 测量所有恒星表面温度
        Map<String, Double> starTemperatures = new HashMap<>();
        for (Map.Entry<String, Thread> entry : starRegistry.entrySet()) {
            starTemperatures.put(entry.getKey(), ThermalSensor.getTemperature(entry.getValue()));
        }

        // 2. 计算合力与下达跃迁指令
        for (Map.Entry<EventLoop, ConcurrentHashMap<String, LongAdder>> planetEntry : universeMatrix.entrySet()) {
            EventLoop nettyLoop = planetEntry.getKey();
            ConcurrentHashMap<String, LongAdder> gravitySources = planetEntry.getValue();

            String dominantRegion = null;
            long maxNetForce = 0;
            long totalRawGravity = 0;

            String currentOrbit = currentOrbitMatrix.get(nettyLoop);
            long localRawGravity = 0;
            double localTemperature = 0.0;

            for (Map.Entry<String, LongAdder> sourceEntry : gravitySources.entrySet()) {
                String starName = sourceEntry.getKey();
                long rawPull = sourceEntry.getValue().sumThenReset();

                if (rawPull > 0) {
                    hasActivity = true;
                    totalRawGravity += rawPull;

                    double temp = starTemperatures.getOrDefault(starName, 0.0);
                    // 热辐射衰减方程
                    long netForce = (long)(rawPull * (1.0 - Math.pow(temp, 4)));

                    if (netForce > maxNetForce) {
                        maxNetForce = netForce;
                        dominantRegion = starName;
                    }

                    if (starName.equals(currentOrbit)) {
                        localRawGravity = rawPull;
                        localTemperature = temp;
                    }
                }
            }

            if (maxNetForce > 0) {
                // 动态宇宙惯性法则：过热则击穿惯性阻尼
                double inertiaMultiplier = 1.5;
                if (currentOrbit != null && localTemperature > 0.85) {
                    inertiaMultiplier = 0.5;
                }

                if (DEBUG_MODE) {
                    double domTemp = starTemperatures.getOrDefault(dominantRegion, 0.0);
                    String heatWarning = domTemp > 0.80 ? " [WARNING: OVERLOADED]" : "";
                    report.append(String.format("NettyLoop [%s] | Total Pull: %d | Dominant Region: [%s] (Net Force: %d, CPU Load: %.1f%%%s) | Current Orbit: [%s]\n",
                            QuantumDrive.getShortName(nettyLoop), totalRawGravity, dominantRegion, maxNetForce, domTemp * 100, heatWarning, currentOrbit));

                    if (currentOrbit != null && localTemperature > 0.85) {
                        report.append(String.format("   -> Local region overloaded (%.1f%% CPU). Inertial dampening reduced.\n", localTemperature * 100));
                    }
                }

                // 跃迁条件判定
                if (maxNetForce > 2000 && !dominantRegion.equals(currentOrbit)) {
                    long requiredForce = (long)(localRawGravity * inertiaMultiplier);
                    if (maxNetForce > requiredForce) {
                        if (DEBUG_MODE) report.append(String.format("   -> Affinity threshold reached (Net: %d > Required: %d). Initiating NUMA migration.\n", maxNetForce, requiredForce));

                        currentOrbitMatrix.put(nettyLoop, dominantRegion);
                        QuantumDrive.initiateQuantumLeap(nettyLoop, dominantRegion, maxNetForce);
                    } else {
                        if (DEBUG_MODE) report.append(String.format("   -> Migration rejected (Net force insufficient).\n"));
                    }
                }
            }
        }

        if (DEBUG_MODE && hasActivity) {
            report.append("==========================================");
            LOGGER.info(report.toString());
        }
    }
}