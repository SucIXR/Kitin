package me.sucixr.kitin.performance.physics;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
public class SocialBubbleEngine {
    private static final SocialBubbleEngine INSTANCE = new SocialBubbleEngine();
    public static SocialBubbleEngine getInstance() { return INSTANCE; }
    public boolean shouldSkipHardCollision(Entity a, Entity b) {
        applySoftRepulsion(a, b);
        applySoftRepulsion(b, a);
        return true;
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