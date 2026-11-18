package dev.nookworks.port.core;

import net.minecraft.core.component.DataComponentType;
import org.jspecify.annotations.Nullable;

/**
 * 26.3's {@code net.minecraft.core.component.DataComponentGetter}: a read-only view of components, which 26.3 hands to
 * {@code BlockEntity.applyImplicitComponents} and which item stacks are too. 1.21.1 hands that method its own
 * {@code BlockEntity.DataComponentInput} (protected, so only a block entity can name it), with the same {@code get}.
 * A block entity's override takes 1.21.1's input and makes this view of it with a method reference
 * ({@code DataComponentGetter components = input::get;}); a stack is {@code stack::get}. The helpers the mod shares
 * between block entities keep taking this type.
 */
@FunctionalInterface
public interface DataComponentGetter {
    /** The component's value, or null when there is none (both versions' {@code get}). */
    <T> @Nullable T get(DataComponentType<T> type);

    default <T> T getOrDefault(DataComponentType<? extends T> type, T fallback) {
        T value = this.get(type);
        return value != null ? value : fallback;
    }

    default boolean has(DataComponentType<?> type) {
        return this.get(type) != null;
    }
}
