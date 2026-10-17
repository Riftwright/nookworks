package dev.nookworks.registry;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.ifworld.IfMirrorBlock;
import dev.nookworks.ifworld.IfMirrorBlockEntity;
import dev.nookworks.travel.DaemonDoorBlock;
import dev.nookworks.travel.DaemonDoorBlockEntity;
import dev.nookworks.travel.DaemonPanelBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The Dock-a-Daemon Door and its Destination Panel - one door to every dimension (see DaemonTravel) - and the If Mirror,
 * the way into the If (see IfWorld).
 */
public final class ModTravel {
    /**
     * Oiled Dawnwood: taken up with an axe, or slowly by hand, and given back either way (it wanted a pickaxe while it
     * was a steel hatch, and a wooden door broken otherwise was lost). Its sounds are its BlockSetType's. Not moved by
     * pistons, which would carry it off its record.
     */
    public static final DeferredBlock<DaemonDoorBlock> DAEMON_DOOR = ModBlocks.BLOCKS.registerBlock("daemon_door", DaemonDoorBlock::new,
            // a Dawnwood door: it sounds of wood when set down, struck or broken, as it does sliding (a barrel's); unsaid,
            // it sounded of stone (review r12)
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(5.0F, 6.0F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredItem<DoubleHighBlockItem> DAEMON_DOOR_ITEM = ModItems.ITEMS.registerItem("daemon_door",
            p -> new DoubleHighBlockItem(DAEMON_DOOR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredBlock<DaemonPanelBlock> DAEMON_PANEL = ModBlocks.BLOCKS.registerBlock("destination_panel",
            DaemonPanelBlock::new, p -> p.mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.COPPER).noOcclusion()
                    .lightLevel(s -> DaemonPanelBlock.LIGHT_LEVEL).pushReaction(PushReaction.POPPED));
    public static final DeferredItem<BlockItem> DAEMON_PANEL_ITEM = ModItems.ITEMS.registerSimpleBlockItem(DAEMON_PANEL);

    /** A standing glass in a Dawnwood frame: wood's strength, glass's sound. */
    public static final DeferredBlock<IfMirrorBlock> IF_MIRROR = ModBlocks.BLOCKS.registerBlock("if_mirror", IfMirrorBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredItem<DoubleHighBlockItem> IF_MIRROR_ITEM = ModItems.ITEMS.registerItem("if_mirror",
            p -> new DoubleHighBlockItem(IF_MIRROR.get(), p), p -> p.useBlockDescriptionPrefix());

    public static final Supplier<BlockEntityType<DaemonDoorBlockEntity>> DAEMON_DOOR_ENTITY = ModBlockEntities.BLOCK_ENTITY_TYPES.register(
            "daemon_door", () -> new BlockEntityType<>(DaemonDoorBlockEntity::new, DAEMON_DOOR.get()));
    public static final Supplier<BlockEntityType<IfMirrorBlockEntity>> IF_MIRROR_ENTITY = ModBlockEntities.BLOCK_ENTITY_TYPES.register(
            "if_mirror", () -> new BlockEntityType<>(IfMirrorBlockEntity::new, IF_MIRROR.get()));

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("travel: {}", DAEMON_DOOR.getId());
    }

    private ModTravel() {}
}
