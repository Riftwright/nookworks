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
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.SIFTER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENERATOR.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.DIESEL_GENERATOR.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CRUSHER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.STONE_MAKER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ELECTRIC_FURNACE.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ELECTRIC_FURNACE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.ELECTRIC_FURNACE.get(), (be, side) -> be.experienceHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.SORTER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBarter.BARTER_TERMINAL_ENTITY.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBarter.BARTER_TERMINAL_ENTITY.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.SPAWN_EGG_BREWER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModTrading.TRADING_COUNTER_ENTITY.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.CRATE.get(), (be, side) -> VanillaContainerWrapper.of(be));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ITEM_WARP_SENDER.get(), (be, side) -> VanillaContainerWrapper.of(be));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.HORIZONTAL_ITEM_WARP_SENDER.get(), (be, side) -> VanillaContainerWrapper.of(be));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.MAGNET_CHEST.get(), (be, side) -> VanillaContainerWrapper.of(be));
        // In from any side, never out: its seeds are for planting (a hopper under it drained them, r8)
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.PLANTER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.RETORT.get(), Sided::new);
        // The Retort's experience (not its mercury, which stays out of fluid systems): pipes and tanks may draw it.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.RETORT.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.STIRRING_TANK.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ELECTROLYTIC_CELL.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.ELECTROLYTIC_CELL.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.ELECTROLYTIC_CELL.get(), (be, side) -> be.waterHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.DISTILLER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.PRINTING_PRESS.get(), Sided::new);

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
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.STIRRING_TANK.get(), (be, side) -> be.fluidHandler());
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
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.OIL_PRESS.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.MOTOR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.RICE_POLISHER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.HAND_QUERN.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.NOODLE_MAKER.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.REPAIR_BOX.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.ABSORPTION_PAD.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.REPAIR_BOX.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.TRASH_CAN.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.REPAIR_BOX.get(), (be, side) -> be.energy());

        // Recycling: the Recycler's intake (its upper block) is the body's too
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModRecycling.COMPRESSOR_ENTITY.get(), Sided::new);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModRecycling.COMPRESSOR_ENTITY.get(), (be, side) -> be.energy());
        event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, be, side) ->
                level.getBlockEntity(RecyclerBlock.lower(state, pos)) instanceof RecyclerBlockEntity body
                        ? new Sided(body, side) : null, ModRecycling.RECYCLER.get());
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
     * A machine's slots as a pipe sees them from {@code side}. Asked from no side, NeoForge's wrapper shows every slot and
     * lets anything out; here what comes out from no side is only what comes out from below (a hopper's way), so a mod
     * asking side-less takes no seeds from a Planter, no takings from a Trading Counter and no ore from a Crusher.
     */
    static final class Sided extends WorldlyContainerWrapper {
        private final WorldlyContainer container;
        private final boolean sideless;

        Sided(WorldlyContainer container, @Nullable Direction side) {
            super(container, side);
            this.container = container;
            this.sideless = side == null;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (sideless && !(java.util.Arrays.stream(container.getSlotsForFace(Direction.DOWN)).anyMatch(slot -> slot == index)
                    && container.canTakeItemThroughFace(index, resource.toStack(), Direction.DOWN))) {
                return 0;
            }
            return super.extract(index, resource, amount, transaction);
        }
    }

    private ModCapabilities() {}
}
