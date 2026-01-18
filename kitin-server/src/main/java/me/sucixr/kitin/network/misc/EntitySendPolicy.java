package me.sucixr.kitin.network.misc;

import net.minecraft.server.level.ServerPlayer;

public final class EntitySendPolicy {

    private static final EntitySendPolicy INSTANCE = new EntitySendPolicy();
    public static EntitySendPolicy getInstance() { return INSTANCE; }

    public double adjustTrackingRange(ServerPlayer player, double baseRange) {
        NetworkConditionProbe.NetworkTier tier =
                NetworkConditionProbe.getInstance().sample(player);

        return switch (tier) {
            case GOOD -> baseRange;
            case FAIR -> baseRange * 0.85;
            case POOR -> baseRange * 0.6;
            case CRITICAL -> baseRange * 0.35;
        };
    }
}