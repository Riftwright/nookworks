package dev.nookworks.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.nookworks.Nookworks;
import dev.nookworks.mixin.FireBlockAccessor;
import dev.nookworks.furniture.ChairBlock;
import dev.nookworks.furniture.DeskBlock;
import dev.nookworks.furniture.StoolBlock;
import dev.nookworks.furniture.WallShelfBlock;
import dev.nookworks.wood.DawnwoodLeavesBlock;
import dev.nookworks.wood.SupportBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Dawnwood, the wood grown from a Rare Seed, and the wooden supports, one per wood.
 * <p>
 * Dawnwood never generates in the world: its only source is the seed, which turns up now and then when dirt is
 * sifted. Its tree is the data-driven feature {@code nookworks:dawnwood}.
 * <p>
 * The set is a whole vanilla wood's: signs, hanging signs and a shelf (vanilla's own blocks, their block entity types
 * told to take ours) and a boat with and without a chest ({@link ModEntities}).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class ModWoods {
    public static final BlockSetType DAWNWOOD_SET = BlockSetType.register(new BlockSetType("nookworks:dawnwood"));
    public static final WoodType DAWNWOOD_TYPE = WoodType.register(new WoodType("nookworks:dawnwood", DAWNWOOD_SET));
    public static final ResourceKey<Feature> DAWNWOOD_TREE = ResourceKey.create(Registries.FEATURE, Nookworks.id("dawnwood"));
    public static final TreeGrower DAWNWOOD_GROWER = new TreeGrower("nookworks:dawnwood", WeightedList.of(DAWNWOOD_TREE),
            WeightedList.of(), WeightedList.of(), DAWNWOOD_TREE);

    private static final MapColor HEARTWOOD = MapColor.TERRACOTTA_LIGHT_GREEN;
    private static final MapColor BARK = MapColor.TERRACOTTA_BROWN;

    // an axe strips the bark, as a vanilla log's: neoforge:transformables (data_maps/block) adds that to the axe's turns
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_DAWNWOOD_LOG = log("stripped_dawnwood_log", HEARTWOOD);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_DAWNWOOD_WOOD = log("stripped_dawnwood_wood", HEARTWOOD);
    public static final DeferredBlock<RotatedPillarBlock> DAWNWOOD_LOG = log("dawnwood_log", BARK);
    public static final DeferredBlock<RotatedPillarBlock> DAWNWOOD_WOOD = log("dawnwood_wood", BARK);
    public static final DeferredBlock<Block> DAWNWOOD_PLANKS = ModBlocks.BLOCKS.registerSimpleBlock("dawnwood_planks",
            p -> wood(p).strength(2.0F, 3.0F));
    public static final DeferredBlock<StairBlock> DAWNWOOD_STAIRS = ModBlocks.BLOCKS.registerBlock("dawnwood_stairs",
            p -> new StairBlock(DAWNWOOD_PLANKS.get().defaultBlockState(), p), p -> wood(p).strength(2.0F, 3.0F));
    public static final DeferredBlock<SlabBlock> DAWNWOOD_SLAB = ModBlocks.BLOCKS.registerBlock("dawnwood_slab",
            SlabBlock::new, p -> wood(p).strength(2.0F, 3.0F));
    public static final DeferredBlock<FenceBlock> DAWNWOOD_FENCE = ModBlocks.BLOCKS.registerBlock("dawnwood_fence",
            FenceBlock::new, p -> wood(p).strength(2.0F, 3.0F));
    public static final DeferredBlock<FenceGateBlock> DAWNWOOD_FENCE_GATE = ModBlocks.BLOCKS.registerBlock("dawnwood_fence_gate",
            p -> new FenceGateBlock(DAWNWOOD_TYPE, p), p -> wood(p).forceSolidOn().strength(2.0F, 3.0F));
    public static final DeferredBlock<DoorBlock> DAWNWOOD_DOOR = ModBlocks.BLOCKS.registerBlock("dawnwood_door",
            p -> new DoorBlock(DAWNWOOD_SET, p), p -> wood(p).strength(3.0F).noOcclusion().pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<TrapDoorBlock> DAWNWOOD_TRAPDOOR = ModBlocks.BLOCKS.registerBlock("dawnwood_trapdoor",
            p -> new TrapDoorBlock(DAWNWOOD_SET, p), p -> wood(p).strength(3.0F).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false));
    public static final DeferredBlock<ButtonBlock> DAWNWOOD_BUTTON = ModBlocks.BLOCKS.registerBlock("dawnwood_button",
            p -> new ButtonBlock(DAWNWOOD_SET, 30, p), p -> p.noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<PressurePlateBlock> DAWNWOOD_PRESSURE_PLATE = ModBlocks.BLOCKS.registerBlock(
            "dawnwood_pressure_plate", p -> new PressurePlateBlock(DAWNWOOD_SET, p),
            p -> wood(p).forceSolidOn().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<DawnwoodLeavesBlock> DAWNWOOD_LEAVES = ModBlocks.BLOCKS.registerBlock("dawnwood_leaves",
            DawnwoodLeavesBlock::new, p -> p.mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(SoundType.GRASS)
                    .noOcclusion().isValidSpawn((state, level, pos, type) -> false).isSuffocating((s, l, p2) -> false)
                    .isViewBlocking((s, l, p2, near) -> false).ignitedByLava().pushReaction(PushReaction.POPPED)
                    .isRedstoneConductor((s, l, p2) -> false));
    public static final DeferredBlock<SaplingBlock> DAWNWOOD_SAPLING = ModBlocks.BLOCKS.registerBlock("dawnwood_sapling",
            p -> new SaplingBlock(DAWNWOOD_GROWER, p), p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks()
                    .instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<FlowerPotBlock> POTTED_DAWNWOOD_SAPLING = ModBlocks.BLOCKS.registerBlock(
            "potted_dawnwood_sapling", p -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, DAWNWOOD_SAPLING, p),
            p -> p.instabreak().noOcclusion().pushReaction(PushReaction.POPPED));

    public static final DeferredBlock<StandingSignBlock> DAWNWOOD_SIGN = ModBlocks.BLOCKS.registerBlock("dawnwood_sign",
            p -> new StandingSignBlock(DAWNWOOD_TYPE, p), p -> sign(p));
    public static final DeferredBlock<WallSignBlock> DAWNWOOD_WALL_SIGN = ModBlocks.BLOCKS.registerBlock("dawnwood_wall_sign",
            p -> new WallSignBlock(DAWNWOOD_TYPE, p), p -> sign(wallOf(p, DAWNWOOD_SIGN.get())));
    public static final DeferredBlock<CeilingHangingSignBlock> DAWNWOOD_HANGING_SIGN = ModBlocks.BLOCKS.registerBlock(
            "dawnwood_hanging_sign", p -> new CeilingHangingSignBlock(DAWNWOOD_TYPE, p), p -> sign(p));
    public static final DeferredBlock<WallHangingSignBlock> DAWNWOOD_WALL_HANGING_SIGN = ModBlocks.BLOCKS.registerBlock(
            "dawnwood_wall_hanging_sign", p -> new WallHangingSignBlock(DAWNWOOD_TYPE, p),
            p -> sign(wallOf(p, DAWNWOOD_HANGING_SIGN.get())));
    public static final DeferredBlock<ShelfBlock> DAWNWOOD_SHELF = ModBlocks.BLOCKS.registerBlock("dawnwood_shelf", ShelfBlock::new,
            p -> p.mapColor(HEARTWOOD).instrument(NoteBlockInstrument.BASS).sound(SoundType.SHELF).ignitedByLava().strength(2.0F, 3.0F));

    /** Every block item of the set, in creative-tab order. The sapling's item is the Rare Seed. */
    public static final List<DeferredItem<? extends BlockItem>> DAWNWOOD_ITEMS = new ArrayList<>();
    public static final DeferredItem<BlockItem> RARE_SEED = ModItems.ITEMS.registerItem("rare_seed",
            p -> new BlockItem(DAWNWOOD_SAPLING.get(), p));

    static {
        for (DeferredBlock<? extends Block> block : List.of(DAWNWOOD_LOG, DAWNWOOD_WOOD, STRIPPED_DAWNWOOD_LOG,
                STRIPPED_DAWNWOOD_WOOD, DAWNWOOD_PLANKS, DAWNWOOD_STAIRS, DAWNWOOD_SLAB, DAWNWOOD_FENCE, DAWNWOOD_FENCE_GATE,
                DAWNWOOD_TRAPDOOR, DAWNWOOD_BUTTON, DAWNWOOD_PRESSURE_PLATE, DAWNWOOD_LEAVES)) {
            DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(block));
        }
        DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerItem("dawnwood_door", p -> new DoubleHighBlockItem(DAWNWOOD_DOOR.get(), p),
                p -> p.useBlockDescriptionPrefix()));
        // as vanilla's signs (26.3 has no SignItem: the sign opens its editor itself when set down)
        DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerItem("dawnwood_sign",
                p -> new StandingAndWallBlockItem(DAWNWOOD_SIGN.get(), DAWNWOOD_WALL_SIGN.get(), Direction.DOWN, p),
                p -> p.stacksTo(16).signText().useBlockDescriptionPrefix()));
        DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerItem("dawnwood_hanging_sign",
                p -> new HangingSignItem(DAWNWOOD_HANGING_SIGN.get(), DAWNWOOD_WALL_HANGING_SIGN.get(), p),
                p -> p.stacksTo(16).signText().useBlockDescriptionPrefix()));
        DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(DAWNWOOD_SHELF));
    }

    public static final DeferredItem<BoatItem> DAWNWOOD_BOAT = ModItems.ITEMS.registerItem("dawnwood_boat",
            p -> new BoatItem(ModEntities.DAWNWOOD_BOAT.get(), p), p -> p.stacksTo(1));
    public static final DeferredItem<BoatItem> DAWNWOOD_CHEST_BOAT = ModItems.ITEMS.registerItem("dawnwood_chest_boat",
            p -> new BoatItem(ModEntities.DAWNWOOD_CHEST_BOAT.get(), p), p -> p.stacksTo(1));

    /** A sign's properties, as vanilla gives its own: no collision, soft, and catching from lava. */
    private static BlockBehaviour.Properties sign(BlockBehaviour.Properties p) {
        return p.mapColor(HEARTWOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(1.0F).ignitedByLava();
    }

    /** A wall sign drops, and is named, as the sign it was placed from. */
    private static BlockBehaviour.Properties wallOf(BlockBehaviour.Properties p, Block standing) {
        return p.overrideLootTable(standing.getLootTable()).overrideDescription(standing.getDescriptionId());
    }

    /** The woods that get a support, in the order vanilla lists its woods; Dawnwood last. */
    public static final List<String> SUPPORT_WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "pale_oak", "crimson", "warped", "bamboo", "dawnwood");
    public static final Map<String, DeferredBlock<SupportBlock>> SUPPORTS;
    public static final Map<String, DeferredItem<BlockItem>> SUPPORT_ITEMS;

    static {
        Map<String, DeferredBlock<SupportBlock>> blocks = new LinkedHashMap<>();
        Map<String, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        for (String wood : SUPPORT_WOODS) {
            boolean nether = wood.equals("crimson") || wood.equals("warped");
            DeferredBlock<SupportBlock> block = ModBlocks.BLOCKS.registerBlock(wood + "_support", SupportBlock::new, p -> {
                p.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).noOcclusion()
                        .sound(nether ? SoundType.NETHER_WOOD : wood.equals("bamboo") ? SoundType.BAMBOO_WOOD
                                : wood.equals("cherry") ? SoundType.CHERRY_WOOD : SoundType.WOOD);
                return nether ? p : p.ignitedByLava();
            });
            blocks.put(wood, block);
            items.put(wood, ModItems.ITEMS.registerSimpleBlockItem(block));
        }
        SUPPORTS = Collections.unmodifiableMap(blocks);
        SUPPORT_ITEMS = Collections.unmodifiableMap(items);
    }

    /**
     * A Chair, a Stool and a Desk for every wood that has a support, drawn with its planks and stripped log by name
     * (tools/gen_wood_data.py). The oak ones were the Chair and the Desk before there were others ({@link RenamedIds}).
     */
    public static final Map<String, DeferredBlock<ChairBlock>> CHAIRS;
    public static final Map<String, DeferredBlock<StoolBlock>> STOOLS;
    public static final Map<String, DeferredBlock<DeskBlock>> DESKS;
    public static final Map<String, DeferredItem<BlockItem>> CHAIR_ITEMS;
    public static final Map<String, DeferredItem<BlockItem>> STOOL_ITEMS;
    public static final Map<String, DeferredItem<BlockItem>> DESK_ITEMS;
    /** A Wall Shelf for every wood too: its planks on black iron brackets (tools/gen_wood_data.py). */
    public static final Map<String, DeferredBlock<WallShelfBlock>> WALL_SHELVES;
    public static final Map<String, DeferredItem<BlockItem>> WALL_SHELF_ITEMS;

    static {
        Map<String, DeferredBlock<ChairBlock>> chairs = new LinkedHashMap<>();
        Map<String, DeferredBlock<StoolBlock>> stools = new LinkedHashMap<>();
        Map<String, DeferredBlock<DeskBlock>> desks = new LinkedHashMap<>();
        Map<String, DeferredItem<BlockItem>> chairItems = new LinkedHashMap<>();
        Map<String, DeferredItem<BlockItem>> stoolItems = new LinkedHashMap<>();
        Map<String, DeferredItem<BlockItem>> deskItems = new LinkedHashMap<>();
        Map<String, DeferredBlock<WallShelfBlock>> shelves = new LinkedHashMap<>();
        Map<String, DeferredItem<BlockItem>> shelfItems = new LinkedHashMap<>();
        for (String wood : SUPPORT_WOODS) {
            DeferredBlock<ChairBlock> chair = ModBlocks.BLOCKS.registerBlock(wood + "_chair", ChairBlock::new, p -> furniture(p, wood));
            DeferredBlock<StoolBlock> stool = ModBlocks.BLOCKS.registerBlock(wood + "_stool", StoolBlock::new, p -> furniture(p, wood));
            DeferredBlock<DeskBlock> desk = ModBlocks.BLOCKS.registerBlock(wood + "_desk", DeskBlock::new, p -> furniture(p, wood));
            chairs.put(wood, chair);
            stools.put(wood, stool);
            desks.put(wood, desk);
            chairItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(chair));
            stoolItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(stool));
            deskItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(desk));
            DeferredBlock<WallShelfBlock> shelf = ModBlocks.BLOCKS.registerBlock(wood + "_wall_shelf", WallShelfBlock::new, p -> furniture(p, wood));
            shelves.put(wood, shelf);
            shelfItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(shelf));
        }
        CHAIRS = Collections.unmodifiableMap(chairs);
        STOOLS = Collections.unmodifiableMap(stools);
        DESKS = Collections.unmodifiableMap(desks);
        CHAIR_ITEMS = Collections.unmodifiableMap(chairItems);
        STOOL_ITEMS = Collections.unmodifiableMap(stoolItems);
        DESK_ITEMS = Collections.unmodifiableMap(deskItems);
        WALL_SHELVES = Collections.unmodifiableMap(shelves);
        WALL_SHELF_ITEMS = Collections.unmodifiableMap(shelfItems);
    }

    /** Furniture of {@code wood}: soft as planks, sounding as that wood does, and burning unless it grew in the Nether. */
    private static BlockBehaviour.Properties furniture(BlockBehaviour.Properties p, String wood) {
        boolean nether = wood.equals("crimson") || wood.equals("warped");
        p.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).noOcclusion()
                .sound(nether ? SoundType.NETHER_WOOD : wood.equals("bamboo") ? SoundType.BAMBOO_WOOD
                        : wood.equals("cherry") ? SoundType.CHERRY_WOOD : SoundType.WOOD);
        return nether ? p : p.ignitedByLava();
    }

    private static DeferredBlock<RotatedPillarBlock> log(String name, MapColor colour) {
        return ModBlocks.BLOCKS.registerBlock(name, RotatedPillarBlock::new, p -> wood(p).mapColor(colour).strength(2.0F));
    }

    private static BlockBehaviour.Properties wood(BlockBehaviour.Properties p) {
        return p.mapColor(HEARTWOOD).instrument(NoteBlockInstrument.BASS).sound(SoundType.WOOD).ignitedByLava();
    }

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {}

    /** Vanilla's sign, hanging sign and shelf block entities, told they belong to Dawnwood's too. */
    @SubscribeEvent
    static void blockEntities(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityTypes.SIGN, DAWNWOOD_SIGN.get(), DAWNWOOD_WALL_SIGN.get());
        event.modify(BlockEntityTypes.HANGING_SIGN, DAWNWOOD_HANGING_SIGN.get(), DAWNWOOD_WALL_HANGING_SIGN.get());
        event.modify(BlockEntityTypes.SHELF, DAWNWOOD_SHELF.get());
    }

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlockAccessor fire = (FireBlockAccessor) (FireBlock) Blocks.FIRE;
            for (Block block : List.of(DAWNWOOD_PLANKS.get(), DAWNWOOD_STAIRS.get(), DAWNWOOD_SLAB.get(), DAWNWOOD_FENCE.get(),
                    DAWNWOOD_FENCE_GATE.get())) {
                fire.nookworks$setFlammable(block, 5, 20);
            }
            for (Block block : List.of(DAWNWOOD_LOG.get(), DAWNWOOD_WOOD.get(), STRIPPED_DAWNWOOD_LOG.get(), STRIPPED_DAWNWOOD_WOOD.get())) {
                fire.nookworks$setFlammable(block, 5, 5);
            }
            fire.nookworks$setFlammable(DAWNWOOD_LEAVES.get(), 30, 60);
            fire.nookworks$setFlammable(DAWNWOOD_SHELF.get(), 30, 20); // as vanilla's shelves
            SUPPORTS.forEach((wood, support) -> {
                if (!wood.equals("crimson") && !wood.equals("warped")) {
                    fire.nookworks$setFlammable(support.get(), 5, 20);
                    fire.nookworks$setFlammable(CHAIRS.get(wood).get(), 5, 20);
                    fire.nookworks$setFlammable(DESKS.get(wood).get(), 5, 20);
                }
            });
            // (the potted sapling joins the empty pot by itself: NeoForge links every full pot to its empty one)
        });
    }

    private ModWoods() {}
}
