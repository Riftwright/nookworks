package dev.nookworks.ifworld;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Nook of the Dead, our zombie add-on, reaches its world through the If. With the If switched off (the start-up config
 * or the world's own choice), the If Mirror is made by no recipe and that world cannot be reached: the add-on does
 * nothing. It is allowed - a pack's say - but said: in the log when the server starts, to an operator or the
 * single-player world's owner as they join, and on the If's switch, locked by the start-up config
 * (the user, 2026-10-18: 「せめてアラート…入れたほうが親切」). The Create World screen asks before switching it off
 * ({@code WorldFeaturesScreen}). The same holds when the If is on but its mirror can be made no more, the parts it is
 * made through switched off (the Sealed Retort's mercury): said so, and named among what the parts list warns of.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class NookOfTheDeadWarning {
    /** Nook of the Dead's mod id. */
    public static final String MOD = "nookofthedead";
    /** The part of ours it needs. */
    public static final String PART = "if_world";
    /** The item of ours it is reached through. */
    public static final String MIRROR = "nookworks:if_mirror";

    /** Whether Nook of the Dead is installed. */
    public static boolean present() {
        return ModList.get() != null && ModList.get().isLoaded(MOD);
    }

    /** Whether it is installed and the If is off, or its mirror can be made no more, so it cannot work. */
    public static boolean stranded() {
        return present() && (!Features.enabled(PART) || !Features.available(MIRROR));
    }

    /** What is said of it stranded: the If off, or only its mirror out of reach. */
    private static String message() {
        return !Features.enabled(PART) ? "message.nookworks.notd_if_off" : "message.nookworks.notd_mirror_lost";
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        if (stranded()) {
            Nookworks.LOGGER.warn("Nook of the Dead is installed but Nookworks' If is switched off, or its mirror can be made no more:"
                    + " its zombie world cannot be reached.");
        }
    }

    @SubscribeEvent
    static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        // an operator, or the one whose single-player world it is (cheats off, no operator rights: the review of 2f6d2054)
        if (event.getEntity() instanceof ServerPlayer player && stranded()
                && (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                        || player.level().getServer().isSingleplayerOwner(player.nameAndId()))) {
            player.sendSystemMessage(Component.translatable(message()).withStyle(ChatFormatting.YELLOW));
        }
    }

    private NookOfTheDeadWarning() {}
}
