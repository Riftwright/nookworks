package dev.nookworks.port.storage;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * Stand-in for 26.3's {@code net.minecraft.world.ContainerHelper}, whose item saves take a {@link ValueOutput} /
 * {@link ValueInput} (1.21.1's take a tag and the registries). The list is written as 26.3 writes it: under
 * {@code Items}, the stacks that are not empty, each with its slot ({@link ItemStackWithSlot}).
 */
public final class ContainerHelper {
    public static final String TAG_ITEMS = "Items";

    private ContainerHelper() {
    }

    public static ItemStack removeItem(List<ItemStack> stacks, int slot, int count) {
        return net.minecraft.world.ContainerHelper.removeItem(stacks, slot, count);
    }

    public static ItemStack takeItem(List<ItemStack> stacks, int slot) {
        return net.minecraft.world.ContainerHelper.takeItem(stacks, slot);
    }

    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> stacks) {
        saveAllItems(output, stacks, true);
    }

    /** With {@code alsoWhenEmpty} false, no {@code Items} list is left when every slot is empty. */
    public static void saveAllItems(ValueOutput output, NonNullList<ItemStack> stacks, boolean alsoWhenEmpty) {
        ValueOutput.TypedOutputList<ItemStackWithSlot> list = output.list(TAG_ITEMS, ItemStackWithSlot.CODEC);
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                list.add(new ItemStackWithSlot(i, stack));
            }
        }
        if (list.isEmpty() && !alsoWhenEmpty) {
            output.discard(TAG_ITEMS);
        }
    }

    /** Puts each saved stack back in its slot; slots the list does not name are left as they are. */
    public static void loadAllItems(ValueInput input, NonNullList<ItemStack> stacks) {
        for (ItemStackWithSlot item : input.listOrEmpty(TAG_ITEMS, ItemStackWithSlot.CODEC)) {
            if (item.isValidInContainer(stacks.size())) {
                stacks.set(item.slot(), item.stack());
            }
        }
    }
}
