package me.sucixr.kitin.other.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
//使用方法:net.minecraft.server.level.ServerEntity.java,查找packet = ClientboundEntityPositionSyncPacket.of(this.entity);这段后面:
//me.sucixr.kitin.debug.EntityPosSyncDebug.record(this.level, this.entity); // Kitin debug: record position sync spam source
public final class EntityPosSyncDebug {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final long PRINT_INTERVAL_NANOS = 1_000_000_000L; // 1s
    private static volatile long lastPrint = System.nanoTime();

    // key 用 String，避免依赖 ResourceLocation/Identifier 具体类名
    private static final ConcurrentHashMap<String, LongAdder> COUNTS = new ConcurrentHashMap<>();

    private EntityPosSyncDebug() {}

    public static void record(ServerLevel level, Entity entity) {
        // 这行是关键：不写 ResourceLocation/Identifier 类型，直接 toString()
        final String typeKey = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));

        COUNTS.computeIfAbsent(typeKey, k -> new LongAdder()).increment();

        final long now = System.nanoTime();
        if (now - lastPrint < PRINT_INTERVAL_NANOS) {
            return;
        }
        lastPrint = now;

        // 打印 Top 10，然后清空（方便你定位“主要元凶”）
        try {
            LOGGER.info("[Kitin] Top EntityPositionSync sources (last ~1s):");
            COUNTS.entrySet().stream()
                    .sorted(Comparator.comparingLong((Map.Entry<String, LongAdder> e) -> e.getValue().sum()).reversed())
                    .limit(10)
                    .forEach(e -> level.getServer().LOGGER.info("  " + e.getKey() + " = " + e.getValue().sum()));
        } catch (Throwable ignore) {
            // 只做调试统计，任何异常都不要影响主逻辑
        } finally {
            COUNTS.clear();
        }
    }
}