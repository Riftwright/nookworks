package dev.nookworks.dev.bot.path;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleBiFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * What the walker may do on its way, and what it costs: the planner's side of the player (its tools, its blocks to
 * build with, how far it dares to fall) and of the person it plays with (which of their blocks it must never break).
 * Plain fields, set before a search; the defaults are a careful survival player with nothing in hand.
 */
public final class Abilities {
    /** Whether blocks may be broken on the way, and which: {@link #mayBreak} says yes to a block, {@link #breakTicks}
     *  says how long it takes (infinite: it cannot be). */
    public boolean canBreak;
    public BiPredicate<BlockState, BlockPos> mayBreak = (state, pos) -> false;
    public ToDoubleBiFunction<BlockState, BlockPos> breakTicks = (state, pos) -> Double.POSITIVE_INFINITY;
    /** How many blocks there are to build with on the way (bridges, pillars); none: never planned. */
    public int placeBlocks;
    /** Whether the walker may sprint (food to spare): needed for the longer jumps. */
    public boolean canSprint = true;
    /** Whether jumps over gaps are planned at all. */
    public boolean parkour = true;
    /** A fall up to this many blocks costs nothing but time (three is safe at full health in vanilla). */
    public double safeFall = 3.0;
    /** The most it may fall at all, hurting itself beyond {@link #safeFall} (none when its health is low). */
    public double maxFall = 3.0;
    /** Only search inside this box, when set (a test's arena, a building site). */
    public @Nullable BoundingBox bounds;
    /** The most nodes one search may open before it settles for the best partial path. */
    public int maxNodes = 80_000;
    /** The most cells in a row with the head under water (a breath's worth, with room to spare; more when the way is
     *  to air itself). */
    public int maxUnderwater = Pathfinder.MAX_UNDERWATER;
    /** Places to keep clear of: a hostile mob, a creeper about to go off. */
    public final List<Avoid> avoid = new ArrayList<>();

    /** Somewhere to keep away from: within {@code radius} of {@code center}, each step there costs {@code penalty}
     *  more. */
    public record Avoid(Vec3 center, double radius, double penalty) {}

    public static Abilities walkOnly() {
        Abilities ab = new Abilities();
        ab.parkour = false;
        return ab;
    }
}
