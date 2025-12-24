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

        // 2. 【核心改动】删除对特定生物（如猪灵、村民）的判断 X

        // 3. 获取压力
        double stress = PerformanceBudgetManager.getInstance().getCurrentStressLevel();

        //联动：直接调用 AI 引擎算好的上下文（半径 32）
        PerformanceBudgetManager.EntityData ctx = PerformanceBudgetManager.getInstance().getContext(entity, 32.0);
        if (ctx == null) return false;

        // 4. 物理层距离保底
        if (ctx.distance() < 1.5) return true;

        // 5. 物理层视线逻辑
        // 这里沿用你原来的逻辑：盯看的 0.5 概率，身后的 0.01
        double physViewWeight = ctx.dot() > 0.8 ? 0.5 : (ctx.dot() > 0 ? 0.05 : 0.01);

        return Math.random() < (physViewWeight * stress);
    }
}