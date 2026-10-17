package dev.nookworks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.nookworks.feature.Features;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings read once, as the game starts ({@code nookworks-startup.toml}): what they change is fixed then - what an
 * item is (whether it can be eaten) and which recipes load - so they cannot follow the world's settings. A server and
 * its players should have the same. The parts of the mod ({@code [features]}) are the modpack's say over every world: a
 * world can leave out more of them when it is made ({@link dev.nookworks.feature.WorldFeatures}), never bring back one
 * switched off here.
 */
public final class NookworksStartupConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FLOUR_BAKERY;
    public static final ModConfigSpec.BooleanValue EDIBLE_RAW_FOODS;
    /** Whether each new world chooses its parts on the Create World screen ({@link dev.nookworks.feature.WorldFeatures}). */
    public static final ModConfigSpec.BooleanValue WORLD_MENU;
    /** Each part of the mod a pack can switch off ({@link dev.nookworks.feature.Features}), by its name. */
    private static final Map<String, ModConfigSpec.BooleanValue> FEATURES = new LinkedHashMap<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        FLOUR_BAKERY = builder.comment("Bread, cookies, cake and pumpkin pie are made of flour, as they are: bread from dough that",
                "is left to rise and then baked. Off, the vanilla recipes from wheat come back (bread dough stays, as another way).")
                .translation("nookworks.configuration.flourBakery").define("flourBakery", true);
        EDIBLE_RAW_FOODS = builder.comment("Things that can be eaten raw can be: kelp, sugar cane, sugar, salt, short grass, mushrooms",
                "and fungi (the red ones are poisonous), honeycomb, cocoa beans.")
                .translation("nookworks.configuration.edibleRawFoods").define("edibleRawFoods", true);
        WORLD_MENU = builder.comment("Each new world chooses which parts below it has: a Nookworks button on the Create World screen",
                "(its More tab), its choice kept in the world's folder (serverconfig/nookworks-features.json) and changed only",
                "there, with the world closed. A part switched off below is off in every world, and not offered. Off, there is",
                "no button and every world has the parts below alone, its own choice ignored.")
                .translation("nookworks.configuration.worldMenu").define("worldMenu", true);
        builder.comment("Parts of the mod a modpack can switch off, in every world (a world can leave out more when it is made:",
                "worldMenu above). Off, a part's items are made by no recipe, sit in no creative tab, are hidden in JEI and come",
                "out of no sifting or trade, and its advancements are gone; what a world already holds stays and works. The",
                "kitchen off takes the flour bakery with it (vanilla's bread, cake, cookies and pie stay).")
                .translation("nookworks.configuration.features").push("features");
        for (String part : Features.PARTS.keySet()) {
            FEATURES.put(part, builder.translation("nookworks.configuration.features." + part).define(part, true));
        }
        builder.pop();
        SPEC = builder.build();
    }

    /**
     * The start-up settings a player's own have to match, by the name recipe conditions use for them: what a server tells
     * its players it plays with. The parts of the mod are not among them: a player goes by the server's
     * ({@link dev.nookworks.feature.WorldFeatures#heard}), whatever their own settings say.
     */
    public static Map<String, Boolean> values() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("flour_bakery", FLOUR_BAKERY.getAsBoolean());
        values.put("edible_raw_foods", EDIBLE_RAW_FOODS.getAsBoolean());
        return values;
    }

    /**
     * What to tell a player whose own start-up settings are not the server's, or nothing when they match. Neither side
     * can change them while it runs - they decide what an item is (edible or not) and which recipes load - so a client
     * that differs eats, or shows recipes, as the server does not; it is told which settings, and how to set them.
     */
    public static Optional<Component> mismatch(Map<String, Boolean> server) {
        List<Component> differ = new ArrayList<>();
        values().forEach((name, mine) -> {
            Boolean theirs = server.get(name);
            if (theirs != null && !theirs.equals(mine)) {
                differ.add(Component.translatable("nookworks.startup_mismatch.setting",
                        Component.translatable(byName(name).getSpec().getTranslationKey()),
                        Component.translatable(theirs ? "options.on" : "options.off")));
            }
        });
        if (differ.isEmpty()) {
            return Optional.empty();
        }
        MutableComponent list = differ.getFirst().copy();
        for (int i = 1; i < differ.size(); i++) {
            list.append(Component.translatable("nookworks.list.separator")).append(differ.get(i));
        }
        return Optional.of(Component.translatable("nookworks.startup_mismatch", list)
                .withStyle(ChatFormatting.GOLD));
    }

    /** A setting by the name recipe conditions use for it ({@code nookworks:setting}), or null for none. */
    public static ModConfigSpec.BooleanValue byName(String name) {
        if (name.startsWith("feature.")) {
            return feature(name.substring("feature.".length()));
        }
        return switch (name) {
            case "flour_bakery" -> FLOUR_BAKERY;
            case "edible_raw_foods" -> EDIBLE_RAW_FOODS;
            default -> null;
        };
    }

    /** The setting that switches the part {@code name} on or off, or null for no such part. */
    public static ModConfigSpec.BooleanValue feature(String name) {
        return FEATURES.get(name);
    }

    /** Whether the bakery's recipes load: its own setting, with the kitchen whose flour and dough it is made of. */
    public static boolean flourBakery() {
        return FLOUR_BAKERY.getAsBoolean() && Features.enabled("kitchen");
    }

    private NookworksStartupConfig() {}
}
