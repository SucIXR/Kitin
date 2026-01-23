package me.sucixr.kitin.safety.chunkbarrier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

public class LazyChunkBarrierPolicy {
    public static boolean isBarrierItem(Item item) {
        return me.sucixr.kitin.config.KitinConfig.lazyChunkBarrierItems.contains(item);
    }

    public static void onCheckMovement(ItemEntity entity) {
        if (entity.tickCount < 20) {
            return;
        }
        if (!isBarrierItem(entity.getItem().getItem())) {
            return;
        }
        if (entity.level() instanceof ServerLevel serverLevel) {
            if (!serverLevel.isPositionEntityTicking(entity.blockPosition())) {
                entity.discard();
            }
        }
    }
}