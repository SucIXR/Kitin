package me.sucixr.kitin.scheduler.physics;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
public class SocialBubbleEngine {
    private static final me.sucixr.kitin.scheduler.old.SocialBubbleEngine INSTANCE = new me.sucixr.kitin.scheduler.old.SocialBubbleEngine();
    public static me.sucixr.kitin.scheduler.old.SocialBubbleEngine getInstance() { return INSTANCE; }
    public boolean shouldSkipHardCollision(Entity a, Entity b) {
        applySoftRepulsion(a, b);
        applySoftRepulsion(b, a);
        return false;
    }
    private void applySoftRepulsion(Entity entity, Entity other) {
        if (entity.isVehicle() || !entity.isPushable()) {
            return;
        }
        double dx = entity.getX() - other.getX();
        double dz = entity.getZ() - other.getZ();
        double max = Math.max(Math.abs(dx), Math.abs(dz));
        if (max >= 0.01) {
            double strength = (1.0 - Math.min(max, 1.0)) * 0.05;
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.add(
                    (dx / max) * strength,
                    0,
                    (dz / max) * strength
            ));
        }
    }
}