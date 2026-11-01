package dev.nookworks.cooking;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What a vegetable becomes in the Nukadoko: data map {@code nookworks:pickling} on items
 * ({@code data/<ns>/data_maps/item/pickling.json}), so datapacks can pickle more. {@code result} after {@code time}
 * ticks in the bran.
 */
public record PicklingRecipe(Holder<Item> result, int time) {
    public static final Codec<PicklingRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("result").forGetter(PicklingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", 12_000).forGetter(PicklingRecipe::time)
    ).apply(i, PicklingRecipe::new));

    public static final DataMapType<Item, PicklingRecipe> DATA_MAP =
            DataMapType.builder(Nookworks.id("pickling"), Registries.ITEM, CODEC).synced(CODEC, false).build();

    /** The recipe for this item, or null when it does not pickle, or would make what cannot be had ({@link Features}). */
    public static @Nullable PicklingRecipe of(ItemStack stack) {
        PicklingRecipe recipe = stack.isEmpty() ? null : stack.typeHolder().getData(DATA_MAP);
        return recipe != null && Features.enabled(recipe.result().value()) ? recipe : null;
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(DATA_MAP);
        }

        private Registration() {}
    }
}
