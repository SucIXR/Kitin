package me.sucixr.kitin.scheduler.policy;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

public final class EntitySyncPolicy {

    private static final EntitySyncPolicy INSTANCE = new EntitySyncPolicy();
    public static EntitySyncPolicy getInstance() { return INSTANCE; }

    // 每隔多少 tick 才允许“硬碰撞强制 PositionSync”触发一次
    // 20 = 1 秒一次；你可以改 10/5 做 A/B
    private static final int HARD_COLLISION_SYNC_INTERVAL_TICKS = 20;

    /**
     * Paper/Moonrise 会在硬碰撞时把 teleportDelay 拉到 9999，导致每 tick 走 PositionSync。
     * 这里把它“限流”，避免 boat/minecart 在农场里刷爆带宽。
     *
     * @return 要写回 ServerEntity.teleportDelay 的值
     */
    public int adjustTeleportDelayForHardCollision(final Entity entity,
                                                   final int tickCount,
                                                   final int currentTeleportDelay) {
        // 只针对“刷包大户”：船/矿车
        final boolean isVehicle = (entity instanceof Boat) || (entity instanceof AbstractMinecart);
        if (!isVehicle) {
            // 其他实体保持原行为（不动上游逻辑）
            return 9999;
        }

        // 有玩家乘坐时，尽量别限流（否则玩家骑乘体验可能抖）
        if (!entity.getPassengers().isEmpty()) {
            return 9999;
        }

        // 3. [关键修复] 即便矿车不动，也发包的问题
        // 如果矿车几乎没动 (比如挤在农场里，或者停在终点)，就不要强制同步了！
        // 让它保持原有的 teleportDelay，这样它会自然计数到 400 (20秒) 才发一次包。
        // SqrLength < 0.0001 代表速度极其微小
        if (entity.getDeltaMovement().lengthSqr() < 0.0001) {
            return currentTeleportDelay; // <--- 重点：原样返回，不重置为0，也不设为9999
        }

        // 空载具：只允许每隔 N tick 才触发一次强制 PositionSync
        // 关键点：返回 401 让后面的逻辑满足 “teleportDelay > 400”
        if ((tickCount % HARD_COLLISION_SYNC_INTERVAL_TICKS) == 0) {
            return 401;
        }

        // 其它 tick：不要把 teleportDelay 拉高（否则又会走 PositionSync）
        // 直接压回 0（最稳）
        return 0;
    }
}
