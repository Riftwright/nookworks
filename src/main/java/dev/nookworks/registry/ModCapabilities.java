package dev.nookworks.registry;

import dev.nookworks.Nookworks;
import dev.nookworks.block.EnergyCableBlock;
import dev.nookworks.block.PipeSides;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import dev.nookworks.recycling.RecyclerBlock;
import dev.nookworks.recycling.RecyclerBlockEntity;

/**
 * Exposes Nookworks inventories and energy to hoppers, pipes and cables from any mod.
 * Vanilla hoppers already use the Container interfaces directly; this covers everyone else.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class ModCapabilities {
    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        // Items
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.SIFTER.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENERATOR.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.DIESEL_GENERATOR.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CRUSHER.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.STONE_MAKER.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ELECTRIC_FURNACE.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ELECTRIC_FURNACE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.ELECTRIC_FURNACE.get(), (be, side) -> be.experienceHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.SORTER.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBarter.BARTER_TERMINAL_ENTITY.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBarter.BARTER_TERMINAL_ENTITY.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.SPAWN_EGG_BREWER.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModTrading.TRADING_COUNTER_ENTITY.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CRATE.get(), (be, side) -> VanillaContainerWrapper.of(be));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ITEM_WARP_SENDER.get(), (be, side) -> VanillaContainerWrapper.of(be));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.HORIZONTAL_ITEM_WARP_SENDER.get(), (be, side) -> VanillaContainerWrapper.of(be));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.MAGNET_CHEST.get(), (be, side) -> VanillaContainerWrapper.of(be));
        // In from any side, never out: its seeds are for planting (a hopper under it drained them, r8)
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.PLANTER.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.RETORT.get(), Sided::of);
        // The Retort's experience (not its mercury, which stays out of fluid systems): pipes and tanks may draw it.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.RETORT.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.STIRRING_TANK.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ELECTROLYTIC_CELL.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ELECTROLYTIC_CELL.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.ELECTROLYTIC_CELL.get(), (be, side) -> be.waterHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.DISTILLER.get(), Sided::of);
        // its methanol on the way out (since 1.5), drawn by pipes and tanks; not its mercury, which stays out of fluid systems
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.DISTILLER.get(), (be, side) -> be.fluidHandler(side));
        // a Distiller's methanol comes out at its tower's Condenser (2026-11-12); null at any other, for an add-on's to answer
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, be, side) -> dev.nookworks.mercury.DistillerBlockEntity
                .condenserTank(level, pos, side), ModBlocks.CONDENSER.get());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.PRINTING_PRESS.get(), Sided::of);

        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.STORAGE.get(), (be, side) -> be.inventory());

        // Energy
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.DIESEL_GENERATOR.get(), (be, side) -> be.energy());
        // drawn on without end, by our cables and any other mod's, while no signal stops it
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CREATIVE_GENERATOR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CRUSHER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.STONE_MAKER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.SPAWNER_LINK_RECEIVER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.LASER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.PLANTER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.RETORT.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.STIRRING_TANK.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.STIRRING_TANK.get(), (be, side) -> be.fluidHandler(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.DIESEL_GENERATOR.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.DISTILLER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.PRINTING_PRESS.get(), (be, side) -> be.energy());
        // Liquid Experience is the opposite of mercury: a public fluid, so other mods' pipes and tanks can use it.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.EXPERIENCE_TANK.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.EXPERIENCE_PIPE.get(), (be, side) -> be.handlerFor(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.COPPER_PIPE.get(), (be, side) -> be.handlerFor(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.SIGHT_GLASS.get(), (be, side) -> be.handlerFor(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.VALVE.get(), (be, side) -> be.handlerFor(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.COPPER_TANK.get(), (be, side) -> be.handlerFor(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.IRON_POT.get(), (be, side) -> be.liquidHandler());
        // The kitchen: a hopper above feeds it, and one underneath takes out what is done (heat comes up through it).
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.IRON_POT.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GRILL.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.TRAY.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.NUKADOKO.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.WATER_PUMP.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.WATER_PUMP.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.OIL_PRESS.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.OIL_PRESS.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.MOTOR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.RICE_POLISHER.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.HAND_QUERN.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.NOODLE_MAKER.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.REPAIR_BOX.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.ABSORPTION_PAD.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.REPAIR_BOX.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.TRASH_CAN.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.REPAIR_BOX.get(), (be, side) -> be.energy());

        // Recycling: the Recycler's intake (its upper block) is the body's too
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModRecycling.COMPRESSOR_ENTITY.get(), Sided::of);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModRecycling.COMPRESSOR_ENTITY.get(), (be, side) -> be.energy());
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(RecyclerBlock.lower(state, pos)) instanceof RecyclerBlockEntity body
                        ? Sided.of(body, side) : null, ModRecycling.RECYCLER.get());
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(RecyclerBlock.lower(state, pos)) instanceof RecyclerBlockEntity body
                        ? body.energy() : null, ModRecycling.RECYCLER.get());

        // Dawnwood's chest boat, as NeoForge does vanilla's: other mods' unloaders and collectors reach its chest
        event.registerEntity(Capabilities.Item.ENTITY, ModEntities.DAWNWOOD_CHEST_BOAT.get(), (boat, _) -> VanillaContainerWrapper.of(boat));
        event.registerEntity(Capabilities.Item.ENTITY_AUTOMATION, ModEntities.DAWNWOOD_CHEST_BOAT.get(), (boat, _) -> VanillaContainerWrapper.of(boat));

        // Mercury is deliberately not exposed as a fluid capability (see MercuryHandler).
        event.registerBlock(Capabilities.Energy.BLOCK,
                (level, pos, state, be, side) -> level instanceof ServerLevel server
                        && (side == null || !PipeSides.isOff(state, side))
                        ? EnergyCableBlock.networkHandler(server, pos, side) : null,
                ModBlocks.ENERGY_CABLE.get());
    }

    /**
     * A machine's slots as another mod's pipe sees them, from whatever face it is on (the user, 2026-11-12, after a viewer
     * asked how other mods' pipes set to insert or extract meet our machines: the pipe's own setting decides, the face
     * does not - 「そのまま進めてよいです」): in, an item goes only to a slot some face of the machine takes it in by; out,
     * only what some face lets out - what the machine made, never its fuel or ingredients. Each goes through that face's
     * own {@code canPlaceItemThroughFace} / {@code canTakeItemThroughFace} (nookTechnology's Compressor gears ask there).
     * A vanilla hopper does not come here: it keeps the machine's faces, a furnace's (in at the top and sides, out below).
     * Side-less, the same. Only a machine ({@link dev.nookworks.machine.MachineAlerts#isMachine}): a sorter's faces are what
     * it does (in at the back, on out of the front), and it, the Trading Counter and the rest keep them - asked from no
     * side, what comes out is only what comes out below (a hopper's way), so no seeds from a Planter, no takings from a
     * Trading Counter.
     */
    static final class Sided {
        private Sided() {}

        /** A machine's slots from any face ({@link AnyFace}); anything else's by its own faces ({@link Faced}). */
        static net.neoforged.neoforge.transfer.ResourceHandler<ItemResource> of(WorldlyContainer container, @Nullable Direction side) {
            boolean machine = container instanceof net.minecraft.world.level.block.entity.BlockEntity be
                    && dev.nookworks.machine.MachineAlerts.isMachine(be);
            return machine ? new AnyFace(container) : new Faced(container, side);
        }
    }

    /**
     * Not a machine (a sorter, the Trading Counter): its own faces, as NeoForge's wrapper shows them - so a sorter feeding
     * it still reads its faces' rule through it (SorterBlockEntity#refuses). Side-less, only what comes out below.
     */
    static final class Faced extends WorldlyContainerWrapper {
        private final WorldlyContainer container;
        private final boolean sideless;

        Faced(WorldlyContainer container, @Nullable Direction side) {
            super(container, side);
            this.container = container;
            this.sideless = side == null;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (this.sideless && !(java.util.Arrays.stream(this.container.getSlotsForFace(Direction.DOWN)).anyMatch(slot -> slot == index)
                    && this.container.canTakeItemThroughFace(index, resource.toStack(), Direction.DOWN))) {
                return 0;
            }
            return super.extract(index, resource, amount, transaction);
        }
    }

    /** A machine from any face: in by some face's own rule, out only what some face lets out (see {@link Sided}). */
    static final class AnyFace implements net.neoforged.neoforge.transfer.ResourceHandler<ItemResource> {
        private final WorldlyContainer container;
        private final WorldlyContainerWrapper all;
        private final java.util.Map<Direction, WorldlyContainerWrapper> byFace = new java.util.EnumMap<>(Direction.class);

        AnyFace(WorldlyContainer container) {
            this.container = container;
            this.all = new WorldlyContainerWrapper(container, null);
            for (Direction face : Direction.values()) {
                this.byFace.put(face, new WorldlyContainerWrapper(container, face));
            }
        }

        @Override
        public int size() {
            return this.all.size();
        }

        @Override
        public ItemResource getResource(int index) {
            return this.all.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return this.all.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return this.all.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return this.all.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            for (Direction face : Direction.values()) {
                int at = place(this.container.getSlotsForFace(face), index);
                if (at >= 0 && this.container.canPlaceItemThroughFace(index, resource.toStack(), face)) {
                    return this.byFace.get(face).insert(at, resource, amount, transaction);
                }
            }
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            for (Direction face : Direction.values()) {
                int at = place(this.container.getSlotsForFace(face), index);
                if (at >= 0 && this.container.canTakeItemThroughFace(index, resource.toStack(), face)) {
                    return this.byFace.get(face).extract(at, resource, amount, transaction);
                }
            }
            return 0;
        }

        /** Where {@code slot} is among a face's slots, or -1. */
        private static int place(int[] slots, int slot) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot) {
                    return i;
                }
            }
            return -1;
        }
    }

    private ModCapabilities() {}
}
