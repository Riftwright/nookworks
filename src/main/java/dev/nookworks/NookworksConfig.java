package dev.nookworks;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/** Server-side gameplay settings (per world, synced to clients). Editable in Mods > Nookworks > Config. */
public final class NookworksConfig {
    public enum Activation implements TranslatableEnum {
        /** Chain breaking unless the player is sneaking (default). */
        NOT_SNEAKING,
        /** Chain breaking only while sneaking. */
        SNEAKING,
        ALWAYS;

        public boolean allows(boolean sneaking) {
            return switch (this) {
                case NOT_SNEAKING -> !sneaking;
                case SNEAKING -> sneaking;
                case ALWAYS -> true;
            };
        }

        /** Button label in the config screen; the config file keeps the constant name. */
        @Override
        public Component getTranslatedName() {
            return Component.translatable(key("activation." + name().toLowerCase(Locale.ROOT)));
        }
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    /**
     * What the config screen's Add button puts at the end of a list of mod ids, for the player to type the id over. A
     * list defined without one has no Add button at all, though the timber list's tooltip says to add ids.
     */
    private static final Supplier<String> NEW_MOD_ID = () -> "";

    public static final ModConfigSpec.BooleanValue TIMBER_ENABLED;
    public static final ModConfigSpec.EnumValue<Activation> TIMBER_ACTIVATION;
    public static final ModConfigSpec.IntValue TIMBER_MAX_BLOCKS;
    public static final ModConfigSpec.BooleanValue TIMBER_STAND_DOWN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TIMBER_CONFLICTS;
    public static final ModConfigSpec.BooleanValue VEIN_ENABLED;
    public static final ModConfigSpec.EnumValue<Activation> VEIN_ACTIVATION;
    public static final ModConfigSpec.IntValue VEIN_MAX_BLOCKS;
    public static final ModConfigSpec.BooleanValue VEIN_STAND_DOWN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> VEIN_CONFLICTS;
    public static final ModConfigSpec.BooleanValue HAMMER_ENABLED;
    public static final ModConfigSpec.EnumValue<Activation> HAMMER_ACTIVATION;
    public static final ModConfigSpec.BooleanValue HAMMER_STAND_DOWN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HAMMER_CONFLICTS;
    public static final ModConfigSpec.BooleanValue MAGNET_ENABLED;
    public static final ModConfigSpec.BooleanValue MAGNET_STAND_DOWN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MAGNET_CONFLICTS;
    public static final ModConfigSpec.BooleanValue WAND_ENABLED;
    public static final ModConfigSpec.BooleanValue WAND_STAND_DOWN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WAND_CONFLICTS;
    public static final ModConfigSpec.IntValue ELEVATOR_RANGE;
    public static final ModConfigSpec.IntValue ITEM_WARP_RANGE;
    public static final ModConfigSpec.IntValue ENERGY_CABLE_REACH;
    public static final ModConfigSpec.IntValue SIFTER_TICKS;
    public static final ModConfigSpec.BooleanValue PROTECT_TOOLS;
    public static final ModConfigSpec.IntValue STORAGE_BREAK_LIMIT;
    public static final ModConfigSpec.BooleanValue RAW_FOOD_SICKNESS;
    public static final ModConfigSpec.BooleanValue CAMPFIRES_PLACED_UNLIT;
    public static final ModConfigSpec.BooleanValue RECIPE_CHOICE;
    public static final ModConfigSpec.BooleanValue PLAYER_PHOTOS;
    public static final ModConfigSpec.BooleanValue FAST_LEAF_DECAY;
    public static final ModConfigSpec.DoubleValue TRADER_VILLAGE_MAP_CHANCE;
    public static final ModConfigSpec.DoubleValue MACHINE_ENERGY;
    public static final ModConfigSpec.DoubleValue MACHINE_TIME;
    public static final ModConfigSpec.DoubleValue GENERATOR_OUTPUT;

