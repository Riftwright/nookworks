package dev.nookworks.cooking;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What runs through a {@link KitchenTapBlock}. It has a cup of its own, {@link #CAPACITY} - a bucket - between what it
 * is set on and what stands under its spout, because a cauldron gives and takes a liquid only a bucket at a time (the
 * user, 2026-11-15: 「大釜にも注げるといいな」 - the tap ran a trickle, and a cauldron took none of it).
 * <p>
 * Open, every tick it draws into the cup from what it is set on - a Copper Tank, a cauldron, another mod's tank, or a
 * Copper Pipe (which also pushes into it) - and pours from the cup into what is under the spout, {@link #PER_TICK} a
 * tick, a bucket in two seconds. What takes only whole buckets (a cauldron) is poured as long as a bucket would take
 * and given it at the end. While it runs it is marked pouring, and what it pours is kept, for the stream drawn and
 * heard. Shut, nothing runs; a pipe may still fill the cup, which waits for it to open.
 * <p>
 * It draws only while something under it would take what it draws, so nothing waits in the cup for nobody (a cauldron's
 * bucket went into it with nothing below, out of sight, and was lost with the tap: 1.7 review); taken away, it gives
 * what is in its cup back to what it is set on. Whoever set it down owns it, and it takes from and pours into only what
 * they could break ({@link DrawRights}): a stranger's tap set on a claimed tank from outside the claim drew it dry by
 * the bucket (1.7 review).
 */
public class KitchenTapBlockEntity extends BlockEntity implements DrawRights.Owned {
    /** Ticks the stream is drawn after the last that ran. */
    public static final int POUR_SHOWN = 12;
    /** How much runs out of it in a tick, in mB: a bucket in two seconds. */
    public static final int PER_TICK = 25;
    /** Its cup: a bucket, as a cauldron gives one. */
    public static final int CAPACITY = 1_000;

    /** Who set it down; null for one of no player (a structure), which asks nobody. */
    private @Nullable UUID owner;
    /** What its owner may take from and give into across a claim's edge, as last asked. */
    private final DrawRights drawRights = new DrawRights();

    private long pouredAt = Long.MIN_VALUE / 2;
    private Fluid poured = Fluids.EMPTY;
    /** Ticks a whole-bucket vessel has been poured into so far (a cauldron), or 0. */
    private int filling;

    /** The cup: filled by a pipe or by the tap's own drawing; emptied only out of the spout ({@link #take}). */
    private final class Cup extends OneTank {
        Cup() {
            super(CAPACITY);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0; // a pipe joined to the tap's back pushes into it, never draws out
        }

        int take(FluidResource resource, int amount, TransactionContext transaction) {
            return super.extract(0, resource, amount, transaction);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            KitchenTapBlockEntity.this.setChanged();
        }
    }

    private final Cup cup = new Cup();

    /** The cup as the spout empties it. */
    private final ResourceHandler<FluidResource> spout = new Outlet(false);

    public KitchenTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KITCHEN_TAP.get(), pos, state);
    }

    /** The way in, for a pipe joined to its back. */
    public ResourceHandler<FluidResource> handler() {
        return this.cup;
    }

    /** What it poured last, for the stream drawn and heard under its spout. */
    public Fluid poured() {
        return this.poured;
    }

    /** How much its cup holds now, in mB. */
    public int held() {
        return this.cup.getAmountAsInt(0);
    }

    /** What it is set on, as a liquid's source: the face of the block behind it, or null. */
    @Nullable ResourceHandler<FluidResource> source() {
        if (this.level == null) {
            return null;
        }
        Direction facing = this.getBlockState().getValue(KitchenTapBlock.FACING);
        return this.level.getCapability(Capabilities.Fluid.BLOCK, this.worldPosition.relative(facing.getOpposite()), facing);
    }

    /** What stands under its spout, as a vessel, or null. */
    private @Nullable ResourceHandler<FluidResource> below() {
        return this.level == null ? null : this.level.getCapability(Capabilities.Fluid.BLOCK, this.worldPosition.below(), Direction.UP);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, KitchenTapBlockEntity tap) {
        if (state.getValue(KitchenTapBlock.OPEN) && tap.run()) {
            tap.pouredAt = level.getGameTime();
        }
        boolean shown = level.getGameTime() - tap.pouredAt <= POUR_SHOWN;
        if (state.getValue(KitchenTapBlock.POURING) != shown) {
            level.setBlock(pos, state.setValue(KitchenTapBlock.POURING, shown), Block.UPDATE_CLIENTS);
        }
    }

    /** One tick open: the cup topped up from what it is set on, and poured from into what is under it. Whether it ran. */
    private boolean run() {
        ResourceHandler<FluidResource> below = this.below();
        if (below == null || !this.may(false)) {
            this.filling = 0;
            return false;
        }
        ResourceHandler<FluidResource> source = this.source();
        int room = CAPACITY - this.held();
        if (source != null && room > 0 && this.may(true)) {
            FluidResource kind = this.held() > 0 ? this.cup.getResource(0) : firstIn(source);
            if (!kind.isEmpty() && (wouldTake(below, kind, PER_TICK) || wouldTake(below, kind, CAPACITY))) {
                Move.transfer(source, this.cup, kind, room);
            }
        }
        int held = this.held();
        if (held <= 0) {
            this.filling = 0;
            return false;
        }
        FluidResource kind = this.cup.getResource(0);
        if (Move.transfer(this.spout, below, kind, Math.min(PER_TICK, held)) > 0) {
            this.filling = 0;
            this.pouredNow(kind.getFluid());
            return true;
        }
        // a vessel that takes only whole buckets (a cauldron): poured for as long as the cup's worth would run, then
        // given it all at once
        if (!wouldTake(below, kind, held)) {
            this.filling = 0;
            return false;
        }
        this.pouredNow(kind.getFluid());
        if (++this.filling * PER_TICK >= held) {
            Move.transfer(this.spout, below, kind, held);
            this.filling = 0;
        }
        return true;
    }

    /**
     * Whether its owner may draw from what it is set on ({@code draw}), or pour into what is under it, across a claim's
     * edge ({@link DrawRights#mayMove}). Only on the server.
     */
    boolean may(boolean draw) {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel server)) {
            return false;
        }
        if (draw) {
            BlockPos from = this.worldPosition.relative(this.getBlockState().getValue(KitchenTapBlock.FACING).getOpposite());
            return DrawRights.mayMove(server, from, server.getBlockEntity(from), this.worldPosition, this);
        }
        BlockPos into = this.worldPosition.below();
        return DrawRights.mayMove(server, this.worldPosition, this, into, server.getBlockEntity(into));
    }

    /** What waits in its cup goes back into what it is set on, if that is there to take it: a liquid is no item to drop. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        ResourceHandler<FluidResource> source = this.source();
        if (this.level != null && !this.level.isClientSide() && source != null && this.held() > 0) {
            Move.transfer(this.spout, source, this.cup.getResource(0), this.held());
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

    /** Whether {@code below} takes {@code amount} of {@code kind}: asked, nothing moved. */
    private static boolean wouldTake(ResourceHandler<FluidResource> below, FluidResource kind, int amount) {
        try (Transaction asking = Transaction.openRoot()) {
            return below.insert(kind, amount, asking) > 0;
        }
    }

    static FluidResource firstIn(ResourceHandler<FluidResource> handler) {
        for (int i = 0; i < handler.size(); i++) {
            if (!handler.getResource(i).isEmpty() && handler.getAmountAsLong(i) > 0) {
                return handler.getResource(i);
            }
        }
        return FluidResource.EMPTY;
    }

    /**
     * What a bucket or a bottle held to the tap is filled from: its cup first, then what it is set on - as one
     * transaction, so a bucket takes a whole bucket or nothing. Nothing goes in by it.
     */
    ResourceHandler<FluidResource> outlet() {
        return new Outlet(true);
    }

    /** The cup emptied out of the spout - and, for a held bucket or bottle, what the tap is set on after it. */
    private final class Outlet implements ResourceHandler<FluidResource> {
        private final boolean throughToSource;

        Outlet(boolean throughToSource) {
            this.throughToSource = throughToSource;
        }

        private @Nullable ResourceHandler<FluidResource> behind() {
            KitchenTapBlockEntity tap = KitchenTapBlockEntity.this;
            return this.throughToSource && tap.may(true) ? tap.source() : null;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            KitchenTapBlockEntity tap = KitchenTapBlockEntity.this;
            if (tap.held() > 0) {
                return tap.cup.getResource(0);
            }
            ResourceHandler<FluidResource> behind = this.behind();
            return behind == null ? FluidResource.EMPTY : firstIn(behind);
        }

        @Override
        public long getAmountAsLong(int index) {
            KitchenTapBlockEntity tap = KitchenTapBlockEntity.this;
            long amount = tap.held();
            ResourceHandler<FluidResource> behind = this.behind();
            FluidResource kind = this.getResource(0);
            if (behind != null && !kind.isEmpty()) {
                for (int i = 0; i < behind.size(); i++) {
                    if (behind.getResource(i).equals(kind)) {
                        amount += behind.getAmountAsLong(i);
                    }
                }
            }
            return amount;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return this.getAmountAsLong(index);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return false;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            KitchenTapBlockEntity tap = KitchenTapBlockEntity.this;
            int taken = 0;
            if (tap.held() > 0 && tap.cup.getResource(0).equals(resource)) {
                taken = tap.cup.take(resource, amount, transaction);
            }
            ResourceHandler<FluidResource> behind = this.behind();
            if (taken < amount && behind != null) {
                taken += behind.extract(resource, amount - taken, transaction);
            }
            return taken;
        }
    }

    /** Something ran through it: the stream shows, in its colour. */
    private void pouredNow(Fluid fluid) {
        if (this.level == null || fluid == Fluids.EMPTY || fluid == this.poured) {
            return;
        }
        this.poured = fluid;
        this.setChanged();
        BlockState state = this.getBlockState();
        this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    /** On the client: the stream's sound held on while it pours. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, KitchenTapBlockEntity tap) {
        if (level.isClientSide() && state.getValue(KitchenTapBlock.POURING)) {
            dev.nookworks.client.TapStream.keep(level, pos, state, tap.poured);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.poured != Fluids.EMPTY) {
            output.store("poured", BuiltInRegistries.FLUID.byNameCodec(), this.poured);
        }
        this.cup.serialize(output.child("cup"));
        output.storeNullable("owner", net.minecraft.core.UUIDUtil.CODEC, this.owner);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.poured = input.read("poured", BuiltInRegistries.FLUID.byNameCodec()).orElse(Fluids.EMPTY);
        input.child("cup").ifPresent(this.cup::deserialize);
        this.owner = input.read("owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** To the client, only what it pours (for the stream): the cup is the server's. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (this.poured != Fluids.EMPTY) {
            tag.putString("poured", BuiltInRegistries.FLUID.getKey(this.poured).toString());
        }
        return tag;
    }
}
