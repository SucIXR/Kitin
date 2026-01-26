package me.sucixr.kitin.performance.entity.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class VillagerSmartHibernation {

    public static boolean checkFreezeTick(Villager villager, ServerLevel level) {
        // 如没有快照，说明没被冻结，返回原版AI
        if (villager.kitin$prisonSnapshot == null) {
            return false;
        }

        // 墙壁完整性检查
        if (checkPrisonIntegrity(villager, level)) {
            // 睡眠状态不能冻结
            if (villager.isSleeping()) {
                unfreeze(villager);
                return false;
            }
            // 手动威胁检测
            boolean hasMemory = villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
            if (hasMemory || ((level.getGameTime() + villager.getId()) % 20 == 0 && hasThreatNearby(villager, level))) {
                unfreeze(villager);
                return false;
            }
            // 玩家交互
            if (level.getNearestPlayer(villager, 6.0D) != null) {
                return false;
            }
            // 补货
            performLifeSupport(villager, level);
            return true;
        } else {
            // 墙破了，瞬间解冻
            unfreeze(villager);
            return false;
        }
    }

    public static void attemptCapture(Villager villager, ServerLevel level) {
        if (villager.kitin$prisonSnapshot == null && !villager.getNavigation().isInProgress()) {
            tryCapturePrison(villager, level);
        }
    }

    private static void unfreeze(Villager villager) {
        villager.kitin$prisonSnapshot = null;
        villager.getNavigation().recomputePath();
    }

    private static void performLifeSupport(Villager villager, ServerLevel level) {
        long timeOfDay = level.getDayTime() % 24000;
        boolean isWorkTime = timeOfDay >= 2000 && timeOfDay < 9000;
        if (isWorkTime && (level.getGameTime() + villager.getId()) % 200 == 0 && villager.shouldRestock(level)) {
            villager.restock();
            villager.playWorkSound();
        }
    }

    private static BlockPos getPrisonCenter(Villager villager, ServerLevel level) {
        BlockPos pos = villager.blockPosition();
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            pos = pos.above();
        }
        return pos;
    }

    private static boolean hasThreatNearby(Villager villager, ServerLevel level) {
        BlockPos center = getPrisonCenter(villager, level);
        int x = center.getX();
        int y = center.getY();
        int z = center.getZ();
        return !level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(
                        x - 8.0, y + 1.0, z - 8.0,
                        x + 9.0, y + 1.5, z + 9.0 // max (注意: x+9 是因为 BlockPos 是左下角，要覆盖到 x+8 的方块边界)
                ),
                entity -> entity instanceof Enemy && entity.isAlive()
        ).isEmpty();
    }

    private static boolean checkPrisonIntegrity(Villager villager, ServerLevel level) {
        BlockPos pos = getPrisonCenter(villager, level);
        BlockState[] snapshot = villager.kitin$prisonSnapshot;

        if (level.getBlockState(pos.above()) != snapshot[0]) return false;
        if (level.getBlockState(pos.north()) != snapshot[1]) return false;
        if (level.getBlockState(pos.south()) != snapshot[2]) return false;
        if (level.getBlockState(pos.west())  != snapshot[3]) return false;
        if (level.getBlockState(pos.east())  != snapshot[4]) return false;
        return true;
    }

    private static void tryCapturePrison(Villager villager, ServerLevel level) {
        BlockPos pos = getPrisonCenter(villager, level);

        BlockState n = level.getBlockState(pos.north());
        BlockState s = level.getBlockState(pos.south());
        BlockState w = level.getBlockState(pos.west());
        BlockState e = level.getBlockState(pos.east());

        if (isBlocking(level, pos.north(), n) &&
                isBlocking(level, pos.south(), s) &&
                isBlocking(level, pos.west(), w) &&
                isBlocking(level, pos.east(), e)) {

            villager.kitin$prisonSnapshot = new BlockState[] {
                    level.getBlockState(pos.above()), n, s, w, e
            };
        }
    }

    private static boolean isBlocking(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.getCollisionShape(level, pos).isEmpty();
    }
}
