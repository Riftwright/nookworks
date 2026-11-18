package dev.nookworks.compat.create;

import java.util.List;
import java.util.function.DoubleSupplier;

import com.simibubi.create.api.stress.BlockStressValues;

import dev.nookworks.Nookworks;
import dev.nookworks.machine.ForeignDrives;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Nookworks with Create (the 1.21.1 port only): Create's shafts turn our hand-worked machines and Drive Wheel lines
 * when set straight against them, nothing in between ({@link CreateDrives}). Set up from {@link Nookworks} by name, only
 * when Create is loaded: nothing here is registered, nor any class of Create's loaded, without it. The design and
 * numbers: tools/port1211/design_create.md.
 */
public final class CreateCompat {
    private CreateCompat() {}

    /** Our entries in Create's own registries (only with Create: this class is loaded only then). */
    static final net.neoforged.neoforge.registries.DeferredRegister<com.simibubi.create.api.behaviour.display.DisplaySource> DISPLAY_SOURCES =
            net.neoforged.neoforge.registries.DeferredRegister.create(com.simibubi.create.api.registry.CreateRegistries.DISPLAY_SOURCE, Nookworks.MODID);
    public static final java.util.function.Supplier<ReadoutDisplaySource> READOUT = DISPLAY_SOURCES.register("readout", ReadoutDisplaySource::new);

    /** The retired shaft block of the port's first rounds (MotorShaftBlock): registered here, so only with Create, for worlds holding one. It has no item. */
    static final net.neoforged.neoforge.registries.DeferredRegister.Blocks BLOCKS =
            net.neoforged.neoforge.registries.DeferredRegister.createBlocks(Nookworks.MODID);
    static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, Nookworks.MODID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<MotorShaftBlock> MOTOR_SHAFT = BLOCKS.registerBlock("motor_shaft",
            MotorShaftBlock::new, // as Create's shaft: andesite's, metal on the map, not solid
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE)
                    .mapColor(net.minecraft.world.level.material.MapColor.METAL).forceSolidOff().noOcclusion());
    @SuppressWarnings("DataFlowIssue") // a block entity type takes no data fixer
    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<MotorShaftBlockEntity>> MOTOR_SHAFT_ENTITY =
            BLOCK_ENTITY_TYPES.register("motor_shaft", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                    (pos, state) -> new MotorShaftBlockEntity(CreateCompat.MOTOR_SHAFT_ENTITY.get(), pos, state), MOTOR_SHAFT.get()).build(null));

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        DISPLAY_SOURCES.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, CreateConfig.SPEC, "nookworks-create-server.toml");
        modEventBus.addListener(CreateCompat::setup);
        NeoForge.EVENT_BUS.addListener(CreateDrives::sweep);
        ForeignDrives.set(CreateDrives.INSTANCE);
        CreateRecipes.register();
        if (FMLEnvironment.dist.isClient()) {
            dev.nookworks.compat.create.client.CreateClient.register(modEventBus, modContainer);
        }
        Nookworks.LOGGER.info("Create is installed: Nookworks' Motors, Drive Wheels and hand-worked machines take part in its rotation network");
    }

    /** The hand-worked machines, with the setting that weighs each in su per RPM. */
    static List<Machine> machines() {
        return List.of(new Machine(ModOil.OIL_PRESS.get(), () -> CreateConfig.get(CreateConfig.OIL_PRESS_IMPACT)),
                new Machine(ModCrops.RICE_POLISHER.get(), () -> CreateConfig.get(CreateConfig.RICE_POLISHER_IMPACT)),
                new Machine(ModKitchen.HAND_QUERN.get(), () -> CreateConfig.get(CreateConfig.HAND_QUERN_IMPACT)),
                new Machine(ModKitchen.NOODLE_MAKER.get(), () -> CreateConfig.get(CreateConfig.NOODLE_MAKER_IMPACT)));
    }

    record Machine(Block block, DoubleSupplier impact) {}

    /** Whether a block of ours has a block entity with a readout: one made to look (no level), never placed. */
    static boolean readsOut(Block block) {
        if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Nookworks.MODID)
                || !(block instanceof net.minecraft.world.level.block.EntityBlock entityBlock)) {
            return false;
        }
        try {
            return entityBlock.newBlockEntity(net.minecraft.core.BlockPos.ZERO, block.defaultBlockState()) instanceof dev.nookworks.machine.Readout;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The hand-worked machines' blocks. */
    public static List<Block> machineBlocks() {
        return machines().stream().map(Machine::block).toList();
    }

    /**
     * Create's table of stress impacts, as an add-on gives its blocks theirs: what each machine weighs on the shaft turning
     * it, and the line Create puts in its item's tooltip.
     */
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (Machine machine : machines()) {
                BlockStressValues.IMPACTS.register(machine.block(), machine.impact());
            }
            // the Motor as Create's generators are listed: the capacity its item's tooltip shows, at its own 30 RPM
            BlockStressValues.CAPACITIES.register(dev.nookworks.registry.ModOil.MOTOR.get(), () -> CreateConfig.get(CreateConfig.MOTOR_CAPACITY));
            BlockStressValues.RPM.register(dev.nookworks.registry.ModOil.MOTOR.get(),
                    new BlockStressValues.GeneratedRpm((int) ForeignDrives.MOTOR_RPM, false));
            // a Display Link on any block of ours that has a readout (a machine's status, a tank's liquid) sends its lines
            for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (readsOut(block)) {
                    com.simibubi.create.api.behaviour.display.DisplaySource.BY_BLOCK.add(block, READOUT.get());
                }
            }
        });
    }
}
