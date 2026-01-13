package me.sucixr.kitin.scheduler.policy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class LazyChunkBarrierPolicy {
    private static final Set<Item> LAG_ITEMS = ImmutableSet.of(
            // 1. 刷黑曜石机
            Items.OBSIDIAN,
            // 2. 刷铁机 (虞美人)
            Items.POPPY,
            // 3. 守卫者农场 (海晶碎片/晶体)
            Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS,
            // 4. 地毯机
            Items.WHITE_CARPET
            //Items.ORANGE_CARPET, Items.MAGENTA_CARPET, Items.LIGHT_BLUE_CARPET,
            //Items.YELLOW_CARPET, Items.LIME_CARPET, Items.PINK_CARPET, Items.GRAY_CARPET,
            //Items.LIGHT_GRAY_CARPET, Items.CYAN_CARPET, Items.PURPLE_CARPET, Items.BLUE_CARPET,
            //Items.BROWN_CARPET, Items.GREEN_CARPET, Items.RED_CARPET, Items.BLACK_CARPET
            // 5. 竹子/仙人掌/甘蔗 (常见经验修补机/全自动农场)
            //Items.BAMBOO, Items.CACTUS, Items.SUGAR_CANE,
            // 6. 腐肉 (猪人塔/僵尸塔)
            //Items.ROTTEN_FLESH
    );

    public static void onCheckMovement(ItemEntity entity) {
        if (entity.tickCount < 20) {
            return;
        }
        if (!LAG_ITEMS.contains(entity.getItem().getItem())) {
            return;
        }
        if (entity.level() instanceof ServerLevel serverLevel) {
            if (!serverLevel.isPositionEntityTicking(entity.blockPosition())) {
                entity.discard();
            }
        }
    }
}