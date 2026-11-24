package dev.nookworks.client;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.feature.Features;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * What the mod's settings screen ({@code client.gui.NookworksConfigScreen}) shows, kept apart from the screen so the
 * GameTests can read it on a server: every setting of the three configs in its group, with its row's picture and how
 * its number is shown, and the round steps a number is chosen from.
 */
public final class SettingsGroups {
    private SettingsGroups() {}

    /** Which config a setting is kept in, which says who may change it and when it takes hold. */
    public enum Scope { WORLD, CLIENT, STARTUP }

    /** How a number is shown. */
    public enum Unit { PLAIN, BLOCKS, TIMES, PERCENT, CHANCE, TICKS, STACKS }

    /** A row of a group: a heading over the settings after it, or a setting. */
    public sealed interface Entry permits Header, Opt {}

    /** A heading, with the picture of what the settings under it are about. */
    public record Header(String key, @Nullable String icon) implements Entry {}

    /** One setting: its value, its config, its row's picture (an item id, or none) and how a number of it is shown. */
    public record Opt(ModConfigSpec.ConfigValue<?> value, Scope scope, @Nullable String icon, Unit unit) implements Entry {
        public String key() {
            return this.value.getSpec().getTranslationKey();
        }

        public ModConfigSpec spec() {
            return switch (this.scope) {
                case WORLD -> NookworksConfig.SPEC;
                case CLIENT -> NookworksClientConfig.SPEC;
                case STARTUP -> NookworksStartupConfig.SPEC;
            };
        }
    }

    /** A group of settings, under an item's picture: its rows, headings among them. */
    public record Group(String id, String icon, List<Entry> entries) {
        public List<Opt> opts() {
            return this.entries.stream().filter(e -> e instanceof Opt).map(e -> (Opt) e).toList();
        }
    }

    private static Header head(String key, @Nullable String icon) {
        return new Header(key, icon);
    }

    private static Opt world(ModConfigSpec.ConfigValue<?> value) {
        return new Opt(value, Scope.WORLD, null, Unit.PLAIN);
    }

    private static Opt world(ModConfigSpec.ConfigValue<?> value, Unit unit) {
        return new Opt(value, Scope.WORLD, null, unit);
    }

    private static Opt world(ModConfigSpec.ConfigValue<?> value, @Nullable String icon, Unit unit) {
        return new Opt(value, Scope.WORLD, icon, unit);
    }

    private static Opt client(ModConfigSpec.ConfigValue<?> value) {
        return new Opt(value, Scope.CLIENT, null, Unit.PLAIN);
    }

    private static Opt client(ModConfigSpec.ConfigValue<?> value, Unit unit) {
        return new Opt(value, Scope.CLIENT, null, unit);
    }

    private static Opt startup(ModConfigSpec.ConfigValue<?> value, @Nullable String icon) {
        return new Opt(value, Scope.STARTUP, icon, Unit.PLAIN);
    }

    /**
     * The settings whose explanation has a picture, where words alone leave it unclear (the user, 2026-11-22: 「説明だから、
     * 文字だけだと分かりにくいところに写真入れた方がいいんじゃない？」): a shot from the game in textures/gui/settings/NAME.png,
     * or, where the picture has words in it, "@NAME", drawn by the settings screen in the player's language.
     */
    public static final java.util.Map<String, String> PICTURES = java.util.Map.ofEntries(
            java.util.Map.entry("nookworks.configuration.hammer.enabled", "hammer_3x3"),
            java.util.Map.entry("nookworks.configuration.hammer.activation", "hammer_3x3"),
            java.util.Map.entry("nookworks.configuration.buildingWand.enabled", "building_wand"),
            java.util.Map.entry("nookworks.configuration.squint.hand", "squint_hand"),
            java.util.Map.entry("nookworks.configuration.hubStone.flow", "hub_stone_flow"),
            java.util.Map.entry("nookworks.configuration.chestSortAllowed", "@chest_sort"),
            java.util.Map.entry("nookworks.configuration.inventory.chestSort", "@chest_sort"),
            java.util.Map.entry("nookworks.configuration.inventory.chestSortOrder", "@chest_sort"),
            java.util.Map.entry("nookworks.configuration.inventory.chestSortReversed", "@chest_sort"),
            java.util.Map.entry("nookworks.configuration.worldMenu", "@world_menu"));

    private static final String C = "nookworks.configuration.";
    private static final String H = "nookworks.settings.header.";

