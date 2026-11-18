package dev.nookworks.port.core;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.RegistryManager;

/**
 * An axe stripping Dawnwood on a NeoForge 21.1 that does not yet read the {@code neoforge:strippables} data map.
 * <p>
 * PORT: 26.3 says which block a log strips to in {@code data/neoforge/data_maps/block/strippables.json}, and the port
 * ships that file as it is; later NeoForge 21.1 builds read it. The older ones packs still carry (21.1.219, which
 * Create and Farmer's Delight ask for) do not know that data map, and there an axe did nothing to our logs: on those
 * alone this answers the axe, with the same two pairs. Where the data map is read, it alone decides (a data pack can
 * change it), and this does nothing.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Stripping {
    private static final ResourceLocation DATA_MAP = ResourceLocation.fromNamespaceAndPath("neoforge", "strippables");
    private static @Nullable Boolean byDataMap;

    private Stripping() {}

    /** Whether this NeoForge reads the strippables data map (asked once, after the data map types are registered). */
    public static boolean byDataMap() {
        Boolean known = byDataMap;
        if (known == null) {
            known = RegistryManager.getDataMap(Registries.BLOCK, DATA_MAP) != null;
            byDataMap = known;
        }
        return known;
    }

    @SubscribeEvent
    static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.AXE_STRIP || byDataMap()) {
            return;
        }
        BlockState state = event.getState();
        if (event.getFinalState() != state) {
            return; // another mod has answered
        }
        Block stripped = state.is(ModWoods.DAWNWOOD_LOG.get()) ? ModWoods.STRIPPED_DAWNWOOD_LOG.get()
                : state.is(ModWoods.DAWNWOOD_WOOD.get()) ? ModWoods.STRIPPED_DAWNWOOD_WOOD.get() : null;
        if (stripped != null) {
            event.setFinalState(stripped.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
        }
    }
}
