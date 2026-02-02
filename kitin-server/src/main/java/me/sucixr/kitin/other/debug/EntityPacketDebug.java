package me.sucixr.kitin.other.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class EntityPacketDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long PRINT_INTERVAL_NANOS = 2_000_000_000L; // 改为2秒打印一次，刷屏慢点
    private static volatile long lastPrint = System.nanoTime();

    // Key格式: "实体类型:包类型" (例如 "minecraft:experience_orb:Motion")
    private static final ConcurrentHashMap<String, LongAdder> COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> BYTES = new ConcurrentHashMap<>();

    private EntityPacketDebug() {}

    // [新增] 矿车日志限流计数器
//    private static final java.util.concurrent.atomic.AtomicInteger MINECART_SAMPLE_COUNT = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * @param packetType 包的简短名称，如 "Motion", "Pos", "Sync"
     * @param estimatedSize 预估包大小
     */
    public static void record(Entity entity, String packetType, int estimatedSize) {
        if (true) return;//临时开关 true为关!


//        // [Kitin Temp] 临时开关：只开启矿车的详细数据监控
//        // 监控 Pos (位置) 和 Motion (速度) 包
//        if (entity instanceof AbstractMinecart) {
//            if (packetType.contains("Pos") || packetType.contains("Motion")) {
//                // [修改] 核心限流逻辑：只有前 10 次允许打印
//                if (MINECART_SAMPLE_COUNT.incrementAndGet() <= 10) {
//                    double dx = entity.getX() - entity.xo;
//                    double dy = entity.getY() - entity.yo;
//                    double dz = entity.getZ() - entity.zo;
//                    net.minecraft.world.phys.Vec3 motion = entity.getDeltaMovement();
//
//                    LOGGER.info("[Minecart Debug] Type: {} | Size: {}B", packetType, estimatedSize);
//                    LOGGER.info(String.format("   -> Pos: %.5f, %.5f, %.5f", entity.getX(), entity.getY(), entity.getZ()));
//                    LOGGER.info(String.format("   -> Delta: %.5f, %.5f, %.5f", dx, dy, dz));
//                    LOGGER.info(String.format("   -> Motion: %.5f, %.5f, %.5f", motion.x, motion.y, motion.z));
//                    LOGGER.info("--------------------------------------------------");
//                }
//            }
//        }

        if (entity == null) return;

        // 组合键：实体名 + 包名
        String entityName = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        String key = entityName + " [" + packetType + "]";

        COUNTS.computeIfAbsent(key, k -> new LongAdder()).increment();
        BYTES.computeIfAbsent(key, k -> new LongAdder()).add(estimatedSize);

        final long now = System.nanoTime();
        if (now - lastPrint < PRINT_INTERVAL_NANOS) return;

        synchronized (EntityPacketDebug.class) {
            if (now - lastPrint < PRINT_INTERVAL_NANOS) return;
            lastPrint = now;

            try {
                long totalBytes = BYTES.values().stream().mapToLong(LongAdder::sum).sum();
                double rate = (totalBytes / 1024.0) / 2.0; // 除以2秒

                LOGGER.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                // 使用 String.format 来处理两位小数，注意这里要用 %.2f 而不是 {:.2f}
                LOGGER.info(String.format("[Kitin Debug] Real-time Traffic: %.2f KB/s", rate));
                LOGGER.info("Top 10 Bandwidth Consumers (Entity + PacketType):");

                COUNTS.entrySet().stream()
                        .map(e -> {
                            String k = e.getKey();
                            long count = e.getValue().sum();
                            long size = BYTES.getOrDefault(k, new LongAdder()).sum();
                            return new Stat(k, count, size);
                        })
                        .sorted(Comparator.comparingLong(Stat::bytes).reversed())
                        .limit(10)
                        .forEach(s -> LOGGER.info(String.format("  %-40s | %5d pkts | %s",
                                s.key, s.count, formatSize(s.bytes))));
                LOGGER.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            } catch (Throwable ignore) {
            } finally {
                COUNTS.clear();
                BYTES.clear();

//                MINECART_SAMPLE_COUNT.set(0);
            }
        }
    }

    private static String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z*10)), " KMGTPE".charAt(z));
    }

    private record Stat(String key, long count, long bytes) {}
}