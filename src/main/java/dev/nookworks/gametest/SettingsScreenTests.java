package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.client.NookworksClientConfig;
import dev.nookworks.client.SettingsGroups;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's own settings screen (2026-11-22) shows every setting of its three configs, each once, in a group of its
 * own - one added to a config and left out of {@link SettingsGroups} would be on NeoForge's list only - with its name and
 * explanation in the language file; and a number is chosen from steps inside its range that hold its default.
 */
final class SettingsScreenTests {
    private SettingsScreenTests() {}

    static void register() {
        NookworksGameTests.test("settings_screen_shows_every_setting_once", SettingsScreenTests::everySettingOnce);
        NookworksGameTests.test("settings_screen_numbers_step_within_their_range", SettingsScreenTests::stepsInRange);
        NookworksGameTests.test("settings_screen_pictures_belong_to_settings_and_exist", SettingsScreenTests::pictures);
    }

    /** Every picture is for a setting on the screen, and every shot named is in the mod (a name mistyped shows none). */
    private static void pictures(GameTestHelper helper) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (SettingsGroups.Group group : SettingsGroups.groups()) {
            for (SettingsGroups.Opt opt : group.opts()) {
                keys.add(opt.key());
            }
        }
        SettingsGroups.PICTURES.forEach((key, name) -> {
            check(helper, keys.contains(key), key + " has a picture but is no setting on the screen");
            if (!name.startsWith("@")) {
                check(helper, SettingsScreenTests.class.getResource("/assets/nookworks/textures/gui/settings/" + name + ".png") != null,
                        key + ": no picture " + name + ".png");
            } else {
                check(helper, java.util.Set.of("@chest_sort", "@world_menu").contains(name), key + ": nothing draws " + name);
            }
        });
        helper.succeed();
    }

    private static void collect(UnmodifiableConfig values, List<ModConfigSpec.ConfigValue<?>> out) {
        for (Object value : values.valueMap().values()) {
            if (value instanceof ModConfigSpec.ConfigValue<?> configValue) {
                out.add(configValue);
            } else if (value instanceof UnmodifiableConfig inner) {
                collect(inner, out);
            }
        }
    }

    private static void everySettingOnce(GameTestHelper helper) {
        Map<ModConfigSpec.ConfigValue<?>, Integer> shown = new IdentityHashMap<>();
        for (SettingsGroups.Group group : SettingsGroups.groups()) {
            check(helper, Language.getInstance().has("nookworks.settings.group." + group.id()), "group " + group.id() + " has a name");
            check(helper, Language.getInstance().has("nookworks.settings.group." + group.id() + ".desc"), "group " + group.id() + " has a line");
            for (SettingsGroups.Opt opt : group.opts()) {
                shown.merge(opt.value(), 1, Integer::sum);
                check(helper, Language.getInstance().has(opt.key()), opt.key() + " has a name");
            }
        }
        List<ModConfigSpec.ConfigValue<?>> all = new ArrayList<>();
        collect(NookworksConfig.SPEC.getValues(), all);
        collect(NookworksClientConfig.SPEC.getValues(), all);
        collect(NookworksStartupConfig.SPEC.getValues(), all);
        check(helper, all.size() > 50, "the configs' settings read: " + all.size());
        for (ModConfigSpec.ConfigValue<?> value : all) {
            String path = String.join(".", value.getPath());
            if (path.equals("features.keepItems")) {
                continue; // a list of item ids for a pack's own file, not a switch: left to NeoForge's list
            }
            check(helper, shown.getOrDefault(value, 0) == 1, path + " is shown once, shown " + shown.getOrDefault(value, 0));
        }
        helper.succeed();
    }

    private static void stepsInRange(GameTestHelper helper) {
        for (SettingsGroups.Group group : SettingsGroups.groups()) {
            for (SettingsGroups.Opt opt : group.opts()) {
                if (opt.value().getSpec().getRange() == null || !(opt.value().getDefault() instanceof Number def)) {
                    continue;
                }
                List<Double> steps = SettingsGroups.steps(opt);
                ModConfigSpec.Range<?> range = opt.value().getSpec().getRange();
                double min = ((Number) range.getMin()).doubleValue();
                double max = ((Number) range.getMax()).doubleValue();
                check(helper, steps.size() >= 2 && steps.size() <= 60, opt.key() + ": " + steps.size() + " steps");
                check(helper, steps.contains(def.doubleValue()), opt.key() + " can be put back to its default " + def);
                for (double step : steps) {
                    check(helper, step >= min - 1e-9 && step <= max + 1e-9, opt.key() + ": step " + step + " out of " + min + ".." + max);
                    if (opt.value() instanceof ModConfigSpec.IntValue) {
                        check(helper, step == Math.rint(step), opt.key() + ": a whole number, not " + step);
                    }
                }
            }
        }
        helper.succeed();
    }
}
