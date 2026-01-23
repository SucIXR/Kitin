package me.sucixr.kitin.network.misc;

import net.minecraft.server.level.ServerPlayer;

public final class ChunkLoadPolicy {

    private static final ChunkLoadPolicy INSTANCE = new ChunkLoadPolicy();
    public static ChunkLoadPolicy getInstance() {
        return INSTANCE;
    }

    /**
     * 是否跳过这次 chunk loader update
     */
    public boolean shouldSkipChunkUpdate(ServerPlayer player) {
        // 如果玩家视距小于 7，缓冲区太小，强制不跳过更新，防止跑出边界
        if (player.getBukkitEntity().getViewDistance() < 7) {
            return false;
        }

        PlayerMovementProbe.State state =
                PlayerMovementProbe.getInstance().sample(player);

        // 小范围晃悠 + 有阻力 → 跳过
        return state.resistance > 0;
    }
}