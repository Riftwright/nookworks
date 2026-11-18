package dev.nookworks.port.core;

/**
 * The block-update flags 26.3 has and 1.21.1 lacks ({@code Block.UPDATE_SKIP_*}), with the same bit values, and the
 * flags of the {@code Level.setBlock} call now running on this thread.
 *
 * <p>1.21.1's {@code Level.setBlock} takes any int and passes unknown bits on unread, so the constants can be used
 * as they are; what 26.3 does with them has to be done by us. {@link BlockRemoval} skips a block entity's
 * {@code preRemoveSideEffects} under {@link #UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS} (carrying a block away must not
 * drop its contents too). For that it needs the flags of the running {@code setBlock}, which 1.21.1 does not hand to
 * {@code onRemove}: a small mixin on {@code Level.setBlock(BlockPos, BlockState, int, int)} calls {@link #push} at its
 * head and {@link #pop} at each return (see GUIDE_core.md, "Block removal"). Without the mixin {@link #current} is
 * {@link #UNKNOWN} and removal behaves as an ordinary {@code setBlock} with {@code UPDATE_ALL}.
 */
public final class BlockFlags {
    private BlockFlags() {}

    /** 26.3: a wire next to the block is not reshaped. */
    public static final int UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE = 128;
    /** 26.3: the old block entity's removal side effects (dropping its contents) are skipped. */
    public static final int UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS = 256;
    /** 26.3: the new block's {@code onPlace} is not called. */
    public static final int UPDATE_SKIP_ON_PLACE = 512;
    /** 26.3: {@code UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | UPDATE_SKIP_ON_PLACE}. */
    public static final int UPDATE_SKIP_ALL_SIDEEFFECTS = 816;
    /** 26.3's value of {@code Block.UPDATE_NONE} (1.21.1's is 4): {@code UPDATE_INVISIBLE | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS}. */
    public static final int UPDATE_NONE = 260;

    /** No {@code setBlock} is known to be running (or the mixin that reports it is not there). */
    public static final int UNKNOWN = -1;

    private static final ThreadLocal<int[]> STACK = ThreadLocal.withInitial(() -> new int[16]);
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /** Called as a {@code Level.setBlock} starts. Nested calls (a removal setting another block) stack. */
    public static void push(int flags) {
        int[] depth = DEPTH.get();
        int[] stack = STACK.get();
        if (depth[0] == stack.length) {
            int[] grown = java.util.Arrays.copyOf(stack, stack.length * 2);
            STACK.set(grown);
            stack = grown;
        }
        stack[depth[0]++] = flags;
    }

    /** Called as that {@code Level.setBlock} returns. */
    public static void pop() {
        int[] depth = DEPTH.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
    }

    /** The flags of the innermost running {@code setBlock}, or {@link #UNKNOWN}. */
    public static int current() {
        int depth = DEPTH.get()[0];
        return depth == 0 ? UNKNOWN : STACK.get()[depth - 1];
    }

    /** Whether the running {@code setBlock} has {@code flag} set; false when nothing is known. */
    public static boolean has(int flag) {
        int flags = current();
        return flags != UNKNOWN && (flags & flag) != 0;
    }
}
