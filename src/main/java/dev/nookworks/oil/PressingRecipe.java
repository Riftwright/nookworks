package dev.nookworks.oil;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What the Oil Press gets out of an item: data map {@code nookworks:pressing} on items
 * ({@code data/<ns>/data_maps/item/pressing.json}), so datapacks can add seeds. {@code oil} millibuckets after
 * {@code turns} turns of the handle, and an Oil Cake with chance {@code cake}.
 */
public record PressingRecipe(int oil, int turns, float cake) {
    public static final Codec<PressingRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 4_000).fieldOf("oil").forGetter(PressingRecipe::oil),
            Codec.intRange(1, 64).optionalFieldOf("turns", 2).forGetter(PressingRecipe::turns),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("cake", 0.0F).forGetter(PressingRecipe::cake)
    ).apply(i, PressingRecipe::new));

    public static final DataMapType<Item, PressingRecipe> DATA_MAP =
            DataMapType.builder(Nookworks.id("pressing"), Registries.ITEM, CODEC).synced(CODEC, false).build();

    /** The recipe for this item, or null when it gives no oil. */
    public static @Nullable PressingRecipe of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(DATA_MAP);
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
