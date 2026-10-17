package dev.nookworks.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.nookworks.barter.BarterTerminalBlockEntity;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * What an empty machine slot shows, faded, to say what goes in it: the one thing a slot takes (the Sifter's mesh, the
 * Stirring Tank's caustic soda), the reagents of the charge in the Retort, or - for a slot many things fit - each of
 * them in turn, a second apiece. Read from the synced data maps, so a datapack's additions show too.
 */
final class MachineHints {
    /** What the experience fill slot takes: a glass bottle or a bucket, shown in turn. */
    private static final List<Item> FILLABLE = List.of(Items.GLASS_BOTTLE, Items.BUCKET);

    static ItemStack hint(MachineLayout layout, int slot, MachineMenu menu) {
        return switch (layout) {
            case SIFTER -> slot == SifterBlockEntity.MESH ? new ItemStack(ModItems.BRASS_MESH.get()) : ItemStack.EMPTY;
            case STIRRING_TANK -> slot == StirringTankBlockEntity.CATALYST ? new ItemStack(ModItems.CAUSTIC_SODA.get())
                    : slot == 0 ? turn(charges()) : ItemStack.EMPTY;
            case RETORT -> slot == 1 || slot == 2 ? reagent(menu, slot - 1)
                    : slot == RetortBlockEntity.FILL ? turn(FILLABLE) : ItemStack.EMPTY;
            case ELECTRIC_FURNACE -> slot == ElectricFurnaceBlockEntity.FILL ? turn(FILLABLE) : ItemStack.EMPTY;
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

    /** The reagent the charge now in the Retort needs in this slot; with no charge, every reagent there is, in turn. */
    private static ItemStack reagent(MachineMenu menu, int index) {
        ItemStack charge = menu.slots.get(0).getItem();
        MercuryRecipes.Retorting recipe = charge.isEmpty() ? null : MercuryRecipes.retorting(charge);
        if (recipe != null) {
            return index < recipe.reagents().size() ? any(recipe.reagents().get(index)) : ItemStack.EMPTY;
        }
        List<Item> all = new ArrayList<>();
        BuiltInRegistries.ITEM.getDataMap(MercuryRecipes.RETORTING).values().forEach(r -> {
            for (var reagent : r.reagents()) {
                Item item = any(reagent).getItem();
                if (!all.contains(item)) {
                    all.add(item);
                }
            }
        });
        // The two slots take turns through the list, a step apart, so they never show the same thing.
        return all.isEmpty() ? ItemStack.EMPTY : new ItemStack(all.get((int) ((tick() + index) % all.size())));
    }

    /** What the Stirring Tank stirs: every amalgam charge, methanol, and what it makes mud of. */
    private static List<Item> charges() {
        List<Item> items = keys(MercuryRecipes.AMALGAMATION);
        items.add(ModOil.METHANOL.get());
        items.addAll(StirringTankBlockEntity.muddied());
        return items;
    }

    private static <T> List<Item> keys(DataMapType<Item, T> map) {
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getDataMap(map).keySet().forEach(key -> BuiltInRegistries.ITEM.getOptional(key).ifPresent(items::add));
        items.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        return items;
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
