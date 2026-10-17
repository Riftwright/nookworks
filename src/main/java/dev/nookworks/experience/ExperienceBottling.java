package dev.nookworks.experience;

import dev.nookworks.feature.Features;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Filling buckets and bottles from a machine's experience: the fill box on the Sealed Retort's and the Electric
 * Furnace's screens (an empty container in, the filled one out, a hopper at either end), and the Experience Tank's
 * right-click. A bucket takes 1,000 mB, a bottle 250 mB - twelve and a half points, which the Enchanting Table turns
 * into a Bottle o' Enchanting (see {@code mixin/EnchantmentMenuMixin}) - about seven points: nothing comes of it for
 * free.
 */
public final class ExperienceBottling {
    public static final int BUCKET = 1_000;
    public static final int BOTTLE = 250;

    /**
     * What the fill box takes: an empty bucket or glass bottle - none with liquid experience switched off, when nothing
     * would fill it: a hopper's bottles would sit in the box for good, and stop the hopper.
     */
    public static boolean fillable(ItemStack stack) {
        return !filled(stack).isEmpty();
    }

    /** What an empty container becomes when filled; empty if it is not one. */
    public static ItemStack filled(ItemStack empty) {
        ItemStack filled = empty.is(Items.BUCKET) ? new ItemStack(ModItems.EXPERIENCE_BUCKET.get())
                : empty.is(Items.GLASS_BOTTLE) ? new ItemStack(ModItems.LIQUID_EXPERIENCE_BOTTLE.get()) : ItemStack.EMPTY;
        return Features.enabled(filled) ? filled : ItemStack.EMPTY; // none made with the part switched off
    }

    public static int amountFor(ItemStack empty) {
        return empty.is(Items.BUCKET) ? BUCKET : BOTTLE;
    }

    /**
     * Fills one container from {@code tank} if it holds enough and the out slot has room: the container at
     * {@code emptySlot} is used up and its filled self goes to {@code filledSlot}. Whether anything happened.
     */
    public static boolean fill(NonNullList<ItemStack> items, int emptySlot, int filledSlot, ResourceHandler<FluidResource> tank) {
        ItemStack in = items.get(emptySlot);
        ItemStack result = filled(in);
        if (result.isEmpty()) {
            return false;
        }
        ItemStack out = items.get(filledSlot);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, result) || out.getCount() >= out.getMaxStackSize())) {
            return false;
        }
        if (!Move.extractExactly(tank, FluidResource.of(ExperienceFluids.SOURCE.get()), amountFor(in))) {
            return false;
        }
        in.shrink(1);
        if (out.isEmpty()) {
            items.set(filledSlot, result);
        } else {
            out.grow(1);
        }
        return true;
    }

    private ExperienceBottling() {}
}
