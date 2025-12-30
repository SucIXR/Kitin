package me.sucixr.kitin.scheduler.old;

import me.sucixr.kitin.scheduler.probe.NetworkConditionProbe;
import me.sucixr.kitin.scheduler.probe.PlayerSnapshot;
import net.minecraft.server.level.ServerPlayer;

public final class WaypointSyncPolicy_2 {

    private static final WaypointSyncPolicy_2 INSTANCE = new WaypointSyncPolicy_2();
    public static WaypointSyncPolicy_2 getInstance() { return INSTANCE; }

    /**
     * 距离越远、网络越差 -> 更新越慢（省带宽）
     * 不做“距离上限”，只做“更新频率”控制。
     */
    public int updateIntervalTicks(ServerPlayer receiver, double distSq) {
        // 距离分档（你可以按自己口味调）
        int base;
        if (distSq <= (32.0 * 32.0)) base = 1;          // 很近：每tick更新
        else if (distSq <= (128.0 * 128.0)) base = 2;   // 中近：2tick
        else if (distSq <= (512.0 * 512.0)) base = 5;   // 中远：5tick
        else base = 10;                                 // 很远：10tick

        // 网络分档：越差越慢
        final NetworkConditionProbe.NetworkTier tier =
                NetworkConditionProbe.getInstance().sample(receiver);

        return switch (tier) {
            case GOOD -> base;
            case FAIR -> Math.min(20, base * 2);
            case POOR -> Math.min(40, base * 4);
            case CRITICAL -> 60; // 极端：每 3 秒一次
        };
    }

    public boolean shouldConsiderTarget(ServerPlayer receiver, PlayerSnapshot target) {
        // 只要同维度 + 对方确实在“发射 waypoint”
        if (!receiver.level().dimension().equals(target.dimension())) return false;
        return target.transmittingWaypoints();
    }

}
