package me.sucixr.kitin.network.packets.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record NeoforgeRecipeSyncPayload(
        Set<RecipeType<?>> recipeTypes,
        List<RecipeHolder<?>> recipes) implements CustomPacketPayload {
    public static final Type<NeoforgeRecipeSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("neoforge", "recipe_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NeoforgeRecipeSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.RECIPE_TYPE).apply(ByteBufCodecs.collection(HashSet::new)), NeoforgeRecipeSyncPayload::recipeTypes,
            RecipeHolder.STREAM_CODEC.apply(ByteBufCodecs.list()), NeoforgeRecipeSyncPayload::recipes,
            NeoforgeRecipeSyncPayload::new);

    public static NeoforgeRecipeSyncPayload create(Collection<RecipeType<?>> recipeTypes, RecipeMap recipes) {
        var recipeTypeSet = Set.copyOf(recipeTypes);
        // Fast-path for empty recipe type set (if no mod wants to sync anything)
        if (recipeTypeSet.isEmpty()) {
            return new NeoforgeRecipeSyncPayload(recipeTypeSet, List.of());
        } else {
            var recipeSubset = recipes.values().stream().filter(h -> recipeTypeSet.contains(h.value().getType())).toList();
            return new NeoforgeRecipeSyncPayload(recipeTypeSet, recipeSubset);
        }
    }

    @NonNull
    @Override
    public Type<NeoforgeRecipeSyncPayload> type() {
        return TYPE;
    }
}
