package dev.nookworks.feature;

import dev.nookworks.Nookworks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Advancements the mod awards itself (their criterion is minecraft:impossible in tools/gen_advancements.py), where no
 * vanilla trigger tells the deed from a mere click: food set out on a Plate, a pipe's side set to pull with the Wrench.
 */
public final class ModAdvancements {
    private ModAdvancements() {
    }

    /**
     * Awards the advancement's criterion to the player, on the server. Nothing happens for a client, a fake player of
     * no account, or an advancement not loaded (a part of the mod switched off takes it out with its items).
     */
    public static void award(@Nullable Player player, String id, String criterion) {
        if (player instanceof ServerPlayer server) {
            AdvancementHolder holder = server.level().getServer().getAdvancements().get(Nookworks.id(id));
            if (holder != null) {
                server.getAdvancements().award(holder, criterion);
            }
        }
    }
}
