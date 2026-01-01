package me.sucixr.kitin.fixes.foliafix;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.phys.Vec3;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.configuration.GlobalConfiguration;

public class SafeSandDuper {
    public static boolean handle(net.minecraft.world.entity.Entity entity) {
        if (!GlobalConfiguration.get().unsupportedSettings.allowUnsafeEndPortalTeleportation ||
                !(entity instanceof FallingBlockEntity fb)) return false;

        boolean destinationIsEnd = entity.level().dimension() == Level.OVERWORLD;
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ServerLevel targetLevel = serverLevel.getServer().getLevel(destinationIsEnd ? Level.END : Level.OVERWORLD);
        if (targetLevel == null) return false;

        final Vec3 vel = entity.getDeltaMovement();
        final BlockState state = fb.getBlockState();
        entity.setPortalCooldown();

        final BlockPos basePos = destinationIsEnd ? ServerLevel.END_SPAWN_POINT : targetLevel.getRespawnData().pos();

        RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                targetLevel,
                basePos.getX() >> 4, basePos.getZ() >> 4,
                () -> {
                    Vec3 finalSpawnPos;

                    if (destinationIsEnd) {
                        EndPlatformFeature.createEndPlatform(targetLevel, basePos.below(), true);
                        finalSpawnPos = Vec3.atBottomCenterOf(basePos);
                    } else {
                        BlockPos adjustedSpawn = targetLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, basePos);
                        finalSpawnPos = Vec3.atBottomCenterOf(adjustedSpawn);
                    }

                    FallingBlockEntity dupe = new FallingBlockEntity(EntityType.FALLING_BLOCK, targetLevel);
                    dupe.setPos(finalSpawnPos.x, finalSpawnPos.y, finalSpawnPos.z);
                    dupe.blockState = state;
                    dupe.time = 1;
                    dupe.autoExpire = false;
                    dupe.setDeltaMovement(vel);

                    targetLevel.addFreshEntity(dupe);
                }
        );
        return true;
    }
}