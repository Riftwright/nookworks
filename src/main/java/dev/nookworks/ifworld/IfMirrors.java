package dev.nookworks.ifworld;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Where the two If Mirrors stand ({@link IfWorld} keeps them): the home one, anywhere but the If, and the If's own. Each
 * is kept by its lower half; one recorded where its chunk is loaded and no mirror stands any more is forgotten.
 */
public final class IfMirrors {
    private IfMirrors() {}

    /** Whether an If Mirror's lower half stands at {@code pos}. */
    public static boolean standsAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof IfMirrorBlock && state.getValue(IfMirrorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * The mirror already standing on {@code level}'s side (in the If, the If's; elsewhere, the home one, in whatever
     * dimension), other than at {@code pos}: what refuses a second one. A record whose chunk is loaded and holds no mirror
     * is forgotten; one in an unloaded chunk is taken at its word.
     */
    public static @Nullable GlobalPos otherThan(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        IfWorld world = IfWorld.get(server);
        GlobalPos there = world.mirrorFor(level.dimension());
        if (there == null || there.dimension().equals(level.dimension()) && there.pos().equals(pos)) {
            return null;
        }
        ServerLevel thereLevel = server.getLevel(there.dimension());
        if (thereLevel == null || thereLevel.isLoaded(there.pos()) && !standsAt(thereLevel, there.pos())) {
            world.forget(there.dimension(), there.pos());
            return null;
        }
        return there;
    }

    /** A mirror set down at {@code pos}: recorded, and those standing told (the If's compass follows its mirror). */
    public static void placed(ServerLevel level, BlockPos pos) {
        IfWorld.get(level.getServer()).record(level.dimension(), pos);
        IfMirrorBlockEntity.refreshAll(level.getServer());
        if (level.dimension().equals(IfWorld.KEY)) {
            IfCompass.pointAll(level.getServer());
        }
    }

    /** The mirror at {@code pos} gone. */
    public static void removed(ServerLevel level, BlockPos pos) {
        IfWorld.get(level.getServer()).forget(level.dimension(), pos);
        IfMirrorBlockEntity.refreshAll(level.getServer());
        if (level.dimension().equals(IfWorld.KEY)) {
            IfCompass.pointAll(level.getServer());
        }
    }

    public static boolean isHome(ServerLevel level, BlockPos pos) {
        GlobalPos home = IfWorld.get(level.getServer()).homeMirror();
        return home != null && home.equals(GlobalPos.of(level.dimension(), pos));
    }

    /** The mirror recorded on {@code level}'s side if it still stands (its chunk loaded to look); else forgotten, null. */
    public static @Nullable BlockPos standing(ServerLevel level) {
        GlobalPos there = IfWorld.get(level.getServer()).mirrorFor(level.dimension());
        if (there == null || !there.dimension().equals(level.dimension())) {
            return null;
        }
        level.getChunk(there.pos());
        if (standsAt(level, there.pos())) {
            return there.pos();
        }
        IfWorld.get(level.getServer()).forget(level.dimension(), there.pos());
        return null;
    }
}
