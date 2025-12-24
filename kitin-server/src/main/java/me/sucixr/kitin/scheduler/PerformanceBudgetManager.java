package me.sucixr.kitin.scheduler;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class PerformanceBudgetManager {
    private static final PerformanceBudgetManager INSTANCE = new PerformanceBudgetManager();
    public static PerformanceBudgetManager getInstance() { return INSTANCE; }

    private static final double SCALE = 16.0;
    private double currentStressLevel = 1.0;

    public double getCurrentStressLevel() {
        return this.currentStressLevel;
    }

    public void onTickStart(long currentMspt) {
        // 关键点：您发现的 5ms 阈值，这会让调度器非常灵敏地压制负载
        double targetStress = 1.0 - (Math.max(0, currentMspt - 5.0) / 15.0);
        this.currentStressLevel = Math.max(0.05, targetStress);
    }

    public boolean shouldProcessAI(Entity entity) {
        double stress = this.currentStressLevel;

        // 1. 优先级拦截：正在攻击或逃跑的生物不降频 (保留此处的优良逻辑)
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            if (mob.getTarget() != null || mob.isSprinting()) return true;
        }

        // 2. 针对特定生物的激进预处理 (回归旧版平方策略)
        if (stress < 0.9) {
            if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) {
                if (entity.level().random.nextDouble() > (stress * stress)) return false;
            }
        }

        // 3. 寻找玩家 (回归原生 API，Paper 对此有良好的区块缓存优化)
        ServerPlayer player = (ServerPlayer) entity.level().getNearestPlayer(entity, 128);
        if (player == null) return false;

        double distance = entity.distanceTo(player);

        // 4. 解决远处逻辑：引入“硬剪枝”
        // 如果生物在 64 格外，且服务器压力大，强制每 10 tick 才运行一次，不走概率
        if (distance > 64.0 && stress < 0.7) {
            return entity.tickCount % 10 == 0;
        }

        // 5. 核心：调大保底距离，解决村民卡顿
        // 村民保底 8 格，猪灵保底 2 格，确保玩家身边的实体不卡
        //double minSafeDistance = (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) ? 2.0 : 8.0;
        //if (distance < minSafeDistance) return true;

        // 6. 极简概率计算 (弃用 sqrt，回归纯乘法)
        double scaled = distance / SCALE;
        double distanceProb = 1.0 / (1.0 + (scaled * scaled));

        Vec3 lookVec = player.getLookAngle();
        Vec3 toEntityVec = entity.position().subtract(player.position()).normalize();
        double dot = lookVec.dot(toEntityVec);

        // 视野权重：看的见 1.0，稍微偏离 0.5，背后 0.1
        double viewMultiplier = dot > 0.5 ? 1.0 : (dot > 0 ? 0.5 : 0.1);

        return entity.getRandom().nextDouble() < (distanceProb * viewMultiplier * stress);
    }
}