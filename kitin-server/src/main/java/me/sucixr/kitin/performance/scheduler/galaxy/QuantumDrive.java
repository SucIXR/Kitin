package me.sucixr.kitin.performance.scheduler.galaxy;

import ca.spottedleaf.concurrentutil.numa.OSNuma;
import io.netty.channel.EventLoop;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kitin Galaxy Scheduler - 量子驱动器
 * 负责通过 JNA 调用操作系统底层 API，修改 Netty 线程的 CPU 亲和性。
 */
public class QuantumDrive {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ConcurrentHashMap<EventLoop, Long> cooldownMatrix = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 30 * 1000L;

    public static void initiateQuantumLeap(EventLoop nettyLoop, String targetRegionName, long gravity) {
        long now = System.currentTimeMillis();
        long lastLeap = cooldownMatrix.getOrDefault(nettyLoop, 0L);

        if (now - lastLeap < COOLDOWN_MS) {
            return;
        }

        cooldownMatrix.put(nettyLoop, now);

        if (GalaxyObservatory.DEBUG_MODE) {
            LOGGER.info("[Kitin Quantum Drive] Initiating thread migration: {} -> {} (Force: {})",
                    getShortName(nettyLoop), targetRegionName, gravity);
        }

        nettyLoop.execute(() -> {
            try {
                performAffinityBinding(targetRegionName);
            } catch (Throwable t) {
                LOGGER.error("[Kitin Quantum Drive] Failed to execute affinity binding task", t);
            }
        });
    }

    private static void performAffinityBinding(String targetRegionName) {
        int targetRegionId = -1;
        Pattern pattern = Pattern.compile("#(\\d+)");
        Matcher matcher = pattern.matcher(targetRegionName);
        if (matcher.find()) {
            try {
                targetRegionId = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        if (targetRegionId == -1) {
            return;
        }

        OSNuma numa = OSNuma.getNativeInstance();

        if (!numa.isAvailable() || numa.getTotalNumaNodes() <= 1) {
            return; // 虚拟机或单节点设备，静默退化
        }

        int targetNodeIndex = targetRegionId % numa.getTotalNumaNodes();

        try {
            Method bindMethod = null;
            for (Method m : numa.getClass().getMethods()) {
                String name = m.getName().toLowerCase();
                if ((name.contains("node") || name.contains("affinity")) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
                    bindMethod = m;
                    break;
                }
            }

            if (bindMethod != null) {
                bindMethod.invoke(numa, targetNodeIndex);
                if (GalaxyObservatory.DEBUG_MODE) {
                    LOGGER.info("[Kitin Quantum Drive] Successfully pinned Netty thread [{}] to NUMA Node [{}]",
                            Thread.currentThread().getName(), targetNodeIndex);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Kitin Quantum Drive] Exception occurred during NUMA affinity binding", e);
        }
    }

    public static String getShortName(EventLoop loop) {
        return "NettyLoop-" + Integer.toHexString(loop.hashCode());
    }
}