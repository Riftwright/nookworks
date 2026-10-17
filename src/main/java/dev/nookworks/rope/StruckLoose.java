package dev.nookworks.rope;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;

/**
 * Lightning knocks rope loose: a rope tied to a lightning rod comes undone when it is struck, and falls (the user,
 * 2026-10-05: "雷落ちたら取れちゃいそう"; a knot anywhere else is let be, as vanilla's lead knots are), and what hung
 * near the strike is knocked down as by a hit. What falls is spared by the same bolt: its further flashes strike again
 * whatever lies there, and an item struck is gone, so the rope would burn up as it fell. A later bolt is a bolt like any
 * other.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class StruckLoose {
    private static final String BOLT = "nookworks_struck_loose_by";

    private StruckLoose() {}

    /** {@code item} fell because {@code bolt} struck what held it (nothing, with no bolt). */
    static void fellFrom(@Nullable ItemEntity item, @Nullable UUID bolt) {
        if (item != null && bolt != null) {
            item.getPersistentData().putString(BOLT, bolt.toString());
        }
    }

    @SubscribeEvent
    static void onStruck(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ItemEntity item
                && item.getPersistentData().getStringOr(BOLT, "").equals(event.getLightning().getStringUUID())) {
            event.setCanceled(true);
        }
    }
}
