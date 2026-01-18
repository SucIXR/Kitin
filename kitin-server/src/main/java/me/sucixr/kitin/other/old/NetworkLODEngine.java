package me.sucixr.kitin.other.old;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class NetworkLODEngine {
    private static final NetworkLODEngine INSTANCE = new NetworkLODEngine();
    public static NetworkLODEngine getInstance() { return INSTANCE; }

    /**
     * 场景一：区块加载控制
     * 结合了“运动阻力”和“网络卡顿”双重判定
     */
    public int getDynamicChunkQuota(ServerPlayer player) {
        // 1. 获取运动阻力判定
        boolean isHighSpeed = LazyChunkSyncController.getInstance().shouldSkipUpdate(player);

        // 2. 获取网络状况
        int ping = player.connection.latency();

        // 逻辑门：如果是高速跑图，强制将区块限额压到最低（例如 1），不管网络好坏
        if (isHighSpeed) return 1;

        // 如果不是高速跑图，则根据网络状况动态分配限额
        if (ping > 200) return 2;
        if (ping > 500) return 0; // 网络极其糟糕，暂停区块发送

        return 5; // 正常网络且低速移动，全速加载
    }

    /**
     * 场景二：实体追踪控制
     * 仅受网络状况和实体密度影响（不被运动阻力拦截）
     */
    public boolean shouldTrackEntity(ServerPlayer player, Entity entity) {
        int ping = player.connection.latency();
        double distSqr = player.distanceToSqr(entity);

        // 动态范围计算：根据网络实时调整
        int range;
        if (ping > 300) {
            range = 12; // 缩减到很小
        } else if (ping > 150) {
            range = 24;
        } else {
            range = 48; // 正常追踪
        }

        return distSqr <= (range * range);
    }
}