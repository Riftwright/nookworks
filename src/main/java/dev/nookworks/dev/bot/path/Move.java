package dev.nookworks.dev.bot.path;

/**
 * How one step of a path is taken. A step may also break blocks first (its {@link Step#breaks()}: a tunnel, a head
 * cleared) and set one down (its {@link Step#place()}: a bridge's floor, a pillar's next block).
 */
public enum Move {
    /** Where the path starts. */
    START,
    /** On the level, up a slab or a stair, or into an open door: no jump. */
    WALK,
    /** One block up, with a jump (from the ground, out of water, or off the top of a ladder). */
    ASCEND,
    /** Off an edge, down onto the ground or into water below. */
    DESCEND,
    /** Through water: across, up or down. */
    SWIM,
    /** Up or down a ladder, vines, scaffolding: anything climbable. */
    CLIMB,
    /** Straight up, a block set down under the feet at the top of a jump. */
    PILLAR,
    /** A running jump over a gap, one to three blocks wide. */
    PARKOUR
}
