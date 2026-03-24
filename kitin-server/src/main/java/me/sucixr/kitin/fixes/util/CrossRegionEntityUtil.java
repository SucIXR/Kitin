package me.sucixr.kitin.fixes.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class CrossRegionEntityUtil {

    public static Player getGlobalPlayer(MinecraftServer server, UUID ownerUUID) {
        if (ownerUUID == null || server == null) return null;
        return server.getPlayerList().getPlayer(ownerUUID);
    }

    public static boolean isPlayerCrossRegion(Player player) {
        if (player == null) return false;
        return !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player);
    }

    public static void wakeUpAndDiscard(Entity targetEntity) {
        if (targetEntity == null || targetEntity.isRemoved()) return;
        if (targetEntity.level() instanceof ServerLevel sl) {
            sl.resetEmptyTime();
            ServerPlayer.placeEnderPearlTicket(sl, targetEntity.chunkPosition());
        }
    }
}