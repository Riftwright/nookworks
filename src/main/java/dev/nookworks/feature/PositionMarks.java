package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * A set of marked block positions, kept per chunk in a chunk attachment. The Fireproof Coating and the memory of
 * which logs a player placed are both this: something true of one block, remembered by where it is.
 * <p>
 * Remembering by position means the mark has to move when the block does. A piston is the one ordinary way a
 * block changes place, so every PositionMarks follows its blocks through a piston push or pull: what is marked
 * before the move is marked where it lands afterwards, and nothing is left behind on the square it left.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class PositionMarks {
    private static final List<PositionMarks> ALL = new ArrayList<>();

    private final Supplier<AttachmentType<LongOpenHashSet>> type;

    /** Treat the attachment's set as immutable: replace it, never change it in place. */
    public PositionMarks(Supplier<AttachmentType<LongOpenHashSet>> type) {
        this.type = type;
        ALL.add(this);
    }

    public boolean has(BlockGetter level, BlockPos pos) {
        if (!(level instanceof Level world) || !world.isLoaded(pos)) {
            return false;
        }
        LevelChunk chunk = world.getChunkAt(pos);
        return chunk.hasData(this.type) && chunk.getData(this.type).contains(pos.asLong());
    }

    /** Marks or unmarks {@code pos}. Returns false if it was already that way. */
    public boolean set(Level level, BlockPos pos, boolean marked) {
        LevelChunk chunk = level.getChunkAt(pos);
        LongOpenHashSet current = chunk.getData(this.type);
        if (current.contains(pos.asLong()) == marked) {
            return false;
        }
        LongOpenHashSet next = new LongOpenHashSet(current);
        if (marked) {
            next.add(pos.asLong());
        } else {
            next.remove(pos.asLong());
        }
        chunk.setData(this.type, next); // marks the chunk dirty, and re-syncs it if the type syncs
        return true;
    }

    // ---- forgetting a block gone without a break ----

    /**
     * A block gone by fire, a Wither, the dragon or decay posts no break event, so its square kept its marks: a placed
     * log burnt away left its mark, and a tree that later grew through the square was taken there for a wall, felling
     * only part way (round two of r21). Every change that tells the blocks around comes through here; one that left air,
     * fire or a liquid forgets the square. Every mark is of the block on its square, so none outlives it.
     */
    @SubscribeEvent
    static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        BlockState now = event.getState();
        if (!(now.isAir() || now.is(BlockTags.FIRE) || now.liquid()) || !(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        for (PositionMarks marks : ALL) {
            if (marks.has(level, event.getPos())) {
                marks.set(level, event.getPos(), false);
            }
        }
    }

    // ---- following blocks through pistons ----

    /** A marked block about to be moved: where it is, which block it is, and where it is going. */
    private record Carried(PositionMarks marks, BlockPos from, Block block, BlockPos to) {}

    /**
     * Moves under way, by dimension and piston. Pre and Post come in pairs around the move itself; keying them by
     * the piston rather than stacking them means a move that another mod cancels after Pre (so no Post follows)
     * is simply overwritten by that piston's next Pre instead of being paired with some other piston's Post.
     */
    private static final Map<Key, Move> PENDING = new HashMap<>();

    /** What one piston move will carry, and the squares whose blocks it will break instead of pushing. */
    private record Move(List<Carried> carried, List<Cleared> cleared) {}

    private record Cleared(PositionMarks marks, BlockPos pos) {}

    private record Key(ResourceKey<Level> dimension, BlockPos piston) {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void beforePiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        List<Carried> carried = new ArrayList<>();
        List<Cleared> cleared = new ArrayList<>();
        Direction push = resolver.getPushDirection();
        for (PositionMarks marks : ALL) {
            for (BlockPos pos : resolver.getToPush()) {
                if (marks.has(level, pos)) {
                    carried.add(new Carried(marks, pos.immutable(), level.getBlockState(pos).getBlock(), pos.relative(push)));
                }
            }
            for (BlockPos pos : resolver.getToDestroy()) {
                if (marks.has(level, pos)) {
                    cleared.add(new Cleared(marks, pos.immutable())); // it breaks and drops; its square is left clean
                }
            }
        }
        Key key = new Key(level.dimension(), event.getPos().immutable());
        if (carried.isEmpty() && cleared.isEmpty()) {
            PENDING.remove(key);
        } else {
            PENDING.put(key, new Move(carried, cleared));
        }
    }

    @SubscribeEvent
    static void afterPiston(PistonEvent.Post event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        Move move = PENDING.remove(new Key(level.dimension(), event.getPos()));
        if (move == null) {
            return;
        }
        // Cleared first: a pushed block may land on the square of one that broke, and brings its own mark or none.
        for (Cleared c : move.cleared()) {
            c.marks().set(level, c.pos(), false);
        }
        List<Carried> carried = move.carried();
        // Only what actually moved: a non-sticky piston retracting pulls nothing, however it resolved.
        List<Carried> moved = carried.stream()
                .filter(c -> level.getBlockEntity(c.to()) instanceof PistonMovingBlockEntity moving
                        && moving.getMovedState().is(c.block()))
                .toList();
        for (Carried c : moved) {
            c.marks().set(level, c.from(), false);
        }
        for (Carried c : moved) {
            c.marks().set(level, c.to(), true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
