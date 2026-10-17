package dev.nookworks.experience;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Transfers that happen for real. NeoForge hands every move a transaction so it can be rolled back as part of
 * a larger one; each call here is its own transaction and commits only what actually went through.
 */
public final class Move {
    public static <T extends Resource> int insert(ResourceHandler<T> handler, T resource, int amount) {
        if (amount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.insert(resource, amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    public static <T extends Resource> int extract(ResourceHandler<T> handler, T resource, int amount) {
        if (amount <= 0) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int moved = handler.extract(resource, amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    /** Puts exactly {@code amount} in, or nothing at all. Returns whether it went in. */
    public static <T extends Resource> boolean insertExactly(ResourceHandler<T> handler, T resource, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            if (handler.insert(resource, amount, transaction) != amount) {
                return false; // closing without a commit rolls back the part that did go in
            }
            transaction.commit();
            return true;
        }
    }

    /** Takes exactly {@code amount} out, or nothing at all. Returns whether it came out. */
    public static <T extends Resource> boolean extractExactly(ResourceHandler<T> handler, T resource, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            if (handler.extract(resource, amount, transaction) != amount) {
                return false;
            }
            transaction.commit();
            return true;
        }
    }

    /**
     * Moves up to {@code amount} from one handler to another as one transaction: what leaves {@code from} is exactly
     * what arrives in {@code to}. Taking first and then finding the other side full (or holding a fluid it will not
     * mix with) would otherwise lose the difference. Returns the amount moved.
     */
    public static <T extends Resource> int transfer(ResourceHandler<T> from, ResourceHandler<T> to, T resource, int amount) {
        if (amount <= 0) {
            return 0;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try (Transaction transaction = Transaction.openRoot()) {
                int taken = from.extract(resource, amount, transaction);
                if (taken <= 0) {
                    return 0;
                }
                int given = to.insert(resource, taken, transaction);
                if (given == taken) {
                    transaction.commit();
                    return given;
                }
                if (given <= 0) {
                    return 0;
                }
                amount = given; // roll back, and try again with only what the other side will take
            }
        }
        return 0;
    }

    private Move() {}
}
