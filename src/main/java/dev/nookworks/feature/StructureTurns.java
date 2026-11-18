package dev.nookworks.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Turning what a thing of ours keeps of other places - the posts a knot's ropes run to, the span a thing hangs on, the
 * wheel at a belt's far end - as a structure template turns and mirrors the build it is in (a structure block, a
 * datapack's or another mod's template; the zombie world's town). A template moves and turns the blocks and the
 * entities' own places, and tells an entity (Entity#rotate, then Entity#mirror) and a block entity
 * (IBlockEntityExtension#applyStructureRotation) how it was turned, but leaves alone whatever places they keep: kept as
 * offsets from the thing's own place, they are turned here - mirrored first, then turned, as the template does it.
 */
public final class StructureTurns {
    private StructureTurns() {}

    /** {@code offset} mirrored as {@code mirror} mirrors a build: left-right reverses z, front-back x. */
    public static BlockPos mirror(BlockPos offset, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> new BlockPos(offset.getX(), offset.getY(), -offset.getZ());
            case FRONT_BACK -> new BlockPos(-offset.getX(), offset.getY(), offset.getZ());
            default -> offset;
        };
    }

    /** {@code offset} turned about the vertical as {@code rotation} turns a build. */
    public static BlockPos rotate(BlockPos offset, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case CLOCKWISE_180 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            default -> offset;
        };
    }

    public static Vec3 mirror(Vec3 offset, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> new Vec3(offset.x, offset.y, -offset.z);
            case FRONT_BACK -> new Vec3(-offset.x, offset.y, offset.z);
            default -> offset;
        };
    }

    public static Vec3 rotate(Vec3 offset, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new Vec3(-offset.z, offset.y, offset.x);
            case CLOCKWISE_180 -> new Vec3(-offset.x, offset.y, -offset.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(offset.z, offset.y, -offset.x);
            default -> offset;
        };
    }

    /**
     * The mirror that, done to what {@code turned} has turned already, gives what {@code mirror} done first and then
     * {@code turned} would: a template mirrors a build before it turns it, but tells an entity the turn first. A quarter
     * turn swaps the two mirrors (left-right across the first axis is front-back across the turned one); a half turn
     * keeps them.
     */
    public static Mirror afterTurn(Mirror mirror, Rotation turned) {
        if (turned != Rotation.CLOCKWISE_90 && turned != Rotation.COUNTERCLOCKWISE_90) {
            return mirror;
        }
        return switch (mirror) {
            case LEFT_RIGHT -> Mirror.FRONT_BACK;
            case FRONT_BACK -> Mirror.LEFT_RIGHT;
            default -> mirror;
        };
    }

    /**
     * Where an entity stuck to a block (a label, a poster) set down by a structure belongs: the middle of the block whose
     * box ({@code boxAtZero}: its box were it stuck to the block at the origin, facing as it does now, turned and
     * mirrored already) has its middle at {@code middle} - where its middle was, moved, turned and mirrored with the
     * build. Not the block its middle is in, which vanilla's setPos gives a hanging entity: for a label that is the block
     * in front of the face it is stuck to, and for a poster an even number of blocks across or high, the next one along.
     */
    public static Vec3 attachedAt(Vec3 middle, AABB boxAtZero) {
        return middle.subtract(boxAtZero.getCenter()).add(0.5, 0.5, 0.5);
    }

    /** A yaw (degrees about the vertical) mirrored as {@code mirror} mirrors a build, as vanilla's Entity#mirror does. */
    public static float mirror(float yaw, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> 180.0F - yaw;
            case FRONT_BACK -> -yaw;
            default -> yaw;
        };
    }

    /** A yaw turned as {@code rotation} turns a build, as vanilla's Entity#rotate does. */
    public static float rotate(float yaw, Rotation rotation) {
        return yaw + switch (rotation) {
            case CLOCKWISE_90 -> 90.0F;
            case CLOCKWISE_180 -> 180.0F;
            case COUNTERCLOCKWISE_90 -> 270.0F;
            default -> 0.0F;
        };
    }
}
