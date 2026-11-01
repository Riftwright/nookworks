package dev.nookworks.cooking;

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
 * What the hand-cranked kitchen machines make of an item, as item data maps a datapack can add to:
 * <ul>
 * <li>{@code nookworks:milling} - the Hand Quern: wheat to flour, bread to crumbs;</li>
 * <li>{@code nookworks:noodle_making} - the Noodle Maker: a sheet of udon dough through its rollers and cutters.</li>
 * </ul>
 * An entry gives {@code count} of {@code result} for one of the item, after {@code turns} turns of the handle.
 */
public record HandWork(Holder<Item> result, int count, int turns) {
    public static final Codec<HandWork> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("result").forGetter(HandWork::result),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(HandWork::count),
            Codec.intRange(1, 64).optionalFieldOf("turns", 3).forGetter(HandWork::turns)).apply(i, HandWork::new));

    public static final DataMapType<Item, HandWork> MILLING = DataMapType.builder(Nookworks.id("milling"), Registries.ITEM, CODEC)
            .synced(CODEC, false).build();
    public static final DataMapType<Item, HandWork> NOODLE_MAKING = DataMapType.builder(Nookworks.id("noodle_making"), Registries.ITEM, CODEC)
            .synced(CODEC, false).build();

    /** What one turn of work makes: no more than a stack of it, whatever a datapack asks. */
    public ItemStack output() {
        return new ItemStack(this.result, Math.min(this.count, new ItemStack(this.result).getMaxStackSize()));
    }

    /** What {@code type} makes of {@code stack}, or null for nothing: nothing that cannot be had ({@link Features}). */
    public static @Nullable HandWork of(DataMapType<Item, HandWork> type, ItemStack stack) {
        HandWork work = stack.isEmpty() ? null : stack.typeHolder().getData(type);
        return work != null && Features.enabled(work.result().value()) ? work : null;
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(MILLING);
            event.register(NOODLE_MAKING);
        }

        private Registration() {}
    }
}
