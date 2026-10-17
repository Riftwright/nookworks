package dev.nookworks.mercury;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.block.PipeSides;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.MachineIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Moves mercury between Nookworks machines and tanks: every handler touching {@code origin} directly or through
 * connected Mercury Pipes is reachable. Producers push into consumers first, then storage; consumers pull from
 * storage only (so two machines never fight over the same batch).
 * <p>
 * A walk of the pipes is kept, as the energy network's is: until a pipe, tank or machine of mercury in the level is
 * set down, broken or has a side turned, or for a second or two - a row of distillation towers asks every tick. Only
 * chunks already loaded are read, so a line of pipe holds no chunk loaded.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class MercuryNetwork {
    private static final int MAX_PIPES = 512;
    private static final int KEEP = 20;
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Whether a line cut short by the pipe limit has been named in the log yet this run. */
    private static boolean toldOfCut;

    /** A walk from one origin: where the handlers it reached are, nearest first. */
    private record Walk(List<BlockPos> handlers, int generation, long until) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Walk>> WALKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> GENERATION = new HashMap<>();

    /** A mercury pipe, tank or machine in {@code level} was set down, broken or had a side turned: its walks are stale. */
    public static void changed(Level level) {
        if (level instanceof ServerLevel server) {
            GENERATION.merge(server.dimension(), 1, Integer::sum);
        }
    }

    /** Every mercury handler reachable from {@code origin}, nearest first; {@code origin} itself excluded. */
    public static List<MercuryHandler> handlers(ServerLevel level, BlockPos origin) {
        Walk walk = walk(level, origin);
        List<MercuryHandler> found = new ArrayList<>(walk.handlers().size());
        for (BlockPos pos : walk.handlers()) {
            LevelChunk chunk = MachineIO.loadedChunk(level, pos);
            if (chunk != null && chunk.getBlockEntity(pos) instanceof MercuryHandler handler && !((BlockEntity) handler).isRemoved()) {
                found.add(handler);
            }
        }
        return found;
    }

    private static Walk walk(ServerLevel level, BlockPos origin) {
        Map<BlockPos, Walk> walks = WALKS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        int generation = GENERATION.getOrDefault(level.dimension(), 0);
        long now = level.getGameTime();
        Walk kept = walks.get(origin);
        if (kept != null && kept.generation() == generation && now < kept.until() && now >= kept.until() - 2L * KEEP) {
            return kept;
        }
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> pipes = new ArrayDeque<>();
        seen.add(origin);
        visit(level, origin, seen, pipes, found);
        int walked = 0;
        while (!pipes.isEmpty() && walked++ < MAX_PIPES) {
            visit(level, pipes.poll(), seen, pipes, found);
        }
        if (!pipes.isEmpty() && !toldOfCut) {
            toldOfCut = true;
            LOGGER.info("The Mercury Pipe line at {} runs on past {} pipes, the most one is followed through: tanks and machines further"
                    + " along are not reached from it. Said once a run.", origin.toShortString(), MAX_PIPES);
        }
        if (walks.size() > 4_096) {
            walks.values().removeIf(w -> w.generation() != generation || now >= w.until());
        }
        Walk walk = new Walk(List.copyOf(found), generation, now + KEEP + Math.floorMod(origin.hashCode(), KEEP));
        walks.put(origin.immutable(), walk);
        return walk;
    }

    /** Forgets every kept walk: the next world opened in this game must not see this one's. */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        WALKS.clear();
        GENERATION.clear();
        toldOfCut = false;
    }

    private static void visit(ServerLevel level, BlockPos from, Set<BlockPos> seen, ArrayDeque<BlockPos> pipes, List<BlockPos> found) {
        LevelChunk fromChunk = MachineIO.loadedChunk(level, from);
        if (fromChunk == null) {
            return;
        }
        BlockState fromState = fromChunk.getBlockState(from);
        boolean fromPipe = fromState.getBlock() instanceof MercuryPipeBlock;
        for (Direction dir : Direction.values()) {
            BlockPos next = from.relative(dir);
            if (fromPipe && !PipeSides.joined(fromState, dir)) {
                continue; // a pipe carries on only through the sides it is joined on
            }
            LevelChunk chunk = MachineIO.loadedChunk(level, next);
            if (chunk == null) {
                continue;
            }
            BlockState nextState = chunk.getBlockState(next);
            if (nextState.getBlock() instanceof MercuryPipeBlock) {
                if (PipeSides.isOff(nextState, dir.getOpposite()) || !seen.add(next)) {
                    continue;
                }
                pipes.add(next);
            } else if (seen.add(next) && chunk.getBlockEntity(next) instanceof MercuryHandler) {
                found.add(next);
            }
        }
    }

    /**
     * Pushes up to {@code max} mB out of {@code source}; returns how much moved. None goes to a machine or tank across a
     * claim's edge whose owner may not take it from there (a stranger's tank on a pipe spliced in outside the claim).
     */
    public static int push(ServerLevel level, BlockPos origin, MercuryHandler source, int max) {
        List<MercuryHandler> targets = new ArrayList<>(handlers(level, origin));
        targets.removeIf(h -> h.mercuryRole() == MercuryHandler.Role.PRODUCER);
        targets.sort(Comparator.comparing(h -> h.mercuryRole() == MercuryHandler.Role.CONSUMER ? 0 : 1));
        int moved = 0;
        for (MercuryHandler target : targets) {
            int offer = source.drainMercury(max - moved, true);
            if (offer <= 0) {
                break;
            }
            int room = target.fillMercury(offer, true);
            if (room <= 0 || !mayMove(level, source, target)) {
                continue; // (asked only of one that would take some)
            }
            int accepted = target.fillMercury(room, false);
            source.drainMercury(accepted, false);
            moved += accepted;
        }
        return moved;
    }

    /**
     * Pulls up to {@code max} mB from storage on the network into {@code sink}; returns how much moved. Not from a tank
     * across a claim's edge, where the sink's owner may not take.
     */
    public static int pull(ServerLevel level, BlockPos origin, MercuryHandler sink, int max) {
        int moved = 0;
        for (MercuryHandler source : handlers(level, origin)) {
            if (source.mercuryRole() != MercuryHandler.Role.STORAGE) {
                continue;
            }
            int room = sink.fillMercury(max - moved, true);
            if (room <= 0) {
                break;
            }
            int offer = source.drainMercury(room, true);
            if (offer <= 0 || !mayMove(level, source, sink)) {
                continue; // (asked only of a tank with some in it)
            }
            int taken = source.drainMercury(offer, false);
            sink.fillMercury(taken, false);
            moved += taken;
        }
        return moved;
    }

    /** Whether claims let mercury go from {@code from} into {@code into} ({@link DrawRights#mayMove}). */
    private static boolean mayMove(ServerLevel level, MercuryHandler from, MercuryHandler into) {
        return !(from instanceof BlockEntity giver) || !(into instanceof BlockEntity taker)
                || DrawRights.mayMove(level, giver.getBlockPos(), giver, taker.getBlockPos(), taker);
    }

    private MercuryNetwork() {}
}
