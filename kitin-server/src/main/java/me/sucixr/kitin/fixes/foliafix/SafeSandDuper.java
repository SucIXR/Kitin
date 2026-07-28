package me.sucixr.kitin.fixes.foliafix;

import ca.spottedleaf.common.time.TickData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.phys.Vec3;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import io.papermc.paper.threadedregions.ThreadedRegionizer;

public class SafeSandDuper {
    public static boolean handle(net.minecraft.world.entity.Entity entity) {
        if (!GlobalConfiguration.get().unsupportedSettings.allowUnsafeEndPortalTeleportation ||
                !(entity instanceof FallingBlockEntity fb)) return false;

        // Kitin fix - 防止重复传送
        if (entity.isOnPortalCooldown()) return false;

        if (me.sucixr.kitin.config.KitinConfig.sandDuperBlacklist.contains(fb.getBlockState().getBlock())) {
            return false;
        }

        // Kitin - TPS 保护
        double minTps = me.sucixr.kitin.config.KitinConfig.sandDuperMinTps;
        if (minTps > 0) {
            double currentTps = -1.0;

            // 尝试获取当前区域的 TPS
            ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = TickRegionScheduler.getCurrentRegion();
            if (region != null) {
                TickRegionScheduler.RegionScheduleHandle handle = region.getData().getRegionSchedulingHandle();
                if (handle != null) {
                    TickData.TickReportData report = handle.getTickReport1m(System.nanoTime());
                    if (report != null && report.tpsData() != null && report.tpsData().segmentAll() != null) {
                        currentTps = report.tpsData().segmentAll().average();
                    }
                }
            }

            // 如果无法获取区域 TPS（例如不在 Region 线程上），则回退到全局 TPS
            if (currentTps < 0) {
                currentTps = org.bukkit.Bukkit.getTPS()[0];
            }

            if (currentTps < minTps) {
                return false; // 交给原版处理，防止误删正常掉落物
            }
        }

        // Kitin fix - 不直接销毁实体，而是禁止掉落物品
        // 否则会导致刷沙机失效
        fb.dropItem = false;

        boolean destinationIsEnd = entity.level().dimension() == Level.OVERWORLD;
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ServerLevel targetLevel = serverLevel.getServer().getLevel(destinationIsEnd ? Level.END : Level.OVERWORLD);
        if (targetLevel == null) return false;

        final Vec3 vel = entity.getDeltaMovement();
        final BlockState state = fb.getBlockState();

        // Kitin fix - 设置传送冷却，防止实体在传送门内重复触发传送
        entity.setPortalCooldown();

        final BlockPos basePos = destinationIsEnd ? ServerLevel.END_SPAWN_POINT : targetLevel.getRespawnData().pos();
        final int chunkX = basePos.getX() >> 4;
        final int chunkZ = basePos.getZ() >> 4;

        // Kitin fix: - 异步预加载目标区块，防止无人时刷沙机不工作
        targetLevel.getWorld().getChunkAtAsync(chunkX, chunkZ, true)
                .thenAccept(chunk -> {
                    if (chunk == null) return;

                    RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                            targetLevel,
                            chunkX, chunkZ,
                            () -> {
                                // Kitin fix - 保持区块加载
                                targetLevel.resetEmptyTime();

                                // 关键修复：调用原版 Entity.placePortalTicket 方法
                                // 这会给目标区块添加一个半径为 3、持续 15 秒的 PORTAL Ticket。
                                // 这完全复刻了原版实体传送后的加载保持行为，防止沙子变成掉落物后区块立即卸载导致的堆积问题。
                                // 修正：之前使用了 placeEnderPearlTicket，这会导致 Ticket 类型错误且受配置影响。
                                // 现在直接在实体生成后调用 placePortalTicket。

                                Vec3 finalSpawnPos;

                                if (destinationIsEnd) {
                                    EndPlatformFeature.createEndPlatform(targetLevel, basePos.below(), true);
                                    finalSpawnPos = Vec3.atBottomCenterOf(basePos);
                                } else {
                                    BlockPos adjustedSpawn = targetLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, basePos);
                                    finalSpawnPos = Vec3.atBottomCenterOf(adjustedSpawn);
                                }

                                FallingBlockEntity dupe = new FallingBlockEntity(EntityTypes.FALLING_BLOCK, targetLevel);
                                dupe.setPos(finalSpawnPos.x, finalSpawnPos.y, finalSpawnPos.z);
                                dupe.blockState = state;
                                dupe.time = 1;
                                dupe.autoExpire = false;
                                dupe.setDeltaMovement(vel);

                                targetLevel.addFreshEntity(dupe);

                                // 再次调用 placePortalTicket 确保万无一失
                                // 这才是正确的做法，使用 PORTAL 类型的 Ticket (半径3)
                                dupe.placePortalTicket(dupe.blockPosition());
                            }
                    );
                });

        return true;
    }
}