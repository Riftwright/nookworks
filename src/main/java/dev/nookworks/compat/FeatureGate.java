package dev.nookworks.compat;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
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
                timberConflicts());
    }

    // The lists as the port's builds before 2026-11-17 wrote them into a world's settings: a file still holding one
    // unchanged is read as today's default (with the ids read from the real 1.21.1 mods added since), one a pack wrote
    // its own list into is read as written - as the hammers' list is below.
    static final List<String> TIMBER_CONFLICTS_UNTIL_1211 = List.of("treechop", "fallingtree", "treeharvester", "ftbultimine",
            "treefeller", "chopdownupdated");
    static final List<String> VEIN_CONFLICTS_UNTIL_1211 = List.of("veinmining", "veinminer", "oreexcavation", "ftbultimine");
    static final List<String> MAGNET_CONFLICTS_UNTIL_1211 = List.of("simplemagnets", "magnet", "sophisticatedbackpacks", "botania",
            "actuallyadditions", "cyclic");
    static final List<String> WAND_CONFLICTS_UNTIL_1211 = List.of("buildinggadgets2", "buildinggadgets", "constructionwand");

    /** {@code value}'s list, or its default now if it still holds the default it had until {@code was}. */
    private static List<? extends String> upgraded(ModConfigSpec.ConfigValue<List<? extends String>> value, List<String> was) {
        List<? extends String> conflicts = value.get();
        return conflicts.equals(was) ? value.getDefault() : conflicts;
    }

    public static List<? extends String> timberConflicts() {
        return upgraded(NookworksConfig.TIMBER_CONFLICTS, TIMBER_CONFLICTS_UNTIL_1211);
    }

    public static List<? extends String> veinConflicts() {
        return upgraded(NookworksConfig.VEIN_CONFLICTS, VEIN_CONFLICTS_UNTIL_1211);
    }

    public static List<? extends String> magnetConflicts() {
        return upgraded(NookworksConfig.MAGNET_CONFLICTS, MAGNET_CONFLICTS_UNTIL_1211);
    }

    public static List<? extends String> wandConflicts() {
        return upgraded(NookworksConfig.WAND_CONFLICTS, WAND_CONFLICTS_UNTIL_1211);
    }

    /**
     * Enchantments of other mods that break a tool's whole vein or trunk themselves: on a tool carrying one, our felling,
     * vein mining and the hammers' 3x3 stand aside for that break - the player chose that tool's way. Cyclic's Excavate
     * (read from its 1.21.1 jar: "harvests the whole vein of connected matching blocks", on any mining tool). Only that
     * tool: Cyclic installed, an axe or pickaxe without it works as ours does.
     */
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> AREA_ENCHANTMENTS = java.util.Set.of(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cyclic", "excavate"));

    /** Whether {@code tool} carries another mod's enchantment that breaks a whole vein or trunk itself. */
    public static boolean anotherModsArea(net.minecraft.world.item.ItemStack tool) {
        return tool.isEnchanted() && tool.getEnchantments().keySet().stream()
                .anyMatch(holder -> holder.unwrapKey().map(key -> AREA_ENCHANTMENTS.contains(key.location())).orElse(false));
    }

    public static boolean vein() {
        return allowed(NookworksConfig.VEIN_ENABLED.getAsBoolean(), NookworksConfig.VEIN_STAND_DOWN.getAsBoolean(),
                veinConflicts());
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
                magnetConflicts());
    }

    public static boolean wand() {
        return allowed(NookworksConfig.WAND_ENABLED.getAsBoolean(), NookworksConfig.WAND_STAND_DOWN.getAsBoolean(),
                wandConflicts());
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
                timberConflicts());
        report("Vein mining", NookworksConfig.VEIN_ENABLED.getAsBoolean(), NookworksConfig.VEIN_STAND_DOWN.getAsBoolean(),
                veinConflicts());
        report("Hammer 3x3", NookworksConfig.HAMMER_ENABLED.getAsBoolean(), NookworksConfig.HAMMER_STAND_DOWN.getAsBoolean(),
                hammerConflicts());
        report("Magnet", NookworksConfig.MAGNET_ENABLED.getAsBoolean(), NookworksConfig.MAGNET_STAND_DOWN.getAsBoolean(),
                magnetConflicts());
        report("Building Wand", NookworksConfig.WAND_ENABLED.getAsBoolean(), NookworksConfig.WAND_STAND_DOWN.getAsBoolean(),
                wandConflicts());
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
