package dev.nookworks.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.block.EnergyCableBlock;
import dev.nookworks.block.PipeSides;
import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Shared item output and energy distribution for Nookworks machines.
 * <p>
 * The energy network: a Generator - or another mod, through a cable's capability - sends power through Energy Cables
 * to every energy handler the cables touch. A network is walked once and shared by everything that sends into it, and
 * kept until something on it or beside it changes ({@link #networkChanged}), or for a second or two at most, so that
 * another mod's machine whose power comes and goes without a block changing is found again. Nothing is read through a
 * chunk that is not loaded already: a network stops at the edge of the loaded world, and never keeps a chunk loaded.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class MachineIO {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Whether a network cut short by the reach has been named in the log yet this run: once is enough to point at it. */
    private static boolean toldOfCut;

    /**
     * Into the container below {@code pos} if there is one (returns what did not fit), otherwise dropped out where there
     * is room ({@link #popOut}); with no room below or on top, all of it comes back and waits in the machine.
     */
    public static ItemStack deliverBelow(ServerLevel level, BlockPos pos, ItemStack stack) {
        var handler = level.getCapability(Capabilities.Item.BLOCK, pos.below(), Direction.UP);
        if (handler != null) {
            return ItemUtil.insertItemReturnRemaining(handler, stack.copy(), false, null);
        }
        return dropOut(level, pos, stack.copy()) ? ItemStack.EMPTY : stack;
    }

    /** Into the container below {@code pos} if there is one; what did not go in, or all of it with none, comes back. */
    public static ItemStack intoBelow(ServerLevel level, BlockPos pos, ItemStack stack) {
        var handler = level.getCapability(Capabilities.Item.BLOCK, pos.below(), Direction.UP);
        return handler == null ? stack : ItemUtil.insertItemReturnRemaining(handler, stack.copy(), false, null);
    }

    /**
     * What a machine worked by its handle has made, sent on much as a dropper sends what it holds (the user, 2026-10-31:
     * 「ドロッパーみたいに、よこにアイテムポイする方式に」 - an Oil Press with a pipe under it for its oil still has its cakes
     * gathered): into a container under it, as before, else into one in front of it, else out of its front - the spout
     * or chute each is drawn with - dropping onto the ground just in front, for a hopper set in the floor there or a
     * Magnet Chest to gather. {@code stack} is shrunk by what went; with a block in front that is neither a container nor
     * open where things come out, the rest waits in the machine. Nothing goes into a chunk not loaded.
     */
    public static void handOut(ServerLevel level, BlockPos pos, Direction front, ItemStack stack) {
        for (Direction side : new Direction[] {Direction.DOWN, front}) {
            BlockPos next = pos.relative(side);
            if (stack.isEmpty() || !level.isLoaded(next)) {
                continue;
            }
            var target = level.getCapability(Capabilities.Item.BLOCK, next, side.getOpposite());
            if (target != null) {
                stack.shrink(Move.insert(target, ItemResource.of(stack), stack.getCount()));
            } else if (side == front && mouthOpen(level, next, front)) {
                // out of the spout, a fifth of a block up, with a little push: it drops in the block in front, not two or
                // three blocks off as a dropper throws (seen in the game: a press on a pipe sent its cakes four blocks)
                var random = level.getRandom();
                double push = random.triangle(0.07, 0.015);
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5 + front.getStepX() * 0.65, pos.getY() + 0.2,
                        pos.getZ() + 0.5 + front.getStepZ() * 0.65, stack.copy(), front.getStepX() * push + random.triangle(0.0, 0.015),
                        0.1, front.getStepZ() * push + random.triangle(0.0, 0.015)));
                stack.setCount(0);
            }
        }
    }

    /**
     * Whether a thing handed out of a machine's front has room to come out into {@code next}: nothing there in the half of
     * the block nearest the machine, from just over a carpet to half way up. A carpet or a snow layer in front is no wall;
     * a slab, a stair, a fence or a pipe is.
     */
    private static boolean mouthOpen(ServerLevel level, BlockPos next, Direction front) {
        VoxelShape shape = level.getBlockState(next).getCollisionShape(level, next);
        if (shape.isEmpty()) {
            return true;
        }
        double x0 = front.getStepX() > 0 ? 0.0 : front.getStepX() < 0 ? 0.5 : 0.0;
        double z0 = front.getStepZ() > 0 ? 0.0 : front.getStepZ() < 0 ? 0.5 : 0.0;
        double x1 = front.getStepX() != 0 ? x0 + 0.5 : 1.0;
        double z1 = front.getStepZ() != 0 ? z0 + 0.5 : 1.0;
        return !Shapes.joinIsNotEmpty(shape, Shapes.box(x0, 0.1, z0, x1, 0.5, z1), BooleanOp.AND);
    }

    /**
     * Drops it out of the block where there is room ({@link #dropOut}); with none, on top all the same, as before 1.3:
     * for a caller that has nowhere to keep it. Kept as it was for other mods (nookTechnology 0.1.1 calls it, by this
     * signature); ours call {@link #dropOut} and keep what finds no room.
     */
    public static void popOut(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (!dropOut(level, pos, stack)) {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack, 0.0, 0.0, 0.0));
        }
    }

    /**
     * Drops below the block if that space is open, otherwise on top if that is, otherwise beside it, the first side
     * open (as the Recycler does); false, and nothing dropped, with all of them closed. It was dropped on top whatever
     * stood there, so with a block on top as well (stone over a Crusher standing on stone, a Vacuum Distiller's column)
     * it came out inside that block and was pushed out of it wherever it could go (nookTechnology's find, 2026-11-01).
     */
    public static boolean dropOut(ServerLevel level, BlockPos pos, ItemStack stack) {
        Vec3 at = null;
        if (open(level, pos.below())) {
            at = new Vec3(pos.getX() + 0.5, pos.getY() - 0.4, pos.getZ() + 0.5);
        } else if (open(level, pos.above())) {
            at = new Vec3(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        } else {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = pos.relative(side);
                // not into lava, fire or water beside it, which only an open cell under it ever took before
                if (level.isLoaded(beside) && open(level, beside) && level.getFluidState(beside).isEmpty()
                        && !level.getBlockState(beside).is(BlockTags.FIRE)) {
                    at = new Vec3(beside.getX() + 0.5, beside.getY() + 0.1, beside.getZ() + 0.5);
                    break;
                }
            }
        }
        if (at == null) {
            return false;
        }
        level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack, 0.0, 0.0, 0.0));
        return true;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * An energy receiver reached through the network, and every side of it the network touches. A machine can
     * refuse energy on one face and take it on another, so all of them are kept, not just the first one found.
     */
    public record Receiver(BlockPos pos, List<Direction> sides) {}

    /**
     * A block that is part of a machine whose block entity stands at another place (a Recycler's intake, over its body):
     * power sent to it is asked of claims as sent to the body, whose owner it is.
     */
    public interface Part {
        /** Where the block entity of the machine whose part stands at {@code pos} as {@code state} is. */
        BlockPos body(BlockState state, BlockPos pos);
    }

    /**
     * What a receiver takes power through, as last found: its handler through each side the network touches, and the
     * block entity that answers for it to claims (its own, or a part's body). Found again when NeoForge says what stands
     * there changed (a block entity set down or taken away, its chunk loaded or unloaded, a block saying its handlers
     * changed), and after a walk's time at most, as the networks are, for another mod's machine that changes unsaid.
     * Until then it is asked as found: every generator on a network looked up every receiver's chunk, block, block entity
     * and each side's capability every tick (the 10-19 performance audit).
     */
    private static final class Feed implements ICapabilityInvalidationListener {
        @Nullable EnergyHandler handler;
        @Nullable BlockEntity owner;
        /**
         * The chunk it was found in, to see it leave the full world: dropping below FULL (on its way to unloading) is
         * told by no invalidation, and a receiver there was nobody's to the per-tick {@link #loadedChunk} it stands for.
         */
        @Nullable LevelChunk chunk;
        /** When it is to be found again; never found yet, or told of a change: now. */
        long until = Long.MIN_VALUE;
        long usedAt;
        boolean listening;

        @Override
        public boolean onInvalidate() {
            this.until = Long.MIN_VALUE;
            this.listening = false;
            // let go of what was there: a base everyone left keeps its feeds until a generator sweeps them, and they
            // held its unloaded chunks and block entities meanwhile (found again before any is read)
            this.handler = null;
            this.owner = null;
            this.chunk = null;
            return false; // listened for again as it is next found
        }
    }

    /**
     * The chunk {@code pos} is in, if it is loaded - without loading it, or keeping it loaded. Reading a block through
     * the level does both: each read leaves a ticket that holds its chunk a tick longer, so a network read every tick
     * would hold every chunk it runs through loaded for good, after everyone had gone, and load one that had gone
     * unloaded on the spot, the server waiting on it.
     */
    public static @Nullable LevelChunk loadedChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /**
     * The receiver's energy handler, through whichever of its touched sides will take energy: an insert is offered
     * to each side in turn. The amount and capacity are the first side's - one machine, one store. Null when no
     * side has a handler any more, or its chunk is not loaded.
     */
    public static @Nullable EnergyHandler handlerOf(ServerLevel level, Receiver receiver) {
        return handlerOf(level, receiver, null, null);
    }

    /**
     * As {@link #handlerOf(ServerLevel, Receiver)}, for power sent from {@code origin} by {@code giver} (the block entity
     * there; null for a cable another mod puts power into): null too where claims keep it from the receiver
     * ({@link DrawRights#mayMove}). Asked only of a receiver with room - one that is full is handed back, to be given
     * nothing. One that tells of no room at all (a capacity of 0: another mod's cable that keeps nothing and passes power
     * straight on) is not taken for full, as it may take power all the same: it is asked too (r15 fix check 4, N3). Its
     * block entity is read through its loaded chunk, as the rest is: read through the level, every receiver's chunk was
     * held loaded by the generator asking every tick (r15 fix check 3).
     */
    public static @Nullable EnergyHandler handlerOf(ServerLevel level, Receiver receiver, @Nullable BlockPos origin, @Nullable BlockEntity giver) {
        Kept kept = KEPT.computeIfAbsent(level.dimension(), k -> new Kept());
        return given(level, kept.feeds.computeIfAbsent(receiver, r -> new Feed()), receiver, origin, giver);
    }

    /** {@link #handlerOf(ServerLevel, Receiver, BlockPos, BlockEntity)}, through the receiver's {@code feed}. */
    private static @Nullable EnergyHandler given(ServerLevel level, Feed feed, Receiver receiver, @Nullable BlockPos origin,
            @Nullable BlockEntity giver) {
        long now = level.getGameTime();
        if (now >= feed.until || feed.chunk != null && !feed.chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)) {
            feed.until = find(level, receiver, feed) ? until(now, receiver.pos()) : now + 1;
        }
        feed.usedAt = now;
        EnergyHandler handler = feed.handler;
        if (handler == null || origin == null
                || handler.getCapacityAsLong() > 0 && handler.getAmountAsLong() >= handler.getCapacityAsLong()
                || DrawRights.mayMove(level, origin, giver, receiver.pos(), feed.owner)) {
            return handler;
        }
        return null; // across a claim's edge from where the power comes, where it may not go
    }

    /**
     * Finds what {@code receiver} takes power through, read through its loaded chunk; out of the loaded world, nothing (it
     * gets nothing, and is not loaded to be given it) - and false, to be looked for again next tick, as the chunk may come
     * back into the full world with no word from NeoForge. Otherwise NeoForge tells the feed when it may have changed.
     */
    private static boolean find(ServerLevel level, Receiver receiver, Feed feed) {
        if (!feed.listening) {
            level.registerCapabilityListener(receiver.pos(), feed);
            feed.listening = true;
        }
        LevelChunk chunk = loadedChunk(level, receiver.pos());
        feed.chunk = chunk;
        if (chunk == null) {
            feed.handler = null;
            feed.owner = null;
            return false;
        }
        BlockState state = chunk.getBlockState(receiver.pos());
        BlockEntity blockEntity = state.hasBlockEntity() ? chunk.getBlockEntity(receiver.pos()) : null;
        feed.handler = sidesHandler(level, receiver, state, blockEntity);
        // a part answers as its body (r4: a Recycler's intake, having no block entity, was asked as nobody's - a
        // stranger's intake touching a claimed cable was powered while the body was refused); the body stands in the
        // same column, so in the same chunk
        feed.owner = blockEntity == null && state.getBlock() instanceof Part part
                ? chunk.getBlockEntity(part.body(state, receiver.pos())) : blockEntity;
        return true;
    }

    /** The handler of the receiver standing as {@code state}, with {@code blockEntity}, through each touched side. */
    private static @Nullable EnergyHandler sidesHandler(ServerLevel level, Receiver receiver, BlockState state, @Nullable BlockEntity blockEntity) {
        List<EnergyHandler> handlers = new ArrayList<>(receiver.sides().size());
        for (Direction side : receiver.sides()) {
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, receiver.pos(), state, blockEntity, side);
            // once each: a machine that gives every side the same store (ours do) took a cable touching two sides twice
            // over, past what it takes in a tick
            if (handler != null && handlers.stream().noneMatch(h -> h == handler)) {
                handlers.add(handler);
            }
        }
        if (handlers.size() <= 1) {
            return handlers.isEmpty() ? null : handlers.get(0);
        }
        EnergyHandler first = handlers.get(0);
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return first.getAmountAsLong();
            }

            @Override
            public long getCapacityAsLong() {
                return first.getCapacityAsLong();
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                int accepted = 0;
                for (EnergyHandler handler : handlers) {
                    if (accepted >= amount) {
                        break;
                    }
                    accepted += handler.insert(amount - accepted, transaction);
                }
                return accepted;
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                int taken = 0;
                for (EnergyHandler handler : handlers) {
                    if (taken >= amount) {
                        break;
                    }
                    taken += handler.extract(amount - taken, transaction);
                }
                return taken;
            }
        };
    }

    /**
     * A cable network as walked from one of its cables: the cables walked, every energy handler they touch with the
     * sides touched, and whether cables were still to follow when the walk stopped at the reach. Shared by every
     * generator beside it and every insert another mod makes into it, until it goes stale or runs out its time.
     */
    private static final class Network {
        final List<Receiver> receivers;
        final boolean cut;
        final long until;
        boolean stale;

        Network(List<Receiver> receivers, boolean cut, long until) {
            this.receivers = receivers;
            this.cut = cut;
            this.until = until;
        }

        boolean fresh(long now) {
            return !this.stale && now < this.until;
        }
    }

    /**
     * Where one origin - a generator, or a cable another mod puts power into - sends: what touches it straight on,
     * and the networks of the cables beside it (or its own), as one list. Fresh while they all are.
     */
    private static final class Reach {
        final List<Receiver> receivers;
        /** Each receiver's feed, in the same order. */
        final List<Feed> feeds;
        final List<Network> networks;
        final boolean cut;
        final long until;
        boolean stale;

        Reach(List<Receiver> receivers, List<Feed> feeds, List<Network> networks, boolean cut, long until) {
            this.receivers = receivers;
            this.feeds = feeds;
            this.networks = networks;
            this.cut = cut;
            this.until = until;
        }

        boolean fresh(long now) {
            if (this.stale || now >= this.until) {
                return false;
            }
            for (Network network : this.networks) {
                if (!network.fresh(now)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** What is kept for one level: each walked cable's network, each origin's reach, each receiver's feed. Server thread only. */
    private static final class Kept {
        final Map<BlockPos, Network> byCable = new HashMap<>();
        final Map<BlockPos, Reach> byOrigin = new HashMap<>();
        final Map<Receiver, Feed> feeds = new HashMap<>();
        long sweptAt;
    }

    private static final Map<ResourceKey<Level>, Kept> KEPT = new HashMap<>();
    /** The least a walk is trusted with nothing changing on it: another mod's machine that changed unseen is found by then. */
    private static final int KEEP = 20;
    /** How often what has run out its time is cleared away, so networks no longer used do not pile up. */
    private static final int SWEEP = 600;

    /**
     * When a walk from {@code start} runs out: {@link #KEEP} ticks and up to as many again, by place - so that the
     * networks of a big base are walked again at different ticks, not all in the same one.
     */
    private static long until(long now, BlockPos start) {
        return now + KEEP + Math.floorMod(start.hashCode(), KEEP);
    }

    /**
     * Something at {@code pos} changed the energy network - a cable set down, broken, or a side of it turned off or on;
     * a machine of ours set down or broken: the networks on or beside it, and the reach of whatever sends from beside
     * it, are walked again when next used. Nothing elsewhere is touched.
     */
    public static void networkChanged(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Kept kept = KEPT.get(server.dimension());
        if (kept == null) {
            return;
        }
        forget(kept, pos);
        for (Direction dir : Direction.values()) {
            forget(kept, pos.relative(dir));
        }
    }

    /** Every network everywhere is stale: the reach setting changed. */
    public static void networkChanged() {
        for (Kept kept : KEPT.values()) {
            kept.byCable.values().forEach(network -> network.stale = true);
            kept.byOrigin.values().forEach(reach -> reach.stale = true);
            kept.byCable.clear();
            kept.byOrigin.clear();
        }
    }

    private static void forget(Kept kept, BlockPos pos) {
        Network network = kept.byCable.remove(pos);
        if (network != null) {
            network.stale = true;
        }
        Reach reach = kept.byOrigin.remove(pos);
        if (reach != null) {
            reach.stale = true;
        }
    }

    /**
     * Every non-cable energy handler reachable from {@code origin}: its direct neighbours and anything connected
     * through Energy Cables, up to the reach of the {@code energyCableReach} setting. {@code origin} itself is never
     * included, nor a Generator (which takes no power in). Each receiver's handler is kept as found until NeoForge says
     * it may have changed ({@link Feed}).
     */
    public static List<Receiver> receivers(ServerLevel level, BlockPos origin) {
        return reach(level, origin).receivers;
    }

    /**
     * Whether a network from {@code origin} runs on past the reach, so that the machines further along get nothing
     * from it. A Generator says so on its screen.
     */
    public static boolean cutShort(ServerLevel level, BlockPos origin) {
        return reach(level, origin).cut;
    }

    private static Reach reach(ServerLevel level, BlockPos origin) {
        Kept kept = KEPT.computeIfAbsent(level.dimension(), k -> new Kept());
        long now = level.getGameTime();
        sweep(kept, now);
        Reach known = kept.byOrigin.get(origin);
        if (known != null && known.fresh(now)) {
            return known;
        }
        Map<BlockPos, List<Direction>> found = new LinkedHashMap<>();
        List<Network> networks = new ArrayList<>();
        LevelChunk chunk = loadedChunk(level, origin);
        if (chunk != null && chunk.getBlockState(origin).getBlock() instanceof EnergyCableBlock) {
            networks.add(networkAt(level, kept, origin, now)); // a cable sends into its own network
        } else if (chunk != null) {
            for (Direction dir : Direction.values()) {
                BlockPos next = origin.relative(dir);
                LevelChunk nextChunk = loadedChunk(level, next);
                if (nextChunk == null) {
                    continue;
                }
                BlockState nextState = nextChunk.getBlockState(next);
                if (nextState.getBlock() instanceof EnergyCableBlock) {
                    if (!PipeSides.isOff(nextState, dir.getOpposite())) {
                        Network network = networkAt(level, kept, next, now);
                        if (networks.stream().noneMatch(n -> n == network)) {
                            networks.add(network);
                        }
                    }
                } else if (receives(level, nextChunk, next, nextState, dir.getOpposite())) {
                    addSide(found, next, dir.getOpposite());
                }
            }
        }
        boolean cut = false;
        for (Network network : networks) {
            cut |= network.cut;
            for (Receiver receiver : network.receivers) {
                if (!receiver.pos().equals(origin)) {
                    for (Direction side : receiver.sides()) {
                        addSide(found, receiver.pos(), side);
                    }
                }
            }
        }
        List<Receiver> receivers = new ArrayList<>(found.size());
        found.forEach((pos, sides) -> receivers.add(new Receiver(pos, List.copyOf(sides))));
        List<Feed> feeds = new ArrayList<>(receivers.size());
        for (Receiver receiver : receivers) {
            feeds.add(kept.feeds.computeIfAbsent(receiver, r -> new Feed()));
        }
        Reach reach = new Reach(List.copyOf(receivers), List.copyOf(feeds), List.copyOf(networks), cut, until(now, origin));
        kept.byOrigin.put(origin.immutable(), reach);
        return reach;
    }

    /** The network of the cable at {@code cable}: as kept, or walked now from there. */
    private static Network networkAt(ServerLevel level, Kept kept, BlockPos cable, long now) {
        Network network = kept.byCable.get(cable);
        return network != null && network.fresh(now) ? network : walk(level, kept, cable.immutable(), now);
    }

    /** Clears away, now and then, what has run out its time and was not walked again. */
    private static void sweep(Kept kept, long now) {
        if (now - kept.sweptAt >= 0 && now - kept.sweptAt < SWEEP) {
            return;
        }
        kept.sweptAt = now;
        kept.byCable.values().removeIf(network -> !network.fresh(now));
        kept.byOrigin.values().removeIf(reach -> !reach.fresh(now));
        // a feed nothing has asked for a while: its listener goes with it (NeoForge holds listeners weakly)
        kept.feeds.values().removeIf(feed -> now - feed.usedAt >= SWEEP || now < feed.usedAt);
    }

    /** Forgets every kept walk: the next world opened in this game must not see this one's. */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        KEPT.clear();
        toldOfCut = false;
    }

    /** Walks the cables from {@code start}, up to the reach, and keeps the network for every cable walked. */
    private static Network walk(ServerLevel level, Kept kept, BlockPos start, long now) {
        Map<BlockPos, List<Direction>> found = new LinkedHashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> walked = new ArrayList<>();
        ArrayDeque<BlockPos> cables = new ArrayDeque<>();
        seen.add(start);
        cables.add(start);
        int reach = NookworksConfig.ENERGY_CABLE_REACH.getAsInt();
        while (!cables.isEmpty() && walked.size() < reach) {
            BlockPos cable = cables.poll();
            walked.add(cable);
            visit(level, cable, seen, cables, found);
        }
        boolean cut = !cables.isEmpty();
        if (cut && !toldOfCut) {
            toldOfCut = true;
            LOGGER.info("The Energy Cable network at {} runs on past {} cables, the most one is followed through (the energyCableReach"
                    + " setting): machines further along get no power from it. Said once a run.", start.toShortString(), reach);
        }
        List<Receiver> receivers = new ArrayList<>(found.size());
        found.forEach((pos, sides) -> receivers.add(new Receiver(pos, List.copyOf(sides))));
        Network network = new Network(List.copyOf(receivers), cut, until(now, start));
        for (BlockPos cable : walked) {
            kept.byCable.put(cable, network);
        }
        return network;
    }

    /**
     * Looks round the cable at {@code cable}: through each side it is joined on, a cable to walk on to (unless its
     * side this way is off) or something that takes power. A cable is walked only once, but a machine is recorded with
     * each side a cable touches. Nothing in an unloaded chunk is looked at.
     */
    private static void visit(ServerLevel level, BlockPos cable, Set<BlockPos> seen, ArrayDeque<BlockPos> cables,
            Map<BlockPos, List<Direction>> found) {
        LevelChunk chunk = loadedChunk(level, cable);
        BlockState state = chunk == null ? null : chunk.getBlockState(cable);
        if (state == null || !(state.getBlock() instanceof EnergyCableBlock)) {
            return;
        }
        for (Direction dir : Direction.values()) {
            if (!PipeSides.joined(state, dir)) {
                continue; // a cable carries on only through the sides it is joined on
            }
            BlockPos next = cable.relative(dir);
            LevelChunk nextChunk = loadedChunk(level, next);
            if (nextChunk == null) {
                continue;
            }
            BlockState nextState = nextChunk.getBlockState(next);
            if (nextState.getBlock() instanceof EnergyCableBlock) {
                if (!PipeSides.isOff(nextState, dir.getOpposite()) && seen.add(next)) {
                    cables.add(next);
                }
            } else if (receives(level, nextChunk, next, nextState, dir.getOpposite())) {
                addSide(found, next, dir.getOpposite());
            }
        }
    }

    /**
     * Whether the block {@code state} at {@code pos}, in its loaded {@code chunk}, has an energy handler on {@code side}
     * that power can be sent to. A Generator (or Diesel Generator, or Creative Generator) takes none in: walked as a
     * receiver it would only be asked every tick.
     */
    private static boolean receives(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state, Direction side) {
        BlockEntity blockEntity = state.hasBlockEntity() ? chunk.getBlockEntity(pos) : null;
        return !(blockEntity instanceof GeneratorBlockEntity || blockEntity instanceof DieselGeneratorBlockEntity
                || blockEntity instanceof CreativeGeneratorBlockEntity)
                && level.getCapability(Capabilities.Energy.BLOCK, pos, state, blockEntity, side) != null;
    }

    private static void addSide(Map<BlockPos, List<Direction>> found, BlockPos pos, Direction side) {
        List<Direction> sides = found.computeIfAbsent(pos.immutable(), p -> new ArrayList<>());
        if (!sides.contains(side)) {
            sides.add(side);
        }
    }

    /**
     * Pushes up to {@code max} FE from {@code source} to every receiver around {@code origin}: an even share first (so
     * one hungry machine cannot starve the rest), then what is left to whoever still takes it. Each tick the turn
     * starts further along, and from a different place for each origin, so that when there is less than a share for
     * every machine, every machine still gets its turn - not the first ones found, every tick. None goes to a receiver
     * claims keep it from ({@link DrawRights#mayMove}, the block at {@code origin} giving it). Returns FE sent.
     */
    public static int pushEnergy(ServerLevel level, BlockPos origin, EnergyHandler source, int max) {
        if (Math.min(max, source.getAmountAsLong()) <= 0) {
            return 0; // an empty store walks no cables
        }
        Reach reach = reach(level, origin);
        List<Receiver> receivers = reach.receivers;
        if (receivers.isEmpty()) {
            return 0;
        }
        LevelChunk here = loadedChunk(level, origin);
        BlockEntity giver = here == null ? null : here.getBlockEntity(origin);
        List<EnergyHandler> targets = new ArrayList<>(receivers.size());
        Set<EnergyHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int start = Math.floorMod(level.getGameTime() + origin.hashCode(), receivers.size());
        for (int i = 0; i < receivers.size(); i++) {
            int at = (start + i) % receivers.size();
            EnergyHandler target = given(level, reach.feeds.get(at), receivers.get(at), origin, giver);
            // one store reached through two blocks (a Recycler cabled at its body and its intake) takes one share
            if (target != null && seen.add(target)) {
                targets.add(target);
            }
        }
        return share(source, targets, (int) Math.min(max, source.getAmountAsLong()));
    }

    /** The least a machine is sent at once: a budget too small to give each that much goes round fewer a tick, in turn. */
    static final int MIN_SHARE = 50;

    /**
     * Sends {@code budget} FE from {@code source} among {@code targets}, in their order: an even share, then the rest.
     * Only to as many as get {@link #MIN_SHARE} or more - the order turns each tick, so each has its turn - and never to
     * one that is full. Spread in crumbs over every machine, it cost a transaction for each, every tick: 40 Generators on
     * 40 Electric Furnaces opened 1,600 a tick, half the base's tick time (r7 load measurement).
     */
    private static int share(EnergyHandler source, List<EnergyHandler> targets, int budget) {
        if (targets.isEmpty() || budget <= 0) {
            return 0;
        }
        List<EnergyHandler> hungry = new ArrayList<>(targets.size());
        List<EnergyHandler> through = new ArrayList<>(0);
        for (EnergyHandler target : targets) {
            if (target.getAmountAsLong() < target.getCapacityAsLong()) {
                hungry.add(target);
            } else if (target.getCapacityAsLong() == 0) {
                through.add(target);
            }
        }
        // one that keeps nothing (another mod's cable passing power straight on) is asked too, after the rest, as
        // handlerOf keeps it on purpose: it never counted as hungry and got nothing (r21, 2026-11-06)
        hungry.addAll(through);
        if (hungry.isEmpty()) {
            return 0;
        }
        int sent = 0;
        int reach = Math.max(1, Math.min(hungry.size(), budget / MIN_SHARE));
        int share = budget / reach;
        for (int i = 0; i < reach && sent < budget; i++) {
            sent += transfer(source, hungry.get(i), Math.min(share, budget - sent));
        }
        for (EnergyHandler target : hungry) {
            if (sent >= budget) {
                break;
            }
            sent += transfer(source, target, budget - sent);
        }
        return sent;
    }

    /**
     * Moves up to {@code amount} FE atomically: only what the target accepts leaves the source, and no more than the
     * source gives. When it gives less than the target would take, the smaller amount is moved: another mod's source
     * may give less than it was asked, and asking the two sides to agree on the larger moved nothing at all to a lone
     * Laser (r7 review).
     */
    public static int transfer(EnergyHandler from, EnergyHandler to, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int accepted = to.insert(amount, tx);
            if (accepted <= 0) {
                return 0;
            }
            int taken = from.extract(accepted, tx);
            if (taken == accepted) {
                tx.commit();
                return taken;
            }
            amount = taken; // closing without commit rolls both sides back; then once more, at what the source gives
        }
        if (amount <= 0) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            if (to.insert(amount, tx) != amount || from.extract(amount, tx) != amount) {
                return 0;
            }
            tx.commit();
            return amount;
        }
    }

    private MachineIO() {}
}
