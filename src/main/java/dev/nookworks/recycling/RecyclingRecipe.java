package dev.nookworks.recycling;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModRecycling;
import dev.nookworks.storage.StorageItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What the Recycler makes of a piece of metal junk: data map {@code nookworks:recycling} on items
 * ({@code data/<ns>/data_maps/item/recycling.json}), so datapacks can add to it. {@code metal} is how many ingots its
 * making took (a fraction for things made several at a time: a rail, a bar); {@code kind} is {@code scrap} (iron, copper,
 * chain - the default) or {@code gold}, which comes back as gold nuggets, nine to the ingot, rather than as scrap: gold is
 * not thrown in with the iron (the user, 2026-10-18).
 * <p>
 * It gives back {@link #RECOVERY} of the metal, times the durability left for worn gear, the fraction a chance of one more.
 * The tag {@code #nookworks:recyclable} lists the same items for sorting (a DataTest keeps the two the same).
 */
public record RecyclingRecipe(float metal, Kind kind) {
    /** Half the metal comes back (the user took the recommendation, 2026-10-18). */
    public static final float RECOVERY = 0.5F;

    public enum Kind implements StringRepresentable {
        SCRAP("scrap"), GOLD("gold");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final Codec<RecyclingRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.01F, 64.0F).fieldOf("metal").forGetter(RecyclingRecipe::metal),
            Kind.CODEC.optionalFieldOf("kind", Kind.SCRAP).forGetter(RecyclingRecipe::kind)
    ).apply(i, RecyclingRecipe::new));

    public static final DataMapType<Item, RecyclingRecipe> DATA_MAP =
            DataMapType.builder(Nookworks.id("recycling"), Registries.ITEM, CODEC).synced(CODEC, false).build();

    /**
     * The recipe for this item, or null when the Recycler does not take it - nor when it holds things (a copper chest
     * picked up with its contents), which would be shredded with it.
     */
    public static @Nullable RecyclingRecipe of(ItemStack stack) {
        if (stack.isEmpty() || holdsThings(stack)) {
            return null;
        }
        RecyclingRecipe recipe = stack.typeHolder().getData(DATA_MAP);
        return recipe != null && Features.enabled(recipe.result()) ? recipe : null; // no scrap that cannot be had
    }

    /**
     * Whether {@code stack} holds other things that a machine would destroy with it: a chest or barrel picked up with
     * its contents, a loaded crossbow's arrow or firework, and our own that keep what they held when broken - a machine's
     * liquids (a Retort's mercury, a Generator's oil), a tank's, a Storage Barrel's stacks (r21, 2026-11-06: the Recycler
     * shredded a Retort with its mercury). The power a machine keeps is no such thing: it would have to be run down
     * first, for a few FE. The Recycler, the Crusher and the Generator refuse such a stack (the dupe and loss audit,
     * 2026-10-19, found the Crusher and the Generator took them).
     */
    public static boolean holdsThings(ItemStack stack) {
        for (Predicate<ItemStack> test : ADD_ON_HOLDS) {
            if (test.test(stack)) {
                return true;
            }
        }
        return !stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).equals(ItemContainerContents.EMPTY)
                || !stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty()
                || stack.getOrDefault(ModDataComponents.HELD_FLUIDS.get(), List.<SimpleFluidContent>of()).stream().anyMatch(fluid -> !fluid.isEmpty())
                || !stack.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).isEmpty()
                || stack.getOrDefault(ModDataComponents.MERCURY.get(), 0) > 0
                || StorageItem.holdsSomething(stack);
    }

    /** Add-ons' word on their own items that hold things ({@link #holdsThingsWhen}). */
    private static final List<Predicate<ItemStack>> ADD_ON_HOLDS = new CopyOnWriteArrayList<>();

    /**
     * For add-ons: {@code test} says an item of theirs holds something a machine would destroy with it (nookTechnology's
     * filled Mob Data Bank, a charged battery), so the Recycler, the Crusher and the Generator refuse it as they refuse a
     * filled chest; the empty one still goes in (r21, 2026-11-06).
     */
    public static void holdsThingsWhen(Predicate<ItemStack> test) {
        ADD_ON_HOLDS.add(test);
    }

    /** What the metal comes back as. */
    public Item result() {
        return this.kind == Kind.GOLD ? Items.GOLD_NUGGET : ModRecycling.SCRAP.get();
    }

    /** How many of {@link #result()} it gives on average, new: half the metal (in nuggets for gold). */
    public float expected() {
        return this.metal * RECOVERY * (this.kind == Kind.GOLD ? 9 : 1);
    }

    /**
     * How long a piece takes, by its metal: three quarters of a second a ingot, from a second for a rail to six for a
     * chestplate or an anvil (the self-review, 2026-10-18: at a flat four seconds a stack of rails took four minutes).
     */
    public int ticks() {
        return Math.max(20, Math.min(120, Math.round(15 * this.metal)));
    }

    /** What recycling {@code input} gives this time; empty when a worn piece rolls nothing. */
    public ItemStack outputFor(ItemStack input, RandomSource random) {
        float expected = this.expected();
        if (input.isDamageableItem()) {
            expected *= 1.0F - (float) input.getDamageValue() / Math.max(1, input.getMaxDamage());
        }
        int count = (int) expected + (random.nextFloat() < expected - (int) expected ? 1 : 0);
        return count <= 0 ? ItemStack.EMPTY : new ItemStack(this.result(), Math.min(count, 64));
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
