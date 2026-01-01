package me.sucixr.kitin.scheduler.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public class PearlSnapshot {
    public record SimplePearlData(
            java.util.UUID pearlId,
            java.util.UUID ownerId,
            String dimensionId,
            double x,
            double y,
            double z,
            double motX,
            double motY,
            double motZ,
            float yRot,
            float xRot) {}

    public static final java.util.Map<java.util.UUID,SimplePearlData> PEARL_DATA_MAP = new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean tickPearl(ThrownEnderpearl pearl){
        final net.minecraft.world.entity.Entity owner = pearl.getOwner();
        final UUID ownerId = owner != null ? owner.getUUID() : null;
        if (ownerId != null) {
            boolean isOnline = pearl.level().getServer().getPlayerList().getPlayer(ownerId) != null;
            if (!isOnline) {
                pearl.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN);
                PEARL_DATA_MAP.remove(ownerId);
                return true;
            }
            pearl.level().dimension().identifier().toString();
            if (!pearl.isRemoved()) {
                PEARL_DATA_MAP.put(ownerId, new SimplePearlData(
                        pearl.getUUID(),
                        ownerId,
                        pearl.level().dimension().identifier().toString(),
                        pearl.getX(), pearl.getY(), pearl.getZ(),
                        pearl.getDeltaMovement().x, pearl.getDeltaMovement().y, pearl.getDeltaMovement().z,
                        pearl.getYRot(), pearl.getXRot()
                ));
            }
        }
        return false;
    }

}
