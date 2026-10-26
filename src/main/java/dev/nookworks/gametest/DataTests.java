package dev.nookworks.gametest;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.jspecify.annotations.Nullable;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.client.HintTooltips;
import dev.nookworks.client.NookworksClientConfig;
import dev.nookworks.compat.jade.JadeConfigs;
import dev.nookworks.compat.jei.InfoPages;
import dev.nookworks.feature.Features;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.registry.ModConditions;
import dev.nookworks.registry.ModCreativeTabs;
import dev.nookworks.registry.ModItems;
import dev.nookworks.sorter.LegacySorterItem;
import dev.nookworks.storage.SortOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * Our data files have to load, not merely exist. A recipe with a bad field is skipped with one line in the log and
 * everything else carries on - the server starts, every other test passes, and the item simply cannot be made in
 * survival. These tests turn that line into a failure.
 */
final class DataTests {
    /** Warnings and errors about us logged while the game started, up to the point the server was ready. */
    private static final List<String> STARTUP_PROBLEMS = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean listening;

    static void register() {
        NookworksGameTests.test("every_data_file_loads", DataTests::everyDataFileLoads);
        NookworksGameTests.test("startup_logs_nothing_about_us", DataTests::startupLogsNothingAboutUs);
        NookworksGameTests.test("every_language_names_everything", DataTests::everyLanguageNamesEverything);
        NookworksGameTests.test("no_lang_file_names_a_key_twice", DataTests::noLangKeyTwice);
        NookworksGameTests.test("every_language_keeps_englishs_placeholders", DataTests::everyLanguageKeepsPlaceholders);
        NookworksGameTests.test("every_shown_key_is_written", DataTests::everyShownKeyIsWritten);
        NookworksGameTests.test("every_item_is_in_a_creative_tab", DataTests::everyItemIsInACreativeTab);
        NookworksGameTests.test("parts_of_the_mod_are_whole", DataTests::partsOfTheModAreWhole);
        NookworksGameTests.test("blocks_keep_their_solidity", DataTests::blocksKeepTheirSolidity);
        NookworksGameTests.test("an_axe_keeps_a_weathering_blocks_state", DataTests::axeKeepsWeatheringState);
    }

