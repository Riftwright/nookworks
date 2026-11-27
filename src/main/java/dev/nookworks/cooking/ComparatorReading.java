package dev.nookworks.cooking;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A comparator's reading of a cooking block that is not a vanilla container - the Iron Pot, the Frying Pan, the Grill,
 * the Wooden Tray and the Nukadoko - by how full its slots are, as vanilla reads a chest or a smoker: 0 empty, 1 with
 * anything in, 15 full (the user, 2026-11-24, after the audit of vanilla's item blocks: 「そのようにして下さい」).
 */
public final class ComparatorReading {
    private ComparatorReading() {}

    /** The reading of {@code items}: each slot's share of what it holds, a stack (or its capacity, if less) being full. */
    public static int of(@Nullable ResourceHandler<ItemResource> items) {
        if (items == null || items.size() == 0) {
            return 0;
        }
        float full = 0;
        boolean any = false;
        for (int i = 0; i < items.size(); i++) {
            ItemResource resource = items.getResource(i);
            long amount = items.getAmountAsLong(i);
            if (resource.isEmpty() || amount <= 0) {
                continue;
            }
            any = true;
            long room = Math.max(1, Math.min(items.getCapacityAsLong(i, resource), resource.getMaxStackSize()));
            full += Math.min(1.0F, (float) amount / room);
        }
        return any ? Mth.lerpDiscrete(full / items.size(), 0, 15) : 0;
    }
}
