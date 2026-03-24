package me.sucixr.kitin.fixes.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Kitin 跨区域实体追踪与桥接工具
 * 专门用于解决 Folia 架构下，实体与主人（Owner）处于不同区域/维度时导致的
 * 找不到人、跨线程崩服、休眠区块幽灵实体等问题。
 */
public class CrossRegionEntityUtil {

    /**
     * 1. 【全局找人】无视维度和区域，强行从内存中抓取玩家实例
     */
    public static Player getGlobalPlayer(MinecraftServer server, UUID ownerUUID) {
        if (ownerUUID == null || server == null) return null;
        return server.getPlayerList().getPlayer(ownerUUID);
    }

    /**
     * 2. 【跨界判定】判断玩家是否已经脱离了当前实体的线程管辖
     * 返回 true 说明玩家去别的区/地狱了，绝对禁止读取其背包或复杂状态！
     */
    public static boolean isPlayerCrossRegion(Player player) {
        if (player == null) return false;
        return !ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(player);
    }

    /**
     * 3. 【跨界唤醒与处决】隔空唤醒实体所在的休眠区块，并强制其销毁。
     * 常用于“新实体替换旧实体”、“跨维度收回”等防套娃操作。
     */
    public static void wakeUpAndDiscard(Entity targetEntity) {
        if (targetEntity == null || targetEntity.isRemoved()) return;

        if (targetEntity.level() instanceof ServerLevel sl) {
            // 重置区块休眠时钟
            sl.resetEmptyTime();
            // 发放临时强加载 Ticket，唤醒区块
            ServerPlayer.placeEnderPearlTicket(sl, targetEntity.chunkPosition());
        }
        // 标记或直接强制销毁
        targetEntity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN);
    }
}