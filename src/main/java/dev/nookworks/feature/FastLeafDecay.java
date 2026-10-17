package dev.nookworks.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * After a tree is felled (timber, laser), its now-unsupported leaves decay within a second or two instead of
 * over minutes. Drops are the normal decay drops (saplings, sticks, apples). Leaves still held by another
 * tree's logs, and player-placed leaves, are left alone: the check is the same "log within 6 leaves" rule
 * vanilla uses, computed directly so it does not depend on distance updates having finished.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class FastLeafDecay {
    private static final int DELAY = 4;
    private static final int PER_TICK = 12;
    private static final int MAX_LEAVES = 2048;

    /** Keyed by dimension, not by the level itself: holding the level would keep an unloaded world alive. */
    private static final Map<ResourceKey<Level>, List<Job>> JOBS =
            new HashMap<>();

    private static final class Job {
        final ArrayDeque<BlockPos> queue;
        /** Who felled the tree: leaves they may not break (a claim, spawn protection) are left to vanilla's slow decay. */
        final Player actor;
        int delay = DELAY;

        Job(List<BlockPos> leaves, Player actor) {
            this.queue = new ArrayDeque<>(leaves);
            this.actor = actor;
        }
    }

    /** Queues the leaves connected to the logs {@code actor} felled. */
    public static void schedule(ServerLevel level, Collection<BlockPos> logs, Player actor) {
        if (!NookworksConfig.FAST_LEAF_DECAY.getAsBoolean()) {
            return;
        }
        List<BlockPos> leaves = connectedLeaves(level, logs);
        if (!leaves.isEmpty()) {
            Collections.shuffle(leaves); // falls away evenly rather than layer by layer
            JOBS.computeIfAbsent(level.dimension(), l -> new ArrayList<>()).add(new Job(leaves, actor));
        }
    }

    static boolean isNaturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static List<BlockPos> connectedLeaves(ServerLevel level, Collection<BlockPos> logs) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos log : logs) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
                BlockPos p = near.immutable();
                if (seen.add(p) && level.isLoaded(p) && isNaturalLeaves(level.getBlockState(p))) {
                    queue.add(p);
                    found.add(p);
                }
            }
        }
        while (!queue.isEmpty() && found.size() < MAX_LEAVES) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (seen.add(next) && level.isLoaded(next) && isNaturalLeaves(level.getBlockState(next))) {
                    queue.add(next);
                    found.add(next);
                }
            }
        }
        return found;
    }

    /** Vanilla's rule: a leaf survives if a log (or other decay-preventing block) is at most 6 leaf steps away. */
    static boolean anchored(ServerLevel level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ArrayDeque<Integer> depths = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        depths.add(1);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int depth = depths.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(BlockTags.PREVENTS_NEARBY_LEAF_DECAY)) {
                    return true;
                }
                if (depth < LeavesBlock.DECAY_DISTANCE - 1 && state.hasProperty(LeavesBlock.DISTANCE)) {
                    queue.add(next);
                    depths.add(depth + 1);
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Job> jobs = JOBS.get(level.dimension());
        if (jobs == null) {
            return;
        }
        jobs.removeIf(job -> step(level, job));
        if (jobs.isEmpty()) {
            JOBS.remove(level.dimension());
        }
    }

    /**
     * Jobs are positions in one world. In single player the next world opened runs on the same JVM, and its
     * dimensions have the same keys, so anything still queued would start stripping leaves in the wrong world.
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        JOBS.clear();
    }

    /** Decays a few leaves; true when the job is finished. */
    private static boolean step(ServerLevel level, Job job) {
        if (job.delay-- > 0) {
            return false;
        }
        if (job.actor.isRemoved()) {
            // gone - logged out, or died and came back as a new player - so not asked about for them: the leaves are
            // left to fall as vanilla's do
            return true;
        }
        for (int i = 0; i < PER_TICK && !job.queue.isEmpty(); i++) {
            BlockPos pos = job.queue.poll();
            if (!level.isLoaded(pos)) {
                continue; // checked before the block is read: reading it would load the chunk again
            }
            BlockState state = level.getBlockState(pos);
            if (isNaturalLeaves(state) && !anchored(level, pos) && Permission.mayBreak(level, pos, job.actor)) {
                Block.dropResources(state, level, pos);
                level.removeBlock(pos, false);
            }
        }
        return job.queue.isEmpty();
    }

    private FastLeafDecay() {}
}