    /** Every group, in the order shown; a setting not in one would not be on this screen (NeoForge's list has it). */
    public static List<Group> groups() {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group("mining", "nookworks:iron_hammer", List.of(
                head(C + "timber", "minecraft:iron_axe"),
                world(NookworksConfig.TIMBER_ENABLED),
                world(NookworksConfig.TIMBER_ACTIVATION),
                world(NookworksConfig.TIMBER_MAX_BLOCKS),
                world(NookworksConfig.FAST_LEAF_DECAY),
                world(NookworksConfig.INSTANT_LEAF_DECAY),
                head(C + "veinMining", "minecraft:iron_pickaxe"),
                world(NookworksConfig.VEIN_ENABLED),
                world(NookworksConfig.VEIN_ACTIVATION),
                world(NookworksConfig.VEIN_MAX_BLOCKS),
                head(C + "hammer", "nookworks:iron_hammer"),
                world(NookworksConfig.HAMMER_ENABLED),
                world(NookworksConfig.HAMMER_ACTIVATION),
                head(H + "all_three", "minecraft:anvil"),
                world(NookworksConfig.PROTECT_TOOLS))));
        groups.add(new Group("tools", "nookworks:magnet", List.of(
                head(C + "magnet", "nookworks:magnet"),
                world(NookworksConfig.MAGNET_ENABLED),
                head(C + "buildingWand", "nookworks:building_wand"),
                world(NookworksConfig.WAND_ENABLED),
                head("item.nookworks.camera", "nookworks:camera"),
                world(NookworksConfig.PLAYER_PHOTOS),
                head(H + "squint", "minecraft:spyglass"),
                client(NookworksClientConfig.SQUINT_ZOOM, Unit.TIMES),
                client(NookworksClientConfig.SQUINT_HAND))));
        groups.add(new Group("machines", "nookworks:generator", List.of(
                head(C + "machines", "nookworks:machine_chassis"),
                world(NookworksConfig.MACHINE_ENERGY, Unit.TIMES),
                world(NookworksConfig.MACHINE_TIME, Unit.TIMES),
                world(NookworksConfig.GENERATOR_OUTPUT, Unit.TIMES),
                world(NookworksConfig.MACHINE_VIBRATIONS),
                head("block.nookworks.sifter", "nookworks:sifter"),
                world(NookworksConfig.SIFTER_TICKS, Unit.TICKS),
                head(H + "reach", "nookworks:elevator"),
                world(NookworksConfig.ENERGY_CABLE_REACH, "nookworks:energy_cable", Unit.PLAIN),
                world(NookworksConfig.ELEVATOR_RANGE, "nookworks:elevator", Unit.BLOCKS),
                world(NookworksConfig.ITEM_WARP_RANGE, "nookworks:item_warp_sender", Unit.BLOCKS))));
        groups.add(new Group("food", "nookworks:iron_pot", List.of(
                world(NookworksConfig.RAW_FOOD_SICKNESS, "minecraft:chicken", Unit.PLAIN),
                world(NookworksConfig.CAMPFIRES_PLACED_UNLIT, "minecraft:campfire", Unit.PLAIN),
                startup(NookworksStartupConfig.FLOUR_BAKERY, "nookworks:flour"),
                startup(NookworksStartupConfig.WHEAT_BREAD, "minecraft:bread"),
                startup(NookworksStartupConfig.EDIBLE_RAW_FOODS, "minecraft:dried_kelp"))));
        groups.add(new Group("storage", "nookworks:iron_storage_barrel", List.of(
                head(H + "chest_sort", "minecraft:chest"),
                world(NookworksConfig.CHEST_SORT_ALLOWED),
                client(NookworksClientConfig.CHEST_SORT),
                client(NookworksClientConfig.CHEST_SORT_ORDER),
                client(NookworksClientConfig.CHEST_SORT_REVERSED),
                head(H + "storage_barrel", "nookworks:iron_storage_barrel"),
                world(NookworksConfig.STORAGE_BREAK_LIMIT, Unit.STACKS),
                head(H + "inventory", "minecraft:hopper"),
                client(NookworksClientConfig.WHEEL_TRANSFER),
                client(NookworksClientConfig.INVERT_WHEEL),
                client(NookworksClientConfig.SHIFT_DRAG),
                head(H + "recipes", "minecraft:crafting_table"),
                world(NookworksConfig.RECIPE_CHOICE),
                client(NookworksClientConfig.ENGLISH_SEARCH))));
        groups.add(new Group("sound_look", "nookworks:machine_bell", List.of(
                head(H + "machine_sounds", "nookworks:machine_bell"),
                client(NookworksClientConfig.MACHINE_SOUND_VOLUME, Unit.PERCENT),
                client(NookworksClientConfig.MACHINE_SOUND_FREQUENCY, Unit.PERCENT),
                client(NookworksClientConfig.MACHINE_STOP_CUE),
                head(H + "looks", "nookworks:standing_mirror"),
                client(NookworksClientConfig.MIRROR_PACK_LIGHT),
                client(NookworksClientConfig.HUB_STONE_FLOW))));
        groups.add(new Group("world", "minecraft:filled_map", List.of(
                world(NookworksConfig.TRADER_VILLAGE_MAP_CHANCE, "minecraft:filled_map", Unit.CHANCE),
                head("item.nookworks.end_seed", "nookworks:end_seed"),
                world(NookworksConfig.END_SEED_MOD_FLOWERS),
                world(NookworksConfig.END_SEED_LEFT_OUT_MODS),
                head(H + "new_worlds", "minecraft:grass_block"),
                startup(NookworksStartupConfig.WORLD_MENU, null))));
        groups.add(new Group("other_mods", "nookworks:wrench", List.of(
                head(C + "timber", "minecraft:iron_axe"),
                world(NookworksConfig.TIMBER_STAND_DOWN),
                world(NookworksConfig.TIMBER_CONFLICTS),
                head(C + "veinMining", "minecraft:iron_pickaxe"),
                world(NookworksConfig.VEIN_STAND_DOWN),
                world(NookworksConfig.VEIN_CONFLICTS),
                head(C + "hammer", "nookworks:iron_hammer"),
                world(NookworksConfig.HAMMER_STAND_DOWN),
                world(NookworksConfig.HAMMER_CONFLICTS),
                head(C + "magnet", "nookworks:magnet"),
                world(NookworksConfig.MAGNET_STAND_DOWN),
                world(NookworksConfig.MAGNET_CONFLICTS),
                head(C + "buildingWand", "nookworks:building_wand"),
                world(NookworksConfig.WAND_STAND_DOWN),
                world(NookworksConfig.WAND_CONFLICTS))));
        // the parts a pack switches off for every world, in the parts list's groups, each with the first of its items
        List<Entry> parts = new ArrayList<>();
        Features.GROUPS.forEach((group, members) -> {
            parts.add(head("nookworks.world_features.group." + group, null));
            for (String part : members) {
                ModConfigSpec.BooleanValue value = NookworksStartupConfig.feature(part);
                List<net.minecraft.resources.Identifier> items = Features.PARTS.getOrDefault(part, List.of());
                if (value != null) {
                    parts.add(startup(value, items.isEmpty() ? null : items.getFirst().toString()));
                }
            }
        });
        groups.add(new Group("parts", "nookworks:machine_chassis", parts));
        return groups;
    }

    /** Round values from a setting's least to its most: whole steps for a short range, 1-2-5 for a long one. */
    public static List<Double> steps(Opt opt) {
        ModConfigSpec.Range<?> range = opt.value().getSpec().getRange();
        double min = ((Number) range.getMin()).doubleValue();
        double max = ((Number) range.getMax()).doubleValue();
        boolean whole = opt.value() instanceof ModConfigSpec.IntValue;
        TreeSet<Double> out = new TreeSet<>();
        out.add(min);
        out.add(max);
        out.add(((Number) opt.value().getDefault()).doubleValue());
        if (opt.spec().isLoaded()) {
            out.add(((Number) opt.value().get()).doubleValue()); // (a client config is not loaded on a server)
        }
        if (opt.unit() == Unit.CHANCE) {
            for (int i = 0; i <= 20; i++) {
                out.add(min + (max - min) * i / 20.0);
            }
        } else if (whole && max - min <= 40) {
            for (double v = min; v <= max; v++) {
                out.add(v);
            }
        } else if (opt.unit() == Unit.PERCENT) {
            for (double v = Math.ceil(min / 5) * 5; v <= max; v += 5) {
                out.add(v);
            }
        } else if (!whole && max / Math.max(min, 1e-9) <= 4) {
            for (double v = Math.ceil(min * 4) / 4; v <= max + 1e-9; v += 0.25) {
                out.add(Math.round(v * 100) / 100.0);
            }
        } else {
            for (double decade = 1e-2; decade <= max * 10; decade *= 10) {
                for (double m : new double[] {1, 1.5, 2, 3, 5, 7.5}) {
                    double v = m * decade;
                    if (v >= min && v <= max && (!whole || v == Math.rint(v))) {
                        out.add(v);
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

}
