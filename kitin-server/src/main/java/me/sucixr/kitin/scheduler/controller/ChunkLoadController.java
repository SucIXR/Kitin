package me.sucixr.kitin.scheduler.controller;

import me.sucixr.kitin.scheduler.policy.ChunkLoadPolicy;
import net.minecraft.server.level.ServerPlayer;

public final class ChunkLoadController {

    public static boolean shouldSkipUpdate(ServerPlayer player) {
        return ChunkLoadPolicy.getInstance().shouldSkipChunkUpdate(player);
    }

}
