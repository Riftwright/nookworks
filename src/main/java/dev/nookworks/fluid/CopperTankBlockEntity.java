package dev.nookworks.fluid;

import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.Readout;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Copper Tank: sixteen buckets of what the copper line carries - water, vegetable oil or biodiesel, one at a time -
 * with a sight glass down each side showing which and how much. Pipes, pumps and machines fill it through the ordinary
 * fluid capability, from any side but the bottom, and it pours out of its bottom into whatever is under it that takes
 * it - a pipe, a machine, another tank - a pipe's worth a tick; buckets and bottles by hand. It keeps what it holds
 * when broken.
 */
public class CopperTankBlockEntity extends BlockEntity implements Readout, DrawRights.Owned, WindowFluid {
    public static final int CAPACITY = 16_000;
    /** What it pours down a tick: as much as a Copper Pipe holds, so a line under it runs full. */
    public static final int POUR = CopperPipeBlockEntity.BUFFER;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
    /** On the client: the liquid its window shows, as the server told it (WindowFluid). */
    private Fluid windowFluid = Fluids.EMPTY;

    /**
     * Who set it down; null for one set down before owners were kept. A stranger's tank set beside a claimed line takes
     * nothing from it.
     */
    private @Nullable UUID owner;
    /** What its owner may take from and give into across a claim's edge, as last asked. */
    private final DrawRights drawRights = new DrawRights();

    private final FluidStacksResourceHandler fluid = Tanks.tank(CAPACITY, CopperTankBlockEntity::holds, true, true, this::changed);
    private final ResourceHandler<FluidResource> bottom = Tanks.drainOnly(this.fluid);
    private @Nullable BlockCapabilityCache<
            ResourceHandler<FluidResource>, @Nullable Direction> below;

    public CopperTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COPPER_TANK.get(), pos, state);
    }

    /** What the copper line carries, ours or another mod's water. */
    public static boolean holds(Fluid fluid) {
        return fluid.isSame(Fluids.WATER) || fluid.isSame(OilFluids.SOURCE.get()) || fluid.isSame(OilFluids.BIODIESEL.get());
    }

    public FluidStacksResourceHandler fluidHandler() {
        return this.fluid;
    }

    /** What a neighbour on {@code side} sees: the tank, but from below one that only lets fluid out. */
    public ResourceHandler<FluidResource> handlerFor(@Nullable Direction side) {
        return side == Direction.DOWN ? this.bottom : this.fluid;
    }

    /**
     * Pours down into whatever is under it that takes what it holds - asked as a pipe's giving is, across a claim's floor
     * as across its edge: a claimed tank on a claim's bottom layer poured into a stranger's tank set under it (r15 fix
     * check 8).
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CopperTankBlockEntity tank) {
        if (tank.amount() <= 0 || !(level instanceof ServerLevel server) || !server.isLoaded(pos.below())) {
            return;
        }
        if (tank.below == null) {
            tank.below = BlockCapabilityCache.create(
                    Capabilities.Fluid.BLOCK, server, pos.below(), Direction.UP);
        }
        var target = tank.below.getCapability();
        if (target != null && DrawRights.mayMove(server, pos, tank, pos.below(), server.getBlockEntity(pos.below()))) {
            Move.transfer(tank.fluid, target, tank.resource(), Math.min(POUR, tank.amount()));
        }
    }

    public int amount() {
        return this.fluid.getAmountAsInt(0);
    }

    public FluidResource resource() {
        return this.fluid.getResource(0);
    }

    private void changed() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
        }
        this.show();
    }

    /** Brings the glass - which liquid, how high - to the contents, on the server, when it differs. */
    private void show() {
        if (this.level == null || this.level.isClientSide() || this.isRemoved()) {
            return;
        }
        BlockState state = this.getBlockState();
        if (!state.hasProperty(LineContent.PROPERTY)) {
            return;
        }
        LineContent content = LineContent.of(this.resource(), this.amount());
        int step = SightGlass.level(this.amount(), CAPACITY);
        if ((state.getValue(LineContent.PROPERTY) != content || state.getValue(SightGlass.LEVEL) != step)
                && this.level.getBlockState(this.worldPosition) == state) {
            this.level.setBlock(this.worldPosition, state.setValue(LineContent.PROPERTY, content).setValue(SightGlass.LEVEL, step), FLAGS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        PipePorts.showOnLoad(this);
        if (this.level instanceof ServerLevel server) {
            server.getServer().execute(this::show);
        }
    }

    public int comparatorSignal() {
        return this.amount() <= 0 ? 0 : 1 + this.amount() * 14 / CAPACITY;
    }

    @Override
    public void jade(Consumer<Component> lines) {
        // nothing: what is in it, and how much, Jade shows itself
    }

    @Override
    public void readout(Consumer<Component> lines) {
        if (this.amount() <= 0) {
            lines.accept(Component.translatable("nookworks.copper_tank.empty", CAPACITY));
        } else {
            lines.accept(Component.translatable("nookworks.copper_tank.holds", this.resource().toStack(1).getHoverName(),
                    this.amount(), CAPACITY));
        }
    }

    @Override
    public @Nullable UUID owner() {
        return this.owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    @Override
    public DrawRights drawRights() {
        return this.drawRights;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
        this.fluid.serialize(output.child("Fluid"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        input.child("Fluid").ifPresent(this.fluid::deserialize);
    }

    /** The client is told which liquid is in the window (WindowFluid), with every change of the block's state. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return WindowFluid.tag(this.amount() > 0 ? this.resource().getFluid() : Fluids.EMPTY);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        this.told(input);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        this.told(input);
    }

    /** On the client: which liquid the window shows; the block drawn again in its colour. */
    private void told(ValueInput input) {
        Fluid told = WindowFluid.read(input);
        if (told != this.windowFluid) {
            this.windowFluid = told;
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
            }
        }
    }

    @Override
    public Fluid windowFluid() {
        return this.windowFluid;
    }


    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Tanks.forgetStackLimit(components);
        FluidStack held = components.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        if (!held.isEmpty() && holds(held.getFluid())) {
            Tanks.set(this.fluid, held.getFluid(), Math.min(CAPACITY, held.getAmount()));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.amount() > 0) {
            components.set(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(new FluidStack(this.resource().getFluid(), this.amount())));
            Tanks.oneToAStack(components);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Fluid");
    }
}
