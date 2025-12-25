package me.sucixr.kitin.scheduler;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
public class SocialBubbleEngine {
    private static final SocialBubbleEngine INSTANCE = new SocialBubbleEngine();
    public static SocialBubbleEngine getInstance() { return INSTANCE; }
    public boolean shouldSkipHardCollision(Entity a, Entity b) {
        boolean aLiving = a instanceof LivingEntity;
        boolean bLiving = b instanceof LivingEntity;

        if (aLiving || bLiving) {
            if (aLiving) applySoftRepulsion((LivingEntity) a, b);
            if (bLiving) applySoftRepulsion((LivingEntity) b, a);
            return true;
        }

        return false;
    }

    private void applySoftRepulsion(LivingEntity entity, Entity other) {
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
        //}
    }
}