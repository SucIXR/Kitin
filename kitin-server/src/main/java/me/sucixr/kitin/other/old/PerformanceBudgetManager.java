package me.sucixr.kitin.other.old;

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
    public double getCurrentStressLevel() {
        return this.currentStressLevel;
    }

    public void onTickStart(long currentMspt) {
        this.tickStartTime = System.nanoTime();
        // 根据上一刻的 MSPT 动态调整压力系数 (EMA 算法)
        double targetStress = 1.0 - (Math.max(0, currentMspt - 5.0) / 15.0); //临时修改成5!
        this.currentStressLevel = Math.max(0.05, targetStress);
    }

    public boolean shouldProcessAI(Entity entity) {
        // 1. 获取全局压力系数
        double stress = this.getCurrentStressLevel();

        // 2. 针对特定生物的激进预处理 (仅在压力大时触发)
        if (stress < 0.9) {
            // 检查是否是僵尸猪灵 (或者是其他你认为可以大规模降频的生物)
            if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) {
                // 猪灵专属激进算法：压力平方策略
                // 这样当 stress 为 0.5 时，猪灵只有 25% (0.5 * 0.5) 的概率运行
                // 而普通生物（如下方的村民）依然保持 50% 的基础概率
                if (entity.level().random.nextDouble() > (stress * stress)) {
                    return false;
                }
            }

            // 如果未来要加其他生物，只需在这里继续添加 else if
            // else if (entity instanceof Villager) { ... }
        }
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

        //保底距离
        double minSafeDistance = (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) ? 2.0 : 8.0;

        if (distance < minSafeDistance) {
            return true;
        }

        return entity.getRandom().nextDouble() < finalProbability;
    }
}