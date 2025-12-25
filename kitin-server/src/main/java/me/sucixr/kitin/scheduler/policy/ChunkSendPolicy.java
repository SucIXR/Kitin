package me.sucixr.kitin.scheduler.policy;

import me.sucixr.kitin.scheduler.probe.NetworkConditionProbe;
import net.minecraft.server.level.ServerPlayer;

public final class ChunkSendPolicy {

    private static final ChunkSendPolicy INSTANCE = new ChunkSendPolicy();
    public static ChunkSendPolicy getInstance() {
        return INSTANCE;
    }

    /**
     * 根据网络状态，对 chunk send rate 做上限裁剪
     */
    public double limitSendRate(ServerPlayer player, double baseRate) {
        NetworkConditionProbe.NetworkTier tier =
                NetworkConditionProbe.getInstance().sample(player);

        return switch (tier) {
            case CRITICAL -> Math.min(baseRate, 2.0);
            case POOR     -> Math.min(baseRate, 5.0);
            case FAIR     -> Math.min(baseRate, 10.0);
            case GOOD     -> baseRate;
        };
    }
}
