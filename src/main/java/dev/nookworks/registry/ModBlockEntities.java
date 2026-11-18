package dev.nookworks.registry;

import dev.nookworks.cooking.PlateBlockEntity;
import dev.nookworks.anchor.WarpAnchorBlockEntity;
import dev.nookworks.block.entity.DiceBlockEntity;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.block.entity.CrateBlockEntity;
import dev.nookworks.block.entity.ItemWarpReceiverBlockEntity;
import dev.nookworks.block.entity.ItemWarpSenderBlockEntity;
import dev.nookworks.block.entity.HorizontalItemWarpReceiverBlockEntity;
import dev.nookworks.block.entity.HorizontalItemWarpSenderBlockEntity;
import dev.nookworks.block.entity.MagnetChestBlockEntity;
import dev.nookworks.block.entity.NestedTimerBlockEntity;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.block.entity.TrashCanBlockEntity;
import dev.nookworks.cooking.GrillBlockEntity;
import dev.nookworks.cooking.HandQuernBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.NoodleMakerBlock;
import dev.nookworks.cooking.NukadokoBlockEntity;
import dev.nookworks.cooking.TrayBlockEntity;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.darkmatter.DarkMatterBlockEntity;
import dev.nookworks.decor.PlacedBottlesBlockEntity;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.experience.AbsorptionPadBlockEntity;
import dev.nookworks.experience.ExperiencePipeBlockEntity;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.RepairBoxBlockEntity;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.SightGlassBlockEntity;
import dev.nookworks.fluid.ValveBlockEntity;
import dev.nookworks.fluid.WaterPumpBlockEntity;
import dev.nookworks.machine.CreativeGeneratorBlockEntity;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.DieselGeneratorBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.ElectrolyticCellBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.machine.StoneMakerBlockEntity;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.mercury.MercuryTankBlockEntity;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.mobcapture.SpawnEggBrewerBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerLinkReceiverBlockEntity;
import dev.nookworks.spawner.SpawnerLinkSenderBlockEntity;
import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.ventilation.VentFanBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.nookworks.port.core.BlockEntityTypeOf;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Nookworks.MODID);

    public static final Supplier<BlockEntityType<CrateBlockEntity>> CRATE = BLOCK_ENTITY_TYPES.register("crate",
            () -> BlockEntityTypeOf.create(CrateBlockEntity::new, ModBlocks.CRATE.get()));

    public static final Supplier<BlockEntityType<ItemWarpSenderBlockEntity>> ITEM_WARP_SENDER = BLOCK_ENTITY_TYPES.register(
            "item_warp_sender", () -> BlockEntityTypeOf.create(ItemWarpSenderBlockEntity::new, ModBlocks.ITEM_WARP_SENDER.get()));

    /** Its owner only: one from before owners were kept gets one, of no owner, as its chunk loads. */
    public static final Supplier<BlockEntityType<ItemWarpReceiverBlockEntity>> ITEM_WARP_RECEIVER = BLOCK_ENTITY_TYPES.register(
            "item_warp_receiver", () -> BlockEntityTypeOf.create(ItemWarpReceiverBlockEntity::new, ModBlocks.ITEM_WARP_RECEIVER.get()));
    public static final Supplier<BlockEntityType<HorizontalItemWarpSenderBlockEntity>> HORIZONTAL_ITEM_WARP_SENDER = BLOCK_ENTITY_TYPES.register(
            "horizontal_item_warp_sender", () -> BlockEntityTypeOf.create(HorizontalItemWarpSenderBlockEntity::new, ModBlocks.HORIZONTAL_ITEM_WARP_SENDER.get()));
    public static final Supplier<BlockEntityType<HorizontalItemWarpReceiverBlockEntity>> HORIZONTAL_ITEM_WARP_RECEIVER = BLOCK_ENTITY_TYPES.register(
            "horizontal_item_warp_receiver", () -> BlockEntityTypeOf.create(HorizontalItemWarpReceiverBlockEntity::new, ModBlocks.HORIZONTAL_ITEM_WARP_RECEIVER.get()));

    public static final Supplier<BlockEntityType<MagnetChestBlockEntity>> MAGNET_CHEST = BLOCK_ENTITY_TYPES.register(
            "magnet_chest", () -> BlockEntityTypeOf.create(MagnetChestBlockEntity::new, ModBlocks.MAGNET_CHEST.get()));

    public static final Supplier<BlockEntityType<LaserBlockEntity>> LASER = BLOCK_ENTITY_TYPES.register(
            "laser", () -> BlockEntityTypeOf.create(LaserBlockEntity::new, ModBlocks.LASER.get()));
    public static final Supplier<BlockEntityType<PlanterBlockEntity>> PLANTER = BLOCK_ENTITY_TYPES.register(
            "planter", () -> BlockEntityTypeOf.create(PlanterBlockEntity::new, ModBlocks.PLANTER.get()));

    public static final Supplier<BlockEntityType<MercuryTankBlockEntity>> MERCURY_TANK = BLOCK_ENTITY_TYPES.register(
            "mercury_tank", () -> BlockEntityTypeOf.create(MercuryTankBlockEntity::new, ModBlocks.MERCURY_TANK.get()));
    public static final Supplier<BlockEntityType<RetortBlockEntity>> RETORT = BLOCK_ENTITY_TYPES.register(
            "retort", () -> BlockEntityTypeOf.create(RetortBlockEntity::new, ModBlocks.RETORT.get()));
    public static final Supplier<BlockEntityType<ElectrolyticCellBlockEntity>> ELECTROLYTIC_CELL = BLOCK_ENTITY_TYPES.register(
            "electrolytic_cell", () -> BlockEntityTypeOf.create(ElectrolyticCellBlockEntity::new, ModBlocks.ELECTROLYTIC_CELL.get()));
    public static final Supplier<BlockEntityType<StirringTankBlockEntity>> STIRRING_TANK = BLOCK_ENTITY_TYPES.register(
            "stirring_tank", () -> BlockEntityTypeOf.create(StirringTankBlockEntity::new, ModBlocks.STIRRING_TANK.get()));
    public static final Supplier<BlockEntityType<DistillerBlockEntity>> DISTILLER = BLOCK_ENTITY_TYPES.register(
            "distiller", () -> BlockEntityTypeOf.create(DistillerBlockEntity::new, ModBlocks.DISTILLER.get()));

    public static final Supplier<BlockEntityType<SifterBlockEntity>> SIFTER = BLOCK_ENTITY_TYPES.register("sifter",
            () -> BlockEntityTypeOf.create(SifterBlockEntity::new, ModBlocks.SIFTER.get()));

    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR = BLOCK_ENTITY_TYPES.register("generator",
            () -> BlockEntityTypeOf.create(GeneratorBlockEntity::new, ModBlocks.GENERATOR.get()));
    public static final Supplier<BlockEntityType<DieselGeneratorBlockEntity>> DIESEL_GENERATOR = BLOCK_ENTITY_TYPES.register(
            "diesel_generator", () -> BlockEntityTypeOf.create(DieselGeneratorBlockEntity::new, ModBlocks.DIESEL_GENERATOR.get()));
    public static final Supplier<BlockEntityType<CreativeGeneratorBlockEntity>> CREATIVE_GENERATOR = BLOCK_ENTITY_TYPES.register(
            "creative_generator", () -> BlockEntityTypeOf.create(CreativeGeneratorBlockEntity::new, ModBlocks.CREATIVE_GENERATOR.get()));
    public static final Supplier<BlockEntityType<CrusherBlockEntity>> CRUSHER = BLOCK_ENTITY_TYPES.register("crusher",
            () -> BlockEntityTypeOf.create(CrusherBlockEntity::new, ModBlocks.CRUSHER.get()));
    public static final Supplier<BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE =
            BLOCK_ENTITY_TYPES.register("electric_furnace", () -> BlockEntityTypeOf.create(ElectricFurnaceBlockEntity::new,
                    ModBlocks.ELECTRIC_FURNACE.get()));
    public static final Supplier<BlockEntityType<StoneMakerBlockEntity>> STONE_MAKER = BLOCK_ENTITY_TYPES.register("stone_maker",
            () -> BlockEntityTypeOf.create(StoneMakerBlockEntity::new, ModBlocks.STONE_MAKER.get()));

    public static final Supplier<BlockEntityType<SorterBlockEntity>> SORTER = BLOCK_ENTITY_TYPES.register("sorter",
            () -> BlockEntityTypeOf.create(SorterBlockEntity::new, ModBlocks.ITEM_SORTER.get(), ModBlocks.PRECISION_SORTER.get(),
                    ModBlocks.GOLD_SORTER.get(), ModBlocks.DIAMOND_SORTER.get(), ModBlocks.NETHERITE_SORTER.get()));

    public static final Supplier<BlockEntityType<StorageBlockEntity>> STORAGE = BLOCK_ENTITY_TYPES.register("storage",
            () -> BlockEntityTypeOf.create(StorageBlockEntity::new, ModBlocks.IRON_STORAGE.get(), ModBlocks.GOLD_STORAGE.get(),
                    ModBlocks.DIAMOND_STORAGE.get(), ModBlocks.NETHERITE_STORAGE.get(), ModBlocks.VELARIUM_STORAGE.get()));

    public static final Supplier<BlockEntityType<SpawnerLinkSenderBlockEntity>> SPAWNER_LINK_SENDER = BLOCK_ENTITY_TYPES.register(
            "spawner_link_sender", () -> BlockEntityTypeOf.create(SpawnerLinkSenderBlockEntity::new, ModBlocks.SPAWNER_LINK_SENDER.get()));
    public static final Supplier<BlockEntityType<SpawnerLinkReceiverBlockEntity>> SPAWNER_LINK_RECEIVER = BLOCK_ENTITY_TYPES.register(
            "spawner_link_receiver", () -> BlockEntityTypeOf.create(SpawnerLinkReceiverBlockEntity::new, ModBlocks.SPAWNER_LINK_RECEIVER.get()));
    public static final Supplier<BlockEntityType<SpawnerControllerBlockEntity>> SPAWNER_CONTROLLER = BLOCK_ENTITY_TYPES.register(
            "spawner_controller", () -> BlockEntityTypeOf.create(SpawnerControllerBlockEntity::new, ModBlocks.SPAWNER_CONTROLLER.get()));

    public static final Supplier<BlockEntityType<PrintingPressBlockEntity>> PRINTING_PRESS = BLOCK_ENTITY_TYPES.register(
            "printing_press", () -> BlockEntityTypeOf.create(PrintingPressBlockEntity::new, ModBlocks.PRINTING_PRESS.get()));

    public static final Supplier<BlockEntityType<ExperienceTankBlockEntity>> EXPERIENCE_TANK =
            BLOCK_ENTITY_TYPES.register("experience_tank",
                    () -> BlockEntityTypeOf.create(ExperienceTankBlockEntity::new, ModBlocks.EXPERIENCE_TANK.get()));
    public static final Supplier<BlockEntityType<NestedTimerBlockEntity>> NESTED_TIMER =
            BLOCK_ENTITY_TYPES.register("nested_timer", () -> BlockEntityTypeOf.create(
                    NestedTimerBlockEntity::new, ModBlocks.NESTED_TIMER.get()));
    public static final Supplier<BlockEntityType<IronPotBlockEntity>> IRON_POT = BLOCK_ENTITY_TYPES.register("iron_pot",
            () -> BlockEntityTypeOf.create(IronPotBlockEntity::new, ModCooking.IRON_POT.get(), ModCooking.FRYING_PAN.get()));
    public static final Supplier<BlockEntityType<NukadokoBlockEntity>> NUKADOKO = BLOCK_ENTITY_TYPES.register("nukadoko",
            () -> BlockEntityTypeOf.create(NukadokoBlockEntity::new, ModCooking.NUKADOKO.get()));
    public static final Supplier<BlockEntityType<GrillBlockEntity>> GRILL = BLOCK_ENTITY_TYPES.register("grill",
            () -> BlockEntityTypeOf.create(GrillBlockEntity::new, ModCooking.GRILL.get()));
    public static final Supplier<BlockEntityType<CopperPipeBlockEntity>> COPPER_PIPE = BLOCK_ENTITY_TYPES.register("copper_pipe",
            () -> BlockEntityTypeOf.create(CopperPipeBlockEntity::new, ModBlocks.COPPER_PIPE.get()));
    public static final Supplier<BlockEntityType<RicePolisherBlockEntity>> RICE_POLISHER = BLOCK_ENTITY_TYPES.register(
            "rice_polisher", () -> BlockEntityTypeOf.create(RicePolisherBlockEntity::new, ModCrops.RICE_POLISHER.get()));
    public static final Supplier<BlockEntityType<VentFanBlockEntity>> VENT_FAN = BLOCK_ENTITY_TYPES.register(
            "ventilation_fan", () -> BlockEntityTypeOf.create(VentFanBlockEntity::new, ModVentilation.VENT_FAN.get()));
    public static final Supplier<BlockEntityType<HandQuernBlock.Entity>> HAND_QUERN = BLOCK_ENTITY_TYPES.register(
            "hand_quern", () -> BlockEntityTypeOf.create(HandQuernBlock.Entity::new, ModKitchen.HAND_QUERN.get()));
    public static final Supplier<BlockEntityType<NoodleMakerBlock.Entity>> NOODLE_MAKER = BLOCK_ENTITY_TYPES.register(
            "noodle_maker", () -> BlockEntityTypeOf.create(NoodleMakerBlock.Entity::new, ModKitchen.NOODLE_MAKER.get()));
    public static final Supplier<BlockEntityType<DiceBlockEntity>> DICE = BLOCK_ENTITY_TYPES.register(
            "dice", () -> BlockEntityTypeOf.create(DiceBlockEntity::new, ModBlocks.DICE.get()));
    public static final Supplier<BlockEntityType<dev.nookworks.decor.PlacedBooksBlockEntity>> PLACED_BOOKS = BLOCK_ENTITY_TYPES.register(
            "placed_books", () -> BlockEntityTypeOf.create(dev.nookworks.decor.PlacedBooksBlockEntity::new, ModBlocks.PLACED_BOOKS.get()));
    public static final Supplier<BlockEntityType<dev.nookworks.decor.BookendBlockEntity>> BOOKEND = BLOCK_ENTITY_TYPES.register(
            "bookend", () -> BlockEntityTypeOf.create(dev.nookworks.decor.BookendBlockEntity::new, ModBlocks.BOOKEND.get(),
                    ModBlocks.BOOK_ROW.get()));
    public static final Supplier<BlockEntityType<dev.nookworks.decor.StandingMirrorBlockEntity>> STANDING_MIRROR = BLOCK_ENTITY_TYPES.register(
            "standing_mirror", () -> BlockEntityTypeOf.create(dev.nookworks.decor.StandingMirrorBlockEntity::new, ModBlocks.STANDING_MIRROR.get()));
    public static final Supplier<BlockEntityType<PlacedBottlesBlockEntity>> PLACED_BOTTLES = BLOCK_ENTITY_TYPES.register(
            "placed_bottles", () -> BlockEntityTypeOf.create(PlacedBottlesBlockEntity::new, ModBlocks.PLACED_BOTTLES.get()));
    public static final Supplier<BlockEntityType<TrayBlockEntity>> TRAY = BLOCK_ENTITY_TYPES.register(
            "wooden_tray", () -> BlockEntityTypeOf.create(TrayBlockEntity::new, ModKitchen.WOODEN_TRAY.get()));
    public static final Supplier<BlockEntityType<PlateBlockEntity>> PLATE = BLOCK_ENTITY_TYPES.register(
            "plate", () -> BlockEntityTypeOf.create(PlateBlockEntity::new, ModKitchen.PLATE.get()));
    public static final Supplier<BlockEntityType<dev.nookworks.cooking.PlacedBowlsBlockEntity>> PLACED_BOWLS = BLOCK_ENTITY_TYPES.register(
            "placed_bowls", () -> BlockEntityTypeOf.create(dev.nookworks.cooking.PlacedBowlsBlockEntity::new, ModKitchen.PLACED_BOWLS.get()));
    public static final Supplier<BlockEntityType<dev.nookworks.cooking.ElectricStoveBlockEntity>> ELECTRIC_STOVE = BLOCK_ENTITY_TYPES.register(
            "electric_stove", () -> BlockEntityTypeOf.create(dev.nookworks.cooking.ElectricStoveBlockEntity::new, ModCooking.ELECTRIC_STOVE.get()));
    public static final Supplier<BlockEntityType<dev.nookworks.cooking.KitchenTapBlockEntity>> KITCHEN_TAP = BLOCK_ENTITY_TYPES.register(
            "kitchen_tap", () -> BlockEntityTypeOf.create(dev.nookworks.cooking.KitchenTapBlockEntity::new, ModCooking.KITCHEN_TAP.get()));
    public static final Supplier<BlockEntityType<OilPressBlockEntity>> OIL_PRESS = BLOCK_ENTITY_TYPES.register("oil_press",
            () -> BlockEntityTypeOf.create(OilPressBlockEntity::new, ModOil.OIL_PRESS.get()));
    public static final Supplier<BlockEntityType<MotorBlockEntity>> MOTOR = BLOCK_ENTITY_TYPES.register("motor",
            () -> BlockEntityTypeOf.create(MotorBlockEntity::new, ModOil.MOTOR.get()));
    public static final Supplier<BlockEntityType<DriveWheelBlockEntity>> DRIVE_WHEEL = BLOCK_ENTITY_TYPES.register("drive_wheel",
            () -> BlockEntityTypeOf.create(DriveWheelBlockEntity::new, ModOil.DRIVE_WHEEL.get()));
    public static final Supplier<BlockEntityType<SightGlassBlockEntity>> SIGHT_GLASS = BLOCK_ENTITY_TYPES.register("sight_glass",
            () -> BlockEntityTypeOf.create(SightGlassBlockEntity::new, ModBlocks.SIGHT_GLASS.get()));
    public static final Supplier<BlockEntityType<ValveBlockEntity>> VALVE = BLOCK_ENTITY_TYPES.register("valve",
            () -> BlockEntityTypeOf.create(ValveBlockEntity::new, ModBlocks.VALVE.get()));
    public static final Supplier<BlockEntityType<CopperTankBlockEntity>> COPPER_TANK = BLOCK_ENTITY_TYPES.register("copper_tank",
            () -> BlockEntityTypeOf.create(CopperTankBlockEntity::new, ModBlocks.COPPER_TANK.get()));
    public static final Supplier<BlockEntityType<WaterPumpBlockEntity>> WATER_PUMP = BLOCK_ENTITY_TYPES.register("water_pump",
            () -> BlockEntityTypeOf.create(WaterPumpBlockEntity::new, ModBlocks.WATER_PUMP.get()));
    public static final Supplier<BlockEntityType<ExperiencePipeBlockEntity>> EXPERIENCE_PIPE =
            BLOCK_ENTITY_TYPES.register("experience_pipe",
                    () -> BlockEntityTypeOf.create(ExperiencePipeBlockEntity::new, ModBlocks.EXPERIENCE_PIPE.get()));
    public static final Supplier<BlockEntityType<AbsorptionPadBlockEntity>> ABSORPTION_PAD =
            BLOCK_ENTITY_TYPES.register("absorption_pad",
                    () -> BlockEntityTypeOf.create(AbsorptionPadBlockEntity::new, ModBlocks.ABSORPTION_PAD.get()));

    public static final Supplier<BlockEntityType<RepairBoxBlockEntity>> REPAIR_BOX =
            BLOCK_ENTITY_TYPES.register("repair_box",
                    () -> BlockEntityTypeOf.create(RepairBoxBlockEntity::new, ModBlocks.REPAIR_BOX.get()));
    public static final Supplier<BlockEntityType<DarkMatterBlockEntity>> DARK_MATTER =
            BLOCK_ENTITY_TYPES.register("dark_matter",
                    () -> BlockEntityTypeOf.create(DarkMatterBlockEntity::new, ModBlocks.DARK_MATTER.get()));
    public static final Supplier<BlockEntityType<WarpAnchorBlockEntity>> WARP_ANCHOR =
            BLOCK_ENTITY_TYPES.register("warp_anchor",
                    () -> BlockEntityTypeOf.create(WarpAnchorBlockEntity::new, ModBlocks.WARP_ANCHOR.get()));
    public static final Supplier<BlockEntityType<TrashCanBlockEntity>> TRASH_CAN =
            BLOCK_ENTITY_TYPES.register("trash_can",
                    () -> BlockEntityTypeOf.create(TrashCanBlockEntity::new, ModBlocks.TRASH_CAN.get()));

    public static final Supplier<BlockEntityType<SpawnEggBrewerBlockEntity>> SPAWN_EGG_BREWER = BLOCK_ENTITY_TYPES.register(
            "spawn_egg_brewer", () -> BlockEntityTypeOf.create(SpawnEggBrewerBlockEntity::new, ModBlocks.SPAWN_EGG_BREWER.get()));

    private ModBlockEntities() {}
}
