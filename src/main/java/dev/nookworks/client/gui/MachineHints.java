package dev.nookworks.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import dev.nookworks.barter.BarterTerminalBlockEntity;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.feature.Features;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.machine.Electrolysis;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.StoneMakerBlockEntity;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.recycling.Compressing;
import dev.nookworks.recycling.CompressorBlockEntity;
import dev.nookworks.recycling.RecyclerBlockEntity;
import dev.nookworks.recycling.RecyclingRecipe;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * What an empty machine slot shows, faded, to say what goes in it: the one thing a slot takes (the Sifter's mesh, the
 * Stirring Tank's caustic soda), the reagents of the charge in the Retort, or - for a slot many things fit - each of
 * them in turn, a second apiece. Read from the synced data maps, so a datapack's additions show too.
 */
public final class MachineHints {
    /** What the Liquid tab's top slot takes: a glass bottle or a bucket, shown in turn. */
    private static final List<Item> FILLABLE = List.of(Items.GLASS_BOTTLE, Items.BUCKET);

    /** What a furnace smelts, shown in turn in the Electric Furnace (its recipes are not the client's to read). */
    private static final List<Item> SMELTED = List.of(Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD, Items.SAND,
            Items.COBBLESTONE, Items.BEEF, Items.POTATO, Items.CLAY_BALL);
    /** What a Generator burns. */
    private static final List<Item> FUELS = List.of(Items.COAL, Items.CHARCOAL, Items.OAK_PLANKS, Items.BLAZE_ROD);
    /** What a Planter sets in the ground. */
    private static final List<Item> PLANTED = List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.OAK_SAPLING);

    static ItemStack hint(MachineLayout layout, int slot, MachineMenu menu) {
        // every machine's main input says what goes in, as nookTechnology's do (r21: the Sifter's, Crusher's, Retort's...
        // were bare squares beside theirs)
        return switch (layout) {
            case SIFTER -> slot == SifterBlockEntity.MESH ? new ItemStack(ModItems.BRASS_MESH.get())
                    : slot == SifterBlockEntity.INPUT ? turn(tagged(SifterBlockEntity.SIFTABLE)) : ItemStack.EMPTY;
            case CRUSHER -> slot == 0 ? turn(taken(keys(CrushingRecipe.DATA_MAP), s -> CrushingRecipe.of(s) != null)) : ItemStack.EMPTY;
            case RECYCLER -> slot == RecyclerBlockEntity.INPUT
                    ? turn(taken(keys(RecyclingRecipe.DATA_MAP), s -> RecyclingRecipe.of(s) != null)) : ItemStack.EMPTY;
            case COMPRESSOR -> slot == CompressorBlockEntity.INPUT ? turn(tagged(Compressing.COMPRESSIBLE)) : ItemStack.EMPTY;
            case ELECTROLYTIC_CELL -> slot == 0 ? turn(taken(keys(Electrolysis.DATA_MAP), s -> Electrolysis.of(s) != null)) : ItemStack.EMPTY;
            case GENERATOR -> slot == 0 ? turn(FUELS) : ItemStack.EMPTY;
            case PLANTER -> slot == 0 ? turn(PLANTED) : ItemStack.EMPTY;
            case STONE_MAKER -> slot == StoneMakerBlockEntity.COOLER ? turn(List.of(Items.WATER_BUCKET, Items.BLUE_ICE))
                    : slot == StoneMakerBlockEntity.LAVA ? new ItemStack(Items.LAVA_BUCKET) : ItemStack.EMPTY;
            case STIRRING_TANK -> slot == StirringTankBlockEntity.CATALYST ? new ItemStack(ModItems.CAUSTIC_SODA.get())
                    : slot == 0 ? turn(charges()) : ItemStack.EMPTY;
            case RETORT -> slot < RetortBlockEntity.INPUT_SLOTS ? retortInput(menu, slot) : ItemStack.EMPTY;
            case ELECTRIC_FURNACE -> slot == ElectricFurnaceBlockEntity.INPUT ? turn(SMELTED) : ItemStack.EMPTY;
            case DIESEL_GENERATOR -> turn(List.of(ModOil.BIODIESEL_BOTTLE.get(), ModOil.BIODIESEL_BUCKET.get()));
            case DISTILLER -> slot == 0 ? turn(keys(MercuryRecipes.DISTILLATION)) : ItemStack.EMPTY;
            case BARTER_TERMINAL -> slot == BarterTerminalBlockEntity.GOLD ? new ItemStack(Items.GOLD_INGOT)
                    : slot == BarterTerminalBlockEntity.BOTTLE ? MobBottleItem.filled(EntityTypes.PIGLIN) : ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
    }

    /** One of {@code items} to show, turning through them when there are several (a tag of every mod's zinc). */
    private static ItemStack any(HolderSet<Item> items) {
        return items.size() == 0 ? ItemStack.EMPTY : new ItemStack(items.get((int) (tick() % items.size())));
    }

    /**
     * What an empty input slot of the Retort could take: what the charge begun in the other slots still lacks and needs a
     * slot of its own for (not a second copper, which goes onto the first), the first empty slot its first such thing
     * and so on; with nothing begun, everything the Retort cooks, in turn, the empty slots a step apart so they never
     * show the same thing.
     */
    private static ItemStack retortInput(MachineMenu menu, int slot) {
        List<ItemStack> held = new ArrayList<>();
        int empty = 0;
        for (int i = 0; i < RetortBlockEntity.INPUT_SLOTS; i++) {
            held.add(menu.slots.get(i).getItem());
            if (i < slot && held.get(i).isEmpty()) {
                empty++;
            }
        }
        if (held.stream().anyMatch(stack -> !stack.isEmpty())) {
            var wanted = RetortBlockEntity.slotNeeds(held);
            if (!wanted.isEmpty() || !RetortBlockEntity.charges(held).isEmpty()) {
                return empty < wanted.size() ? any(wanted.get(empty)) : ItemStack.EMPTY;
            }
        }
        List<Item> keys = keys(MercuryRecipes.RETORTING);
        return keys.isEmpty() ? ItemStack.EMPTY : new ItemStack(keys.get((int) ((tick() + empty) % keys.size())));
    }

    /** What the Stirring Tank stirs: every amalgam charge, methanol, and what it makes mud of. */
    private static List<Item> charges() {
        List<Item> items = keys(MercuryRecipes.AMALGAMATION);
        items.add(ModOil.METHANOL.get());
        items.addAll(StirringTankBlockEntity.muddied());
        return items;
    }

    /** What of {@code items} the machine takes ({@code takes}): no hint of a recipe a switched-off part leaves out. */
    private static List<Item> taken(List<Item> items, Predicate<ItemStack> takes) {
        return items.stream().filter(item -> takes.test(new ItemStack(item))).toList();
    }

    /** The data map's items, those a switched-off part takes left out (the fix re-check of r21). */
    private static <T> List<Item> keys(DataMapType<Item, T> map) {
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getDataMap(map).keySet().forEach(key -> BuiltInRegistries.ITEM.getOptional(key)
                .filter(Features::enabled).ifPresent(items::add));
        items.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        return items;
    }

    /** The items of a tag the client has, in a steady order. */
    private static List<Item> tagged(TagKey<Item> tag) {
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder -> {
            if (Features.enabled(holder.value())) {
                items.add(holder.value());
            }
        });
        items.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        return items;
    }

    /**
     * The Liquid tab's top slot: an empty bucket or glass bottle, in turn. Public so an add-on's own Liquid tab fades
     * the same hint.
     */
    public static ItemStack emptyContainer() {
        return turn(FILLABLE);
    }

    /**
     * The Liquid tab's bottom slot: the bottles and buckets of what the machine takes in (a tank its screen shows flowing
     * in), in turn.
     */
    static ItemStack filledContainer(MachineMenu menu) {
        List<Fluid> taken = new ArrayList<>();
        for (int i = 0; i < menu.layout().tanks().size(); i++) {
            if (menu.layout().tanks().get(i).flow() == MachineLayout.Flow.IN) {
                taken.addAll(fluids(menu.tankKind(i)));
            }
        }
        return filledContainer(taken);
    }

    /**
     * The Liquid tab's bottom slot for a machine taking {@code fluids}: their bottles and buckets, in turn (no water
     * bottle, which is a potion). Public so an add-on's own Liquid tab fades the same hint.
     */
    public static ItemStack filledContainer(List<Fluid> fluids) {
        List<Item> filled = new ArrayList<>();
        for (Fluid fluid : fluids) {
            ItemStack bottle = dev.nookworks.fluid.Bottles.filled(fluid);
            if (!bottle.isEmpty() && !filled.contains(bottle.getItem()) && !bottle.is(Items.POTION)) {
                filled.add(bottle.getItem());
            }
            Item bucket = fluid == dev.nookworks.mercury.MercuryFluids.SOURCE.get() ? ModItems.MERCURY_BUCKET.get() : fluid.getBucket();
            if (bucket != Items.AIR && !filled.contains(bucket)) {
                filled.add(bucket);
            }
        }
        return turn(filled);
    }

    private static List<Fluid> fluids(GuiTank.Kind kind) {
        return switch (kind) {
            case WATER -> List.of(Fluids.WATER);
            case VEGETABLE_OIL -> List.of(OilFluids.SOURCE.get());
            case BIODIESEL -> List.of(OilFluids.BIODIESEL.get());
            case LIQUID_FUEL -> List.of(OilFluids.SOURCE.get(), OilFluids.BIODIESEL.get());
            case METHANOL -> List.of(OilFluids.METHANOL.get());
            case WOOD_VINEGAR -> List.of(OilFluids.WOOD_VINEGAR.get());
            case EXPERIENCE -> List.of(ExperienceFluids.SOURCE.get());
            case MERCURY -> List.of(dev.nookworks.mercury.MercuryFluids.SOURCE.get());
            case NETHER_AIR -> List.of();
        };
    }

    private static ItemStack turn(List<Item> items) {
        return items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get((int) (tick() % items.size())));
    }

    /** Seconds, for the turns. */
    private static long tick() {
        return System.currentTimeMillis() / 1_000;
    }

    private MachineHints() {}
}