    /**
     * The parts a pack can switch off ({@code features.json}): each item named is one of ours, in one part only, and
     * each part has its setting, and what a part or item needs ({@code _needs}) is a part; every recipe that makes an
     * item of a part (or takes one to make another), and every advancement shown with one, is held by that part's
     * condition, so switching it off leaves no way to have the part's items. A recipe that makes something of no part
     * from a part's item is held by nothing: what a world holds keeps working. A tab passes on only what is let through.
     */
    private static void partsOfTheModAreWhole(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Map<Identifier, String> partOf = new HashMap<>();
        Features.PARTS.forEach((part, ids) -> {
            if (NookworksStartupConfig.feature(part) == null) {
                problems.add("no setting for part " + part);
            }
            for (Identifier id : ids) {
                if (!id.getNamespace().equals(Nookworks.MODID) || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
                    problems.add(part + " names no item of ours: " + id);
                }
                String before = partOf.put(id, part);
                if (before != null) {
                    problems.add(id + " is in both " + before + " and " + part);
                }
            }
        });
        Features.NEEDS.forEach((what, needs) -> {
            if (!Features.PARTS.containsKey(what) && !partOf.containsKey(Identifier.tryParse(what))) {
                problems.add("_needs names neither a part nor an item of one: " + what);
            }
            needs.stream().filter(need -> !Features.PARTS.containsKey(need)).forEach(need -> problems.add(what + " needs no part: " + need));
        });
        var resources = helper.getLevel().getServer().getResourceManager();
        for (String folder : List.of("recipe", "advancement")) {
            for (var entry : resources.listResources(folder, id -> id.getNamespace().equals(Nookworks.MODID) && id.getPath().endsWith(".json")).entrySet()) {
                try (var reader = entry.getValue().openAsReader()) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    Set<String> held = new HashSet<>();
                    if (json.has("neoforge:conditions")) {
                        for (var c : json.getAsJsonArray("neoforge:conditions")) {
                            if ("nookworks:feature".equals(c.getAsJsonObject().get("type").getAsString())) {
                                held.add(c.getAsJsonObject().get("feature").getAsString());
                            }
                        }
                    }
                    Set<String> needs = new HashSet<>();
                    if (folder.equals("recipe")) {
                        Identifier result = resultOf(json);
                        if (result == null || partOf.containsKey(result)) {
                            namedItems(json, needs, partOf);
                        }
                    } else if (json.has("display") && !entry.getKey().getPath().startsWith("advancement/recipes/")) {
                        String icon = json.getAsJsonObject("display").getAsJsonObject("icon").get("id").getAsString();
                        String part = partOf.get(Identifier.parse(icon));
                        if (part != null) {
                            needs.add(part);
                        }
                    }
                    needs.removeAll(held);
                    if (!needs.isEmpty()) {
                        problems.add(entry.getKey() + " is not held by " + needs);
                    }
                } catch (Exception e) {
                    problems.add("cannot read " + entry.getKey() + ": " + e);
                }
            }
        }
        // a creative tab passes on only what is let through
        List<ItemStack> shown = new ArrayList<>();
        Features.shown((parameters, output) -> {
            output.accept(Items.STONE);
            output.accept(ModItems.LASER.get());
        }, stack -> !stack.is(ModItems.LASER.get())).accept(null, (stack, visibility) -> shown.add(stack));
        NookworksGameTests.check(helper, shown.size() == 1 && shown.getFirst().is(Items.STONE), "a tab leaves out what is held back");
        NookworksGameTests.check(helper, new ModConditions.Feature("kitchen").test(null) == Features.enabled("kitchen"),
                "the condition follows the part's switch");
        // a part or item that needs another goes with it (with every part on but the one named)
        NookworksGameTests.check(helper, !Features.enabled("barter_terminal", part -> !part.equals("mob_bottle"))
                && Features.enabled("barter_terminal", part -> !part.equals("magnets")), "the Barter Terminal goes with the Mob Bottle");
        NookworksGameTests.check(helper, !Features.enabled(ModItems.ABSORPTION_PAD.get(), part -> !part.equals("magnets"))
                && Features.enabled(ModItems.EXPERIENCE_TANK.get(), part -> !part.equals("magnets")), "the Absorption Pad goes with the Magnet");
        for (String name : NookworksGameTests.switchableTests()) {
            if (BuiltInRegistries.TEST_FUNCTION.getOptional(Nookworks.id(name)).isEmpty()) {
                problems.add("a switchable test that is no test: " + name);
            }
        }
        NookworksGameTests.check(helper, problems.isEmpty(), String.join(" | ", problems));
        helper.succeed();
    }

    /** The item a recipe makes, or null for none named. */
    private static @Nullable Identifier resultOf(JsonObject recipe) {
        if (!recipe.has("result")) {
            return null;
        }
        JsonElement result = recipe.get("result");
        String id = result.isJsonPrimitive() ? result.getAsString()
                : result.isJsonObject() && result.getAsJsonObject().has("id") ? result.getAsJsonObject().get("id").getAsString() : null;
        return id == null ? null : Identifier.tryParse(id);
    }

    /** The parts of every item a recipe names, its result included (not #tags). */
    private static void namedItems(JsonElement json, Set<String> parts, Map<Identifier, String> partOf) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String value = json.getAsString();
            if (value.contains(":") && !value.startsWith("#") && Identifier.tryParse(value) != null && partOf.containsKey(Identifier.parse(value))) {
                parts.add(partOf.get(Identifier.parse(value)));
            }
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(e -> namedItems(e, parts, partOf));
        } else if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(e -> {
                if (!e.getKey().equals("neoforge:conditions") && !e.getKey().equals("type")) {
                    namedItems(e.getValue(), parts, partOf);
                }
            });
        }
    }

    /** Called while the mod is constructed, before any registry or data pack is read. */
    static void listen() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        AbstractAppender appender = new AbstractAppender("NookworksStartupProblems", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (!listening || !event.getLevel().isMoreSpecificThan(Level.WARN) || STARTUP_PROBLEMS.size() > 50) {
                    return;
                }
                String message = event.getMessage().getFormattedMessage();
                boolean ours = message.contains(Nookworks.MODID + ":") || message.contains("dev.nookworks");
                Throwable thrown = event.getThrown();
                for (Throwable t = thrown; t != null && !ours; t = t.getCause()) {
                    for (StackTraceElement frame : t.getStackTrace()) {
                        if (frame.getClassName().startsWith("dev.nookworks") && !frame.getClassName().startsWith("dev.nookworks.gametest")) {
                            ours = true;
                            break;
                        }
                    }
                }
                if (ours) {
                    STARTUP_PROBLEMS.add(event.getLevel() + " [" + event.getLoggerName() + "] " + message);
                }
            }
        };
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        context.updateLoggers();
        listening = true;
        // Once the server is up, anything logged belongs to a test run, not to loading.
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> listening = false);
    }

    /**
     * Every recipe, loot table and advancement file of ours made it into the game. Files guarded by a
     * {@code neoforge:conditions} (other mods' compat) are left out: those may legitimately stay unloaded.
     */
    private static void everyDataFileLoads(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var resources = server.getResourceManager();
        List<String> missing = new ArrayList<>();
        int recipes = 0;
        int tables = 0;
        int advancements = 0;
        for (var entry : resources.listResources("recipe", id -> id.getNamespace().equals(Nookworks.MODID) && id.getPath().endsWith(".json")).entrySet()) {
            if (conditional(entry.getValue())) {
                continue;
            }
            Identifier id = strip(entry.getKey(), "recipe/");
            recipes++;
            if (server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).isEmpty()) {
                missing.add("recipe " + id);
            }
        }
        var lootTables = server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE);
        for (var entry : resources.listResources("loot_table", id -> id.getNamespace().equals(Nookworks.MODID) && id.getPath().endsWith(".json")).entrySet()) {
            if (conditional(entry.getValue())) {
                continue;
            }
            Identifier id = strip(entry.getKey(), "loot_table/");
            tables++;
            if (lootTables.get(ResourceKey.create(Registries.LOOT_TABLE, id)).isEmpty()) {
                missing.add("loot table " + id);
            }
        }
        for (var entry : resources.listResources("advancement", id -> id.getNamespace().equals(Nookworks.MODID) && id.getPath().endsWith(".json")).entrySet()) {
            if (conditional(entry.getValue())) {
                continue;
            }
            Identifier id = strip(entry.getKey(), "advancement/");
            advancements++;
            if (server.getAdvancements().get(id) == null) {
                missing.add("advancement " + id);
            }
        }
        // And each recipe is unlocked in the recipe book by something, or players without JEI never see it.
        Set<Identifier> unlocked = new HashSet<>();
        for (var holder : server.getAdvancements().getAllAdvancements()) {
            holder.value().rewards().recipes().forEach(key -> unlocked.add(key.identifier()));
        }
        for (var holder : server.getRecipeManager().getRecipes()) {
            Identifier id = holder.id().identifier();
            if (id.getNamespace().equals(Nookworks.MODID) && !holder.value().isSpecial() && !unlocked.contains(id)) {
                missing.add("recipe-book unlock for " + id);
            }
        }
        NookworksGameTests.check(helper, recipes > 100 && tables > 50 && advancements > 20,
                "found too few files to trust the check: " + recipes + " recipes, " + tables + " tables, " + advancements + " advancements");
        NookworksGameTests.check(helper, missing.isEmpty(), "failed to load: " + missing);
        helper.succeed();
    }

    /** Nothing of ours logged a warning or an error while the game and the world loaded. */
    private static void startupLogsNothingAboutUs(GameTestHelper helper) {
        List<String> problems;
        synchronized (STARTUP_PROBLEMS) {
            problems = List.copyOf(STARTUP_PROBLEMS);
        }
        NookworksGameTests.check(helper, problems.isEmpty(), problems.size() + " startup problems: " + String.join(" | ", problems));
        helper.succeed();
    }

    /**
     * Every language has English's keys and no others (a key in one file only shows as the raw key - or English - in
     * the rest), and they include a name for every switch our Jade plugin adds (Jade stops a development game's
     * resource loading over one without a name, and shows the raw key elsewhere).
     */
    private static void everyLanguageNamesEverything(GameTestHelper helper) {
        var en = lang("en_us");
        List<String> problems = new ArrayList<>();
        for (String code : LANGUAGES) {
            var other = lang(code);
            en.stream().filter(key -> !other.contains(key)).forEach(key -> problems.add("no " + code + " for " + key));
            other.stream().filter(key -> !en.contains(key)).forEach(key -> problems.add(code + " has " + key + ", English has not"));
        }
        for (Identifier id : JadeConfigs.ALL) {
            String key = JadeConfigs.translationKey(id);
            if (!en.contains(key)) {
                problems.add("Jade switch " + id + " has no name (" + key + ")");
            }
        }
        NookworksGameTests.check(helper, en.size() > 500, "found only " + en.size() + " English keys");
        NookworksGameTests.check(helper, problems.isEmpty(), String.join(" | ", problems));
        helper.succeed();
    }

    /**
     * Every key the game shows a text by has its text, in every language: the names of our blocks, items, entities,
     * liquids, enchantments and tabs, the death messages of our damage, the states and modes the screens build keys
     * from, every setting with its tooltip, JEI's pages and the hints under item names. A key without a text shows on
     * the screen as the bare key, and nothing else would notice.
     */
    private static void everyShownKeyIsWritten(GameTestHelper helper) {
        var en = lang("en_us");
        var others = LANGUAGES.stream().map(DataTests::lang).toList();
        List<String> problems = new ArrayList<>();
        BiConsumer<String, String> ours = (what, key) -> {
            if (key == null) {
                problems.add(what + " has no lang key");
            } else if (!en.contains(key) || others.stream().anyMatch(other -> !other.contains(key))) {
                problems.add(what + " has no text for " + key);
            }
        };
        // a registered thing may borrow its name from vanilla: then the game's own text is the one shown
        BiConsumer<String, String> named = (what, key) -> {
            if (!en.contains(key) && !Language.getInstance().has(key)) {
                problems.add(what + " has no name (" + key + ")");
            }
        };
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(Nookworks.MODID)) {
                named.accept("item " + id, item.getDescriptionId());
            }
        }
        for (var block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getNamespace().equals(Nookworks.MODID)) {
                named.accept("block " + id, block.getDescriptionId());
            }
        }
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id.getNamespace().equals(Nookworks.MODID)) {
                named.accept("entity " + id, type.getDescriptionId());
            }
        }
        for (var type : NeoForgeRegistries.FLUID_TYPES) {
            Identifier id = NeoForgeRegistries.FLUID_TYPES.getKey(type);
            if (id != null && id.getNamespace().equals(Nookworks.MODID)) {
                named.accept("fluid " + id, type.getDescriptionId());
            }
        }
        for (var tab : ModCreativeTabs.CREATIVE_MODE_TABS.getEntries()) {
            ours.accept("creative tab " + tab.getId(), key(tab.get().getDisplayName()));
        }
        var registries = helper.getLevel().registryAccess();
        registries.lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .filter(enchantment -> enchantment.key().identifier().getNamespace().equals(Nookworks.MODID))
                .forEach(enchantment -> ours.accept("enchantment " + enchantment.key().identifier(), key(enchantment.value().description())));
        registries.lookupOrThrow(Registries.DAMAGE_TYPE).listElements()
                .filter(damage -> damage.key().identifier().getNamespace().equals(Nookworks.MODID))
                .forEach(damage -> {
                    ours.accept("damage " + damage.key().identifier(), "death.attack." + damage.value().msgId());
                    ours.accept("damage " + damage.key().identifier(), "death.attack." + damage.value().msgId() + ".player");
                });

        for (var status : MachineStatus.values()) {
            ours.accept("machine status " + status, status.translationKey());
        }
        for (var mode : RedstoneMode.values()) {
            ours.accept("redstone mode " + mode, mode.translationKey());
        }
        for (var kind : GuiTank.Kind.values()) {
            ours.accept("tank " + kind, kind.translationKey());
        }
        for (var order : SortOrder.values()) {
            ours.accept("sort order " + order, order.translationKey());
        }
        for (var activation : NookworksConfig.Activation.values()) {
            ours.accept("activation " + activation, key(activation.getTranslatedName()));
        }
        for (ModConfigSpec spec : List.of(NookworksConfig.SPEC, NookworksStartupConfig.SPEC,
                NookworksClientConfig.SPEC)) {
            settings(spec, spec.getValues(), List.of(), ours);
        }
        InfoPages.each((item, text) -> ours.accept("JEI page on " + BuiltInRegistries.ITEM.getKey(item), key(text)));
        HintTooltips.keys().forEach(key -> ours.accept("hint", key));
        NookworksGameTests.check(helper, problems.isEmpty(), String.join(" | ", problems));
        helper.succeed();
    }

    /** Each setting has a name and a tooltip in the config screen, each section a name. */
    private static void settings(ModConfigSpec spec, UnmodifiableConfig values, List<String> path, BiConsumer<String, String> ours) {
        for (UnmodifiableConfig.Entry entry : values.entrySet()) {
            List<String> at = new ArrayList<>(path);
            at.add(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof UnmodifiableConfig section) {
                ours.accept("setting section " + String.join(".", at), spec.getLevelTranslationKey(at));
                settings(spec, section, at, ours);
            } else if (value instanceof ModConfigSpec.ConfigValue<?> setting) {
                String key = setting.getSpec().getTranslationKey();
                ours.accept("setting " + String.join(".", at), key);
                if (key != null) {
                    ours.accept("setting " + String.join(".", at), key + ".tooltip");
                }
            }
        }
    }

    private static String key(Component text) {
        return text.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }

    /**
     * Everything of ours is in one of our creative tabs, as every new block is to be: the tabs are where a player
     * in creative, and JEI's list, find what the mod has. The old tiered sorters are made no more and stay out.
     */
    private static void everyItemIsInACreativeTab(GameTestHelper helper) {
        var level = helper.getLevel();
        var parameters = new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess());
        Set<Item> shown = new HashSet<>();
        for (var tab : ModCreativeTabs.CREATIVE_MODE_TABS.getEntries()) {
            tab.get().buildContents(parameters);
            tab.get().getDisplayItems().forEach(stack -> shown.add(stack.getItem()));
        }
        List<String> missing = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(Nookworks.MODID) && !shown.contains(item)
                    && !(item instanceof LegacySorterItem) && Features.enabled(item)) {  // a part switched off is in no tab
                missing.add(id.toString());
            }
        }
        NookworksGameTests.check(helper, missing.isEmpty(), "in no creative tab: " + String.join(", ", missing));
        helper.succeed();
    }

    /**
     * No language file has a key twice: the game keeps the last one and drops the first without a word, so a text added
     * under a key already used takes the old one's place wherever that is shown (r6: a door's "set it into a wall"
     * read as another message, its "%s" bare).
     */
    private static void noLangKeyTwice(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String code : ALL_LANGUAGES) {
            String path = "/assets/" + Nookworks.MODID + "/lang/" + code + ".json";
            try (var in = Nookworks.class.getResourceAsStream(path);
                    var reader = new com.google.gson.stream.JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                Set<String> seen = new java.util.HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    reader.skipValue();
                    if (!seen.add(key)) {
                        problems.add(code + ": " + key);
                    }
                }
            } catch (IOException e) {
                problems.add(code + ": " + e);
            }
        }
        NookworksGameTests.check(helper, problems.isEmpty(), "keys twice: " + problems);
        helper.succeed();
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?([sd%])");

    /**
     * Every translation takes the arguments its English text takes, has as many line breaks and the same colour codes: a
     * lost %s drops the value it stands for, an extra one shows on the screen as "%s" (or the whole text falls back to
     * its key), and a line break more or less breaks a JEI page or a tooltip where English does not. A translation may
     * name an argument by number (%1$s) to put it elsewhere, or twice.
     */
    private static void everyLanguageKeepsPlaceholders(GameTestHelper helper) {
        Map<String, String> en = texts("en_us");
        List<String> problems = new ArrayList<>();
        for (String code : LANGUAGES) {
            texts(code).forEach((key, text) -> {
                String english = en.get(key);
                if (english == null) {
                    return;  // every_language_names_everything reports it
                }
                if (!arguments(text).equals(arguments(english))) {
                    problems.add(code + " " + key + " takes " + arguments(text) + ", English " + arguments(english));
                }
                if (text.chars().filter(c -> c == '\n').count() != english.chars().filter(c -> c == '\n').count()) {
                    problems.add(code + " " + key + " breaks its lines otherwise than English");
                }
                if (!colours(text).equals(colours(english))) {
                    problems.add(code + " " + key + " has other colour codes than English");
                }
            });
        }
        NookworksGameTests.check(helper, problems.isEmpty(), String.join(" | ", problems));
        helper.succeed();
    }

    /** The arguments a text takes, by number, then a "%%" for each percent sign it writes. */
    private static List<String> arguments(String text) {
        Set<Integer> numbers = new TreeSet<>();
        int next = 0;
        int percent = 0;
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            if (m.group(2).equals("%")) {
                percent++;
            } else {
                numbers.add(m.group(1) != null ? Integer.parseInt(m.group(1)) : ++next);
            }
        }
        List<String> out = new ArrayList<>(numbers.stream().map(String::valueOf).toList());
        out.addAll(Collections.nCopies(percent, "%%"));
        return out;
    }

    private static List<String> colours(String text) {
        List<String> codes = new ArrayList<>();
        for (int i = text.indexOf('\u00a7'); i >= 0 && i + 1 < text.length(); i = text.indexOf('\u00a7', i + 1)) {
            codes.add(text.substring(i, i + 2));
        }
        Collections.sort(codes);
        return codes;
    }

    /** A lang file of ours, key by key. */
    static Map<String, String> texts(String code) {
        String path = "/assets/" + Nookworks.MODID + "/lang/" + code + ".json";
        try (var in = Nookworks.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no " + path);
            }
            Map<String, String> texts = new HashMap<>();
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
                    .forEach(e -> texts.put(e.getKey(), e.getValue().getAsString()));
            return texts;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /**
     * Every language the mod is in besides English: each names everything English does, and nothing else. The regional
     * copies (zh_hk, the Spanish of Latin America, pt_pt) are tools/gen_lang_copies.py's copies of another file: the game
     * falls back from them to English only, never to their sibling.
     */
    private static final List<String> LANGUAGES = List.of("ja_jp", "zh_cn", "zh_tw", "zh_hk", "ko_kr", "es_es", "es_mx",
            "es_ar", "es_cl", "es_ec", "es_uy", "es_ve", "ru_ru", "pt_br", "pt_pt");
    /** English and the rest: what a test of every language goes through. */
    static final List<String> ALL_LANGUAGES = Stream.concat(Stream.of("en_us"), LANGUAGES.stream()).toList();

    static Set<String> lang(String code) {
        String path = "/assets/" + Nookworks.MODID + "/lang/" + code + ".json";
        try (var in = Nookworks.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no " + path);
            }
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return Set.copyOf(json.keySet());
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /**
     * Whether a file may stay unloaded: it has a condition other than one on a part of the mod that is on (another
     * mod's compat, a start-up setting). A file held only by parts that are on - most of ours - has to load.
     */
    private static boolean conditional(Resource resource) {
        try (var reader = resource.openAsReader()) {
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject() || !json.getAsJsonObject().has("neoforge:conditions")) {
                return false;
            }
            for (var condition : json.getAsJsonObject().getAsJsonArray("neoforge:conditions")) {
                var object = condition.getAsJsonObject();
                boolean partOn = "nookworks:feature".equals(object.get("type").getAsString())
                        && Features.enabled(object.get("feature").getAsString());
                if (!partOn) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;   // unreadable: let the lookup report it
        }
    }

    /**
     * The 26.3 axe trap (HANDOFF, "Things that cost time"): for an entry of neoforge:oxidizables or neoforge:waxables,
     * NeoForge makes the axe's way back itself unless the entry says {@code generate_block_transform: false} - and the
     * transform it makes sets the default state of the block it goes back to, so a hanging lantern scraped fell, and a
     * sheet scraped lay down. Every entry of ours whose block has properties says false and has a neoforge:transformables
     * entry for the axe that copies them: checked here by scraping each block an axe works on - every property moved off
     * its default - and finding the block it goes back to with the same properties.
     */
    private static void axeKeepsWeatheringState(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Map<Block, Block> scraped = new LinkedHashMap<>(); // what an axe works on -> what it is to become
        BuiltInRegistries.BLOCK.getDataMap(NeoForgeDataMaps.OXIDIZABLES).forEach((key, oxidizable) -> {
            Block from = BuiltInRegistries.BLOCK.getValueOrThrow(key);
            if (ours(from)) {
                if (!from.getStateDefinition().getProperties().isEmpty() && oxidizable.generateBlockTransform()) {
                    problems.add(key.identifier() + " (oxidizables) has properties and lets NeoForge make its axe transform");
                }
                scraped.put(oxidizable.nextOxidationStage(), from);
            }
        });
        BuiltInRegistries.BLOCK.getDataMap(NeoForgeDataMaps.WAXABLES).forEach((key, waxable) -> {
            Block from = BuiltInRegistries.BLOCK.getValueOrThrow(key);
            if (ours(from)) {
                if (!from.getStateDefinition().getProperties().isEmpty() && waxable.generateBlockTransform()) {
                    problems.add(key.identifier() + " (waxables) has properties and lets NeoForge make its axe transform");
                }
                scraped.put(waxable.waxed(), from);
            }
        });
        NookworksGameTests.check(helper, !scraped.isEmpty(), "our weathering blocks are found in the data maps");
        BlockPos at = new BlockPos(3, 1, 3);
        // stone over it as under it, so that what hangs has its ceiling (the transform's state is fitted to its neighbours)
        helper.setBlock(at.above(), net.minecraft.world.level.block.Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        scraped.forEach((target, back) -> {
            BlockState state = target.defaultBlockState();
            for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
                state = state.cycle(property);
            }
            helper.setBlock(at, state);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            BlockPos absolute = helper.absolutePos(at);
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
            BlockState after = helper.getBlockState(at);
            String name = BuiltInRegistries.BLOCK.getKey(target).toString();
            if (!after.is(back)) {
                problems.add("an axe on " + name + " gave " + after + ", not " + BuiltInRegistries.BLOCK.getKey(back));
                return;
            }
            for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
                if (after.hasProperty(property) && !after.getValue(property).equals(state.getValue(property))) {
                    problems.add("an axe on " + name + " lost its " + property.getName() + " (" + state.getValue(property)
                            + " -> " + after.getValue(property) + ")");
                }
            }
        });
        NookworksGameTests.check(helper, problems.isEmpty(), String.join(" | ", problems));
        helper.succeed();
    }

    private static boolean ours(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Nookworks.MODID);
    }

    /**
     * 26.3 reads what blocks motion (suffocates, stands in the rain's way, is a floor to teleport to, turns a flow) and
     * what a flood washes away from two block tags, where 26.2 worked both out from a block's shape. Every block of ours
     * keeps what 26.2 made of it: solid in any state (BlockState#isSolid, the old reckoning), in #minecraft:blocks_motion;
     * never solid and holding no liquid, in #minecraft:washed_away_by_fluids. Signs are left to vanilla's own tags.
     */
    private static void blocksKeepTheirSolidity(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(Nookworks.MODID) || block instanceof SignBlock) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            if (block.getStateDefinition().getPossibleStates().stream().anyMatch(BlockState::isSolid)) {
                if (!state.is(BlockTags.BLOCKS_MOTION)) {
                    wrong.add(id + " is solid, but not in #minecraft:blocks_motion");
                }
            } else if (!(block instanceof LiquidBlockContainer) && !state.is(BlockTags.WASHED_AWAY_BY_FLUIDS)) {
                wrong.add(id + " is not solid, but not in #minecraft:washed_away_by_fluids");
            }
        }
        NookworksGameTests.check(helper, wrong.isEmpty(), String.join(" | ", wrong));
        helper.succeed();
    }

    private static Identifier strip(Identifier file, String folder) {
        String path = file.getPath();
        return Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring(folder.length(), path.length() - ".json".length()));
    }

    private DataTests() {}
}