    static {
        BUILDER.translation(key("timber")).push("timber");
        TIMBER_ENABLED = BUILDER.comment("Fell a whole tree when a log is broken with an axe.")
                .translation(key("timber.enabled")).define("enabled", true);
        TIMBER_ACTIVATION = BUILDER.comment("When tree felling triggers.")
                .translation(key("timber.activation")).defineEnum("activation", Activation.NOT_SNEAKING);
        TIMBER_MAX_BLOCKS = BUILDER.comment("Maximum extra logs broken at once, by an axe or a Laser.")
                .translation(key("timber.maxBlocks")).defineInRange("maxBlocks", 256, 1, 1024);
        FAST_LEAF_DECAY = BUILDER.comment("Leaves of a felled tree decay within seconds (normal drops, saplings included).")
                .translation(key("timber.fastLeafDecay")).define("fastLeafDecay", true);
        TIMBER_STAND_DOWN = BUILDER.comment("Turn tree felling off when another tree-felling mod is loaded.")
                .translation(key("timber.standDownIfConflicting")).define("standDownIfConflicting", true);
        TIMBER_CONFLICTS = BUILDER.comment("Mod ids that also fell trees. Add or remove ids as the pack needs.")
                .translation(key("timber.conflictingMods")).defineListAllowEmpty("conflictingMods",
                        List.of("treechop", "fallingtree", "treeharvester", "ftbultimine", "treefeller", "chopdownupdated"),
                        NEW_MOD_ID, o -> o instanceof String);
        BUILDER.pop();

        BUILDER.translation(key("veinMining")).push("veinMining");
        VEIN_ENABLED = BUILDER.comment("Mine a whole ore vein when an ore is broken with a suitable pickaxe.")
                .translation(key("veinMining.enabled")).define("enabled", true);
        VEIN_ACTIVATION = BUILDER.comment("When vein mining triggers.")
                .translation(key("veinMining.activation")).defineEnum("activation", Activation.NOT_SNEAKING);
        VEIN_MAX_BLOCKS = BUILDER.comment("Maximum extra ore blocks broken at once.")
                .translation(key("veinMining.maxBlocks")).defineInRange("maxBlocks", 64, 1, 512);
        VEIN_STAND_DOWN = BUILDER.comment("Turn vein mining off when another vein-mining mod is loaded.")
                .translation(key("veinMining.standDownIfConflicting")).define("standDownIfConflicting", true);
        VEIN_CONFLICTS = BUILDER.comment("Mod ids that also mine whole veins.")
                .translation(key("veinMining.conflictingMods")).defineListAllowEmpty("conflictingMods",
                        List.of("veinmining", "veinminer", "oreexcavation", "ftbultimine"),
                        NEW_MOD_ID, o -> o instanceof String);
        BUILDER.pop();

        BUILDER.translation(key("hammer")).push("hammer");
        HAMMER_ENABLED = BUILDER.comment("Hammers mine a 3x3 area facing the player.")
                .translation(key("hammer.enabled")).define("enabled", true);
        HAMMER_ACTIVATION = BUILDER.comment("When 3x3 mining triggers.")
                .translation(key("hammer.activation")).defineEnum("activation", Activation.NOT_SNEAKING);
        HAMMER_STAND_DOWN = BUILDER.comment("Turn 3x3 mining off when another hammer/AOE-mining mod is loaded. The hammer still breaks one block.")
                .translation(key("hammer.standDownIfConflicting")).define("standDownIfConflicting", true);
        HAMMER_CONFLICTS = BUILDER.comment("Mod ids that also mine a 3x3 (or similar) with hammers.")
                .translation(key("hammer.conflictingMods")).defineListAllowEmpty("conflictingMods",
                        List.of("justhammers", "hammers", "vanilla_hammers", "ftbultimine"),
                        NEW_MOD_ID, o -> o instanceof String);
        BUILDER.pop();

        BUILDER.translation(key("magnet")).push("magnet");
        MAGNET_ENABLED = BUILDER.comment("Magnets switched on, anywhere in your inventory, pull dropped items and XP orbs.",
                        "The Magnet Chest works either way.")
                .translation(key("magnet.enabled")).define("enabled", true);
        MAGNET_STAND_DOWN = BUILDER.comment("Turn the Magnets off when another item-magnet mod is loaded (the Magnet Chest stays).")
                .translation(key("magnet.standDownIfConflicting")).define("standDownIfConflicting", true);
        MAGNET_CONFLICTS = BUILDER.comment("Mod ids that also pull dropped items to the player.")
                .translation(key("magnet.conflictingMods")).defineListAllowEmpty("conflictingMods",
                        // (Item Collectors puts a collector down; it has no magnet to hold, so ours has nothing to give way to)
                        List.of("simplemagnets", "magnet", "sophisticatedbackpacks", "botania", "actuallyadditions", "cyclic"),
                        NEW_MOD_ID, o -> o instanceof String);
        BUILDER.pop();

        BUILDER.translation(key("buildingWand")).push("buildingWand");
        WAND_ENABLED = BUILDER.comment("The Building Wand spreads a clicked block across a flat face.")
                .translation(key("buildingWand.enabled")).define("enabled", true);
        WAND_STAND_DOWN = BUILDER.comment("Turn the Building Wand off when another building-wand/gadgets mod is loaded.")
                .translation(key("buildingWand.standDownIfConflicting")).define("standDownIfConflicting", true);
        WAND_CONFLICTS = BUILDER.comment("Mod ids that also place many blocks in one click.")
                .translation(key("buildingWand.conflictingMods")).defineListAllowEmpty("conflictingMods",
                        List.of("buildinggadgets2", "buildinggadgets", "constructionwand"),
                        NEW_MOD_ID, o -> o instanceof String);
        BUILDER.pop();

        BUILDER.translation(key("machines")).push("machines");
        MACHINE_ENERGY = BUILDER.comment("Multiplies the power every machine of ours uses: a tick's work, a plant, a repair, a felled tree, a",
                "controlled spawner. 1 is as made; 2 makes them twice as hungry, for a pack whose other mods make power cheaply.")
                .translation(key("machines.energyUse")).defineInRange("energyUse", 1.0, 0.1, 10.0);
        MACHINE_TIME = BUILDER.comment("Multiplies how long our machines take over each job. A job costs the same power however long it",
                "takes: slower, they draw less a tick. The Sifter has a setting of its own (sifterTicks).")
                .translation(key("machines.workTime")).defineInRange("workTime", 1.0, 0.1, 10.0);
        GENERATOR_OUTPUT = BUILDER.comment("Multiplies the power a Generator or Diesel Generator makes a tick, from any fuel.")
                .translation(key("machines.generatorOutput")).defineInRange("generatorOutput", 1.0, 0.1, 10.0);
        BUILDER.pop();

        ELEVATOR_RANGE = BUILDER.comment("How far (in blocks) an elevator looks for the next one above or below.")
                .translation(key("elevatorRange")).defineInRange("elevatorRange", 64, 2, 384);

        ITEM_WARP_RANGE = BUILDER.comment("How far (in blocks) a sender looks for a receiver: an Item Warp Sender above or below, a Horizontal Item Warp Sender straight ahead.")
                .translation(key("itemWarpRange")).defineInRange("itemWarpRange", 64, 2, 384);

        ENERGY_CABLE_REACH = BUILDER.comment("How many Energy Cables one network is followed through. Machines further along get no power,",
                "and a Generator on it says so on its screen. A longer reach costs more time each time a network is walked,",
                "which is when something on it changes, and every second or two.")
                .translation(key("energyCableReach")).defineInRange("energyCableReach", 512, 64, 8192);

        SIFTER_TICKS = BUILDER.comment("Ticks a hopper-fed Sifter needs per block (20 ticks = 1 second); twice as many through a Brass Mesh.",
                        "Hand sifting is instant.")
                .translation(key("sifterTicks")).defineInRange("sifterTicks", 20, 1, 1200);

        PROTECT_TOOLS = BUILDER.comment("Stop chain breaking before the tool would break.")
                .translation(key("protectTools")).define("protectTools", true);

        STORAGE_BREAK_LIMIT = BUILDER.comment("A Storage Barrel that would spill more stacks than this cannot be broken by hand",
                "(empty it first): its contents drop when it is broken, and tens of thousands of item entities at once",
                "would stall the server. Taken away by anything else (a block breaker, an explosion, a /setblock or /fill",
                "with destroy), such a barrel leaves as one item holding everything, as a shulker box does; a barrel under",
                "the limit spills. A command that replaces it without destroy takes its contents with it, as with any chest.")
                .translation(key("storageBreakStackLimit")).defineInRange("storageBreakStackLimit", 256, 0, 1_000_000);

        RAW_FOOD_SICKNESS = BUILDER.comment("Raw meat and fish can make you ill a while later, as food poisoning does: raw chicken always,",
                "pork half the time, beef, mutton and rabbit a quarter (milder), raw fish one time in twenty (a parasite). Cook it",
                "first. How likely, for which food, is the nookworks:food_poisoning item data map.")
                .translation(key("rawFoodSickness")).define("rawFoodSickness", true);

        CAMPFIRES_PLACED_UNLIT = BUILDER.comment("A campfire or soul campfire a player places starts out unlit, as a fire laid and not yet lit:",
                "flint and steel, a fire charge or a burning arrow lights it. Off, it is lit as soon as it is placed, as in",
                "vanilla. Campfires already in the world are left as they are.")
                .translation(key("campfiresPlacedUnlit")).define("campfiresPlacedUnlit", true);

        RECIPE_CHOICE = BUILDER.comment("When a crafting grid matches recipes that make different things (two mods' recipes for the same",
                "ingredients), a button by the result lets the player pick which to make, and the pick is remembered. Off by itself",
                "when Polymorph is installed, which does the same.")
                .translation(key("recipeChoice")).define("recipeChoice", true);

        PLAYER_PHOTOS = BUILDER.comment("Cameras take photos, which players' games send the server and the server shows others on posters. Off,",
                "cameras take none and the server sends no photo to anyone: posters of them show blank paper. An operator can take",
                "one photo out with /nookworks photo remove <number> (the number is in a poster's tooltip with F3+H).")
                .translation(key("playerPhotos")).define("playerPhotos", true);

        TRADER_VILLAGE_MAP_CHANCE = BUILDER.comment("How likely a Wandering Trader is to sell a map to the nearest village to a player who has never",
                "been in one (a cartographer, who would sell one, lives in a village). 0 never; 1 every trader. The price is the",
                "trade nookworks:wandering_trader/village_map.")
                .translation(key("traderVillageMapChance")).defineInRange("traderVillageMapChance", 0.5, 0.0, 1.0);
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static String key(String path) {
        return Nookworks.MODID + ".configuration." + path;
    }

    private NookworksConfig() {}
}
