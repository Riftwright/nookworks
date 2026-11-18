package dev.nookworks.port.transfer;

import java.util.Objects;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/** Stand-in for NeoForge 26.3's {@code transfer.ResourceHandlerUtil}: the part of it the mod uses. */
public final class ResourceHandlerUtil {
    private ResourceHandlerUtil() {}

    /**
     * Moves up to {@code amount} of the first resource of {@code from} that passes {@code filter} (and more of that same
     * resource from later indices) into {@code to}. Each index moves in a transaction of its own under
     * {@code transaction} (a root if null), committed only when the extract gives exactly what the insert took. Returns
     * what moved, or null if nothing did.
     */
    public static <T extends Resource> @Nullable ResourceStack<T> moveFirst(@Nullable ResourceHandler<T> from, @Nullable ResourceHandler<T> to,
            Predicate<T> filter, int amount, @Nullable TransactionContext transaction) {
        Objects.requireNonNull(filter, "filter");
        TransferPreconditions.checkNonNegative(amount);
        if (from == null || to == null || amount == 0) {
            return null;
        }
        int moved = 0;
        T chosen = null;
        for (int i = 0, n = from.size(); i < n && moved < amount; i++) {
            T resource = from.getResource(i);
            boolean candidate = chosen == null ? !resource.isEmpty() && filter.test(resource) : chosen.equals(resource);
            if (!candidate) {
                continue;
            }
            int available;
            try (Transaction probe = Transaction.open(transaction)) {
                available = from.extract(i, resource, amount - moved, probe);
            }
            if (available == 0) {
                continue;
            }
            try (Transaction step = Transaction.open(transaction)) {
                int taken = to.insert(resource, available, step);
                if (taken == 0 || from.extract(i, resource, taken, step) != taken) {
                    continue; // nothing fits, or the source will not give exactly that: this index is left as it was
                }
                step.commit();
                moved += taken;
                chosen = resource;
            }
        }
        return moved > 0 ? new ResourceStack<>(chosen, moved) : null;
    }

    /**
     * Puts up to {@code amount} of {@code resource} into {@code handler} as a player's inventory takes a stack: first
     * into the indices that already hold something (topping up those of the same resource), then into the empty ones,
     * each in index order. Done in a transaction of its own under {@code transaction} (a root if null), committed
     * whatever went in. Returns how much went in (0 for no handler).
     */
    public static <T extends Resource> int insertStacking(@Nullable ResourceHandler<T> handler, T resource, int amount,
            @Nullable TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (handler == null || amount == 0) {
            return 0;
        }
        try (Transaction inner = Transaction.open(transaction)) {
            int moved = 0;
            int size = handler.size();
            for (int pass = 0; pass < 2 && moved < amount; pass++) {
                boolean wantEmpty = pass == 1;
                for (int i = 0; i < size && moved < amount; i++) {
                    if (handler.getResource(i).isEmpty() == wantEmpty) {
                        moved += handler.insert(i, resource, amount - moved, inner);
                    }
                }
            }
            inner.commit();
            return moved;
        }
    }

    /** Whether some index holds {@code resource}. */
    public static <T extends Resource> boolean contains(ResourceHandler<T> handler, T resource) {
        return indexOf(handler, resource) >= 0;
    }

    /** The first index holding {@code resource}, or -1. */
    public static <T extends Resource> int indexOf(ResourceHandler<T> handler, T resource) {
        TransferPreconditions.checkNonEmpty(resource);
        for (int i = 0, n = handler.size(); i < n; i++) {
            if (resource.equals(handler.getResource(i))) {
                return i;
            }
        }
        return -1;
    }

    /** The first resource that passes {@code filter} and could be extracted now (tried in a transaction that aborts). */
    public static <T extends Resource> @Nullable T findExtractableResource(ResourceHandler<T> handler, Predicate<T> filter,
            @Nullable TransactionContext transaction) {
        try (Transaction probe = Transaction.open(transaction)) {
            for (int i = 0, n = handler.size(); i < n; i++) {
                T resource = handler.getResource(i);
                if (!resource.isEmpty() && filter.test(resource) && handler.extract(i, resource, handler.getAmountAsInt(i), probe) > 0) {
                    return resource;
                }
            }
            return null;
        }
    }
}
