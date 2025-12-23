package me.sucixr.kitin.scheduler;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin; // 导入猪灵类
import java.util.List;

public class SocialBubbleEngine {

    private static final SocialBubbleEngine INSTANCE = new SocialBubbleEngine();
    public static SocialBubbleEngine getInstance() { return INSTANCE; }

    public boolean shouldProcessCollision(LivingEntity entity) {
        // 1. 传送门保底逻辑（保留，这对所有生物进入掉落井都重要）
        if (entity.level().getBlockState(entity.blockPosition()).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
            return true;
        }

        // 2. 【核心改动】删除对特定生物（如猪灵、村民）的判断

        // 3. 获取压力
        double stress = PerformanceBudgetManager.getInstance().getCurrentStressLevel();

        // 4. 压力下的通用裁剪逻辑
        if (stress < 0.9) {
            // 寻找最近玩家
            ServerPlayer player = (ServerPlayer) entity.level().getNearestPlayer(entity, 32);
            if (player == null) return false; // 附近没玩家，任何生物都不跑碰撞

            double distance = entity.distanceTo(player);

            // 贴脸保证物理 (1.5格)
            if (distance < 1.5) return true;

            // 视线逻辑（通用）
            Vec3 lookVec = player.getLookAngle();
            Vec3 relVec = entity.position().subtract(player.position());
            double relLen = relVec.length();

            if (relLen > 0.001) {
                double dot = lookVec.dot(relVec.scale(1.0 / relLen));
                // 玩家正盯着看的生物给 50% 物理概率，身后的生物给 1%
                double physViewWeight = dot > 0.8 ? 0.5 : (dot > 0 ? 0.05 : 0.01);

                // 综合压力评分进行随机剔除
                return Math.random() < (physViewWeight * stress);
            }
        }

        return true; // 压力小时默认开启物理
    }
}