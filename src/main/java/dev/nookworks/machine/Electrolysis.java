package dev.nookworks.machine;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What the Electrolytic Cell makes of an item: data map {@code nookworks:electrolysis}. {@code count} of
 * {@code result}, with a {@code bonus_chance} of {@code bonus} more; {@code water} mB of water used up; and, where the
 * cell works as a mercury cell, {@code mercury} mB of its mercury lost a run - the cell must hold at least
 * {@link ElectrolyticCellBlockEntity#MERCURY_NEEDED} of it to run such a recipe at all.
 */
public record Electrolysis(Holder<Item> result, int count, float bonusChance, int bonus, int water, int mercury, boolean needsMercury) {
    public static final Codec<Electrolysis> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("result").forGetter(Electrolysis::result),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Electrolysis::count),
            Codec.floatRange(0, 1).optionalFieldOf("bonus_chance", 0.0F).forGetter(Electrolysis::bonusChance),
            Codec.intRange(1, 64).optionalFieldOf("bonus", 1).forGetter(Electrolysis::bonus),
            Codec.intRange(0, 4000).optionalFieldOf("water", 0).forGetter(Electrolysis::water),
            Codec.intRange(0, 1000).optionalFieldOf("mercury", 0).forGetter(Electrolysis::mercury),
            Codec.BOOL.optionalFieldOf("needs_mercury", false).forGetter(Electrolysis::needsMercury)
    ).apply(i, Electrolysis::new));

    public static final DataMapType<Item, Electrolysis> DATA_MAP =
            DataMapType.builder(Nookworks.id("electrolysis"), Registries.ITEM, CODEC).synced(CODEC, false).build();

    /** The work for {@code stack}, or null for none: none that makes what cannot be had ({@link Features}). */
    public static @Nullable Electrolysis of(ItemStack stack) {
        Electrolysis recipe = stack.isEmpty() ? null : stack.typeHolder().getData(DATA_MAP);
        return recipe != null && Features.enabled(recipe.result().value()) ? recipe : null;
    }

    /** What a run makes: no more than a stack of it, whatever a datapack asks. */
    public ItemStack output() {
        return new ItemStack(this.result, Math.min(this.count, new ItemStack(this.result).getMaxStackSize()));
    }

    /**
     * What a run on {@code input} makes: as {@link #output()}, keeping what the input carried - the name and pose of a
     * copper golem statue taken back to bright copper.
     */
    public ItemStack output(ItemStack input) {
        ItemStack made = this.output();
        return input.isEmpty() ? made : input.transmuteCopy(made.getItem(), made.getCount());
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
