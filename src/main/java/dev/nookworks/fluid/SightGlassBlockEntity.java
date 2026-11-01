package dev.nookworks.fluid;

import java.util.function.Consumer;

import dev.nookworks.machine.Readout;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The Sight Glass's chamber: a length of copper line (see {@link CopperPipeBlockEntity}) that shows what goes through
 * it. A pipe in a running line fills and empties within a tick, so what the glass shows is the most it held over the
 * last half second: a line backed up or running full shows full, one trickling shows a little, a dry one empty -
 * steady, not flickering with every tick's hand-on.
 */
public class SightGlassBlockEntity extends CopperPipeBlockEntity implements Readout, WindowFluid {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
    /** Ticks between readings. */
    private static final int WINDOW = 10;

    private int peak;
    private Fluid seen = Fluids.EMPTY;
    private int ticks;
    /** The last reading shown, for Jade: a running line's glass is often empty at the moment it is looked at. */
    private int shownAmount;
    private Fluid shownFluid = Fluids.EMPTY;
    /** On the client: the liquid its window shows, as the server told it (WindowFluid). */
    private Fluid windowFluid = Fluids.EMPTY;

    public SightGlassBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIGHT_GLASS.get(), pos, state);
    }

    @Override
    protected void contentsChanged(FluidStack previous) {
        super.contentsChanged(previous);
        this.note();
    }

    private void note() {
        if (this.stored() > this.peak) {
            this.peak = this.stored();
        }
        if (this.stored() > 0) {
            this.seen = this.resource().getFluid();
        }
    }

    @Override
    protected void ticked(ServerLevel level) {
        this.note();
        if (++this.ticks < WINDOW) {
            return;
        }
        this.ticks = 0;
        this.show(this.peak, this.peak > 0 ? this.seen : Fluids.EMPTY);
        this.peak = this.stored();
        this.seen = this.stored() > 0 ? this.resource().getFluid() : Fluids.EMPTY;
    }

    /** Brings what the glass shows - which liquid, how high - to a reading; the block is set only when that changes. */
    private void show(int amount, Fluid fluid) {
        if (this.level == null || this.level.isClientSide() || this.isRemoved()) {
            return;
        }
        BlockState state = this.getBlockState();
        if (!state.hasProperty(LineContent.PROPERTY)) {
            return;
        }
        Fluid wasShown = this.shownAmount > 0 ? this.shownFluid : Fluids.EMPTY;
        this.shownAmount = amount;
        this.shownFluid = fluid;
        LineContent content = amount > 0 ? LineContent.of(fluid) : LineContent.EMPTY;
        int step = SightGlass.level(amount, BUFFER);
        if (state.getValue(LineContent.PROPERTY) != content || state.getValue(SightGlass.LEVEL) != step) {
            this.level.setBlock(this.worldPosition, state.setValue(LineContent.PROPERTY, content).setValue(SightGlass.LEVEL, step), FLAGS);
            this.level.updateNeighbourForOutputSignal(this.worldPosition, state.getBlock());
        } else if (content == LineContent.OTHER && fluid != wasShown) {
            // one other liquid after another, as high: the state is the same, the client is told all the same (WindowFluid)
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** A fitting's pipe tells nothing (FluidPipeBlockEntity); the glass's client is told which liquid is in the window (WindowFluid), with every change of the block's state. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return WindowFluid.tag(this.shownAmount > 0 ? this.shownFluid : Fluids.EMPTY);
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

    /** The reading a comparator gets: the same half-second peak the glass shows. */
    public int reading() {
        return SightGlass.level(Math.max(this.peak, this.stored()), BUFFER);
    }

    @Override
    public void jade(Consumer<Component> lines) {
        // nothing: what flows through it, and how much, Jade shows itself (r15 fix check)
    }

    @Override
    public void readout(Consumer<Component> lines) {
        if (this.shownAmount <= 0 || this.shownFluid == Fluids.EMPTY) {
            lines.accept(Component.translatable("nookworks.sight_glass.empty"));
        } else {
            lines.accept(Component.translatable("nookworks.sight_glass.holds", new FluidStack(this.shownFluid, 1).getHoverName(),
                    this.shownAmount, BUFFER));
        }
    }
}
