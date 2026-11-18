package dev.nookworks.dev.bot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.nookworks.NookworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * DEVELOPMENT ONLY: the agent bot's own way down - a stair three wide and three high dug with a Nookworks hammer, and
 * level tunnels three by three from its foot - worked out here, out of the client package, so the GameTests check its
 * shape and its care headlessly (BotDigTests). The user's idea (2026-10-22): every trip down a cave that day ended in a
 * death on the way (zombies, skeletons, a flooded passage, sand), and none of its own stairs would have had them.
 * <p>
 * A step: from where the feet are, the 3x3 of the next column is opened - a stair's a block lower than the feet (so the
 * floor goes down one), a tunnel's level with them - by one hammer blow at its middle, on the face toward the player
 * (the hammer mines the 3x3 in the plane it is struck in). Before that, the cells to open and every cell touching them
 * are looked at: lava or water there, or a build, and the step is not dug.
 */
public final class Stairs {
    private Stairs() {}

    public enum Kind { STAIR, TUNNEL }

    /**
     * One step of a dig: {@code open} the nine cells to clear (top row first: what falls comes down into rows still to be
     * dug), {@code aim} the block the hammer strikes, on {@code face}; {@code floor} the block the feet will stand on, and
     * {@code to} where the feet go.
     */
    public record Step(Kind kind, BlockPos from, Direction dir, List<BlockPos> open, BlockPos aim, Direction face, BlockPos floor, BlockPos to) {}

    public static Step next(Kind kind, BlockPos feet, Direction dir) {
        BlockPos col = feet.relative(dir);
        int low = kind == Kind.STAIR ? feet.getY() - 1 : feet.getY();
        Direction side = dir.getClockWise();
        List<BlockPos> open = new ArrayList<>(9);
        for (int y = low + 2; y >= low; y--) {
            for (int s : new int[] {0, -1, 1}) {
                open.add(new BlockPos(col.getX() + side.getStepX() * s, y, col.getZ() + side.getStepZ() * s));
            }
        }
        BlockPos to = new BlockPos(col.getX(), low, col.getZ());
        return new Step(kind, feet, dir, open, to.above(), dir.getOpposite(), to.below(), to);
    }

    /** Where to strike: the middle of the aimed block's face toward the player. From the feet's cell the eyes see that
     *  point straight on (its middle lies behind the block over it, for a stair: aimed there, the blow fell on the wrong
     *  face and the 3x3 lay flat). */
    public static Vec3 aimPoint(Step s) {
        Direction f = s.face();
        return Vec3.atCenterOf(s.aim()).add(f.getStepX() * 0.5, f.getStepY() * 0.5, f.getStepZ() * 0.5);
    }

