package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.nookworks.NookworksNetwork;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.feature.Features;
import dev.nookworks.feature.WorldFeatures;
import dev.nookworks.registry.ModConditions;
import dev.nookworks.registry.ModCreativeTabs;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Each world's own choice of the parts of the mod (r8, the user: "ワールド生成のときに選べるようなメニュー"): kept in the
 * world's folder and read back, seen by the recipe and advancement conditions of the world whose data packs load, under
 * the start-up config's say, which a pack can also use to have no choice at all. Every test puts what it changed back
 * before it ends - the choice, the settings - so no other test sees it; the Create World screen itself is the client's,
 * looked at in the game.
 */
final class R8WorldFeaturesTests {
    private R8WorldFeaturesTests() {}

    static void register() {
        NookworksGameTests.test("a_worlds_choice_of_parts_is_written_into_its_folder_and_read_back", R8WorldFeaturesTests::choiceIsSavedAndReadBack);
        NookworksGameTests.test("a_part_a_world_left_out_has_no_recipe_there", R8WorldFeaturesTests::leftOutPartHasNoRecipe);
        NookworksGameTests.test("the_start_up_config_is_over_every_worlds_choice", R8WorldFeaturesTests::startUpConfigIsOverTheWorld);
        NookworksGameTests.test("a_pack_can_do_without_the_choice_per_world", R8WorldFeaturesTests::packCanTurnTheChoiceOff);
        NookworksGameTests.test("players_go_by_the_parts_of_the_servers_world", R8WorldFeaturesTests::playersFollowTheServer);
        NookworksGameTests.test("a_worlds_parts_change_in_game_for_its_next_opening", R8WorldFeaturesTests::partsChangeInGame);
    }

