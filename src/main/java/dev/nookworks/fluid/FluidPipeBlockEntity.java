package dev.nookworks.fluid;

import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.PipeSides;
import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.Readout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A pipe that carries fluid a step at a time (the Experience Pipe, the Copper Pipe), one kind at a time.
 * <p>
 * It is a fluid handler with a small buffer rather than a network: each tick it hands what it holds on, to tanks and
 * machines first and then along the line. Each pipe keeps one number, its {@link #reach}: how many pipes away the
 * nearest thing that takes its fluid is (0 when one is right beside it), worked out from its neighbours every tick the
 * way a redstone signal fades. Fluid goes to a neighbour pipe that is nearer to somewhere to go, all of it that fits,
 * and only evens out between pipes as near as each other. That carries the last drop along a line instead of leaving
 * a millibucket stuck in the pipe before the tank (which would keep any other fluid out of the line for good), and it
 * needs no graph to keep in sync. Both ends are plain NeoForge fluid capabilities, so it will just as happily feed
 * another mod's tank, or be fed by another mod's pump.
 * <p>
 * A pipe only pushes, except through a side set to pull with the Wrench ({@link #cycle}): through that side it draws
 * what the block there holds - a tank, a machine's output, another mod's tank - into the line, and it never pours
 * anything back that way. So a line can run from one tank to another, which pushing alone could not do: a tank does
 * not push. Which sides pull is kept here, not in the block state: six more three-way sides would multiply the pipe's
 * 4,096 states (8,192 for the Experience Pipe's) by 729 - every one built at each start, as the fittings' were before
 * they lost their switchable sides.
 * <p>
 * Across a claim's edge it neither draws nor hands on where claims say no ({@link DrawRights#mayMove}): a stranger's pipe
 * joined to a claimed line from outside gets nothing, and is not counted as a way to a taker either, so the line stands
 * still rather than pulling a tank dry into a pipe that cannot pass it on.
 */
public abstract class FluidPipeBlockEntity extends BlockEntity implements Readout, DrawRights.Owned {
    /** Who set it down; null for one set down before owners were kept. */
    private @Nullable UUID owner;
    /** What its owner may draw from and hand into across a claim's edge, as last asked. */
    private final DrawRights drawRights = new DrawRights();

    /** A reach that means "nothing takes this fluid anywhere along the line". Also the longest line that still flows. */
    public static final int NOWHERE = 256;
    private static final Direction[] SIDES = Direction.values();
    private static final String PULLING = "Pulling";
    private static final int ALL_SIDES = (1 << 6) - 1;

    protected final FluidStacksResourceHandler fluid;
    /** The buffer as a pulling side shows it: it takes in, and nothing draws out of it that way. */
    private final ResourceHandler<FluidResource> intake;
    private final int buffer;
    /** The sides set to pull, a bit for each {@link Direction#ordinal()}. Saved; a pipe from before has none. */
    private int pulling;
    private int reach = NOWHERE;
    /** Which side a tick starts handing out from, turned each tick so a branch shares fairly. */
    private int turn;
    @SuppressWarnings("unchecked")
    private final @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>[] caches = new BlockCapabilityCache[6];

    protected FluidPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int buffer) {
        super(type, pos, state);
        this.buffer = buffer;
        this.fluid = new OneTank(buffer) {
            @Override
            public boolean isValid(int index, FluidResource resource) {
                return FluidPipeBlockEntity.this.carries(resource);
            }

            @Override
            protected void onContentsChanged(int index, FluidStack previous) {
                FluidPipeBlockEntity.this.contentsChanged(previous);
            }
        };
        this.intake = Tanks.fillOnly(this.fluid);
    }

    /** The one fluid this pipe carries. */
    protected abstract boolean carries(FluidResource resource);

    /** The fluids an empty pipe asks its neighbours about, to know whether they would take anything it may carry. */
    protected abstract List<Fluid> candidates();

    /**
     * What pipes this one runs on with: a pipe of the same family is part of the same line, sharing its reach (the
     * Copper Pipe and the fittings set in it - a Sight Glass, a Valve - are one family). By default, its own type.
     */
    protected Object family() {
        return this.getType();
    }

    /** Whether fluid gets through it at all just now (a Valve that is shut: nothing in, nothing out, no reach). */
    protected boolean passes() {
        return true;
    }

    /**
     * The buffer changed; subclasses that show their contents update the block here. It only has to be saved: no
     * comparator reads a pipe (the Sight Glass's reading is its block's, and it tells comparators itself when that
     * changes), and the neighbour updates a full {@code setChanged} sends ran two to four times a tick for every pipe
     * of a flowing line (r16 S3).
     */
    protected void contentsChanged(FluidStack previous) {
        MachineBlockEntity.savePower(this);
    }

    public int stored() {
        return this.fluid.getAmountAsInt(0);
    }

    public FluidResource resource() {
        return this.fluid.getResource(0);
    }

    /**
     * Empties the pipe on the spot (a Wrench on it): a line full of the wrong fluid can be cleared a pipe at a time
     * without breaking it. What it held is let out as {@link #spill} lets it out. Returns what was in it.
     */
    public FluidStack flush(ServerLevel level) {
        int held = this.stored();
        if (held <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack was = new FluidStack(this.resource().getFluid(), held);
        this.fluid.set(0, FluidResource.EMPTY, 0);
        this.spill(level, was);
        return was;
    }

    /** What a flushed pipe does with what it held: poured away, with a splash. The Experience Pipe lets it out as orbs. */
    protected void spill(ServerLevel level, FluidStack held) {
        BlockPos pos = this.worldPosition;
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                12, 0.25, 0.25, 0.25, 0.1);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    /**
     * Whether this pipe's line leads to something besides {@code except} that takes {@code resource} now (asked, never
     * committed), walking at most {@code limit} pipes. The Stirring Tank sends biodiesel up into an empty pipe only when
     * it goes somewhere: a feed pipe set on a tank already holding biodiesel was filled with it, and the oil line
     * plugged (the round-two fix re-check).
     */
    public boolean leadsToTakerOf(ServerLevel level, FluidResource resource, BlockPos except, int limit) {
        java.util.ArrayDeque<FluidPipeBlockEntity> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(this);
        seen.add(this.worldPosition);
        while (!queue.isEmpty()) {
            FluidPipeBlockEntity pipe = queue.poll();
            BlockPos at = pipe.worldPosition;
            for (Direction side : SIDES) {
                BlockPos next = at.relative(side);
                if (next.equals(except) || !pipe.open(level, at, side) || pipe.pullsFrom(side)) {
                    continue;
                }
                FluidPipeBlockEntity other = pipe.pipeOn(level, at, side);
                if (other != null) {
                    if (seen.size() < limit && seen.add(next)) {
                        queue.add(other);
                    }
                    continue;
                }
                ResourceHandler<FluidResource> handler = pipe.handlerOn(level, at, side);
                if (handler != null) {
                    try (Transaction test = Transaction.openRoot()) {
                        if (handler.insert(resource, 1, test) > 0) {
                            return true; // never committed: only asking
                        }
                    }
                }
            }
        }
        return false;
    }

    /** How many pipes away the nearest taker of this pipe's fluid is, or {@link #NOWHERE}. */
    public int reach() {
        return this.reach;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidPipeBlockEntity pipe) {
        if (level instanceof ServerLevel server) {
            pipe.measure(server, pos);
            if (pipe.pulling != 0) {
                pipe.draw(server, pos);
            }
            if (pipe.stored() > 0) {
                pipe.give(server, pos);
            }
            pipe.turn = (pipe.turn + 1) % SIDES.length;
            pipe.ticked(server);
        }
    }

    /** After each tick's work: a fitting that shows its contents reads them here. */
    protected void ticked(ServerLevel level) {}

    /** What sits on {@code side}: a pipe of this kind, or null. */
    private @Nullable FluidPipeBlockEntity pipeOn(ServerLevel level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos.relative(side)) instanceof FluidPipeBlockEntity pipe && pipe.family() == this.family() ? pipe : null;
    }

    private @Nullable ResourceHandler<FluidResource> handlerOn(ServerLevel level, BlockPos pos, Direction side) {
        int i = side.ordinal();
        if (this.caches[i] == null) {
            this.caches[i] = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, pos.relative(side), side.getOpposite());
        }
        return this.caches[i].getCapability();
    }

    private boolean open(ServerLevel level, BlockPos pos, Direction side) {
        // never load a chunk to look at it; and nothing leaves through a side that is not joined
        return this.passes() && level.isLoaded(pos.relative(side)) && PipeSides.joined(this.getBlockState(), side);
    }

    /** Works out {@link #reach} from the neighbours: 0 beside a taker, else one more than the nearest pipe's. */
    private void measure(ServerLevel level, BlockPos pos) {
        int best = NOWHERE;
        for (Direction side : SIDES) {
            if (!this.open(level, pos, side)) {
                continue;
            }
            FluidPipeBlockEntity other = this.pipeOn(level, pos, side);
            if (other != null) {
                if (this.pullsFrom(side)) {
                    this.setPulling(side, false); // a pipe of the line set where the tank stood: it joins as pipes do
                }
                if (other.reach + 1 < best && this.mayGive(level, pos, side, other)) {
                    best = other.reach + 1;
                }
            } else if (!this.pullsFrom(side) && this.takes(this.handlerOn(level, pos, side)) && this.mayGive(level, pos, side, null)) {
                // (a side it pulls from is where fluid comes from, never where it goes)
                best = 0;
                break;
            }
        }
        this.reach = Math.min(best, NOWHERE);
    }

    /**
     * Whether claims let what this pipe holds go into the block on {@code side} ({@code there}, its block entity, if
     * known): see {@link DrawRights#mayMove}. An empty pipe is not asked - it counts every way on, and is asked once it
     * has something to hand on - since each asking is a break event every listener hears.
     */
    private boolean mayGive(ServerLevel level, BlockPos pos, Direction side, @Nullable BlockEntity there) {
        if (this.stored() <= 0) {
            return true;
        }
        BlockPos next = pos.relative(side);
        return DrawRights.mayMove(level, pos, this, next, there != null ? there : level.getBlockEntity(next));
    }

    /** {@link #candidates()} as resources, made once: an empty pipe asked its neighbours with a new list every tick. */
    private @Nullable List<FluidResource> candidateResources;

    private List<FluidResource> candidateResources() {
        if (this.candidateResources == null) {
            this.candidateResources = this.candidates().stream().map(FluidResource::of).toList();
        }
        return this.candidateResources;
    }

    /** Whether {@code handler} would take some of what this pipe holds (or, empty, of anything it may carry). */
    private boolean takes(@Nullable ResourceHandler<FluidResource> handler) {
        if (handler == null) {
            return false;
        }
        List<FluidResource> tried = this.stored() > 0 ? List.of(this.resource()) : this.candidateResources();
        try (Transaction test = Transaction.openRoot()) {
            for (FluidResource resource : tried) {
                if (handler.insert(resource, 1, test) > 0) {
                    return true; // never committed: only asking
                }
            }
        }
        return false;
    }

    /**
     * Hands fluid on: first to anything that is not a pipe of this kind, then all it can to pipes nearer to a taker,
     * and last half the difference to pipes as near as this one that hold less. Nothing ever goes to a pipe farther
     * from a taker, which is what stops two pipes passing the same fluid back and forth.
     */
    private void give(ServerLevel level, BlockPos pos) {
        for (int pass = 0; pass < 3; pass++) {
            for (int n = 0; n < SIDES.length; n++) {
                int held = this.stored();
                if (held <= 0) {
                    return;
                }
                Direction side = SIDES[(this.turn + n) % SIDES.length];
                if (!this.open(level, pos, side)) {
                    continue;
                }
                FluidPipeBlockEntity other = this.pipeOn(level, pos, side);
                int amount;
                if (pass == 0) {
                    if (other != null || this.pullsFrom(side)) {
                        continue; // pipes come after; and nothing goes back out of a side that pulls
                    }
                    amount = held;
                } else if (other == null) {
                    continue;
                } else if (pass == 1) {
                    if (other.reach >= this.reach) {
                        continue;
                    }
                    amount = held;
                } else {
                    if (other.reach != this.reach) {
                        continue;
                    }
                    amount = (held - other.stored()) / 2; // even out, never uphill
                    if (amount <= 0) {
                        continue;
                    }
                }
                ResourceHandler<FluidResource> target = this.handlerOn(level, pos, side);
                if (target != null && this.mayGive(level, pos, side, other)) { // (not across a claim's edge where it may not go)
                    Move.transfer(this.fluid, target, this.resource(), amount);
                }
            }
        }
    }

    /**
     * Draws fluid in through the sides set to pull, as much as the buffer has room for: a pipe pulls no faster than it
     * hands on, so a pulling line runs as full as one a tank pours into (a bucket a tick in a Copper Pipe, 400 mB in an
     * Experience Pipe). Only while something down the line takes what it carries ({@link #reach}) - or a line with
     * nowhere to go would fill itself from the tank and sit full - and only a fluid this pipe carries, the one it
     * holds if it holds any.
     */
    private void draw(ServerLevel level, BlockPos pos) {
        if (this.reach >= NOWHERE) {
            return;
        }
        for (int n = 0; n < SIDES.length; n++) {
            Direction side = SIDES[(this.turn + n) % SIDES.length];
            if (!this.pullsFrom(side) || !this.open(level, pos, side)) {
                continue;
            }
            if (this.pipeOn(level, pos, side) != null) {
                this.setPulling(side, false); // (as in measure, which may have stopped at a taker before this side)
                continue;
            }
            int room = this.buffer - this.stored();
            if (room <= 0) {
                return;
            }
            ResourceHandler<FluidResource> source = this.handlerOn(level, pos, side);
            if (source == null) {
                continue;
            }
            for (int i = 0; i < source.size(); i++) {
                FluidResource held = source.getResource(i);
                if (held.isEmpty() || !this.carries(held) || this.stored() > 0 && !held.equals(this.resource())) {
                    continue;
                }
                BlockPos from = pos.relative(side);
                if (!DrawRights.mayMove(level, from, level.getBlockEntity(from), pos, this)) {
                    break; // across a claim's edge, where its owner may not take (asked only once there is something to take)
                }
                if (Move.transfer(source, this.fluid, held, room) > 0) {
                    break;
                }
            }
        }
    }

    /** What the Wrench's sneak-click leaves a side as. */
    public enum SideSetting { JOINED, PULLING, OFF }

    /** Whether {@code side} is set to pull (whether or not anything is there to pull from just now). */
    public boolean pullsFrom(Direction side) {
        return (this.pulling & 1 << side.ordinal()) != 0;
    }

    /** The sides set to pull that are joined to what they pull from, a bit for each: what is drawn and read out. */
    public int pullingJoined() {
        if (this.pulling == 0) {
            return 0; // most pipes: asked by the renderer every frame
        }
        int sides = 0;
        for (Direction side : SIDES) {
            if (this.pullsFrom(side) && PipeSides.joined(this.getBlockState(), side)) {
                sides |= 1 << side.ordinal();
            }
        }
        return sides;
    }

    /** Whether {@code side} may be set to pull: it is joined to something that is not a pipe of this line. */
    public boolean mayPull(ServerLevel level, Direction side) {
        return PipeSides.joined(this.getBlockState(), side) && this.pipeOn(level, this.worldPosition, side) == null;
    }

    /**
     * The Wrench's sneak-click on {@code side}: joined, then pulling, then off, then joined again. Pulling comes next
     * to joined because it is a way of being joined - the arm stays, fluid passes, only which way changes - and so off
     * still turns straight back to joined, as it always has: a side put back on never starts draining a tank by
     * surprise. Only a side against something that is not a pipe of the line can pull; between two pipes the click
     * goes on switching the side off and on, both pipes together ({@link PipeSides#toggle}).
     */
    public SideSetting cycle(ServerLevel level, Direction side) {
        BlockState state = this.getBlockState();
        if (PipeSides.isOff(state, side)) {
            PipeSides.toggle(level, this.worldPosition, state, side);
            return SideSetting.JOINED;
        }
        if (!this.pullsFrom(side) && this.mayPull(level, side)) {
            this.setPulling(side, true);
            return SideSetting.PULLING;
        }
        this.setPulling(side, false);
        PipeSides.toggle(level, this.worldPosition, state, side);
        return SideSetting.OFF;
    }

    private void setPulling(Direction side, boolean on) {
        int was = this.pulling;
        this.pulling = on ? was | 1 << side.ordinal() : was & ~(1 << side.ordinal());
        if (this.pulling == was) {
            return;
        }
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS); // the client draws the flange
            this.level.invalidateCapabilities(this.worldPosition); // and that side now only takes in
        }
    }

    /** Which sides it pulls from, for Jade: the rest a look at the pipe tells. */
    @Override
    public void readout(Consumer<Component> lines) {
        int sides = this.pullingJoined();
        for (Direction side : SIDES) {
            if ((sides & 1 << side.ordinal()) != 0) {
                lines.accept(Component.translatable("nookworks.pipe.pulling",
                        Component.translatable("direction.nookworks." + side.getSerializedName())));
            }
        }
    }

    /**
     * A structure set down turned or mirrored turns the pulling sides with it, as {@link PipeSides#turned} turns the
     * joins and the sides switched off, or a pipe would pull from its old side's way.
     */
    @Override
    public void applyStructureRotation(Mirror mirror, Rotation rotation) {
        int turned = 0;
        for (Direction side : SIDES) {
            if (this.pullsFrom(side)) {
                turned |= 1 << rotation.rotate(mirror.mirror(side)).ordinal();
            }
        }
        this.pulling = turned;
    }

    /**
     * The buffer neighbours see, from any side that is not switched off - and, for a fitting that joins only along its
     * axis (a Sight Glass, a Valve), only from the two ends: a tank set on top of one poured into its side.
     */
    public @Nullable ResourceHandler<FluidResource> handlerFor(@Nullable Direction side) {
        if (side != null && PipeSides.isOff(this.getBlockState(), side)) {
            return null; // switched off with the wrench: nothing comes in or goes out this way
        }
        if (side != null && this.getBlockState().getBlock() instanceof CopperFittingBlock
                && side.getAxis() != this.getBlockState().getValue(CopperFittingBlock.AXIS)) {
            return null;
        }
        if (!this.passes()) {
            return null; // a shut valve
        }
        return side != null && this.pullsFrom(side) ? this.intake : this.fluid;
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
        output.storeNullable("Owner", net.minecraft.core.UUIDUtil.CODEC, this.owner);
        this.fluid.serialize(output.child("Fluid"));
        if (this.pulling != 0) {
            output.putByte(PULLING, (byte) this.pulling);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        input.child("Fluid").ifPresent(this.fluid::deserialize);
        this.pulling = input.getByteOr(PULLING, (byte) 0) & ALL_SIDES; // a pipe saved before there was pulling has none
    }

    /**
     * The client is told only which sides pull, to draw their flanges; what flows, the blocks' states show. A fitting
     * (a Sight Glass, a Valve) has no sides to set and sends nothing.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (PipeSides.isPipe(this.getBlockState())) {
            tag.putByte(PULLING, (byte) this.pulling);
        }
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return PipeSides.isPipe(this.getBlockState()) ? ClientboundBlockEntityDataPacket.create(this) : null;
    }
}
