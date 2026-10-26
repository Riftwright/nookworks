package dev.nookworks.feature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.compat.FeatureGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Timber (fell a whole tree with an axe) and vein mining (mine a whole ore vein with a pickaxe).
 * Extra blocks are broken through {@link ExtraBreaks}.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class ChainBreaking {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BreakBlockEvent event) {
        if (ExtraBreaks.inProgress() || Permission.asking() || event.isCanceled()
                || !(event.getPlayer() instanceof ServerPlayer player)
                || event.getPlayer() instanceof FakePlayer
                || !(event.getLevel() instanceof ServerLevel level)
                || player.preventsBlockDrops()) {
            return;
        }
        BlockPos origin = event.getPos();
        BlockState state = event.getState();
        ItemStack tool = player.getMainHandItem();
        boolean sneaking = player.isShiftKeyDown();

        List<BlockPos> targets;
        if (state.is(BlockTags.LOGS) && tool.is(ItemTags.AXES)
                && FeatureGate.timber()
                && NookworksConfig.TIMBER_ACTIVATION.get().allows(sneaking)) {
            if (PlacedLogs.isBuiltLog(level, origin, state.getBlock())) {
                return; // a log someone put there is building, not tree
            }
            targets = collect(level, origin, state.getBlock(), NookworksConfig.TIMBER_MAX_BLOCKS.getAsInt(), true);
            if (!looksLikeTree(level, origin, targets)) {
                return;
            }
            List<BlockPos> tree = new ArrayList<>(targets);
            tree.add(origin);
            FastLeafDecay.schedule(level, tree, player, tool);
            Rootbound.felling(level, player, tool, origin, targets); // read now, while its crown still tells the kind
        } else if (state.is(Tags.Blocks.ORES) && tool.is(ItemTags.PICKAXES)
                && state.canHarvestBlock(level, origin, player)
                && FeatureGate.vein()
                && NookworksConfig.VEIN_ACTIVATION.get().allows(sneaking)) {
            targets = collect(level, origin, veinOf(state), NookworksConfig.VEIN_MAX_BLOCKS.getAsInt());
            targets.removeIf(p -> !level.getBlockState(p).canHarvestBlock(level, p, player)); // a harder ore of the family
        } else {
            return;
        }

        ExtraBreaks.breakAll(player, tool, targets);
    }

    /**
     * What counts as the same vein as {@code ore}: the same block, or any block sharing one of its {@code c:ores/...}
     * tags - iron ore and deepslate iron ore are one vein where the stone gives way to deepslate. A modded ore
     * without those tags is its own vein, as before.
     */
    static Predicate<BlockState> veinOf(BlockState ore) {
        List<TagKey<Block>> family = ore.getBlock().builtInRegistryHolder().tags()
                .filter(tag -> tag.location().getNamespace().equals("c") && tag.location().getPath().startsWith("ores/"))
                .toList();
        Block block = ore.getBlock();
        return state -> state.is(block) || family.stream().anyMatch(state::is);
    }

    /** As {@link #collect(ServerLevel, BlockPos, Block, int, boolean)}, for anything {@code sameKind} takes, all round. */
    public static List<BlockPos> collect(ServerLevel level, BlockPos origin, Predicate<BlockState> sameKind, int limit) {
        return search(level, origin, pos -> sameKind.test(level.getBlockState(pos)), limit);
    }

    /**
     * Breadth-first search over all 26 neighbours for blocks of the same type, nearest first.
     * The origin itself is excluded (vanilla breaks it). Timber ignores anything below the origin, and never
     * takes or walks through a log that was built rather than grown ({@link PlacedLogs}), so a cabin or a
     * village house against a tree stays up.
     */
    public static List<BlockPos> collect(ServerLevel level, BlockPos origin, Block block, int limit, boolean upwardsOnly) {
        return search(level, origin, pos -> !(upwardsOnly && pos.getY() < origin.getY()) && level.getBlockState(pos).is(block)
                && !(upwardsOnly && PlacedLogs.isBuiltLog(level, pos, block)), limit);
    }

    /** Out from {@code origin} (itself left out) over all 26 neighbours, nearest first: up to {@code limit} loaded places {@code takes} takes. */
    private static List<BlockPos> search(ServerLevel level, BlockPos origin, Predicate<BlockPos> takes, int limit) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < limit) {
            BlockPos current = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                if (found.size() >= limit) {
                    break;
                }
                BlockPos immutable = next.immutable();
                if (seen.add(immutable) && level.isLoaded(immutable) && takes.test(immutable)) {
                    found.add(immutable);
                    queue.add(immutable);
                }
            }
        }
        return found;
    }

    /**
     * A real tree has a natural crown touching its logs; log cabins do not. A crown is what is in
     * {@link #TREE_CROWNS}: leaves nobody placed (a placed leaf is persistent), and a huge fungus's wart blocks and
     * shroomlight, which have no such mark - so a crimson or warped stem fells as a tree. A wall of stems someone built
     * beside wart blocks they set there is kept as a log wall is, by {@link PlacedLogs}: a placed stem is never taken,
     * nor starts a felling (the crown was the guard only before logs were known as placed).
     */
    public static boolean looksLikeTree(ServerLevel level, BlockPos origin, List<BlockPos> logs) {
        List<BlockPos> all = new ArrayList<>(logs);
        all.add(origin);
        for (BlockPos log : all) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
                if (level.isLoaded(near) && isNaturalCrown(level.getBlockState(near))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * What crowns a tree: leaves, and the wart blocks and shroomlight of the Nether's huge fungi. A pack adds its own
     * (another mod's leaves outside #minecraft:leaves, a fungus of its own) here.
     */
    public static final TagKey<Block> TREE_CROWNS = TagKey.create(Registries.BLOCK, Nookworks.id("tree_crowns"));

    /** A crown block nobody placed: one of {@link #TREE_CROWNS}, not marked persistent as placed leaves are. */
    static boolean isNaturalCrown(BlockState state) {
        return state.is(TREE_CROWNS) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
    }

    private ChainBreaking() {}
}