    /**
     * The world's parts changed in a world from the mod's settings (the user, 2026-10-31): a choice sent is written with
     * every part named, one not sent left on, and read back as the world's choice for its next opening - not put in
     * force now. A player who is neither the world's single player owner nor an operator may look but not change it
     * ({@code WorldPartsEditing.set} checks the same before it writes). The payload carries the parts whole.
     */
    private static void partsChangeInGame(GameTestHelper helper) {
        inTempWorld(helper, world -> {
            try {
                WorldFeatures.Choice before = WorldFeatures.current();
                var written = dev.nookworks.feature.WorldPartsEditing.save(world, Map.of("laser", false, "recycling", false));
                check(helper, written.keySet().equals(Features.PARTS.keySet()) && !written.get("laser") && written.get("kitchen"),
                        "every part written, one not sent on: " + written);
                var read = WorldFeatures.read(world);
                check(helper, read != null && !read.parts().get("laser") && !read.parts().get("recycling") && read.parts().get("storage"),
                        "it is the world's choice when it next opens: " + read);
                check(helper, WorldFeatures.current() == before, "and not put in force while the world is open");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        var player = (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        check(helper, !dev.nookworks.feature.WorldPartsEditing.mayChange(player), "a player who is no operator may not change them");
        // the server's own start-up config goes with it: a player whose game switches a part off is shown the server's (r19)
        var view = dev.nookworks.feature.WorldPartsEditing.view(player);
        check(helper, view.offered().keySet().equals(Features.PARTS.keySet())
                && Features.PARTS.keySet().stream().allMatch(part -> view.offered().get(part) == WorldFeatures.offered(part))
                && view.menu() == WorldFeatures.menuShown() && !view.editable(), "what the server allows is shown, as it is there: " + view);
        var payload = new NookworksNetwork.WorldPartsFile(new dev.nookworks.feature.WorldPartsEditing.View(
                Map.of("laser", false, "kitchen", true), Map.of("kitchen", true, "generators", false), true, true));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        NookworksNetwork.WorldPartsFile.STREAM_CODEC.encode(buffer, payload);
        check(helper, NookworksNetwork.WorldPartsFile.STREAM_CODEC.decode(buffer).equals(payload), "the file's parts reach the client whole");
        helper.succeed();
    }

    /**
     * The file holds every part, the left-out ones false, and reads back as written; a part missing from it is on, a
     * name of no part or a value that is not true or false is passed over, and a broken file - or none - is no choice.
     */
    private static void choiceIsSavedAndReadBack(GameTestHelper helper) {
        inTempWorld(helper, world -> {
            try {
                check(helper, WorldFeatures.read(world) == null, "a world without the file has no choice");
                WorldFeatures.write(world, Map.of("kitchen", false, "laser", false));
                String text = Files.readString(world.resolve(WorldFeatures.FILE), StandardCharsets.UTF_8);
                var json = JsonParser.parseString(text).getAsJsonObject();
                check(helper, Features.PARTS.keySet().stream().allMatch(json::has), "every part is named in the file: " + text);
                var read = WorldFeatures.read(world);
                check(helper, read != null && !read.parts().get("kitchen") && !read.parts().get("laser")
                        && read.parts().get("storage"), "it reads back as written: " + read);
                check(helper, world.toAbsolutePath().normalize().equals(read.world()), "with the world it belongs to");

                Files.writeString(world.resolve(WorldFeatures.FILE), "{\"laser\": false, \"no_such_part\": false, \"kitchen\": \"no\"}",
                        StandardCharsets.UTF_8);
                read = WorldFeatures.read(world);
                check(helper, read != null && !read.parts().get("laser") && !read.parts().containsKey("kitchen")
                        && !read.parts().containsKey("no_such_part"), "a part missing or not true or false is left on: " + read);

                Files.writeString(world.resolve(WorldFeatures.FILE), "{ not json", StandardCharsets.UTF_8);
                check(helper, WorldFeatures.read(world) == null, "a broken file is no choice: the start-up config alone");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        helper.succeed();
    }

    /**
     * Opening a world - its pack list made from its folder, as the client and a dedicated server do just before its data
     * packs load - puts its choice in force: the Elevator's recipe and its recipe-book unlock are held back by their
     * condition there, and nowhere else. Made from the Create World screen, the world's choice is written into its new
     * folder and read back the same way.
     */
    private static void leftOutPartHasNoRecipe(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean before = Features.enabled("elevator");
        var ops = new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), ICondition.IContext.EMPTY);
        JsonElement recipe = data(server, "recipe/elevator.json");
        JsonElement unlock = data(server, "advancement/recipes/misc/elevator.json");
        check(helper, ICondition.conditionsMatched(ops, recipe) == before, "the recipe loads as the start-up config has the Elevator");
        WorldFeatures.Choice was = WorldFeatures.current();
        boolean menu = NookworksStartupConfig.WORLD_MENU.getAsBoolean();
        inTempWorld(helper, world -> {
            try {
                set(NookworksStartupConfig.WORLD_MENU, true);
                WorldFeatures.write(world, Map.of("elevator", false));
                opening(world);
                check(helper, !Features.enabled("elevator") && !ICondition.conditionsMatched(ops, recipe)
                        && !ICondition.conditionsMatched(ops, unlock), "in the world that left it out, no recipe and no unlock");
                check(helper, Features.enabled("printing") == WorldFeatures.offered("printing"), "what it kept is as before");
                WorldFeatures.closed(server);
                check(helper, WorldFeatures.current() != null, "another server closing leaves the world's choice alone");
                Files.delete(world.resolve(WorldFeatures.FILE));
                opening(world);
                check(helper, WorldFeatures.current() == null && Features.enabled("elevator") == before
                        && ICondition.conditionsMatched(ops, recipe) == before, "a world without the file: as the start-up config has it");

                // made on the Create World screen: what was chosen there goes into the new folder before the world opens
                WorldFeatures.creatingWorld();
                check(helper, WorldFeatures.choosing().values().stream().allMatch(on -> on), "the screen starts with nothing left out");
                Map<String, Boolean> chosen = new LinkedHashMap<>(WorldFeatures.choosing());
                chosen.put("elevator", false);
                WorldFeatures.choose(chosen);
                check(helper, !ICondition.conditionsMatched(ops, recipe), "the screen's data packs load again without the recipe");
                WorldFeatures.made(world);
                WorldFeatures.restore(null);
                opening(world);
                check(helper, WorldFeatures.current() != null && !Features.enabled("elevator"), "the world opens with what was chosen");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                set(NookworksStartupConfig.WORLD_MENU, menu);
                WorldFeatures.restore(was);
            }
        });
        check(helper, Features.enabled("elevator") == before, "and put back as it was");
        helper.succeed();
    }

    /**
     * A part the start-up config switches off is off in a world that kept it, is not offered, and is off on the screen
     * (shown, not switchable); a part the world left out is off though the start-up config has it on. What a part needs
     * goes with it as before: a world without the Mob Bottle has no Barter Terminal.
     */
    private static void startUpConfigIsOverTheWorld(GameTestHelper helper) {
        WorldFeatures.Choice was = WorldFeatures.current();
        var kitchen = NookworksStartupConfig.feature("kitchen");
        boolean kitchenWas = kitchen.getAsBoolean();
        boolean menu = NookworksStartupConfig.WORLD_MENU.getAsBoolean();
        try {
            set(NookworksStartupConfig.WORLD_MENU, true);
            set(kitchen, false);
            WorldFeatures.restore(new WorldFeatures.Choice(null, Map.of("kitchen", true)));
            check(helper, !Features.enabled("kitchen") && !WorldFeatures.offered("kitchen") && !WorldFeatures.effective().get("kitchen"),
                    "switched off at start-up, off in a world that kept it");
            check(helper, !new ModConditions.Feature("kitchen").test(ICondition.IContext.EMPTY)
                    && !NookworksStartupConfig.flourBakery(), "its recipes, and the bakery's, do not load");
            set(kitchen, true);
            check(helper, Features.enabled("kitchen") && WorldFeatures.offered("kitchen"), "switched on at start-up, the world's choice decides");
            WorldFeatures.restore(new WorldFeatures.Choice(null, Map.of("kitchen", false, "mob_bottle", false)));
            check(helper, !Features.enabled("kitchen") && !new ModConditions.Feature("kitchen").test(ICondition.IContext.EMPTY)
                    && !new ModConditions.Setting("feature.kitchen", true).test(ICondition.IContext.EMPTY),
                    "a world that left the kitchen out has none, by either condition");
            check(helper, !Features.enabled("barter_terminal"), "a world without the Mob Bottle has no Barter Terminal");
        } finally {
            set(kitchen, kitchenWas);
            set(NookworksStartupConfig.WORLD_MENU, menu);
            WorldFeatures.restore(was);
        }
        helper.succeed();
    }

    /**
     * With {@code worldMenu} off the pack has the parts alone: the Create World screen has no button (it asks
     * {@link WorldFeatures#menuShown}), a world's file is not read and a new world gets none, and a choice in force
     * changes nothing.
     */
    private static void packCanTurnTheChoiceOff(GameTestHelper helper) {
        WorldFeatures.Choice was = WorldFeatures.current();
        boolean menu = NookworksStartupConfig.WORLD_MENU.getAsBoolean();
        boolean laser = WorldFeatures.offered("laser");
        inTempWorld(helper, world -> {
            try {
                set(NookworksStartupConfig.WORLD_MENU, false);
                check(helper, !WorldFeatures.menuShown(), "no button on the Create World screen");
                WorldFeatures.write(world, Map.of("laser", false));
                opening(world);
                check(helper, WorldFeatures.current() == null && Features.enabled("laser") == laser, "a world's file is not read");
                WorldFeatures.restore(new WorldFeatures.Choice(null, Map.of("laser", false)));
                check(helper, Features.enabled("laser") == laser, "nor is a choice in force heard");
                Files.delete(world.resolve(WorldFeatures.FILE));
                WorldFeatures.made(world);
                check(helper, !Files.exists(world.resolve(WorldFeatures.FILE)), "a new world gets no file");
                set(NookworksStartupConfig.WORLD_MENU, true);
                check(helper, WorldFeatures.menuShown() && !Features.enabled("laser"), "on again, the choice counts");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                set(NookworksStartupConfig.WORLD_MENU, menu);
                WorldFeatures.restore(was);
            }
        });
        helper.succeed();
    }

    /**
     * A player on a server is told which parts its world has, and their tabs and JEI go by it - not by their own
     * start-up config, which no longer has to match the server's on the parts. A client hosting the world itself keeps
     * its own (the same) choice; leaving the server forgets its word.
     */
    private static void playersFollowTheServer(GameTestHelper helper) {
        Map<String, Boolean> parts = new LinkedHashMap<>(WorldFeatures.effective());
        parts.put("magnets", false);
        parts.put("kitchen", false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        NookworksNetwork.WorldParts.STREAM_CODEC.encode(buffer, new NookworksNetwork.WorldParts(parts));
        var sent = NookworksNetwork.WorldParts.STREAM_CODEC.decode(buffer).parts();
        check(helper, sent.equals(parts), "sent whole: " + sent);
        boolean magnets = Features.enabled("magnets");
        try {
            WorldFeatures.heard(sent, true);
            check(helper, Features.enabled("magnets") == magnets, "hosting the world, the client keeps its own");
            WorldFeatures.heard(sent, false);
            check(helper, !Features.enabled("magnets") && !Features.enabled(ModItems.ABSORPTION_PAD.get()),
                    "on another's server, the server's word: no Magnets, nor the pad made with one");
            ModCreativeTabs.partsKnown();
            check(helper, ModCreativeTabs.KITCHEN.get().getIconItem().is(ModOil.OIL_PRESS_ITEM.get()),
                    "a world without the kitchen: the Kitchen tab's icon is picked again, not the rice of the last one");
        } finally {
            WorldFeatures.leftServer();
            ModCreativeTabs.partsKnown();
        }
        check(helper, Features.enabled("magnets") == magnets, "left, its word is forgotten");
        check(helper, NookworksStartupConfig.values().keySet().stream().noneMatch(name -> name.startsWith("feature.")),
                "the parts are not among the settings a player is told differ");
        helper.succeed();
    }

    // ---- helpers ----

    /** A world's folder of its own, its pack list made as a world being opened has it: its choice is read then. */
    private static void opening(Path world) {
        Path base = world.getParent();
        try (LevelStorageSource.LevelStorageAccess access = LevelStorageSource.createDefault(base).createAccess(world.getFileName().toString())) {
            if (!access.getLevelPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(world.toAbsolutePath().normalize())) {
                throw new IllegalStateException("not the world asked for: " + access.getLevelPath(LevelResource.ROOT));
            }
            ServerPacksSource.createPackRepository(access);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code body} with a fresh world folder, deleted after. */
    private static void inTempWorld(GameTestHelper helper, Consumer<Path> body) {
        Path base;
        try {
            base = Files.createTempDirectory("nookworks-worldparts");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path world = base.resolve("world");
        try {
            Files.createDirectories(world);
            body.accept(world);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try (Stream<Path> files = Files.walk(base)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            } catch (IOException ignored) {
                // a temporary folder: the system clears it in time
            }
        }
    }

    private static JsonElement data(MinecraftServer server, String path) {
        var resource = server.getResourceManager().getResource(Identifier.fromNamespaceAndPath("nookworks", path))
                .orElseThrow(() -> new IllegalStateException("no data file " + path));
        try (var reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A start-up setting for the length of a test: nothing is written to disk. */
    private static void set(ModConfigSpec.BooleanValue setting, boolean value) {
        setting.set(value);
        setting.clearCache();
        WorldFeatures.changed(); // what parts switched off take along is worked out again
    }
}