    /** Every cell touching the cells to open, but those: where lava or water would come in from. */
    public static Set<BlockPos> shell(Step s) {
        Set<BlockPos> open = Set.copyOf(s.open());
        Set<BlockPos> out = new LinkedHashSet<>();
        for (BlockPos p : s.open()) {
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!open.contains(n)) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    /** What keeps a step from being dug: lava or water in it or beside it, or a block it may not dig. */
    public record Hazard(String what, BlockPos pos) {
        public boolean fluid() {
            return this.what.equals("lava") || this.what.equals("water");
        }
    }

    /**
     * Why the step must not be dug, or null if it may be. {@code mayDig}: whether a block in the way may be dug (natural
     * rock and ground, ores - never a build); a block the feet pass through anyway (air, a plant) always may.
     */
    public static @Nullable Hazard hazard(BlockGetter level, Step s, BiPredicate<BlockState, BlockPos> mayDig) {
        for (BlockPos p : s.open()) {
            Hazard h = fluid(level, p);
            if (h != null) {
                return h;
            }
            BlockState state = level.getBlockState(p);
            if (!passable(level, p, state) && !mayDig.test(state, p)) {
                return new Hazard(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), p);
            }
        }
        for (BlockPos p : shell(s)) {
            Hazard h = fluid(level, p);
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    private static @Nullable Hazard fluid(BlockGetter level, BlockPos p) {
        FluidState f = level.getFluidState(p);
        if (f.isEmpty()) {
            return null;
        }
        return new Hazard(f.is(net.minecraft.tags.FluidTags.LAVA) ? "lava" : "water", p);
    }

    /** A cell the body goes through as it is: air, a plant, a snow layer - nothing to dig. */
    public static boolean passable(BlockGetter level, BlockPos p, BlockState state) {
        return state.isAir() || state.getCollisionShape(level, p).isEmpty() && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock);
    }

    /** Whether the cells to open are clear. */
    public static boolean cleared(BlockGetter level, Step s) {
        for (BlockPos p : s.open()) {
            if (!passable(level, p, level.getBlockState(p))) {
                return false;
            }
        }
        return true;
    }

    /** Whether a block must be set down under where the feet go: nothing there to stand on (a cave under the step), or
     *  something that hurts. */
    public static boolean needsFloor(BlockGetter level, Step s) {
        BlockState under = level.getBlockState(s.floor());
        return !under.isFaceSturdy(level, s.floor(), Direction.UP) || under.is(Blocks.MAGMA_BLOCK) || !under.getFluidState().isEmpty();
    }

    /** How many open cells lie under the floor, to the first solid one (up to {@code max}): a drop under a missing floor. */
    public static int dropUnder(BlockGetter level, Step s, int max) {
        int n = 0;
        BlockPos.MutableBlockPos p = s.floor().mutable();
        while (n < max && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
            n++;
            p.move(Direction.DOWN);
        }
        return n;
    }

    /**
     * Whether to hold sneak for the hammer's 3x3, by the server's setting ({@code hammer.activation}): by default
     * sneaking makes it mine one block, so the hammer is swung standing - and only so.
     */
    public static boolean sneakForHammer(NookworksConfig.Activation activation) {
        return !activation.allows(false) && activation.allows(true);
    }

    /** Whether to hold sneak for a hammer to mine one block only (a block on the walker's way, an ore): by default,
     *  yes. (Mining in every case, the setting "always", it cannot.) */
    public static boolean sneakForOne(NookworksConfig.Activation activation) {
        return !activation.allows(true);
    }

    /** Whether the hammer would take the 3x3 round this block at all: it strikes only what a pickaxe mines (dirt and
     *  sand it breaks alone, without Earthmover). */
    public static boolean hammerTakes(BlockState state) {
        return !state.isAir() && state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !state.hasBlockEntity();
    }

    /** Rock and ground natural to the underground that a dig may go through, beyond what the walker digs on its way:
     *  the deep's tuff, calcite and dripstone. */
    public static boolean deepRock(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.SMOOTH_BASALT) || state.is(Tags.Blocks.ORES)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MOSS_BLOCK);
    }

    /** Ores showing in the walls, floor and roof the step opened: the cells touching it, nearest the new feet first. */
    public static List<BlockPos> ores(BlockGetter level, Step s) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : shell(s)) {
            if (level.getBlockState(p).is(Tags.Blocks.ORES)) {
                out.add(p);
            }
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(s.to())));
        return out;
    }

    /** What vein mining takes with an ore (as Nookworks' vein mining sees it): the same block, or one sharing a
     *  {@code c:ores/...} tag - iron in stone and iron in deepslate are one vein. */
    public static Predicate<BlockState> veinOf(BlockState ore) {
        List<TagKey<Block>> family = ore.getBlock().builtInRegistryHolder().tags()
                .filter(tag -> tag.location().getNamespace().equals("c") && tag.location().getPath().startsWith("ores/")).toList();
        Block block = ore.getBlock();
        return state -> state.is(block) || family.stream().anyMatch(state::is);
    }

    /** The vein an ore is part of (faces and edges, up to {@code limit}). */
    public static List<BlockPos> vein(BlockGetter level, BlockPos start, int limit) {
        Predicate<BlockState> same = veinOf(level.getBlockState(start));
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
        open.add(start);
        seen.add(start);
        while (!open.isEmpty() && out.size() < limit) {
            BlockPos p = open.poll();
            if (!same.test(level.getBlockState(p))) {
                continue;
            }
            out.add(p);
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (seen.add(n.immutable())) {
                    open.add(n.immutable());
                }
            }
        }
        return out;
    }

    /** Whether a vein may be mined out from the tunnel: no lava or water touching any of it (the hole it leaves would let
     *  them in). */
    public static boolean veinSafe(BlockGetter level, List<BlockPos> vein) {
        for (BlockPos p : vein) {
            for (Direction d : Direction.values()) {
                if (!level.getFluidState(p.relative(d)).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A direction from a command word: north, south, east, west (or n s e w); null if none. */
    public static @Nullable Direction direction(String word) {
        return switch (word.toLowerCase(java.util.Locale.ROOT)) {
            case "north", "n" -> Direction.NORTH;
            case "south", "s" -> Direction.SOUTH;
            case "east", "e" -> Direction.EAST;
            case "west", "w" -> Direction.WEST;
            default -> null;
        };
    }
}
