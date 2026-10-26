package dev.nookworks.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.nookworks.Nookworks;
import dev.nookworks.block.CrateBlock;
import dev.nookworks.block.CreativeGeneratorBlock;
import dev.nookworks.block.CrusherBlock;
import dev.nookworks.block.DiceBlock;
import dev.nookworks.block.DieselGeneratorBlock;
import dev.nookworks.block.ElectricFurnaceBlock;
import dev.nookworks.block.ElectrolyticCellBlock;
import dev.nookworks.block.EnergyCableBlock;
import dev.nookworks.block.FertileSoilBlock;
import dev.nookworks.block.GeneratorBlock;
import dev.nookworks.block.HubStoneBlock;
import dev.nookworks.block.ItemWarpReceiverBlock;
import dev.nookworks.block.ItemWarpSenderBlock;
import dev.nookworks.block.LaserBlock;
import dev.nookworks.block.HorizontalItemWarpBlock;
import dev.nookworks.block.MagnetChestBlock;
import dev.nookworks.block.NestedTimerBlock;
import dev.nookworks.block.OilLampBlock;
import dev.nookworks.block.PebblesBlock;
import dev.nookworks.block.PlanterBlock;
import dev.nookworks.block.PrintingPressBlock;
import dev.nookworks.block.RepairBoxBlock;
import dev.nookworks.block.RopeBlock;
import dev.nookworks.block.SconceBlock;
import dev.nookworks.block.SifterBlock;
import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.StoneMakerBlock;
import dev.nookworks.block.StorageBlock;
import dev.nookworks.block.ThinRopeBlock;
import dev.nookworks.block.TrashCanBlock;
import dev.nookworks.block.WallSconceBlock;
import dev.nookworks.block.WorkbenchBlock;
import dev.nookworks.darkmatter.DarkMatterBlock;
import dev.nookworks.decor.PlacedBottlesBlock;
import dev.nookworks.experience.AbsorptionPadBlock;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperiencePipeBlock;
import dev.nookworks.experience.ExperienceTankBlock;
import dev.nookworks.experience.LiquidExperienceBlock;
import dev.nookworks.fluid.CopperPipeBlock;
import dev.nookworks.fluid.CopperTankBlock;
import dev.nookworks.fluid.SightGlassBlock;
import dev.nookworks.fluid.ValveBlock;
import dev.nookworks.fluid.WaterPumpBlock;
import dev.nookworks.mercury.MercuryBlock;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.mercury.MercuryMachineBlock;
import dev.nookworks.mercury.MercuryPipeBlock;
import dev.nookworks.mercury.MercuryTankBlock;
import dev.nookworks.mercury.RetortBlock;
import dev.nookworks.mobcapture.SpawnEggBrewerBlock;
import dev.nookworks.sorter.LegacySorterBlock;
import dev.nookworks.sorter.SorterGear;
import dev.nookworks.sorter.SorterTier;
import dev.nookworks.spawner.SpawnerControllerBlock;
import dev.nookworks.spawner.SpawnerLinkReceiverBlock;
import dev.nookworks.spawner.SpawnerLinkSenderBlock;
import dev.nookworks.storage.StorageTier;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.world.level.block.WeatheringLanternBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Nookworks.MODID);

    /** As the Storage Barrels: no explosion breaks a block this resistant (see {@link #keeperProperties}). */
    public static final float KEEPER_BLAST_RESISTANCE = 1200.0F;

    public static final DeferredBlock<RopeBlock> ROPE = BLOCKS.registerBlock("rope", RopeBlock::new,
            p -> p.mapColor(MapColor.WOOL).noCollision().strength(0.2F).sound(SoundType.WOOL)
                    .ignitedByLava().pushReaction(PushReaction.POPPED));

    /** A cord to hang things from, not to climb (it is not in #climbable). */
    public static final DeferredBlock<ThinRopeBlock> THIN_ROPE = BLOCKS.registerBlock("thin_rope",
            ThinRopeBlock::new, p -> p.mapColor(MapColor.WOOL).noCollision().strength(0.2F)
                    .sound(SoundType.WOOL).ignitedByLava().pushReaction(PushReaction.POPPED));

    public static final DeferredBlock<SconceBlock> SCONCE = BLOCKS.registerBlock("sconce", SconceBlock::new,
            p -> sconceProperties(p));

    public static final DeferredBlock<WallSconceBlock> WALL_SCONCE = BLOCKS.registerBlock("wall_sconce", WallSconceBlock::new,
            p -> sconceProperties(p.overrideLootTable(SCONCE.get().getLootTable())
                    .overrideDescription(SCONCE.get().getDescriptionId())));

    /** Oil Lamp (油火皿): a clay dish of oil with a wick; its light only while lit. */
    public static final DeferredBlock<OilLampBlock> OIL_LAMP = BLOCKS.registerBlock("oil_lamp",
            OilLampBlock::new, p -> p.mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.3F).noOcclusion()
                    .sound(SoundType.DECORATED_POT).pushReaction(PushReaction.POPPED)
                    .lightLevel(state -> state.getValue(OilLampBlock.LIT) ? OilLampBlock.LIGHT_LEVEL : 0));
    public static final DeferredBlock<WorkbenchBlock> IRON_WORKBENCH = BLOCKS.registerBlock("iron_workbench",
            WorkbenchBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<WorkbenchBlock> BRASS_WORKBENCH = BLOCKS.registerBlock("brass_workbench",
            WorkbenchBlock::new, p -> p.mapColor(MapColor.GOLD).strength(3.0F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<CrateBlock> CRATE = BLOCKS.registerBlock("crate", CrateBlock::new,
            p -> p.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F)
                    .sound(SoundType.WOOD).ignitedByLava());

    public static final DeferredBlock<Block> ELEVATOR = BLOCKS.registerSimpleBlock("elevator",
            p -> stoneProperties(p));

    public static final DeferredBlock<ItemWarpSenderBlock> ITEM_WARP_SENDER = BLOCKS.registerBlock("item_warp_sender",
            ItemWarpSenderBlock::new, p -> stoneProperties(p));
    public static final DeferredBlock<ItemWarpReceiverBlock> ITEM_WARP_RECEIVER = BLOCKS.registerBlock("item_warp_receiver",
            ItemWarpReceiverBlock::new, p -> stoneProperties(p));
    public static final DeferredBlock<HorizontalItemWarpBlock> HORIZONTAL_ITEM_WARP_SENDER = BLOCKS.registerBlock("horizontal_item_warp_sender",
            p -> new HorizontalItemWarpBlock(true, p), p -> stoneProperties(p));
    public static final DeferredBlock<HorizontalItemWarpBlock> HORIZONTAL_ITEM_WARP_RECEIVER = BLOCKS.registerBlock("horizontal_item_warp_receiver",
            p -> new HorizontalItemWarpBlock(false, p), p -> stoneProperties(p));

    // A sieve on legs with a funnel under it, dropping into whatever stands below: a chest there has to open, and a
    // chest will not open under a block that carries redstone. The cabinet machines stay solid, as they look.
    public static final DeferredBlock<SifterBlock> SIFTER = BLOCKS.registerBlock("sifter", SifterBlock::new,
            p -> machineProperties(p, true).isRedstoneConductor((state, level, pos) -> false));

    public static final DeferredBlock<GeneratorBlock> GENERATOR = BLOCKS.registerBlock("generator", GeneratorBlock::new,
            p -> keeperProperties(p).lightLevel(state -> state.getValue(GeneratorBlock.LIT) ? GeneratorBlock.LIGHT_LEVEL : 0));
    // No fire to light a room with: its lamp glows, as a quad of its own. Open, for the stack standing on its lid.
    public static final DeferredBlock<DieselGeneratorBlock> DIESEL_GENERATOR = BLOCKS.registerBlock("diesel_generator",
            DieselGeneratorBlock::new, p -> keeperProperties(p).noOcclusion());
    // Creative mode's, as vanilla's command block is: no survival player breaks it (a creative one does at a touch), no
    // explosion, wither or dragon either, and it drops nothing.
    public static final DeferredBlock<CreativeGeneratorBlock> CREATIVE_GENERATOR = BLOCKS.registerBlock("creative_generator",
            CreativeGeneratorBlock::new, p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.METAL).noLootTable());
    public static final DeferredBlock<CrusherBlock> CRUSHER = BLOCKS.registerBlock("crusher", CrusherBlock::new,
            p -> machineProperties(p, true));
    public static final DeferredBlock<PrintingPressBlock> PRINTING_PRESS = BLOCKS.registerBlock("printing_press",
            PrintingPressBlock::new, p -> machineProperties(p, true));
    public static final DeferredBlock<ElectrolyticCellBlock> ELECTROLYTIC_CELL = BLOCKS.registerBlock("electrolytic_cell",
            ElectrolyticCellBlock::new, p -> keeperProperties(p));
    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.registerBlock("electric_furnace",
            ElectricFurnaceBlock::new, p -> machineProperties(p, false).lightLevel(
                    state -> state.getValue(ElectricFurnaceBlock.LIT) ? ElectricFurnaceBlock.LIGHT_LEVEL : 0));
    public static final DeferredBlock<StoneMakerBlock> STONE_MAKER = BLOCKS.registerBlock("stone_maker", StoneMakerBlock::new,
            p -> machineProperties(p, false));
    public static final DeferredBlock<EnergyCableBlock> ENERGY_CABLE = BLOCKS.registerBlock("energy_cable", EnergyCableBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.3F).sound(SoundType.COPPER).noOcclusion().forceSolidOn());

    // Two sorters, by what they tell apart; their speed is the Sorter Gear fitted in them (ModItems).
    public static final DeferredBlock<SorterBlock> ITEM_SORTER = sorter("item_sorter", SorterTier.ITEM, SoundType.IRON);
    public static final DeferredBlock<SorterBlock> PRECISION_SORTER = sorter("precision_sorter", SorterTier.PRECISION, SoundType.NETHERITE_BLOCK);
    // 0.2.0's tiered sorters, kept only to become Item Sorters with their gear (LegacySorterBlock).
    public static final DeferredBlock<LegacySorterBlock> GOLD_SORTER = legacySorter("gold_sorter",
            SorterGear.GOLD, SoundType.METAL);
    public static final DeferredBlock<LegacySorterBlock> DIAMOND_SORTER = legacySorter("diamond_sorter",
            SorterGear.DIAMOND, SoundType.METAL);
    public static final DeferredBlock<LegacySorterBlock> NETHERITE_SORTER = legacySorter("netherite_sorter",
            SorterGear.NETHERITE, SoundType.NETHERITE_BLOCK);

    // Storage Barrels are wooden barrels in metal hoops: they sound as wood, and an axe takes them as well as a pickaxe.
    public static final DeferredBlock<StorageBlock> IRON_STORAGE = storage("iron_storage_barrel", StorageTier.IRON, SoundType.WOOD);
    public static final DeferredBlock<StorageBlock> GOLD_STORAGE = storage("gold_storage_barrel", StorageTier.GOLD, SoundType.WOOD);
    public static final DeferredBlock<StorageBlock> DIAMOND_STORAGE = storage("diamond_storage_barrel", StorageTier.DIAMOND, SoundType.WOOD);
    public static final DeferredBlock<StorageBlock> NETHERITE_STORAGE = storage("netherite_storage_barrel", StorageTier.NETHERITE, SoundType.WOOD);
    public static final DeferredBlock<StorageBlock> VELARIUM_STORAGE = storage("velarium_storage_barrel", StorageTier.VELARIUM, SoundType.WOOD);

    public static final DeferredBlock<SpawnerLinkSenderBlock> SPAWNER_LINK_SENDER = BLOCKS.registerBlock("spawner_link_sender",
            SpawnerLinkSenderBlock::new, p -> metalProperties(p)
                    .lightLevel(state -> state.getValue(SpawnerLinkSenderBlock.LIT) ? SpawnerLinkSenderBlock.LIGHT_LEVEL : 0));
    public static final DeferredBlock<SpawnerLinkReceiverBlock> SPAWNER_LINK_RECEIVER = BLOCKS.registerBlock("spawner_link_receiver",
            SpawnerLinkReceiverBlock::new, p -> metalProperties(p)
                    .lightLevel(state -> state.getValue(SpawnerLinkReceiverBlock.LIT) ? SpawnerLinkReceiverBlock.LIGHT_LEVEL : 0));
    // The console model is a desk with a tilted screen, not a full cube, so it must not hide its neighbours' faces.
    public static final DeferredBlock<SpawnerControllerBlock> SPAWNER_CONTROLLER = BLOCKS.registerBlock("spawner_controller",
            SpawnerControllerBlock::new, p -> metalProperties(p).noOcclusion()
                    .lightLevel(state -> state.getValue(SpawnerControllerBlock.LIT) ? SpawnerControllerBlock.LIGHT_LEVEL : 0));

    // ---- Mercury ----
    public static final DeferredBlock<MercuryBlock> MERCURY = BLOCKS.registerBlock("mercury",
            p -> new MercuryBlock(MercuryFluids.SOURCE.get(), p),
            p -> p.mapColor(MapColor.METAL).replaceable().noCollision().strength(100.0F)
                    .pushReaction(PushReaction.POPPED).noLootTable().liquid().sound(SoundType.EMPTY));
    public static final DeferredBlock<MercuryTankBlock> MERCURY_TANK = BLOCKS.registerBlock("mercury_tank",
            MercuryTankBlock::new, p -> keeperProperties(p));
    public static final DeferredBlock<MercuryPipeBlock> MERCURY_PIPE = BLOCKS.registerBlock("mercury_pipe",
            MercuryPipeBlock::new, p -> p.mapColor(MapColor.METAL)
                    .strength(1.5F).sound(SoundType.IRON).noOcclusion().forceSolidOn());
    public static final DeferredBlock<RetortBlock> RETORT = BLOCKS.registerBlock("retort",
            RetortBlock::new, p -> keeperProperties(p).lightLevel(
                    state -> state.getValue(RetortBlock.LIT)
                            ? RetortBlock.LIGHT_LEVEL : 0));
    public static final DeferredBlock<MercuryMachineBlock> STIRRING_TANK = BLOCKS.registerBlock("stirring_tank",
            p -> new MercuryMachineBlock(MercuryMachineBlock.Kind.STIRRING_TANK, p),
            p -> keeperProperties(p).noOcclusion());
    public static final DeferredBlock<MercuryMachineBlock> DISTILLER = BLOCKS.registerBlock("distiller",
            p -> new MercuryMachineBlock(MercuryMachineBlock.Kind.DISTILLER, p),
            p -> keeperProperties(p).noOcclusion());
    public static final DeferredBlock<Block> DISTILLATION_COLUMN = BLOCKS.registerSimpleBlock("distillation_column",
            p -> machineProperties(p, true));
    public static final DeferredBlock<Block> CONDENSER = BLOCKS.registerSimpleBlock("condenser", p -> machineProperties(p, true));
    /** The blackened steel frame every powered machine is built on. A crafting material, nothing more. */
    public static final DeferredBlock<Block> MACHINE_CHASSIS = BLOCKS.registerSimpleBlock("machine_chassis", p -> machineProperties(p, true));
    /** A lantern put out (dev.nookworks.decor.Lanterns): vanilla's lantern, its flame behind sooty glass, and no light. */
    public static final DeferredBlock<LanternBlock> UNLIT_LANTERN = BLOCKS.registerBlock("unlit_lantern",
            LanternBlock::new, ModBlocks::unlitLanternProperties);
    public static final DeferredBlock<LanternBlock> UNLIT_SOUL_LANTERN = BLOCKS.registerBlock(
            "unlit_soul_lantern", LanternBlock::new, ModBlocks::unlitLanternProperties);
    /**
     * A copper lantern put out, for each of vanilla's eight, by the lit one's id ("unlit_" before it): the four that
     * weather go on weathering as the lit ones do, and take wax (data maps neoforge:oxidizables and waxables).
     */
    public static final Map<String, DeferredBlock<LanternBlock>> UNLIT_COPPER_LANTERNS = unlitCopperLanterns();
    /** Books laid down on a block, up to three in a pile (dev.nookworks.decor.PlacedBooksBlock). No item: books lay it. */
    public static final DeferredBlock<dev.nookworks.decor.PlacedBooksBlock> PLACED_BOOKS = BLOCKS.registerBlock("placed_books",
            dev.nookworks.decor.PlacedBooksBlock::new, p -> p.mapColor(MapColor.COLOR_BROWN).strength(0.2F)
                    .sound(SoundType.CHISELED_BOOKSHELF).noOcclusion().forceSolidOff().pushReaction(PushReaction.POPPED));
    /** A Bookend: books stand upright in it, spines out, up to five (dev.nookworks.decor.BookendBlock). */
    public static final DeferredBlock<dev.nookworks.decor.BookendBlock> BOOKEND = BLOCKS.registerBlock("bookend",
            dev.nookworks.decor.BookendBlock::new, p -> p.mapColor(MapColor.METAL).strength(0.8F)
                    .sound(SoundType.LANTERN).noOcclusion().forceSolidOff().pushReaction(PushReaction.POPPED));
    /** Books standing on in a row past a full Bookend, five to a block (dev.nookworks.decor.BookendBlock). No item. */
    public static final DeferredBlock<dev.nookworks.decor.BookendBlock> BOOK_ROW = BLOCKS.registerBlock("book_row",
            p -> new dev.nookworks.decor.BookendBlock(p, false), p -> p.mapColor(MapColor.COLOR_BROWN).strength(0.2F)
                    .sound(SoundType.CHISELED_BOOKSHELF).noOcclusion().forceSolidOff().pushReaction(PushReaction.POPPED));
    /** Bottles set down on a block, up to four (dev.nookworks.decor.PlacedBottlesBlock); no item of its own. */
    public static final DeferredBlock<PlacedBottlesBlock> PLACED_BOTTLES = BLOCKS.registerBlock("placed_bottles",
            PlacedBottlesBlock::new, p -> p.mapColor(MapColor.NONE).strength(0.3F).sound(SoundType.GLASS)
                    .noOcclusion().pushReaction(PushReaction.POPPED));
    /** A cauldron of vegetable oil, filled by bucket or pipe: Velarium is quenched in it (dev.nookworks.quench). */
    public static final DeferredBlock<dev.nookworks.quench.OilCauldronBlock> VEGETABLE_OIL_CAULDRON = BLOCKS.registerBlock(
            "vegetable_oil_cauldron", dev.nookworks.quench.OilCauldronBlock::new,
            p -> p.mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(2.0F).noOcclusion());
    public static final DeferredBlock<Block> VELARIUM_BLOCK = BLOCKS.registerSimpleBlock("velarium_block",
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK));

    // ---- Liquid Experience ----
    public static final DeferredBlock<LiquidBlock> LIQUID_EXPERIENCE = BLOCKS.registerBlock("liquid_experience",
            p -> new LiquidExperienceBlock(ExperienceFluids.SOURCE.get(), p),
            p -> p.mapColor(MapColor.EMERALD).replaceable().noCollision().strength(100.0F)
                    .pushReaction(PushReaction.POPPED).noLootTable().liquid()
                    .lightLevel(s -> 7).sound(SoundType.EMPTY));
    public static final DeferredBlock<ExperienceTankBlock> EXPERIENCE_TANK = BLOCKS.registerBlock("experience_tank",
            ExperienceTankBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(3.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL)); // a steel cabinet with a window
    /** What the Crusher makes of cobbled deepslate and netherrack, for the Sifter: rock is crushed before it is sifted. */
    public static final DeferredBlock<ColoredFallingBlock> DEEPSLATE_GRAVEL = BLOCKS.registerBlock(
            "deepslate_gravel", p -> new ColoredFallingBlock(new ColorRGBA(0xFF46464E), p),
            p -> p.mapColor(MapColor.DEEPSLATE).strength(0.6F).sound(SoundType.GRAVEL));
    public static final DeferredBlock<ColoredFallingBlock> NETHER_GRIT = BLOCKS.registerBlock(
            "nether_grit", p -> new ColoredFallingBlock(new ColorRGBA(0xFF7A3030), p),
            p -> p.mapColor(MapColor.NETHER).strength(0.5F).sound(SoundType.NETHERRACK));
    /** And of End Stone (the user, 2026-10-10): sifted, chorus, ender pearls and the End's other things come out. */
    public static final DeferredBlock<ColoredFallingBlock> END_GRAVEL = BLOCKS.registerBlock(
            "end_gravel", p -> new ColoredFallingBlock(new ColorRGBA(0xFFC9C98C), p),
            p -> p.mapColor(MapColor.SAND).strength(0.6F).sound(SoundType.GRAVEL));
    /**
     * Ground halfway between dirt and cobblestone, for paths and yards: stones packed close in earth. As firm as it is
     * between the two, and dug with a shovel or broken with a pickaxe alike; it does not fall.
     */
    public static final DeferredBlock<Block> STONY_DIRT = BLOCKS.registerSimpleBlock("stony_dirt",
            p -> p.mapColor(MapColor.DIRT).strength(1.2F).sound(SoundType.GRAVEL));
    /** Small stones to scatter, one to four in a block: picked up at a touch, by hand or pickaxe alike; a piston pops them. */
    public static final DeferredBlock<PebblesBlock> PEBBLES = BLOCKS.registerBlock("pebbles", PebblesBlock::new,
            p -> p.mapColor(MapColor.STONE).instabreak().noCollision().sound(SoundType.STONE).pushReaction(PushReaction.POPPED));

    public static final DeferredBlock<CopperPipeBlock> COPPER_PIPE = BLOCKS.registerBlock("copper_pipe",
            CopperPipeBlock::new, p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.5F)
                    .sound(SoundType.COPPER).noOcclusion().forceSolidOn());
    /** Fittings in a copper line, and the tank it fills: brass, glass and copper, one maker's set with the pipe. */
    public static final DeferredBlock<SightGlassBlock> SIGHT_GLASS = BLOCKS.registerBlock("sight_glass",
            SightGlassBlock::new, p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.5F)
                    .sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<ValveBlock> VALVE = BLOCKS.registerBlock("valve",
            ValveBlock::new, p -> p.mapColor(MapColor.GOLD).strength(1.5F)
                    .sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<CopperTankBlock> COPPER_TANK = BLOCKS.registerBlock("copper_tank",
            CopperTankBlock::new, p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.0F, KEEPER_BLAST_RESISTANCE)
                    .sound(SoundType.COPPER).noOcclusion()); // a keeper (keeperProperties), in copper
    public static final DeferredBlock<WaterPumpBlock> WATER_PUMP = BLOCKS.registerBlock("water_pump",
            WaterPumpBlock::new, p -> machineProperties(p, true));
    public static final DeferredBlock<ExperiencePipeBlock> EXPERIENCE_PIPE = BLOCKS.registerBlock("experience_pipe",
            ExperiencePipeBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.5F)
                    .noOcclusion().sound(SoundType.GLASS).forceSolidOn());
    public static final DeferredBlock<AbsorptionPadBlock> ABSORPTION_PAD = BLOCKS.registerBlock("absorption_pad",
            AbsorptionPadBlock::new,
            p -> metalProperties(p).noOcclusion().forceSolidOn());

    public static final DeferredBlock<RepairBoxBlock> REPAIR_BOX = BLOCKS.registerBlock("repair_box",
            RepairBoxBlock::new, p -> metalProperties(p).noOcclusion());
    /** Puts out the light round it (dev.nookworks.darkmatter). Rings as the amethyst it is made from. */
    public static final DeferredBlock<DarkMatterBlock> DARK_MATTER = BLOCKS.registerBlock("dark_matter",
            DarkMatterBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST));
    public static final DeferredBlock<TrashCanBlock> TRASH_CAN = BLOCKS.registerBlock("trash_can",
            TrashCanBlock::new, p -> metalProperties(p).noOcclusion());

    public static final DeferredBlock<FertileSoilBlock> FERTILE_SOIL = BLOCKS.registerBlock("nourished_soil",
            FertileSoilBlock::new, p -> p.mapColor(MapColor.DIRT)
                    .strength(0.5F).sound(SoundType.GRAVEL).randomTicks());

    public static final DeferredBlock<LaserBlock> LASER = BLOCKS.registerBlock("laser",
            LaserBlock::new, p -> metalProperties(p).noOcclusion());
    public static final DeferredBlock<PlanterBlock> PLANTER = BLOCKS.registerBlock("planter",
            PlanterBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.0F)
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<MagnetChestBlock> MAGNET_CHEST = BLOCKS.registerBlock("magnet_chest",
            MagnetChestBlock::new, p -> metalProperties(p));

    public static final DeferredBlock<HubStoneBlock> HUB_STONE = BLOCKS.registerBlock("hub_stone",
            HubStoneBlock::new, p -> stoneProperties(p));

    public static final DeferredBlock<SpawnEggBrewerBlock> SPAWN_EGG_BREWER = BLOCKS.registerBlock("spawn_egg_brewer",
            SpawnEggBrewerBlock::new, p -> p.mapColor(MapColor.METAL).strength(0.5F).lightLevel(state -> 1).noOcclusion()
                    .sound(SoundType.METAL));

    public static final DeferredBlock<NestedTimerBlock> NESTED_TIMER = BLOCKS.registerBlock("nested_timer",
            NestedTimerBlock::new, p -> p.instabreak().sound(SoundType.STONE).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<DiceBlock> DICE = BLOCKS.registerBlock("dice",
            DiceBlock::new, p -> p.mapColor(MapColor.QUARTZ).strength(0.5F).sound(SoundType.BONE_BLOCK)
                    .pushReaction(PushReaction.POPPED).dynamicShape().noOcclusion());

    /**
     * A machine in the family's steel frame (tools/gen_machine_models.py): it breaks and sounds as metal. {@code open}:
     * its shape does not fill the block - a vessel on a plinth, a sieve on legs, a hopper - so it must not hide the
     * faces of the blocks beside it, or the world shows through round it.
     */
    private static BlockBehaviour.Properties machineProperties(BlockBehaviour.Properties p, boolean open) {
        p = metalProperties(p);
        return open ? p.noOcclusion() : p;
    }

    private static BlockBehaviour.Properties metalProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    /**
     * A block that keeps a liquid in its item when broken - a tank, a machine with a tank: it drops to any tool or a
     * bare hand, and no explosion breaks it, as the Storage Barrels, so what it holds is never lost to a missing
     * pickaxe or a creeper (the user, 2026-10-01). A pickaxe is still the quick way.
     */
    private static BlockBehaviour.Properties keeperProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(3.0F, KEEPER_BLAST_RESISTANCE).sound(SoundType.METAL);
    }

    /**
     * A banded barrel: it drops to any tool or a bare hand, as a wooden barrel does (an axe is quicker), and no explosion
     * breaks it, so a creeper cannot scatter thousands of stacks.
     */
    private static DeferredBlock<StorageBlock> storage(String name, StorageTier tier, SoundType sound) {
        return BLOCKS.registerBlock(name, p -> new StorageBlock(tier, p),
                p -> p.mapColor(MapColor.WOOD).strength(3.0F, 1200.0F).sound(sound));
    }

    private static DeferredBlock<SorterBlock> sorter(String name, SorterTier tier, SoundType sound) {
        return BLOCKS.registerBlock(name, p -> new SorterBlock(tier, p),
                p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(sound));
    }

    private static DeferredBlock<LegacySorterBlock> legacySorter(String name, SorterGear gear,
            SoundType sound) {
        return BLOCKS.registerBlock(name, p -> new LegacySorterBlock(gear, p),
                p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(sound));
    }

    private static BlockBehaviour.Properties stoneProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).strength(1.5F, 6.0F)
                .requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties unlitLanternProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).noOcclusion()
                .pushReaction(PushReaction.POPPED);
    }

    private static Map<String, DeferredBlock<LanternBlock>> unlitCopperLanterns() {
        Map<String, DeferredBlock<LanternBlock>> lanterns = new LinkedHashMap<>();
        for (WeatheringCopper.WeatherState age : WeatheringCopper.WeatherState.values()) {
            String lit = WeatheringCopperCollection.PREFIXES.weathering().pick(age) + "copper_lantern";
            lanterns.put(lit, BLOCKS.<LanternBlock>registerBlock("unlit_" + lit, p -> new WeatheringLanternBlock(age, p),
                    ModBlocks::unlitLanternProperties));
        }
        for (WeatheringCopper.WeatherState age : WeatheringCopper.WeatherState.values()) {
            String lit = WeatheringCopperCollection.PREFIXES.waxed().pick(age) + "copper_lantern";
            lanterns.put(lit, BLOCKS.registerBlock("unlit_" + lit, LanternBlock::new, ModBlocks::unlitLanternProperties));
        }
        return lanterns;
    }

    private static BlockBehaviour.Properties sconceProperties(BlockBehaviour.Properties p) {
        return p.noCollision().strength(0.5F)
                .lightLevel(state -> state.getValue(SconceBlock.LIT) ? SconceBlock.LIGHT_LEVEL : 0).sound(SoundType.LANTERN)
                .pushReaction(PushReaction.POPPED);
    }

    private ModBlocks() {}
}
