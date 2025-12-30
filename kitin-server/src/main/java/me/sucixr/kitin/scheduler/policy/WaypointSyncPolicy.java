package me.sucixr.kitin.scheduler.policy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.waypoints.WaypointTransmitter;

public final class WaypointSyncPolicy {

    private static final WaypointSyncPolicy INSTANCE = new WaypointSyncPolicy();
    public static WaypointSyncPolicy getInstance() { return INSTANCE; }

    private static final double SCALE = 512 * 512;

    public boolean shouldDisconnect(ServerPlayer receiver, WaypointTransmitter tx,
                                    boolean featureEnabled, double maxRange) {

        if (!featureEnabled || maxRange <= 0.0) return true;

        if (tx instanceof Entity e) {
            if (e.level() != receiver.level()) return true;

            final double dx = receiver.getX() - e.getX();
            final double dz = receiver.getZ() - e.getZ();

            return (dx * dx + dz * dz) > (maxRange * maxRange);
        }

        return false;
    }

    public boolean shouldSkipUpdate(ServerPlayer receiver, WaypointTransmitter tx) {
        if (!(tx instanceof Entity e)) return false;

        final double dx = receiver.getX() - e.getX();
        final double dz = receiver.getZ() - e.getZ();
        final double distSq = dx*dx + dz*dz;
        int interval = 5 + (int)(distSq / SCALE);
        long currentTick = receiver.level().getGameTime();
        if(interval>100){
            interval = 100;
        }
        return (currentTick + e.getId()) % interval != 0;
    }
}