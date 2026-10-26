package dev.nookworks.compat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Turns overlapping QoL off when another mod already does the same job, the way inventory tweaks stand down
 * for Mouse Tweaks. The lists are config, so a pack can add or remove ids. Unique blocks (Magnet Chest, our
 * hammer items as 1x1 tools) stay; only the overlapping behaviour is gated.
 * <p>
 * What stood down, and for which mod, is logged once as a world starts: otherwise a player whose axe stopped
 * felling trees has nothing to tell them why.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class FeatureGate {
    public static boolean timber() {
        return allowed(NookworksConfig.TIMBER_ENABLED.getAsBoolean(), NookworksConfig.TIMBER_STAND_DOWN.getAsBoolean(),
                NookworksConfig.TIMBER_CONFLICTS.get());
    }

    public static boolean vein() {
        return allowed(NookworksConfig.VEIN_ENABLED.getAsBoolean(), NookworksConfig.VEIN_STAND_DOWN.getAsBoolean(),
                NookworksConfig.VEIN_CONFLICTS.get());
    }

    public static boolean hammer() {
        return allowed(NookworksConfig.HAMMER_ENABLED.getAsBoolean(), NookworksConfig.HAMMER_STAND_DOWN.getAsBoolean(),
                hammerConflicts());
    }

    /** The hammers' default list until 1.2.0, which held FTB Ultimine. */
    static final List<String> HAMMER_CONFLICTS_UNTIL_1_2 = List.of("justhammers", "hammers", "vanilla_hammers", "ftbultimine");

    /**
     * The mods the hammers stand down for. FTB Ultimine is no longer among them (the user, 2026-10-23: ハンマーは止めない):
     * it mines what is joined to the block, a tool of its own beside ours, and a pack that has it - most do - was left
     * with seven hammers digging one block. A settings file still holding the old default list unchanged is read as
     * today's; one a pack wrote its own list into is read as written.
     */
    public static List<? extends String> hammerConflicts() {
        List<? extends String> conflicts = NookworksConfig.HAMMER_CONFLICTS.get();
        return conflicts.equals(HAMMER_CONFLICTS_UNTIL_1_2) ? HAMMER_CONFLICTS_UNTIL_1_2.subList(0, 3) : conflicts;
    }

    public static boolean magnet() {
        return allowed(NookworksConfig.MAGNET_ENABLED.getAsBoolean(), NookworksConfig.MAGNET_STAND_DOWN.getAsBoolean(),
                NookworksConfig.MAGNET_CONFLICTS.get());
    }

    public static boolean wand() {
        return allowed(NookworksConfig.WAND_ENABLED.getAsBoolean(), NookworksConfig.WAND_STAND_DOWN.getAsBoolean(),
                NookworksConfig.WAND_CONFLICTS.get());
    }

    /** {@code enabled} is the master switch; {@code standDown} plus a loaded conflict id turns the feature off. */
    public static boolean allowed(boolean enabled, boolean standDown, List<? extends String> conflicts) {
        if (!enabled) {
            return false;
        }
        return !standDown || !anyLoaded(conflicts);
    }

    /** The first of {@code modIds} that is loaded, or null. */
    public static @Nullable String firstLoaded(List<? extends String> modIds) {
        ModList mods = ModList.get();
        for (String id : modIds) {
            if (id != null && !id.isBlank() && mods.isLoaded(id)) {
                return id;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        report("Tree felling", NookworksConfig.TIMBER_ENABLED.getAsBoolean(), NookworksConfig.TIMBER_STAND_DOWN.getAsBoolean(),
                NookworksConfig.TIMBER_CONFLICTS.get());
        report("Vein mining", NookworksConfig.VEIN_ENABLED.getAsBoolean(), NookworksConfig.VEIN_STAND_DOWN.getAsBoolean(),
                NookworksConfig.VEIN_CONFLICTS.get());
        report("Hammer 3x3", NookworksConfig.HAMMER_ENABLED.getAsBoolean(), NookworksConfig.HAMMER_STAND_DOWN.getAsBoolean(),
                hammerConflicts());
        report("Magnet", NookworksConfig.MAGNET_ENABLED.getAsBoolean(), NookworksConfig.MAGNET_STAND_DOWN.getAsBoolean(),
                NookworksConfig.MAGNET_CONFLICTS.get());
        report("Building Wand", NookworksConfig.WAND_ENABLED.getAsBoolean(), NookworksConfig.WAND_STAND_DOWN.getAsBoolean(),
                NookworksConfig.WAND_CONFLICTS.get());
    }

    private static void report(String feature, boolean enabled, boolean standDown, List<? extends String> conflicts) {
        String by = standDown ? firstLoaded(conflicts) : null;
        if (enabled && by != null) {
            Nookworks.LOGGER.info("{} is off: {} is loaded and does the same job (set standDownIfConflicting to false in "
                    + "nookworks-server.toml to keep ours)", feature, by);
        }
    }

    public static boolean anyLoaded(List<? extends String> modIds) {
        return firstLoaded(modIds) != null;
    }

    private FeatureGate() {}
}
