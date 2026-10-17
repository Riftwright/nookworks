package dev.nookworks.rope;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a line strung between posts is made of: Thin Rope or Thin Chain. Both are tied, carry what hangs and come down
 * alike ({@link RopeSpans}); a chain is drawn as iron links, sounds of iron, and rattles and sways a little when
 * something goes through it ({@link ChainSway}). A knot keeps which of its spans are chain; a span saved before there
 * were chains is rope.
 */
public enum SpanMaterial {
    ROPE("rope", 1),
    CHAIN("chain", 2);

    /** The messages a chain says in its own words ("Something is in the chain's way"); the rest are the rope's. */
    private static final Set<String> OWN_WORDS = Set.of("too_far", "blocked", "held_by", "needs", "no_room", "too_steep");
    private static final String ROPE_MESSAGE = "message.nookworks.rope.";

    private final String word;
    /** Its bit in a knot's note of what is tied there. */
    final int bit;

    SpanMaterial(String word, int bit) {
        this.word = word;
        this.bit = bit;
    }

    /** What a span of it is paid out from, and drops as: a Thin Rope or a Thin Chain a block. */
    public Item item() {
        return this == CHAIN ? ModRope.THIN_CHAIN.get() : ModItems.THIN_ROPE.get();
    }

    /** The line {@code stack} strings, or null when it strings none. */
    public static @Nullable SpanMaterial of(ItemStack stack) {
        if (stack.is(ModRope.THIN_CHAIN.get())) {
            return CHAIN;
        }
        return stack.is(ModItems.THIN_ROPE.get()) ? ROPE : null;
    }

    /** Tied to a post: a lead's knot, or a chain set down. */
    public SoundEvent tied() {
        return this == CHAIN ? ModSounds.THIN_CHAIN_TIE.get() : SoundEvents.LEAD_TIED;
    }

    /** Let go of, or cut. */
    public SoundEvent untied() {
        return this == CHAIN ? ModSounds.THIN_CHAIN_UNTIE.get() : SoundEvents.LEAD_UNTIED;
    }

    /** Come down as a post it was tied to goes. */
    public SoundEvent snapped() {
        return this == CHAIN ? ModSounds.THIN_CHAIN_BREAK.get() : SoundEvents.LEAD_BREAK;
    }

    /**
     * The message {@code ropeKey} ({@code message.nookworks.rope.*}) in this line's words: a chain's own where it has one
     * ({@code message.nookworks.chain.*}), the rope's where the words are the same for both.
     */
    public String message(String ropeKey) {
        if (this == ROPE || !ropeKey.startsWith(ROPE_MESSAGE) || !OWN_WORDS.contains(ropeKey.substring(ROPE_MESSAGE.length()))) {
            return ropeKey;
        }
        return "message.nookworks." + this.word + "." + ropeKey.substring(ROPE_MESSAGE.length());
    }
}
