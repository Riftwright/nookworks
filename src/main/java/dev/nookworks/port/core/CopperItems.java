package dev.nookworks.port.core;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 26.3's weathering copper collections of {@code Items} ({@code Items.COPPER_BLOCK.waxed().exposed()}),
 * gathered from 1.21.1's one constant per block. tx/core.py points {@code Items.COPPER_BLOCK.waxed()} and the
 * others here. Only the kinds 1.21.1 has all eight of are given.
 */
public final class CopperItems {
    private CopperItems() {}

    public static final WeatheringCopperCollection<Item> COPPER_BLOCK = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.COPPER_BLOCK, Items.EXPOSED_COPPER, Items.WEATHERED_COPPER, Items.OXIDIZED_COPPER),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_COPPER_BLOCK, Items.WAXED_EXPOSED_COPPER, Items.WAXED_WEATHERED_COPPER, Items.WAXED_OXIDIZED_COPPER));
    public static final WeatheringCopperCollection<Item> CUT_COPPER = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.CUT_COPPER, Items.EXPOSED_CUT_COPPER, Items.WEATHERED_CUT_COPPER, Items.OXIDIZED_CUT_COPPER),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_CUT_COPPER, Items.WAXED_EXPOSED_CUT_COPPER, Items.WAXED_WEATHERED_CUT_COPPER, Items.WAXED_OXIDIZED_CUT_COPPER));
    public static final WeatheringCopperCollection<Item> CHISELED_COPPER = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.CHISELED_COPPER, Items.EXPOSED_CHISELED_COPPER, Items.WEATHERED_CHISELED_COPPER, Items.OXIDIZED_CHISELED_COPPER),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_CHISELED_COPPER, Items.WAXED_EXPOSED_CHISELED_COPPER, Items.WAXED_WEATHERED_CHISELED_COPPER, Items.WAXED_OXIDIZED_CHISELED_COPPER));
    public static final WeatheringCopperCollection<Item> CUT_COPPER_STAIRS = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.CUT_COPPER_STAIRS, Items.EXPOSED_CUT_COPPER_STAIRS, Items.WEATHERED_CUT_COPPER_STAIRS, Items.OXIDIZED_CUT_COPPER_STAIRS),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_CUT_COPPER_STAIRS, Items.WAXED_EXPOSED_CUT_COPPER_STAIRS, Items.WAXED_WEATHERED_CUT_COPPER_STAIRS, Items.WAXED_OXIDIZED_CUT_COPPER_STAIRS));
    public static final WeatheringCopperCollection<Item> CUT_COPPER_SLAB = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.CUT_COPPER_SLAB, Items.EXPOSED_CUT_COPPER_SLAB, Items.WEATHERED_CUT_COPPER_SLAB, Items.OXIDIZED_CUT_COPPER_SLAB),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_CUT_COPPER_SLAB, Items.WAXED_EXPOSED_CUT_COPPER_SLAB, Items.WAXED_WEATHERED_CUT_COPPER_SLAB, Items.WAXED_OXIDIZED_CUT_COPPER_SLAB));
    public static final WeatheringCopperCollection<Item> COPPER_DOOR = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.COPPER_DOOR, Items.EXPOSED_COPPER_DOOR, Items.WEATHERED_COPPER_DOOR, Items.OXIDIZED_COPPER_DOOR),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_COPPER_DOOR, Items.WAXED_EXPOSED_COPPER_DOOR, Items.WAXED_WEATHERED_COPPER_DOOR, Items.WAXED_OXIDIZED_COPPER_DOOR));
    public static final WeatheringCopperCollection<Item> COPPER_TRAPDOOR = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.COPPER_TRAPDOOR, Items.EXPOSED_COPPER_TRAPDOOR, Items.WEATHERED_COPPER_TRAPDOOR, Items.OXIDIZED_COPPER_TRAPDOOR),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_COPPER_TRAPDOOR, Items.WAXED_EXPOSED_COPPER_TRAPDOOR, Items.WAXED_WEATHERED_COPPER_TRAPDOOR, Items.WAXED_OXIDIZED_COPPER_TRAPDOOR));
    public static final WeatheringCopperCollection<Item> COPPER_GRATE = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.COPPER_GRATE, Items.EXPOSED_COPPER_GRATE, Items.WEATHERED_COPPER_GRATE, Items.OXIDIZED_COPPER_GRATE),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_COPPER_GRATE, Items.WAXED_EXPOSED_COPPER_GRATE, Items.WAXED_WEATHERED_COPPER_GRATE, Items.WAXED_OXIDIZED_COPPER_GRATE));
    public static final WeatheringCopperCollection<Item> COPPER_BULB = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Items.COPPER_BULB, Items.EXPOSED_COPPER_BULB, Items.WEATHERED_COPPER_BULB, Items.OXIDIZED_COPPER_BULB),
            new WeatheringCopperCollection.ByState<>(Items.WAXED_COPPER_BULB, Items.WAXED_EXPOSED_COPPER_BULB, Items.WAXED_WEATHERED_COPPER_BULB, Items.WAXED_OXIDIZED_COPPER_BULB));
}
