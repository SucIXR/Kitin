package me.sucixr.kitin.scheduler.controller;

import me.sucixr.kitin.scheduler.policy.AIPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class AIExecutionController {

    private static final AIExecutionController INSTANCE = new AIExecutionController();
    public static AIExecutionController getInstance() { return INSTANCE; }

    private final AIPolicy policy = new AIPolicy();

    public boolean shouldTickAI(Entity entity) {
        ServerPlayer player =
                (ServerPlayer) entity.level().getNearestPlayer(entity, 128);

        if (player == null) return false;

        double baseProb = policy.computeProbability(entity, player);
        double finalProb = policy.applyEntityBias(entity, baseProb);

        // 保底距离
        double minDistance =
                (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin)
                        ? 2.0 : 8.0;

        if (entity.distanceTo(player) < minDistance) {
            return true;
        }

        return entity.getRandom().nextDouble() < finalProb;
    }
}
