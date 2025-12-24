package me.sucixr.kitin.scheduler;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class PerformanceBudgetManager {
    private static final PerformanceBudgetManager INSTANCE = new PerformanceBudgetManager();
    public static PerformanceBudgetManager getInstance() { return INSTANCE; }

    private static final double SCALE = 32.0;
    private double currentStressLevel = 1.0;

    public double getCurrentStressLevel() {
        return this.currentStressLevel;
    }

    public void onTickStart(long currentMspt) {
        double targetStress = 1.0 - (Math.max(0, currentMspt - 20) / 50);
        this.currentStressLevel = Math.max(0.05, targetStress);
    }

    public boolean shouldProcessAI(Entity entity) {
        //传送门方块内生物强制保留活性
        if (entity.level().getBlockState(entity.blockPosition()).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
            return true;
        }
        double stress = this.currentStressLevel;

        //正在攻击或逃跑的生物不降频
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            if (mob.getTarget() != null || mob.isSprinting()) return true;
        }

        //针对特定生物的激进预处理
        if (stress < 0.7) {
            if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) {
                if (entity.level().random.nextDouble() > (stress * stress)) return false;
            }
        }

        //寻找玩家
        ServerPlayer player = (ServerPlayer) entity.level().getNearestPlayer(entity, 128);
        if (player == null) return false;

        double distance = entity.distanceTo(player);

        //远处的直接放弃
        if (distance > 48.0 && stress < 0.7) {
            return entity.tickCount % 10 == 0;
        }

        //大保底，不要了，放入视距权重
        //double minSafeDistance = (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) ? 2.0 : 8.0;
        //if (distance < minSafeDistance) return true;

        //视距权重
        double extra = distance - 8.0;
        double scaled = Math.min(1.0,extra / SCALE);
        double distanceProb = 1.0 - (scaled * scaled);

        Vec3 lookVec = player.getLookAngle();
        Vec3 toEntityVec = entity.position().subtract(player.position()).normalize();
        double dot = lookVec.dot(toEntityVec);
        //视野权重
        double viewMultiplier = dot > 0.3 ? 1.0 : (dot > 0 ? 0.5 : 0.1);

        return entity.getRandom().nextDouble() < (distanceProb * viewMultiplier * stress);
    }
}