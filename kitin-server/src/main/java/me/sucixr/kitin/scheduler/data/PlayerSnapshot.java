package me.sucixr.kitin.scheduler.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class PlayerSnapshot {

    private final UUID uuid;
    private final double x, y, z;
    private final ResourceKey<Level> dimension;
    private final boolean transmittingWaypoints;

    private PlayerSnapshot(UUID uuid, double x, double y, double z,
                           ResourceKey<Level> dimension,
                           boolean transmittingWaypoints) {
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.transmittingWaypoints = transmittingWaypoints;
    }

    public static PlayerSnapshot capture(final ServerPlayer p) {

        final ResourceKey<Level> dim = p.level().dimension();

        final boolean tx = p.isTransmittingWaypoint();

        return new PlayerSnapshot(
                p.getUUID(),
                p.getX(), p.getY(), p.getZ(),
                dim,
                tx
        );
    }

    public UUID uuid() { return uuid; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public ResourceKey<Level> dimension() { return dimension; }

    public boolean transmittingWaypoints() { return transmittingWaypoints; }
}
