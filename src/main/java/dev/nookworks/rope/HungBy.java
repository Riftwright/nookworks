package dev.nookworks.rope;

import java.util.List;

import org.joml.Vector3fc;

import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModRope;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;

/**
 * The end a hung thing is tied on by - a bottle by its neck, a fish by its tail, wheat and corn by their stalks, a
 * flower upside down by its stem, as flowers are hung to dry - and, given its outline, where the cord ties on and how it
 * hangs from there. The client reads the outline off the item's model; the geometry is here, where it can be tested.
 *
 * <p>Each end is a direction in the frame the item is drawn in. That is its picture turned round (the FIXED display
 * turns an item about, so it faces out of an item frame): the picture's right is this frame's left.
 */
public enum HungBy {
    /** Bottles by the neck, onions by theirs, berries by the sprig, and most things. */
    TOP(0, 1),
    /** A fish, whose tail is at its picture's top right, head down. */
    TAIL(-1, 1),
    /** Wheat, corn and kelp, by the stalk ends at the picture's bottom left, heads down. */
    STALKS(1, -1),
    /** A flower, by the foot of its stem, upside down. */
    STEM(0, -1);

    private static final float PIXEL = 1.0F / 16;

    private final float x;
    private final float y;

    HungBy(float x, float y) {
        float length = Mth.sqrt(x * x + y * y);
        this.x = x / length;
        this.y = y / length;
    }

    public static HungBy of(ItemStack stack) {
        if (stack.is(BlockItemTags.SMALL_FLOWERS.item())) {
            return STEM;
        }
        if (stack.is(Items.WHEAT) || stack.is(Items.KELP) || stack.is(ModKitchen.CORN.get())) {
            return STALKS;
        }
        if (stack.is(Items.PUFFERFISH)) {
            return TOP; // round and seen face on: by the top, a lantern of a fish
        }
        boolean fish = stack.is(ItemTags.FISHES) || stack.is(Tags.Items.FOODS_RAW_FISH) || stack.is(Tags.Items.FOODS_COOKED_FISH)
                || stack.is(ModRope.DRIED_FISH.get());
        return fish ? TAIL : TOP;
    }

    /**
     * Where the cord ties on and the turn that hangs the rest below it, for an outline (its points' x and y, a block
     * across, centred): a pixel in from the outline's far end this way, and turned about z so that the middle of the
     * outline hangs straight below, as a thing on a string does. Returns {x, y, turn in radians}; with no outline, the
     * top middle and no turn.
     */
    public float[] tieOn(List<? extends Vector3fc> outline) {
        if (outline.isEmpty()) {
            return new float[] {0, 0.5F, 0};
        }
        float far = -Float.MAX_VALUE;
        for (Vector3fc v : outline) {
            far = Math.max(far, v.x() * this.x + v.y() * this.y);
        }
        float endX = 0, endY = 0, middleX = 0, middleY = 0;
        int ends = 0;
        for (Vector3fc v : outline) {
            if (v.x() * this.x + v.y() * this.y > far - PIXEL / 2) {
                endX += v.x();
                endY += v.y();
                ends++;
            }
            middleX += v.x();
            middleY += v.y();
        }
        float tieX = endX / ends - this.x * PIXEL;
        float tieY = endY / ends - this.y * PIXEL;
        middleX /= outline.size();
        middleY /= outline.size();
        float turn = (float) (-Math.PI / 2 - Math.atan2(middleY - tieY, middleX - tieX));
        return new float[] {tieX, tieY, turn};
    }
}
