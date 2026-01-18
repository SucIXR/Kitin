package me.sucixr.kitin.other.old.scheduler.policy;

import me.sucixr.kitin.network.misc.ServerStressProbe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class AIPolicy {

    private static final double DISTANCE_SCALE = 16.0;

    public double computeProbability(Entity entity, ServerPlayer player) {
        double stress = ServerStressProbe.getInstance().getStressLevel();

        // --- 距离衰减 ---
        double distance = entity.distanceTo(player);
        double scaled = distance / DISTANCE_SCALE;
        double distanceProb = 1.0 / (1.0 + (scaled * scaled));

        // --- 视野权重 ---
        Vec3 look = player.getLookAngle();
        Vec3 dir = entity.position().subtract(player.position()).normalize();
        double dot = look.dot(dir);

        double viewMultiplier =
                dot > 0.5 ? 1.0 :
                        dot > 0.0 ? 0.5 :
                                0.1;

        return distanceProb * viewMultiplier * stress;
    }

    /**
     * 特殊生物策略（原猪灵逻辑）
     */
    public double applyEntityBias(Entity entity, double baseProb) {
        if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) {
            return baseProb * baseProb; // 压力平方
        }
        return baseProb;
    }
}
