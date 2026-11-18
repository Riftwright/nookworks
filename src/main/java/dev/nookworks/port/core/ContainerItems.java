package dev.nookworks.port.core;

import java.util.Iterator;
import java.util.NoSuchElementException;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * 26.3's {@code Container} is {@code Iterable<ItemStack>} (every slot, 0 to {@code getContainerSize()}, the stacks
 * themselves); 1.21.1's is not. {@code for (ItemStack stack : inventory)} becomes
 * {@code for (ItemStack stack : ContainerItems.of(inventory))}, the same slots in the same order.
 */
public final class ContainerItems {
    private ContainerItems() {}

    public static Iterable<ItemStack> of(Container container) {
        return () -> new Iterator<>() {
            private final int size = container.getContainerSize();
            private int index;

            @Override
            public boolean hasNext() {
                return this.index < this.size;
            }

            @Override
            public ItemStack next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }
                return container.getItem(this.index++);
            }
        };
    }
}
