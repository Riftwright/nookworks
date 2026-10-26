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

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * over minutes - or all at once as it comes down, with {@code instantLeafDecay} on (a CurseForge comment,
 * 2026-10-26). Drops are the normal decay drops (saplings, sticks, apples); felled by an axe with Silk Touch, the
 * leaves themselves, as that axe would break them (the same comment: shearing leaves is a bother), for a point of the
 * axe's durability every {@link #SILK_LEAVES_PER_POINT} leaves - a quarter of what cutting them by hand costs, more than
 * the nothing a felling without Silk Touch costs (the user, 2026-10-26) - unless the axe's leaf gathering is switched
 * off ({@link LeafGathering}). Leaves still held by another
 * tree's logs, and player-placed leaves, are left alone: the check is the same "log within 6 leaves" rule
 * vanilla uses, computed directly so it does not depend on distance updates having finished.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class FastLeafDecay {
    private static final int DELAY = 4;
    private static final int PER_TICK = 12;
    /** Instant decay's share a tick: a crown in one to a few ticks, not thousands of breaks and drops in one. */
    private static final int INSTANT_PER_TICK = 256;
    private static final int MAX_LEAVES = 2048;
    /** Silk Touch leaves a point of the axe's durability buys: the first leaf costs one, and each fourth after it. */
    private static final int SILK_LEAVES_PER_POINT = 4;

    /** Keyed by dimension, not by the level itself: holding the level would keep an unloaded world alive. */
    private static final Map<ResourceKey<Level>, List<Job>> JOBS =
            new HashMap<>();

    private static final class Job {
        final ArrayDeque<BlockPos> queue;
        /** Who felled the tree: leaves they may not break (a claim, spawn protection) are left to vanilla's slow decay. */
        final Player actor;
        /**
         * The Silk Touch axe that felled the tree, itself (not a copy: it is worn), the leaves dropped as it would break
         * them while the actor still holds it; null otherwise.
         */
        final @Nullable ItemStack silkTool;
        final boolean instant;
        int delay;
        /** Leaves dropped as leaves so far: every {@link #SILK_LEAVES_PER_POINT}th wears the axe a point. */
        int silkLeaves;

        Job(List<BlockPos> leaves, Player actor, @Nullable ItemStack silkTool, boolean instant) {
            this.queue = new ArrayDeque<>(leaves);
            this.actor = actor;
            this.silkTool = silkTool;
            this.instant = instant;
            // instant still waits a tick or two: the logs are broken after this is scheduled, and still hold the leaves now
            this.delay = instant ? 1 : DELAY;
        }
    }

    /** Queues the leaves connected to the logs {@code actor} felled with {@code tool} (empty for a Laser). */
    public static void schedule(ServerLevel level, Collection<BlockPos> logs, Player actor, ItemStack tool) {
        if (!NookworksConfig.FAST_LEAF_DECAY.getAsBoolean()) {
            return;
        }
        List<BlockPos> leaves = connectedLeaves(level, logs);
        if (!leaves.isEmpty()) {
            Collections.shuffle(leaves); // falls away evenly rather than layer by layer
            // its leaf gathering switched off, a Silk Touch axe leaves them to decay as any other does (LeafGathering)
            ItemStack silkTool = !tool.isEmpty() && LeafGathering.hasSilkTouch(tool) && LeafGathering.gathers(tool) ? tool : null;
            JOBS.computeIfAbsent(level.dimension(), l -> new ArrayList<>())
                    .add(new Job(leaves, actor, silkTool, NookworksConfig.INSTANT_LEAF_DECAY.getAsBoolean()));
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
        for (int i = 0; i < (job.instant ? INSTANT_PER_TICK : PER_TICK) && !job.queue.isEmpty(); i++) {
            BlockPos pos = job.queue.poll();
            if (!level.isLoaded(pos)) {
                continue; // checked before the block is read: reading it would load the chunk again
            }
            BlockState state = level.getBlockState(pos);
            if (isNaturalLeaves(state) && !anchored(level, pos) && Permission.mayBreak(level, pos, job.actor)) {
                if (job.silkTool != null && silkHeld(level, job)) {
                    Block.dropResources(state, level, pos, null, job.actor, job.silkTool);
                    if (job.silkLeaves++ % SILK_LEAVES_PER_POINT == 0) {
                        job.silkTool.hurtAndBreak(1, job.actor, EquipmentSlot.MAINHAND); // Unbreaking spares some, as by hand
                    }
                } else {
                    Block.dropResources(state, level, pos);
                }
                level.removeBlock(pos, false);
            }
        }
        return job.queue.isEmpty();
    }

    /**
     * Whether the Silk Touch axe still takes the leaves as leaves: held in the actor's main hand, in this world (switched
     * away, broken, or carried through a portal, the rest decay as without it) and, with protectTools on, not on its last point - the leaves left then decay as
     * usual rather than the axe breaking on them.
     */
    private static boolean silkHeld(ServerLevel level, Job job) {
        ItemStack tool = job.silkTool;
        if (tool == null || tool.isEmpty() || job.actor.level() != level || job.actor.getMainHandItem() != tool) {
            return false;
        }
        return !(NookworksConfig.PROTECT_TOOLS.getAsBoolean() && tool.isDamageableItem()
                && tool.getMaxDamage() - tool.getDamageValue() <= 1);
    }

    private FastLeafDecay() {}
}
