package dev.nookworks.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.nookworks.Nookworks;
import dev.nookworks.mixin.FireBlockAccessor;
import dev.nookworks.furniture.BenchBlock;
import dev.nookworks.furniture.ChairBlock;
import dev.nookworks.furniture.DeskBlock;
import dev.nookworks.furniture.StoolBlock;
import dev.nookworks.furniture.WallShelfBlock;
import dev.nookworks.wood.DawnwoodLeavesBlock;
import dev.nookworks.wood.SupportBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.SignItem;
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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import dev.nookworks.port.core.Props;
import dev.nookworks.port.core.EntityBoatItem;

/**
 * Dawnwood, the wood grown from a Rare Seed, and the wooden supports, one per wood.
 * <p>
 * Dawnwood never generates in the world: its only source is the seed, which turns up now and then when dirt is
 * sifted. Its tree is the data-driven feature {@code nookworks:dawnwood}.
 * <p>
 * The set is a whole vanilla wood's: signs, hanging signs and a shelf (vanilla's own blocks, their block entity types
 * told to take ours) and a boat with and without a chest ({@link ModEntities}).
 * <p>
 * PORT: 1.21.1 has no shelves: the Dawnwood shelf is left out (tools/port1211/dropped_ids.txt), as are the supports,
 * furniture and turned planks of pale oak and poplar, woods 1.21.1 lacks.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class ModWoods {
    public static final BlockSetType DAWNWOOD_SET = BlockSetType.register(new BlockSetType("nookworks:dawnwood"));
    public static final WoodType DAWNWOOD_TYPE = WoodType.register(new WoodType("nookworks:dawnwood", DAWNWOOD_SET));
    // 1.21.1: a configured feature (data/nookworks/worldgen/configured_feature/dawnwood.json), as vanilla's trees are
    public static final ResourceKey<ConfiguredFeature<?, ?>> DAWNWOOD_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, Nookworks.id("dawnwood"));
    // the one tree; no two-by-two form, no flowering form (26.3: trees, mega trees, flower trees, shortest)
    public static final TreeGrower DAWNWOOD_GROWER = new TreeGrower("nookworks:dawnwood", Optional.empty(),
            Optional.of(DAWNWOOD_TREE), Optional.empty());

    private static final MapColor HEARTWOOD = MapColor.TERRACOTTA_LIGHT_GREEN;
    private static final MapColor BARK = MapColor.TERRACOTTA_BROWN;

    // an axe strips the bark, as a vanilla log's: neoforge:transformables (data_maps/block) adds that to the axe's turns
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_DAWNWOOD_LOG = log("stripped_dawnwood_log", HEARTWOOD);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_DAWNWOOD_WOOD = log("stripped_dawnwood_wood", HEARTWOOD);
    public static final DeferredBlock<RotatedPillarBlock> DAWNWOOD_LOG = log("dawnwood_log", BARK);
    public static final DeferredBlock<RotatedPillarBlock> DAWNWOOD_WOOD = log("dawnwood_wood", BARK);
    public static final DeferredBlock<Block> DAWNWOOD_PLANKS = ModBlocks.BLOCKS.registerSimpleBlock("dawnwood_planks",
            Props.block(p -> wood(p).strength(2.0F, 3.0F)));
    public static final DeferredBlock<StairBlock> DAWNWOOD_STAIRS = ModBlocks.BLOCKS.registerBlock("dawnwood_stairs",
            p -> new StairBlock(DAWNWOOD_PLANKS.get().defaultBlockState(), p), Props.block(p -> wood(p).strength(2.0F, 3.0F)));
    public static final DeferredBlock<SlabBlock> DAWNWOOD_SLAB = ModBlocks.BLOCKS.registerBlock("dawnwood_slab",
            SlabBlock::new, Props.block(p -> wood(p).strength(2.0F, 3.0F)));
    public static final DeferredBlock<FenceBlock> DAWNWOOD_FENCE = ModBlocks.BLOCKS.registerBlock("dawnwood_fence",
            FenceBlock::new, Props.block(p -> wood(p).strength(2.0F, 3.0F)));
    public static final DeferredBlock<FenceGateBlock> DAWNWOOD_FENCE_GATE = ModBlocks.BLOCKS.registerBlock("dawnwood_fence_gate",
            p -> new FenceGateBlock(DAWNWOOD_TYPE, p), Props.block(p -> wood(p).forceSolidOn().strength(2.0F, 3.0F)));
    public static final DeferredBlock<DoorBlock> DAWNWOOD_DOOR = ModBlocks.BLOCKS.registerBlock("dawnwood_door",
            p -> new DoorBlock(DAWNWOOD_SET, p), Props.block(p -> wood(p).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<TrapDoorBlock> DAWNWOOD_TRAPDOOR = ModBlocks.BLOCKS.registerBlock("dawnwood_trapdoor",
            p -> new TrapDoorBlock(DAWNWOOD_SET, p), Props.block(p -> wood(p).strength(3.0F).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)));
    public static final DeferredBlock<ButtonBlock> DAWNWOOD_BUTTON = ModBlocks.BLOCKS.registerBlock("dawnwood_button",
            p -> new ButtonBlock(DAWNWOOD_SET, 30, p), Props.block(p -> p.noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<PressurePlateBlock> DAWNWOOD_PRESSURE_PLATE = ModBlocks.BLOCKS.registerBlock(
            "dawnwood_pressure_plate", p -> new PressurePlateBlock(DAWNWOOD_SET, p),
            Props.block(p -> wood(p).forceSolidOn().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<DawnwoodLeavesBlock> DAWNWOOD_LEAVES = ModBlocks.BLOCKS.registerBlock("dawnwood_leaves",
            DawnwoodLeavesBlock::new, Props.block(p -> p.mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(SoundType.GRASS)
                    .noOcclusion().isValidSpawn((state, level, pos, type) -> false).isSuffocating((s, l, p2) -> false)
                    .isViewBlocking((s, l, p2) -> false).ignitedByLava().pushReaction(PushReaction.DESTROY)
                    .isRedstoneConductor((s, l, p2) -> false)));
    public static final DeferredBlock<SaplingBlock> DAWNWOOD_SAPLING = ModBlocks.BLOCKS.registerBlock("dawnwood_sapling",
            p -> new SaplingBlock(DAWNWOOD_GROWER, p), Props.block(p -> p.mapColor(MapColor.PLANT).noCollission().randomTicks()
                    .instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<FlowerPotBlock> POTTED_DAWNWOOD_SAPLING = ModBlocks.BLOCKS.registerBlock(
            "potted_dawnwood_sapling", p -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, DAWNWOOD_SAPLING, p),
            Props.block(p -> p.instabreak().noOcclusion().pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<StandingSignBlock> DAWNWOOD_SIGN = ModBlocks.BLOCKS.registerBlock("dawnwood_sign",
            p -> new StandingSignBlock(DAWNWOOD_TYPE, p), Props.block(p -> sign(p)));
    public static final DeferredBlock<WallSignBlock> DAWNWOOD_WALL_SIGN = ModBlocks.BLOCKS.registerBlock("dawnwood_wall_sign",
            p -> new WallSignBlock(DAWNWOOD_TYPE, p), Props.block(p -> sign(wallOf(p, DAWNWOOD_SIGN))));
    public static final DeferredBlock<CeilingHangingSignBlock> DAWNWOOD_HANGING_SIGN = ModBlocks.BLOCKS.registerBlock(
            "dawnwood_hanging_sign", p -> new CeilingHangingSignBlock(DAWNWOOD_TYPE, p), Props.block(p -> sign(p)));
    public static final DeferredBlock<WallHangingSignBlock> DAWNWOOD_WALL_HANGING_SIGN = ModBlocks.BLOCKS.registerBlock(
            "dawnwood_wall_hanging_sign", p -> new WallHangingSignBlock(DAWNWOOD_TYPE, p),
            Props.block(p -> sign(wallOf(p, DAWNWOOD_HANGING_SIGN))));

    /** Every block item of the set, in creative-tab order. The sapling's item is the Rare Seed. */
    public static final List<DeferredItem<? extends BlockItem>> DAWNWOOD_ITEMS = new ArrayList<>();
    // PORT: 1.21.1 names a plain BlockItem after its block (the Rice Plant, not Brown Rice); ItemNameBlockItem keeps the
    // item's own name, as 26.3 gives every item
    public static final DeferredItem<BlockItem> RARE_SEED = ModItems.ITEMS.registerItem("rare_seed",
            p -> new net.minecraft.world.item.ItemNameBlockItem(DAWNWOOD_SAPLING.get(), p));

    static {
        for (DeferredBlock<? extends Block> block : List.of(DAWNWOOD_LOG, DAWNWOOD_WOOD, STRIPPED_DAWNWOOD_LOG,
                STRIPPED_DAWNWOOD_WOOD, DAWNWOOD_PLANKS, DAWNWOOD_STAIRS, DAWNWOOD_SLAB, DAWNWOOD_FENCE, DAWNWOOD_FENCE_GATE,
                DAWNWOOD_TRAPDOOR, DAWNWOOD_BUTTON, DAWNWOOD_PRESSURE_PLATE, DAWNWOOD_LEAVES)) {
            DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(block));
        }
        DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerItem("dawnwood_door", p -> new DoubleHighBlockItem(DAWNWOOD_DOOR.get(), p),
                Props.item(p -> p)));
        // as vanilla's signs (1.21.1's SignItem opens the editor once the sign is set down, as 26.3's signText does)
        DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerItem("dawnwood_sign",
                p -> new SignItem(p, DAWNWOOD_SIGN.get(), DAWNWOOD_WALL_SIGN.get()),
                Props.item(p -> p.stacksTo(16))));
        DAWNWOOD_ITEMS.add(ModItems.ITEMS.registerItem("dawnwood_hanging_sign",
                p -> new HangingSignItem(DAWNWOOD_HANGING_SIGN.get(), DAWNWOOD_WALL_HANGING_SIGN.get(), p),
                Props.item(p -> p.stacksTo(16))));
    }

    public static final DeferredItem<BoatItem> DAWNWOOD_BOAT = ModItems.ITEMS.registerItem("dawnwood_boat",
            p -> new EntityBoatItem(ModEntities.DAWNWOOD_BOAT, false, p), Props.item(p -> p.stacksTo(1)));
    public static final DeferredItem<BoatItem> DAWNWOOD_CHEST_BOAT = ModItems.ITEMS.registerItem("dawnwood_chest_boat",
            p -> new EntityBoatItem(ModEntities.DAWNWOOD_CHEST_BOAT, true, p), Props.item(p -> p.stacksTo(1)));

    /** A sign's properties, as vanilla gives its own: no collision, soft, and catching from lava. */
    private static BlockBehaviour.Properties sign(BlockBehaviour.Properties p) {
        return p.mapColor(HEARTWOOD).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollission().strength(1.0F).ignitedByLava();
    }

    /**
     * A wall sign drops, and is named, as the sign it was placed from (1.21.1's wall signs take their item's name; its
     * properties are built before the sign is registered, so the loot table is looked up later).
     */
    private static BlockBehaviour.Properties wallOf(BlockBehaviour.Properties p, java.util.function.Supplier<? extends Block> standing) {
        return p.lootFrom(standing);
    }

    /**
     * The woods that get a support (and the furniture), in the order vanilla lists its woods; Dawnwood last. Poplar,
     * 26.3's wood, since 1.2.0 (the user, 2026-10-23: it had been left out).
     * PORT: 1.21.1 has no pale oak or poplar: their supports, furniture and turned planks are left out.
     */
    public static final List<String> SUPPORT_WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "crimson", "warped", "bamboo", "dawnwood");
    public static final Map<String, DeferredBlock<SupportBlock>> SUPPORTS;
    public static final Map<String, DeferredItem<BlockItem>> SUPPORT_ITEMS;

    static {
        Map<String, DeferredBlock<SupportBlock>> blocks = new LinkedHashMap<>();
        Map<String, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        for (String wood : SUPPORT_WOODS) {
            boolean nether = wood.equals("crimson") || wood.equals("warped");
            DeferredBlock<SupportBlock> block = ModBlocks.BLOCKS.registerBlock(wood + "_support", SupportBlock::new, Props.block(p -> {
                p.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).noOcclusion()
                        .sound(nether ? SoundType.NETHER_WOOD : wood.equals("bamboo") ? SoundType.BAMBOO_WOOD
                                : wood.equals("cherry") ? SoundType.CHERRY_WOOD : SoundType.WOOD);
                return nether ? p : p.ignitedByLava();
            }));
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
    /** A Bench for every wood too: a long seat that joins its neighbours (tools/gen_wood_data.py, gen_bench.py). */
    public static final Map<String, DeferredBlock<BenchBlock>> BENCHES;
    public static final Map<String, DeferredItem<BlockItem>> BENCH_ITEMS;
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
        Map<String, DeferredBlock<BenchBlock>> benches = new LinkedHashMap<>();
        Map<String, DeferredItem<BlockItem>> benchItems = new LinkedHashMap<>();
        Map<String, DeferredBlock<WallShelfBlock>> shelves = new LinkedHashMap<>();
        Map<String, DeferredItem<BlockItem>> shelfItems = new LinkedHashMap<>();
        for (String wood : SUPPORT_WOODS) {
            DeferredBlock<ChairBlock> chair = ModBlocks.BLOCKS.registerBlock(wood + "_chair", ChairBlock::new, Props.block(p -> furniture(p, wood)));
            DeferredBlock<StoolBlock> stool = ModBlocks.BLOCKS.registerBlock(wood + "_stool", StoolBlock::new, Props.block(p -> furniture(p, wood)));
            DeferredBlock<DeskBlock> desk = ModBlocks.BLOCKS.registerBlock(wood + "_desk", DeskBlock::new, Props.block(p -> furniture(p, wood)));
            chairs.put(wood, chair);
            stools.put(wood, stool);
            desks.put(wood, desk);
            chairItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(chair));
            stoolItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(stool));
            deskItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(desk));
            DeferredBlock<BenchBlock> bench = ModBlocks.BLOCKS.registerBlock(wood + "_bench", BenchBlock::new, Props.block(p -> furniture(p, wood)));
            benches.put(wood, bench);
            benchItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(bench));
            DeferredBlock<WallShelfBlock> shelf = ModBlocks.BLOCKS.registerBlock(wood + "_wall_shelf", WallShelfBlock::new, Props.block(p -> furniture(p, wood)));
            shelves.put(wood, shelf);
            shelfItems.put(wood, ModItems.ITEMS.registerSimpleBlockItem(shelf));
        }
        CHAIRS = Collections.unmodifiableMap(chairs);
        STOOLS = Collections.unmodifiableMap(stools);
        DESKS = Collections.unmodifiableMap(desks);
        CHAIR_ITEMS = Collections.unmodifiableMap(chairItems);
        STOOL_ITEMS = Collections.unmodifiableMap(stoolItems);
        DESK_ITEMS = Collections.unmodifiableMap(deskItems);
        BENCHES = Collections.unmodifiableMap(benches);
        BENCH_ITEMS = Collections.unmodifiableMap(benchItems);
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
        return ModBlocks.BLOCKS.registerBlock(name, RotatedPillarBlock::new, Props.block(p -> wood(p).mapColor(colour).strength(2.0F)));
    }

    private static BlockBehaviour.Properties wood(BlockBehaviour.Properties p) {
        return p.mapColor(HEARTWOOD).instrument(NoteBlockInstrument.BASS).sound(SoundType.WOOD).ignitedByLava();
    }

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {}

    /** Vanilla's sign and hanging sign block entities, told they belong to Dawnwood's too. */
    @SubscribeEvent
    static void blockEntities(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SIGN, DAWNWOOD_SIGN.get(), DAWNWOOD_WALL_SIGN.get());
        event.modify(BlockEntityType.HANGING_SIGN, DAWNWOOD_HANGING_SIGN.get(), DAWNWOOD_WALL_HANGING_SIGN.get());
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
            SUPPORTS.forEach((wood, support) -> {
                if (!wood.equals("crimson") && !wood.equals("warped")) {
                    fire.nookworks$setFlammable(support.get(), 5, 20);
                    fire.nookworks$setFlammable(CHAIRS.get(wood).get(), 5, 20);
                    fire.nookworks$setFlammable(DESKS.get(wood).get(), 5, 20);
                    fire.nookworks$setFlammable(BENCHES.get(wood).get(), 5, 20);
                    // the stool and the wall shelf were left out: they did not catch fire where the chair beside them did
                    fire.nookworks$setFlammable(STOOLS.get(wood).get(), 5, 20);
                    fire.nookworks$setFlammable(WALL_SHELVES.get(wood).get(), 5, 20);
                }
            });
            // (26.3: the potted sapling joins the empty pot by itself; 1.21.1's NeoForge needs it told)
            ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(DAWNWOOD_SAPLING.getId(), POTTED_DAWNWOOD_SAPLING);
        });
    }

    private ModWoods() {}
}
