package dev.nookworks.port.transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Joins the mod's capabilities ({@link Capabilities}, the 26.3 kind) and NeoForge 21.1's ({@code IItemHandler},
 * {@code IFluidHandler}, {@code IEnergyStorage}), both ways, so other 1.21.1 mods' pipes, tanks and cables see our
 * blocks and ours see theirs.
 *
 * <p>Registered last of all ({@link EventPriority#LOWEST}: FML posts the event phase by phase across every mod), once
 * every mod's providers are in. Then, capability by capability, for every block, entity type and item:
 * <ul>
 * <li>OUT - it has a provider of ours: it gets one of the 21.1 capability, our handler behind an adapter
 *     ({@link LegacyOut}).</li>
 * <li>IN - it has none of ours: it gets one of ours, wrapping what the 21.1 capability gives at the time of asking
 *     ({@link LegacyIn}); for vanilla's own containers (chests - double ones as their two halves -, barrels, hoppers,
 *     furnaces by their faces ...) our transactional {@link VanillaContainerWrapper} / {@link WorldlyContainerWrapper}
 *     over the container itself, so a move that aborts truly puts everything back.</li>
 * </ul>
 * A thing never gets both directions of one capability, so no bridge leads back to itself; and an adapter met on the
 * other side is unwrapped (one of ours handed back by another mod's proxy block is ours again, not a wrapper of a
 * wrapper). Each provider asks at call time, so {@code BlockCapabilityCache} invalidation goes on working as before:
 * invalidating a position invalidates every capability there, ours included.
 *
 * <p>A provider registered by another mod at LOWEST after ours is not seen by the OUT pass (the IN providers ask at call
 * time and see it).
 */
@EventBusSubscriber(modid = "nookworks")
public final class TransferBridges {
    private TransferBridges() {}

    private static final Set<BlockEntityType<?>> VANILLA_SIDED = Set.of(BlockEntityType.FURNACE, BlockEntityType.SMOKER,
            BlockEntityType.BLAST_FURNACE, BlockEntityType.BREWING_STAND, BlockEntityType.SHULKER_BOX);
    private static final Set<BlockEntityType<?>> VANILLA_PLAIN = Set.of(BlockEntityType.BARREL, BlockEntityType.HOPPER,
            BlockEntityType.DISPENSER, BlockEntityType.DROPPER, BlockEntityType.JUKEBOX, BlockEntityType.CRAFTER,
            BlockEntityType.DECORATED_POT, BlockEntityType.CHISELED_BOOKSHELF);
    private static final Set<EntityType<?>> VANILLA_CONTAINER_ENTITIES = Set.of(EntityType.CHEST_BOAT, EntityType.CHEST_MINECART,
            EntityType.HOPPER_MINECART);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void register(RegisterCapabilitiesEvent event) {
        blocks(event);
        entities(event);
        items(event);
    }

    // ---- blocks

    private static void blocks(RegisterCapabilitiesEvent event) {
        var realItems = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        var realFluids = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK;
        var realEnergy = net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK;
        Split items = split(event, Capabilities.Item.BLOCK);
        Split fluids = split(event, Capabilities.Fluid.BLOCK);
        Split energy = split(event, Capabilities.Energy.BLOCK);

        // OUT
        register(event, realItems, items.ours, (level, pos, state, be, side) -> {
            // A vanilla hopper, dropper or crafter must reach a block of ours through its Container and its faces, as in
            // 26.3 (ModCapabilities.Sided): mixin.VanillaInventoryCodeHooksMixin keeps NeoForge 21.1's hopper hooks from
            // taking this adapter instead.
            ResourceHandler<ItemResource> ours = Capabilities.Item.BLOCK.getCapability(level, pos, state, be, side);
            return ours == null ? null : ours instanceof LegacyIn.Items legacy ? legacy.real() : new LegacyOut.Items(ours);
        });
        register(event, realFluids, fluids.ours, (level, pos, state, be, side) -> {
            ResourceHandler<FluidResource> ours = Capabilities.Fluid.BLOCK.getCapability(level, pos, state, be, side);
            return ours == null ? null : ours instanceof LegacyIn.Fluids legacy ? legacy.real() : new LegacyOut.Fluids(ours);
        });
        register(event, realEnergy, energy.ours, (level, pos, state, be, side) -> {
            EnergyHandler ours = Capabilities.Energy.BLOCK.getCapability(level, pos, state, be, side);
            return ours == null ? null : ours instanceof LegacyIn.Energy legacy ? legacy.real() : new LegacyOut.Energy(ours);
        });

        // IN
        register(event, Capabilities.Item.BLOCK, items.theirs, (level, pos, state, be, side) -> {
            ResourceHandler<ItemResource> vanilla = vanillaItems(level, pos, state, be, side);
            if (vanilla != null) {
                return vanilla;
            }
            IItemHandler real = level.getCapability(realItems, pos, state, be, side);
            return real == null ? null : LegacyIn.items(real, level, Vec3.atCenterOf(pos));
        });
        register(event, Capabilities.Fluid.BLOCK, fluids.theirs, (level, pos, state, be, side) -> {
            IFluidHandler real = level.getCapability(realFluids, pos, state, be, side);
            return real == null ? null : LegacyIn.fluids(real);
        });
        register(event, Capabilities.Energy.BLOCK, energy.theirs, (level, pos, state, be, side) -> {
            IEnergyStorage real = level.getCapability(realEnergy, pos, state, be, side);
            return real == null ? null : LegacyIn.energy(real);
        });
    }

    /** The blocks that have a provider of {@code ours}, and those that have none. */
    private record Split(Block[] ours, Block[] theirs) {}

    private static Split split(RegisterCapabilitiesEvent event, BlockCapability<?, ?> ours) {
        List<Block> with = new ArrayList<>();
        List<Block> without = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            (event.isBlockRegistered(ours, block) ? with : without).add(block);
        }
        return new Split(with.toArray(Block[]::new), without.toArray(Block[]::new));
    }

    private static <T> void register(RegisterCapabilitiesEvent event, BlockCapability<T, @Nullable Direction> capability, Block[] blocks,
            net.neoforged.neoforge.capabilities.IBlockCapabilityProvider<T, @Nullable Direction> provider) {
        if (blocks.length > 0) {
            event.registerBlock(capability, (level, pos, state, be, side) -> {
                int[] depth = DEPTH.get();
                if (depth[0] >= MAX_DEPTH) {
                    return null;
                }
                depth[0]++;
                try {
                    return provider.getCapability(level, pos, state, be, side);
                } finally {
                    depth[0]--;
                }
            }, blocks);
        }
    }

    /**
     * How deep bridge lookups are nested on this thread. A lookup through a bridge can reach another mod's provider that
     * looks up its neighbours, which can come back through a bridge (two cable networks of two mods side by side, each
     * asking the other): past this depth a bridge answers nothing rather than overflow the stack.
     */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static final int MAX_DEPTH = 32;

    /**
     * Vanilla's own containers, as ours over the container itself (NeoForge 21.1 gives them InvWrappers, which cannot be
     * rolled back): a chest with its other half, the furnaces, brewing stand and shulker box by face, the rest whole.
     */
    private static @Nullable ResourceHandler<ItemResource> vanillaItems(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be,
            @Nullable Direction side) {
        if (be == null) {
            return null;
        }
        BlockEntityType<?> type = be.getType();
        if ((type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST) && be instanceof ChestBlockEntity chest
                && state.getBlock() instanceof ChestBlock) {
            return chest(level, pos, state, chest);
        }
        if (VANILLA_SIDED.contains(type) && be instanceof WorldlyContainer worldly) {
            return new WorldlyContainerWrapper(worldly, side);
        }
        if (VANILLA_PLAIN.contains(type) && be instanceof Container container) {
            return VanillaContainerWrapper.of(container);
        }
        return null;
    }

    /** A chest, or both halves of a double one in vanilla's order (the half of type RIGHT first, as ChestBlock combines them). */
    private static ResourceHandler<ItemResource> chest(Level level, BlockPos pos, BlockState state, ChestBlockEntity chest) {
        ChestType kind = state.getValue(ChestBlock.TYPE);
        if (kind != ChestType.SINGLE) {
            BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(state.getBlock()) && otherState.getValue(ChestBlock.TYPE) == kind.getOpposite()
                    && level.getBlockEntity(otherPos) instanceof ChestBlockEntity other) {
                ResourceHandler<ItemResource> self = VanillaContainerWrapper.of(chest);
                ResourceHandler<ItemResource> half = VanillaContainerWrapper.of(other);
                return kind == ChestType.RIGHT ? new CombinedResourceHandler<>(self, half) : new CombinedResourceHandler<>(half, self);
            }
        }
        return VanillaContainerWrapper.of(chest);
    }

    // ---- entities

    private static void entities(RegisterCapabilitiesEvent event) {
        var realItems = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ENTITY;
        var realAutomation = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ENTITY_AUTOMATION;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            entity(event, type, Capabilities.Item.ENTITY, realItems);
            entity(event, type, Capabilities.Item.ENTITY_AUTOMATION, realAutomation);
        }
    }

    private static <E extends Entity, C> void entity(RegisterCapabilitiesEvent event, EntityType<E> type,
            EntityCapability<ResourceHandler<ItemResource>, C> ours, EntityCapability<IItemHandler, C> real) {
        if (event.isEntityRegistered(ours, type)) {
            event.registerEntity(real, type, (entity, context) -> {
                ResourceHandler<ItemResource> handler = ours.getCapability(entity, context);
                return handler == null ? null : handler instanceof LegacyIn.Items legacy ? legacy.real() : new LegacyOut.Items(handler);
            });
        } else {
            event.registerEntity(ours, type, (entity, context) -> {
                if (VANILLA_CONTAINER_ENTITIES.contains(entity.getType()) && entity instanceof ContainerEntity container) {
                    return VanillaContainerWrapper.of(container);
                }
                // a player's inventory goes through 21.1's own wrapper, which keeps armour to its slots
                IItemHandler handler = real.getCapability(entity, context);
                return handler == null ? null : LegacyIn.items(handler, entity.level(), entity.position());
            });
        }
    }

    // ---- items

    private static void items(RegisterCapabilitiesEvent event) {
        var realFluids = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM;
        List<Item> ours = new ArrayList<>();
        List<Item> theirs = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            (event.isItemRegistered(Capabilities.Fluid.ITEM, item) ? ours : theirs).add(item);
        }
        if (!ours.isEmpty()) {
            event.registerItem(realFluids, (stack, context) -> {
                ItemAccesses.OfStack access = new ItemAccesses.OfStack(stack, false);
                ResourceHandler<FluidResource> handler = Capabilities.Fluid.ITEM.getCapability(stack, access);
                return handler == null ? null : new LegacyOut.ItemFluids(handler, access);
            }, ours.toArray(Item[]::new));
        }
        if (!theirs.isEmpty()) {
            // asked with no access (not by the mod, which always gives one), the stack is only read
            event.registerItem(Capabilities.Fluid.ITEM, (stack, access) ->
                    realFluids.getCapability(stack.copyWithCount(1), null) == null ? null
                            : new LegacyIn.ItemFluids(access != null ? access : new ItemAccesses.OfStack(stack)),
                    theirs.toArray(Item[]::new));
        }
    }
}
