package dev.nookworks.port.core;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 26.3's weathering copper collections of {@code Blocks} ({@code Blocks.COPPER_BLOCK.waxed().exposed()}),
 * gathered from 1.21.1's one constant per block. tx/core.py points {@code Blocks.COPPER_BLOCK.waxed()} and the
 * others here. Only the kinds 1.21.1 has all eight of are given.
 */
public final class CopperBlocks {
    private CopperBlocks() {}

    public static final WeatheringCopperCollection<Block> COPPER_BLOCK = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER, Blocks.WAXED_OXIDIZED_COPPER));
    public static final WeatheringCopperCollection<Block> CUT_COPPER = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER));
    public static final WeatheringCopperCollection<Block> CHISELED_COPPER = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.CHISELED_COPPER, Blocks.EXPOSED_CHISELED_COPPER, Blocks.WEATHERED_CHISELED_COPPER, Blocks.OXIDIZED_CHISELED_COPPER),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_CHISELED_COPPER, Blocks.WAXED_EXPOSED_CHISELED_COPPER, Blocks.WAXED_WEATHERED_CHISELED_COPPER, Blocks.WAXED_OXIDIZED_CHISELED_COPPER));
    public static final WeatheringCopperCollection<Block> CUT_COPPER_STAIRS = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.CUT_COPPER_STAIRS, Blocks.EXPOSED_CUT_COPPER_STAIRS, Blocks.WEATHERED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER_STAIRS),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS));
    public static final WeatheringCopperCollection<Block> CUT_COPPER_SLAB = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.CUT_COPPER_SLAB, Blocks.EXPOSED_CUT_COPPER_SLAB, Blocks.WEATHERED_CUT_COPPER_SLAB, Blocks.OXIDIZED_CUT_COPPER_SLAB),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_CUT_COPPER_SLAB, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB));
    public static final WeatheringCopperCollection<Block> COPPER_DOOR = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.COPPER_DOOR, Blocks.EXPOSED_COPPER_DOOR, Blocks.WEATHERED_COPPER_DOOR, Blocks.OXIDIZED_COPPER_DOOR),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_COPPER_DOOR, Blocks.WAXED_EXPOSED_COPPER_DOOR, Blocks.WAXED_WEATHERED_COPPER_DOOR, Blocks.WAXED_OXIDIZED_COPPER_DOOR));
    public static final WeatheringCopperCollection<Block> COPPER_TRAPDOOR = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.COPPER_TRAPDOOR, Blocks.EXPOSED_COPPER_TRAPDOOR, Blocks.WEATHERED_COPPER_TRAPDOOR, Blocks.OXIDIZED_COPPER_TRAPDOOR),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_COPPER_TRAPDOOR, Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR, Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR, Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR));
    public static final WeatheringCopperCollection<Block> COPPER_GRATE = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.COPPER_GRATE, Blocks.EXPOSED_COPPER_GRATE, Blocks.WEATHERED_COPPER_GRATE, Blocks.OXIDIZED_COPPER_GRATE),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_COPPER_GRATE, Blocks.WAXED_EXPOSED_COPPER_GRATE, Blocks.WAXED_WEATHERED_COPPER_GRATE, Blocks.WAXED_OXIDIZED_COPPER_GRATE));
    public static final WeatheringCopperCollection<Block> COPPER_BULB = new WeatheringCopperCollection<>(
            new WeatheringCopperCollection.ByState<>(Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB, Blocks.WEATHERED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB),
            new WeatheringCopperCollection.ByState<>(Blocks.WAXED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB));
}
