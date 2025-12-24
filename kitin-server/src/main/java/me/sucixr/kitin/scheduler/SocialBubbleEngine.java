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
        // 1. 获取基础压力
        double stress = PerformanceBudgetManager.getInstance().getCurrentStressLevel();

        // 2. 物理层特有的激进判定：如果压力开始出现 (MSPT > 35ms)，立即进入高效模式
        if (stress < 0.9) {
            ServerPlayer player = (ServerPlayer) entity.level().getNearestPlayer(entity, 32); // 物理检测范围缩短至32格
            if (player == null) return false; // 附近没玩家，直接不跑碰撞

            double distance = entity.distanceTo(player);

            // --- 激进物理保底逻辑 ---
            // 只有在 1.5 格内（贴脸）才强制保证物理，否则进入概率/密度判定
            if (distance < 1.5) return true;

            // 3. 计算视野权重 (物理专用：身后和侧面几乎瞬间进入气泡模式)
            Vec3 lookVec = player.getLookAngle();
            Vec3 relVec = entity.position().subtract(player.position());
            double relLen = relVec.length();

            double physViewWeight = 0.01; // 默认身后概率 1%
            if (relLen > 0.001) {
                double dot = lookVec.dot(relVec.scale(1.0 / relLen));
                // 只有玩家准星正对着看时才给 50% 概率，其余时间极低
                physViewWeight = dot > 0.8 ? 0.5 : (dot > 0 ? 0.05 : 0.01);
            }

            // 修补 A: 移除原本的 getEntities 密度判定，改用常驻随机判定，极大提升基础性能
            double finalProb = physViewWeight * stress;

            // 修补 B: 针对猪灵大幅调低频率
            // 猪灵在压力下即便被看着，也只有极低概率进行碰撞计算，视觉上完全可以接受
            if (entity instanceof ZombifiedPiglin) {
                finalProb *= 0.1; // 在原有概率基础上再砍掉 90% 的频率
            }
                return entity.getRandom().nextDouble() < (physViewWeight * stress);
        }
        return true;
    }
}