package me.sucixr.kitin.network.packets.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.SkipPacketDecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;
import java.util.List;

public record FabricRecipeSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricRecipeSyncPayload> CODEC = Entry.CODEC.apply(ByteBufCodecs.list())
            .map(FabricRecipeSyncPayload::new, FabricRecipeSyncPayload::entries);

    public static final Type<FabricRecipeSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabric", "recipe_sync"));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(RecipeSerializer<?> serializer, List<RecipeHolder<?>> recipes) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.ofMember(
                Entry::write,
                Entry::read
        );

        private static Entry read(RegistryFriendlyByteBuf buf) {
            Identifier recipeSerializerId = buf.readIdentifier();
            RecipeSerializer<?> recipeSerializer = BuiltInRegistries.RECIPE_SERIALIZER.getValue(recipeSerializerId);

            if (recipeSerializer == null) {
                throw new SkipPacketDecoderException("Tried syncing unsupported packet serializer '" + recipeSerializerId + "'!");
            }

            int count = buf.readVarInt();
            var list = new ArrayList<RecipeHolder<?>>();

            for (int i = 0; i < count; i++) {
                ResourceKey<Recipe<?>> id = buf.readResourceKey(Registries.RECIPE);
                //noinspection deprecation
                Recipe<?> recipe = recipeSerializer.streamCodec().decode(buf);
                list.add(new RecipeHolder<>(id, recipe));
            }

            return new Entry(recipeSerializer, list);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeIdentifier(BuiltInRegistries.RECIPE_SERIALIZER.getKey(this.serializer));

            buf.writeVarInt(this.recipes.size());

            //noinspection unchecked,deprecation
            StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> serializer = ((StreamCodec<RegistryFriendlyByteBuf, Recipe<?>>) this.serializer.streamCodec());

            for (RecipeHolder<?> recipe : this.recipes) {
                buf.writeResourceKey(recipe.id());
                serializer.encode(buf, recipe.value());
            }
        }
    }
}
