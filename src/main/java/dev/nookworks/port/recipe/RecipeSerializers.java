package dev.nookworks.port.recipe;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 26.3's {@code new RecipeSerializer<>(mapCodec, streamCodec)}: a recipe serializer is a record there and an interface
 * with {@code codec()} and {@code streamCodec()} in 1.21.1. {@code RecipeSerializers.of(MAP_CODEC, STREAM_CODEC)} is
 * the same serializer.
 */
public final class RecipeSerializers {
    private RecipeSerializers() {}

    public static <T extends Recipe<?>> RecipeSerializer<T> of(MapCodec<T> codec,
            StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<T> codec() {
                return codec;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
                return streamCodec;
            }
        };
    }
}
