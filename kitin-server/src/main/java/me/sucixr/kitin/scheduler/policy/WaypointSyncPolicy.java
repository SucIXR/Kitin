package me.sucixr.kitin.scheduler.policy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.waypoints.WaypointTransmitter;

public final class WaypointSyncPolicy {

    private static final WaypointSyncPolicy INSTANCE = new WaypointSyncPolicy();
    public static WaypointSyncPolicy getInstance() { return INSTANCE; }

    private static final double SCALE = 332.0;
    //private static final double SCALE_SQ = SCALE * SCALE;
    private static final int BASE_INTERVAL = 5;
    private static final int MAX_INTERVAL = 100;

    private static final double ALPHA = 0.9604339;
    private static final double BETA = 0.3978247;

    public int getUpdateIntervalOrDisconnect(ServerPlayer receiver, WaypointTransmitter tx,
                                             boolean featureEnabled, double maxRange) {

        if (!featureEnabled || maxRange <= 0.0) return -1; // -1 代表断开

        if (tx instanceof Entity e) {
            if (e.level() != receiver.level()) return -1; // 跨世界断开

            final double dx = receiver.getX() - e.getX();
            final double dz = receiver.getZ() - e.getZ();
            final double distSq = dx * dx + dz * dz;

            if (distSq > (maxRange * maxRange)) {
                return -1; // 超出最大范围，断开
            }

            double absX = Math.abs(dx);
            double absZ = Math.abs(dz);
            double max = Math.max(absX, absZ);
            double min = Math.min(absX, absZ);
            double approxDist = (ALPHA * max) + (BETA * min);

            if (approxDist < SCALE) {
                return BASE_INTERVAL;
            }

            double ratio = approxDist / SCALE;
            int dynamicInterval = (int) (BASE_INTERVAL * ratio);

            return Math.min(dynamicInterval, MAX_INTERVAL);
        }

        // 非实体类型的路点，默认 20 Tick
        return 20;
    }
}