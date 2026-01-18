package me.sucixr.kitin.network.misc;

import net.minecraft.server.level.ServerPlayer;

public final class ChunkLoadController {

    public static boolean shouldSkipUpdate(ServerPlayer player) {
        return ChunkLoadPolicy.getInstance().shouldSkipChunkUpdate(player);
    }

}
