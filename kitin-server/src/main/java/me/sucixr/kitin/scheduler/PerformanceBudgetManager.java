package me.sucixr.kitin.scheduler;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class PerformanceBudgetManager {
    // --- 添加单例模式 ---
    private static final PerformanceBudgetManager INSTANCE = new PerformanceBudgetManager();
    public static PerformanceBudgetManager getInstance() {
        return INSTANCE;
    }
    // ------------------

    private static final double SCALE = 16.0; // 这里的缩放比例决定了距离衰减的快慢
    private long tickStartTime;
    private double currentStressLevel = 1.0; // 服务器压力系数 (1.0 = 轻松, 0.0 = 爆炸)

    public void onTickStart(long currentMspt) {
        this.tickStartTime = System.nanoTime();
        // 根据上一刻的 MSPT 动态调整压力系数 (EMA 算法)
        double targetStress = 1.0 - (Math.max(0, currentMspt - 35.0) / 15.0);
        this.currentStressLevel = Math.max(0.05, targetStress);
    }

    public boolean shouldProcessAI(Entity entity) {
        ServerPlayer player = (ServerPlayer) entity.level().getNearestPlayer(entity, 128);
        if (player == null) return false;

        // 1. 计算距离权重 (参考 Canvas 算法)
        double distance = entity.distanceTo(player);
        double scaled = distance / SCALE;
        double distanceProb = 1.0 / (1.0 + (scaled * scaled));

        // 2. 计算视野权重
        Vec3 lookVec = player.getLookAngle();
        Vec3 toEntityVec = entity.position().subtract(player.position()).normalize();
        double dot = lookVec.dot(toEntityVec);
        // 视野外(dot < 0)概率减半，视野正中心概率增加
        double viewMultiplier = dot > 0.5 ? 1.0 : (dot > 0 ? 0.5 : 0.1);

        // 3. 最终概率 = 距离概率 * 视野权重 * 服务器压力系数
        double finalProbability = distanceProb * viewMultiplier * currentStressLevel;

        // 特殊保底：8格内强制 Tick，或者随机命中
        if (distance < 8.0) return true;

        return entity.getRandom().nextDouble() < finalProbability;
    }
}