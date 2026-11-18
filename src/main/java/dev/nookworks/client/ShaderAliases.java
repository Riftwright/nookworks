package dev.nookworks.client;

import java.util.List;
import java.util.function.Supplier;

import dev.nookworks.registry.GlazedPots;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModWoods;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Shader packs tell leaves and plants apart by a table of vanilla block names (their {@code block.properties}), and
 * make what is on it sway in the wind; a block of ours is on no pack's table, so Dawnwood's leaves stood stock still
 * among the oak's. Here each of our plants borrows the number its vanilla counterpart has in the pack's table - the
 * leaves an oak leaf's, the crops wheat's and carrots', corn tall grass's - state for state where their properties
 * match. Called from the optional Iris hook, when Iris builds the table; a block the pack already names keeps its own.
 */
public final class ShaderAliases {
    /** Ours, and the vanilla block a shader pack should treat it as. */
    private record Alias(Supplier<? extends Block> ours, Block vanilla) {}

    private static final List<Alias> ALIASES = List.of(
            new Alias(ModWoods.DAWNWOOD_LEAVES, Blocks.OAK_LEAVES),
            new Alias(ModWoods.DAWNWOOD_SAPLING, Blocks.OAK_SAPLING),
            new Alias(ModWoods.POTTED_DAWNWOOD_SAPLING, Blocks.POTTED_OAK_SAPLING),
            new Alias(ModCrops.RICE_CROP, Blocks.WHEAT),
            new Alias(ModCrops.RICE_PANICLES, Blocks.WHEAT),
            new Alias(ModKitchen.CORN_CROP, Blocks.TALL_GRASS),
            new Alias(ModKitchen.ONION_CROP, Blocks.CARROTS),
            new Alias(GlazedPots.TRAIL_GREEN, Blocks.VINE),
            new Alias(GlazedPots.TRAIL_PINK, Blocks.VINE));

    private ShaderAliases() {}

    /**
     * {@code ids} with our plants added, each state under the number of the vanilla state that has the same values
     * for the properties the two share (or else of the vanilla block's default state). Our states the pack names
     * itself, and vanilla blocks it does not name, are left as they are.
     */
    public static Object2IntMap<BlockState> withAliases(Object2IntMap<BlockState> ids) {
        Object2IntMap<BlockState> out = new Object2IntOpenHashMap<>(ids);
        out.defaultReturnValue(ids.defaultReturnValue());
        for (Alias alias : ALIASES) {
            Block ours = alias.ours().get();
            for (BlockState state : ours.getStateDefinition().getPossibleStates()) {
                if (out.containsKey(state)) {
                    continue;
                }
                BlockState twin = twin(state, alias.vanilla());
                if (ids.containsKey(twin)) {
                    out.put(state, ids.getInt(twin));
                } else if (ids.containsKey(alias.vanilla().defaultBlockState())) {
                    out.put(state, ids.getInt(alias.vanilla().defaultBlockState()));
                }
            }
        }
        return out;
    }

    /** The vanilla block's state with {@code state}'s values for the properties both have by name. */
    static BlockState twin(BlockState state, Block vanilla) {
        BlockState twin = vanilla.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            twin = copy(state, property, twin);
        }
        return twin;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, Property<T> property, BlockState to) {
        for (Property<?> theirs : to.getProperties()) {
            if (theirs.getName().equals(property.getName())) {
                String value = property.getName(from.getValue(property));
                return setByName(to, theirs, value);
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState setByName(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}
