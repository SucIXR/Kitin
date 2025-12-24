package me.sucixr.kitin.scheduler;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class SocialBubbleEngine {
    private static final SocialBubbleEngine INSTANCE = new SocialBubbleEngine();
    public static SocialBubbleEngine getInstance() { return INSTANCE; }

    public boolean shouldProcessCollision(LivingEntity entity) {
        // 1. 传送门保底 (猪人塔必需)
        if (entity.level().getBlockState(entity.blockPosition()).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
            return true;
        }

        double stress = PerformanceBudgetManager.getInstance().getCurrentStressLevel();

        // 2. 只有压力大时才开启物理裁剪
        if (stress < 0.9) {
            ServerPlayer player = (ServerPlayer) entity.level().getNearestPlayer(entity, 32);
            if (player == null) return false;

            double distance = entity.distanceTo(player);
            if (distance < 1.5) return true; // 贴脸保底

            // 3. 极简视线判定
            Vec3 lookVec = player.getLookAngle();
            Vec3 relVec = entity.position().subtract(player.position());
            double dot = lookVec.dot(relVec.normalize());

            double physViewWeight = dot > 0.8 ? 0.5 : (dot > 0 ? 0.05 : 0.01);
            double finalProb = physViewWeight * stress;

            // 猪灵额外削减频率
            if (entity instanceof ZombifiedPiglin) finalProb *= 0.1;

            return entity.getRandom().nextDouble() < finalProb;
        }
        return true;
    }
}