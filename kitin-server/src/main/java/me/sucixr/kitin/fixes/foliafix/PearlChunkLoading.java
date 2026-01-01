package me.sucixr.kitin.fixes.foliafix;

import me.sucixr.kitin.scheduler.data.PearlSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;

import static me.sucixr.kitin.scheduler.data.PearlSnapshot.PEARL_DATA_MAP;

public class PearlChunkLoading {
    public static void saveEnderPearls(ServerPlayer player, ValueOutput output) {
        PearlSnapshot.SimplePearlData data = PEARL_DATA_MAP.remove(player.getUUID());

        if (data != null) {
            ValueOutput.ValueOutputList valueOutputList = output.childrenList("ender_pearls");
            ValueOutput child = valueOutputList.addChild();
            net.minecraft.resources.Identifier dimId = net.minecraft.resources.Identifier.parse(data.dimensionId());
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId);
            child.store("ender_pearl_dimension", net.minecraft.world.level.Level.RESOURCE_KEY_CODEC, dimKey);
            child.putString("id", "minecraft:ender_pearl");
            child.store("Owner", net.minecraft.core.UUIDUtil.CODEC, data.ownerId());
            child.store("Pos", net.minecraft.world.phys.Vec3.CODEC, new net.minecraft.world.phys.Vec3(data.x(), data.y(), data.z()));
            child.store("Motion", net.minecraft.world.phys.Vec3.CODEC, new net.minecraft.world.phys.Vec3(data.motX(), data.motY(), data.motZ()));
            child.store("Rotation", net.minecraft.world.phys.Vec2.CODEC, new net.minecraft.world.phys.Vec2(data.yRot(), data.xRot()));
        }
    }
}
