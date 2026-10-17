package dev.nookworks.registry;

import java.util.Map;

import dev.nookworks.Nookworks;

/**
 * Ids renamed, so they say what the thing is called now. A world saved with the old id still loads: the registries
 * map it to the new one. (The Hub Stone was the Redstone Cogstone up to 0.2.0.)
 */
public final class RenamedIds {
    private static final Map<String, String> BLOCKS_AND_ITEMS = Map.ofEntries(
            Map.entry("fertile_soil", "nourished_soil"),
            Map.entry("line_warp_sender", "horizontal_item_warp_sender"),
            Map.entry("line_warp_receiver", "horizontal_item_warp_receiver"),
            Map.entry("iron_storage", "iron_storage_barrel"),
            Map.entry("gold_storage", "gold_storage_barrel"),
            Map.entry("diamond_storage", "diamond_storage_barrel"),
            Map.entry("netherite_storage", "netherite_storage_barrel"),
            Map.entry("water_pipe", "copper_pipe"),
            Map.entry("donabe", "iron_pot"),
            Map.entry("chair", "oak_chair"),
            Map.entry("desk", "oak_desk"),
            Map.entry("cogstone", "hub_stone"),
            Map.entry("iron_sorter", "item_sorter")); // speed is the gear's now; the plain sorter is not iron's
    private static final Map<String, String> ITEMS_ONLY = Map.of("trace_amalgam", "mixed_amalgam", "rice", "brown_rice", "biodiesel", "biodiesel_bottle",
            "return_scroll", "lode_pearl", // the scroll gave way to the pearl before release; one bound nowhere binds to a lodestone
            "sodium_amalgam", "caustic_soda", // made by the Stirring Tank up to 0.2.0, then by nothing; it distilled to caustic soda
            "ancient_seed", "rare_seed"); // renamed before 1.0: vanilla's ancient cities and archaeology keep that word
    private static final Map<String, String> BLOCK_ENTITIES = Map.of("line_warp_sender", "horizontal_item_warp_sender",
            "water_pipe", "copper_pipe",
            "donabe", "iron_pot");

    public static void register() {
        BLOCKS_AND_ITEMS.forEach((from, to) -> {
            ModBlocks.BLOCKS.addAlias(Nookworks.id(from), Nookworks.id(to));
            ModItems.ITEMS.addAlias(Nookworks.id(from), Nookworks.id(to));
        });
        ITEMS_ONLY.forEach((from, to) -> ModItems.ITEMS.addAlias(Nookworks.id(from), Nookworks.id(to)));
        BLOCK_ENTITIES.forEach((from, to) -> ModBlockEntities.BLOCK_ENTITY_TYPES.addAlias(Nookworks.id(from), Nookworks.id(to)));
    }

    private RenamedIds() {}
}
