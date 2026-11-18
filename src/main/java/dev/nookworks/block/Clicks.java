package dev.nookworks.block;

import net.minecraft.world.InteractionResult;

/** What a block answers to a click it turns down. */
public final class Clicks {
    /**
     * A block's {@code useItemOn} (or {@code useWithoutItem}) saying no - full, not yours, the land refused - spends the
     * click. Not {@link InteractionResult#FAIL}: from a FAIL the server goes on to use the held item on the clicked face
     * as on any block, so a receiver refused its pairing was set down against the sender all the same, and an Iron Pot
     * onto a grill still holding food (r7 multiplayer test). CONSUME stops both sides and swings no arm: nothing was done.
     */
    public static final InteractionResult REFUSED = InteractionResult.CONSUME;

    private Clicks() {}
}
