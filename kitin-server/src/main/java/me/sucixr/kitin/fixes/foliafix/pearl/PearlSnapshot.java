package me.sucixr.kitin.fixes.foliafix.pearl;

import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;

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
            float xRot,
            long creationTime
    ) {}

    public static final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, SimplePearlData>> PEARL_DATA_MAP = new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean tickPearl(ThrownEnderpearl pearl,UUID ownerId){
        if (ownerId != null) {
            var playerPearls = PEARL_DATA_MAP.computeIfAbsent(ownerId, k -> new java.util.concurrent.ConcurrentHashMap<>());
            SimplePearlData oldData = playerPearls.get(pearl.getUUID());
            long timestamp = (oldData != null) ? oldData.creationTime() : System.currentTimeMillis();
            boolean isOnline = pearl.level().getServer().getPlayerList().getPlayer(ownerId) != null;
            if (!isOnline) {
                pearl.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN);
                PEARL_DATA_MAP.remove(ownerId);
                return true;
            }
            PEARL_DATA_MAP.computeIfAbsent(ownerId, k -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put(pearl.getUUID(), new SimplePearlData(
                        pearl.getUUID(),
                        ownerId,
                        pearl.level().dimension().identifier().toString(),
                        pearl.getX(), pearl.getY(), pearl.getZ(),
                        pearl.getDeltaMovement().x, pearl.getDeltaMovement().y, pearl.getDeltaMovement().z,
                        pearl.getYRot(), pearl.getXRot(),
                        timestamp
                ));
            }
        return false;
    }

}
