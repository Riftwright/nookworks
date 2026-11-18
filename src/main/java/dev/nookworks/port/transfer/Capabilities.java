package dev.nookworks.port.transfer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;

/**
 * Stand-in for NeoForge 26.3's {@code capabilities.Capabilities}, with the names the mod uses. Each is a real NeoForge
 * 21.1 capability, of our own ({@code nookworks:} ids), typed with the stand-in handlers: so every
 * {@code level.getCapability(Capabilities.Item.BLOCK, pos, side)}, {@code BlockCapabilityCache.create(...)} and
 * {@code event.registerBlockEntity(Capabilities.Item.BLOCK, ...)} in the mod works as written.
 *
 * <p>Other mods do not know these: {@link TransferBridges} shows ours to them as 1.21.1's {@code IItemHandler},
 * {@code IFluidHandler} and {@code IEnergyStorage} (NeoForge's {@code Capabilities.ItemHandler.BLOCK} ...), and theirs to
 * us through these.
 */
public final class Capabilities {
    private Capabilities() {}

    public static final class Energy {
        private Energy() {}

        public static final BlockCapability<EnergyHandler, @Nullable Direction> BLOCK =
                BlockCapability.createSided(id("energy_handler"), EnergyHandler.class);
    }

    public static final class Fluid {
        private Fluid() {}

        public static final BlockCapability<ResourceHandler<FluidResource>, @Nullable Direction> BLOCK =
                BlockCapability.createSided(id("fluid_handler"), ResourceHandler.asClass());
        /** A fluid item's tanks (a bucket's), reached through an {@link ItemAccess} that the handler changes. */
        public static final ItemCapability<ResourceHandler<FluidResource>, ItemAccess> ITEM =
                ItemCapability.create(id("fluid_handler"), ResourceHandler.asClass(), ItemAccess.class);
    }

    public static final class Item {
        private Item() {}

        public static final BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> BLOCK =
                BlockCapability.createSided(id("item_handler"), ResourceHandler.asClass());
        public static final EntityCapability<ResourceHandler<ItemResource>, @Nullable Void> ENTITY =
                EntityCapability.createVoid(id("item_handler"), ResourceHandler.asClass());
        public static final EntityCapability<ResourceHandler<ItemResource>, @Nullable Direction> ENTITY_AUTOMATION =
                EntityCapability.createSided(id("item_handler_automation"), ResourceHandler.asClass());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("nookworks", path);
    }
}
