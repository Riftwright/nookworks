package dev.nookworks.rope;

import java.util.List;

import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModRope;
import dev.nookworks.wood.SupportBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

/**
 * A post that takes a redstone signal switches the lights hung on the lines tied at it (the user, 2026-10-16:
 * "柱で受けるのは天才だな", "フックでかかってるロープも対応できる？"): a lever beside a fence post or on the block under it, a
 * powered block beside it, dust run up to it - and for a Hook or a wall bracket's brace, the wall or ceiling it is fixed
 * to powered too. Filament Bulbs go on and lanterns are lit as the signal comes, and off and out as it goes; between the
 * two a hand still switches each one (sneaking), so a lever and a hand never fight over a light. Each post switches only the lines tied at it, not the
 * lines beyond the post at their far end.
 * <p>
 * A signal at a post out of a claim does not switch a light hung inside one: a signal is no player, so it is asked as
 * nobody - where nobody may break at the light but may at the post, the line runs into land the post's side has no say
 * in, and that light is let be.
 */
public final class RopeSignals {
    private RopeSignals() {}

    /** Whether the post at {@code pos} takes a signal: its own neighbours, or a Hook's or a bracket's wall or ceiling. */
    public static boolean powered(Level level, BlockPos pos) {
        if (level.hasNeighborSignal(pos)) {
            return true;
        }
        BlockPos fixed = fixedTo(level.getBlockState(pos), pos);
        return fixed != null && level.hasNeighborSignal(fixed);
    }

    /** The block a Hook or a wall bracket is fixed to; null for a post that stands by itself. */
    private static @Nullable BlockPos fixedTo(BlockState state, BlockPos pos) {
        if (state.is(ModRope.HOOK.get())) {
            AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
            return face == AttachFace.CEILING ? pos.above() : face == AttachFace.FLOOR ? pos.below()
                    : pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        }
        if (SupportBlock.braced(state)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return pos.relative(facing.getOpposite());
        }
        return null;
    }

    /**
     * Whether the things hung on the lines tied at {@code post} are all in: the entities of every chunk a line from it can
     * reach. A world just opened, or a player come back, loads entities after blocks - a signal changed meanwhile (a
     * daylight sensor) switched only the lights already in, and the rest stayed wrong until the next change (the review
     * of the 10-16 commits).
     */
    public static boolean linesLoaded(ServerLevel level, BlockPos post) {
        int reach = (int) Math.ceil(RopeSpans.MAX_LENGTH) + 2;
        for (int cx = (post.getX() - reach) >> 4; cx <= (post.getX() + reach) >> 4; cx++) {
            for (int cz = (post.getZ() - reach) >> 4; cz <= (post.getZ() + reach) >> 4; cz++) {
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Every light hung on a line tied at {@code post}, switched {@code on} or off. How many changed. */
    public static int switchLines(ServerLevel level, BlockPos post, boolean on) {
        double reach = RopeSpans.MAX_LENGTH + 2;
        List<HungItemEntity> lights = level.getEntitiesOfClass(HungItemEntity.class, new AABB(post).inflate(reach),
                hung -> !hung.isRemoved() && hung.switchable() && (post.equals(hung.spanFrom()) || post.equals(hung.spanTo())));
        if (lights.isEmpty()) {
            return 0;
        }
        FakePlayer nobody = Permission.actor(level, null);
        Boolean postOpen = null;
        int changed = 0;
        for (HungItemEntity light : lights) {
            if (!Permission.mayBreak(level, light.blockPosition(), nobody)) {
                if (postOpen == null) {
                    postOpen = Permission.mayBreak(level, post, nobody);
                }
                if (postOpen) {
                    continue; // a signal from open land, into a claim (or spawn): not the post's to switch
                }
            }
            changed += light.switchLight(level, on) ? 1 : 0;
        }
        return changed;
    }
}
