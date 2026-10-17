package dev.nookworks.dowsing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The search a Nook Dowsing Rod makes for the nearest biome it is after, as /locate biome makes it - columns 32 blocks
 * apart in a spiral out from where it starts, each sampled down its height every 64 blocks - but a few milliseconds a
 * tick instead of all at once: out to 6,400 blocks, where the biome is scarce, a search made in one go held the server
 * up for a second or more. It asks the world's biome noise, not its chunks: nothing is loaded or generated.
 * <p>
 * Or for the nearest structure of a tag, as /locate structure makes it ({@link StructureSearch}), the same few
 * milliseconds a tick.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class DowsingSearch {
    public static final int RADIUS = 6400;
    private static final int STEP_HORIZONTAL = 32;
    private static final int STEP_VERTICAL = 64;
    /** Nanoseconds of searching a tick, every search together: shared out evenly among those running. */
    private static final long BUDGET = 3_000_000L;

    private static final List<DowsingSearch> RUNNING = new ArrayList<>();

    public enum Outcome {
        /** Found, at {@link Result#at}. */
        FOUND,
        /** Not within {@link #RADIUS}. */
        NONE,
        /** The dimension has none of it anywhere (the Nether, the End): told at once, with no search. */
        NOT_HERE
    }

    public record Result(Outcome outcome, @Nullable BlockPos at) {}

    /** The work of one search, a slice at a time: its result when done, null while there is more to look at. */
    interface Stepper {
        @Nullable Result step(long deadline);
    }

    private final ServerLevel level;
    private final Stepper stepper;
    private final Consumer<Result> done;
    private @Nullable Object owner;

    private DowsingSearch(ServerLevel level, Stepper stepper, Consumer<Result> done) {
        this.level = level;
        this.stepper = stepper;
        this.done = done;
    }

    /** The biome search: columns in a spiral out from {@code origin}, each sampled down its height. */
    private static Stepper biomes(ServerLevel level, BlockPos origin, Set<Holder<Biome>> wanted, int radius) {
        BlockPos from = origin.immutable();
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        // as BiomeSource#findClosestBiome3d samples (26.3 reads the climate through a resolver)
        BiomeResolver resolver = source.createCachingResolver(level.getChunkSource().randomState());
        Iterator<BlockPos.MutableBlockPos> columns = BlockPos.spiralAround(BlockPos.ZERO, Math.floorDiv(radius, STEP_HORIZONTAL),
                Direction.EAST, Direction.SOUTH).iterator();
        int[] heights = Mth.outFromOrigin(from.getY(), level.getMinY() + 1, level.getMaxY() + 1, STEP_VERTICAL).toArray();
        return deadline -> {
            while (columns.hasNext()) {
                BlockPos.MutableBlockPos column = columns.next();
                int x = from.getX() + column.getX() * STEP_HORIZONTAL;
                int z = from.getZ() + column.getZ() * STEP_HORIZONTAL;
                for (int y : heights) {
                    if (wanted.contains(resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)))) {
                        return new Result(Outcome.FOUND, new BlockPos(x, y, z));
                    }
                }
                if (System.nanoTime() > deadline) {
                    return null;
                }
            }
            return new Result(Outcome.NONE, null);
        };
    }

    /**
     * Starts a search from {@code origin} for a biome {@code wanted} takes, out to {@code radius}; {@code done} hears how
     * it went, in a later tick. One search an owner at a time (a player's UUID; owners are told apart by equals): false,
     * and nothing started, while theirs runs.
     */
    public static boolean start(ServerLevel level, BlockPos origin, Predicate<Holder<Biome>> wanted, int radius, @Nullable Object owner,
            Consumer<Result> done) {
        if (owner != null && running(owner)) {
            return false;
        }
        Set<Holder<Biome>> candidates = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .filter(wanted).collect(Collectors.toUnmodifiableSet());
        if (candidates.isEmpty()) {
            done.accept(new Result(Outcome.NOT_HERE, null));
            return true;
        }
        run(level, biomes(level, origin, candidates, radius), owner, done);
        return true;
    }

    /**
     * Starts a search from {@code origin} for the nearest structure of {@code wanted} the world makes, out to
     * {@code radius}, as {@link #start} does for a biome: NOT_HERE at once where this world makes none of them.
     */
    public static boolean startStructures(ServerLevel level, BlockPos origin, TagKey<Structure> wanted, int radius, @Nullable Object owner,
            Consumer<Result> done) {
        if (owner != null && running(owner)) {
            return false;
        }
        StructureSearch search = StructureSearch.of(level, origin, wanted, radius);
        if (search == null) {
            done.accept(new Result(Outcome.NOT_HERE, null));
            return true;
        }
        run(level, search, owner, done);
        return true;
    }

    private static void run(ServerLevel level, Stepper stepper, @Nullable Object owner, Consumer<Result> done) {
        DowsingSearch search = new DowsingSearch(level, stepper, done);
        search.owner = owner;
        RUNNING.add(search);
    }

    /** Whether a search of {@code owner}'s is running. */
    public static boolean running(Object owner) {
        return RUNNING.stream().anyMatch(search -> owner.equals(search.owner));
    }

    /** The same search made all at once, for the tests. */
    public static Result findNow(ServerLevel level, BlockPos origin, Predicate<Holder<Biome>> wanted, int radius) {
        Result[] found = new Result[1];
        start(level, origin, wanted, radius, null, result -> found[0] = result);
        return found[0] != null ? found[0] : finishLast();
    }

    /** The structure search made all at once, for the tests. */
    public static Result findStructureNow(ServerLevel level, BlockPos origin, TagKey<Structure> wanted, int radius) {
        Result[] found = new Result[1];
        startStructures(level, origin, wanted, radius, null, result -> found[0] = result);
        return found[0] != null ? found[0] : finishLast();
    }

    private static Result finishLast() {
        DowsingSearch search = RUNNING.removeLast();
        Result result;
        while ((result = search.stepper.step(Long.MAX_VALUE)) == null) {
            // to the end
        }
        return result;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (RUNNING.isEmpty()) {
            return;
        }
        // an even share each, so one long search (one that finds nothing looks at every column) holds no other up; a
        // copy, as what hears of a search's end may start another
        long share = BUDGET / RUNNING.size();
        for (DowsingSearch search : List.copyOf(RUNNING)) {
            if (search.level.getServer() != event.getServer()) {
                RUNNING.remove(search); // left over from a world closed in the middle of it
                continue;
            }
            Result result = search.stepper.step(System.nanoTime() + share);
            if (result != null) {
                RUNNING.remove(search);
                search.done.accept(result);
            }
        }
    }

    /** A world closed in the middle of a search: nothing of it is held on to until the next is opened. */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        RUNNING.clear();
    }
}
