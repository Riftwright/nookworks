package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.block.ThinRopeBlock;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.furniture.SeatEntity;
import dev.nookworks.label.LabelEntity;
import dev.nookworks.label.LabelItem;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.poster.PosterEntity;
import dev.nookworks.poster.Posters;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.rope.HungBannerEntity;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Worlds from earlier versions keep what is in them: every block of the mod as one version saved it, with things in it,
 * loaded by today's mod. A fixture is a structure of every block of ours, set down in a grid - double blocks whole -
 * with items, fluids and energy put in what takes them, and beside it what each place held (the block, its state, the
 * items, fluids and energy in it, a Drive Wheel's belts). The test sets each fixture down - the game's data fixers and
 * our block entities' readers at work as on a world opened from disk - and finds the same, place by place.
 * <p>
 * From 1.0 a fixture also holds a scene beside the grid with every kind of entity of ours a world keeps
 * ({@link #setScene}): a Thin Rope strung between two posts with a patterned banner, a Filament Bulb, a bottle and a
 * player's head hung on it, a Thin Chain with a head and glow berries, a bottle under a Thin Rope let down from a
 * ceiling, a poster and two printed photos (two by two, two by one) on walls, a picture label and a letter label on a
 * filled crate and a banner's label on a chest, each off the middle of its face, a seat left on a chair, a Dawnwood boat
 * and a filled chest boat, and a Motor's wheel belted on to two more (one belt of rope, one of chain). Each entity of
 * ours is kept as it was found ({@link #entity}: its kind, where it is, and what its save holds that matters - a knot's
 * spans, a hung thing's item with its components (a banner's patterns, a head's owner) and its span, a poster's design
 * and where it lies, a label's text, item and where on its face it sits), and found the same as the fixture loads - each read from its
 * save as a chunk read from disk brings it in ({@link #loadEntities}); then the grid is taken
 * away and, two seconds on, the scene is looked at again: everything still where it was, belts still on, nothing
 * dropped - but the seat, which with no one in it is meant to go on its first tick. A fixture from before 1.0 has no
 * scene and is checked as it always was. (A thrown Lode Pearl is not in the scene: it flies on, it is not kept.)
 * <p>
 * Contents are read as a player's hopper or pipe would read them - the items, fluids and energy a block shows through
 * NeoForge's capabilities (mercury, kept off them on purpose, is not seen). A chunk read from disk is not updated as
 * it loads, so neither is a fixture as it is set down: blocks with nothing to hang from stay as they were.
 * <p>
 * A release makes its fixture with {@code .\tools\gradle.ps1 runGameTestServer -PcaptureCompat}, which writes
 * {@code data/nookworks/structure/compat/<version>.nbt} and {@code .json}; it is added to {@link #FIXTURES} and kept for
 * good. A change that does not keep an old world's things - a block renamed, a property renamed, a field read under
 * another name - fails it. A change that does so on purpose, with a fix for old worlds, says so in the fixture's
 * expectations. Neither file goes in the jar.
 * <p>
 * A rope knot saved before there were chains is tried by {@code ChainTests}: a knot of rope alone is written as it was
 * then, and one written then loads with every span rope.
 */
final class SaveCompat {
    /**
     * The fixtures kept, oldest first: each one release's every block (from 1.0, its entities too). {@code 1_0_0_mc26_2}
     * is 1.0's as the Minecraft 26.2 build kept it (on that branch, as {@code 1_0_0}), read here through the game's data
     * fixers: a world from 26.2 opened in 26.3.
     */
    static final List<String> FIXTURES = List.of("0_4_0", "0_5_0", "0_5_1", "0_6_0", "0_6_1", "0_6_2", "0_7_0", "0_8_0", "0_9_0", "1_0_0", "1_0_0_mc26_2", "1_1_0", "1_2_0", "1_2_1", "1_2_2", "1_3_0", "1_3_1", "1_3_2", "1_4_0"); // 0_4_0 made from 5e0cb8f, the jar uploaded as 0.4.0
    /** Where a fixture is set down: far from the tests' own plots, high over the test level's floor. */
    private static final BlockPos AT = new BlockPos(48000, 140, 48000);
    private static final int PER_ROW = 24;
    /** The scene of entities: its size, and the gap between it and the grid of blocks. */
    private static final Vec3i SCENE = new Vec3i(34, 7, 8);
    private static final int SCENE_GAP = 3;
    /** How long a scene is left to tick before it is looked at again. */
    private static final int LATER = 40;
    /** How long the plot is waited for to load, in real time, and the most ticks that can take (a tick waited sleeps 2 ms). */
    private static final long WAIT_NANOS = 60_000_000_000L;
    private static final int WAIT_TICKS = 31_000;

    private SaveCompat() {}

    static void register() {
        String dir = System.getProperty("nookworks.captureCompat");
        String name = System.getProperty("nookworks.captureCompatName");
        if (dir != null && name != null) {
            // a release making its fixture (the check of the fixtures waits for the next run, the new one in)
            NookworksGameTests.test("save_compat_capture", WAIT_TICKS + 100, helper -> capture(helper, Path.of(dir), name));
        } else {
            // each fixture with a scene waits LATER ticks for its second look
            NookworksGameTests.test("save_compat_old_worlds_keep_every_block", WAIT_TICKS + 100 + (LATER + 10) * FIXTURES.size(),
                    SaveCompat::oldWorldsKeepEveryBlock);
        }
    }

    // ---- the test ----

    private static void oldWorldsKeepEveryBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // the plot of the largest fixture, kept loaded and ticking throughout: an entity set down in a chunk whose
        // entities are not yet loaded is not found, and a scene is watched for a while
        Vec3i most = new Vec3i(1, 1, 1);
        for (String name : FIXTURES) {
            Vec3i size = level.getStructureTemplateManager().get(Nookworks.id("compat/" + name)).map(StructureTemplate::getSize).orElse(most);
            most = new Vec3i(Math.max(most.getX(), size.getX()), Math.max(most.getY(), size.getY()), Math.max(most.getZ(), size.getZ()));
        }
        Vec3i plot = most;
        load(level, AT, plot);
        force(level, AT, plot, true);
        AfterTest.undo(helper, () -> force(level, AT, plot, false));
        whenReady(helper, AT, plot, System.nanoTime() + WAIT_NANOS, () -> step(helper, 0, new ArrayList<>(), new int[1]));
    }

    /** Fixture {@code index} set down and looked at, then the next; at the end, what was found. */
    private static void step(GameTestHelper helper, int index, List<String> problems, int[] checked) {
        ServerLevel level = helper.getLevel();
        if (index == FIXTURES.size()) {
            check(helper, problems.isEmpty(), problems.size() + " thing(s) changed: " + String.join("; ", problems.subList(0, Math.min(40, problems.size()))));
            check(helper, checked[0] > 100, "the fixtures hold too little: " + checked[0] + " places");
            helper.succeed();
            return;
        }
        String name = FIXTURES.get(index);
        Identifier id = Nookworks.id("compat/" + name);
        StructureTemplate template = level.getStructureTemplateManager().get(id).orElse(null);
        JsonObject expected = expectations(name);
        if (template == null || expected == null) {
            problems.add(name + ": fixture missing");
            step(helper, index + 1, problems, checked);
            return;
        }
        Vec3i size = template.getSize();
        clearAll(level, AT, size);
        // as a chunk read from disk is set: no neighbour updates, no shapes worked out, no drops; its entities after
        template.placeInWorld(level, AT, AT, new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(true),
                RandomSource.create(0), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        loadEntities(level, template, name, problems);
        JsonObject places = expected.getAsJsonObject("places");
        for (Map.Entry<String, JsonElement> entry : places.entrySet()) {
            JsonObject now = place(level, at(entry.getKey()));
            JsonObject was = intended(name, entry.getValue().getAsJsonObject());
            if (!now.equals(was)) {
                problems.add(name + " at " + entry.getKey() + ": was " + entry.getValue() + ", now " + now);
            }
            checked[0]++;
        }
        if (!expected.has("entities")) {
            if (!name.matches("0_.*")) {
                problems.add(name + ": a fixture from 1.0 on keeps its scene of entities, and this one has none");
            }
            clearAll(level, AT, size);
            step(helper, index + 1, problems, checked);
            return;
        }
        List<JsonObject> entities = new ArrayList<>();
        expected.getAsJsonArray("entities").forEach(e -> entities.add(e.getAsJsonObject()));
        compareEntities(level, name + " as it loads", size, entities, problems);
        // the grid goes, so its machines do nothing while the scene is watched
        JsonObject scene = expected.getAsJsonObject("scene");
        BlockPos sceneFrom = at(scene.get("from").getAsString());
        BlockPos sceneTo = at(scene.get("to").getAsString());
        AABB sceneBox = AABB.encapsulatingFullBlocks(sceneFrom, sceneTo);
        for (BlockPos pos : BlockPos.betweenClosed(AT, AT.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            if (!sceneBox.contains(Vec3.atCenterOf(pos))) {
                clear(level, pos);
            }
        }
        helper.runAfterDelay(LATER, () -> {
            // all but a seat with no one in it, which goes on its first tick (furniture/SeatEntity)
            List<JsonObject> lasting = entities.stream().filter(e -> !e.get("type").getAsString().equals(Nookworks.MODID + ":seat")).toList();
            compareEntities(level, name + " two seconds on", size, lasting, problems);
            for (Map.Entry<String, JsonElement> entry : places.entrySet()) {
                JsonElement belts = entry.getValue().getAsJsonObject().get("belts");
                if (belts != null && !belts.equals(place(level, at(entry.getKey())).get("belts"))) {
                    problems.add(name + " two seconds on, at " + entry.getKey() + ": belts were " + belts + ", now "
                            + place(level, at(entry.getKey())).get("belts"));
                }
            }
            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, box(size));
            if (!dropped.isEmpty()) {
                problems.add(name + " two seconds on: dropped " + dropped.stream().map(ItemEntity::getItem).toList());
            }
            clearAll(level, AT, size);
            step(helper, index + 1, problems, checked);
        });
    }

    /**
     * The fixture's entities as a chunk read from disk brings them in: each read from its save as kept (brought up to
     * date by the data fixers with the fixture) where it was - not set down as a structure sets down a copy, which moves
     * and turns it (tried by {@code R15CheckFourTests} and {@code StructureCopyTests}).
     */
    private static void loadEntities(ServerLevel level, StructureTemplate template, String name, List<String> problems) {
        for (CompoundTag info : template.save(new CompoundTag()).getListOrEmpty("entities").compoundStream().toList()) {
            CompoundTag nbt = info.getCompoundOrEmpty("nbt").copy();
            ListTag step = info.getListOrEmpty("pos");
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(AT.getX() + step.getDoubleOr(0, 0.0)));
            pos.add(DoubleTag.valueOf(AT.getY() + step.getDoubleOr(1, 0.0)));
            pos.add(DoubleTag.valueOf(AT.getZ() + step.getDoubleOr(2, 0.0)));
            nbt.put("Pos", pos);
            List<Entity> loaded = new ArrayList<>();
            EntityType.loadEntityRecursive(nbt, level, new EntitySpawnRequest(EntitySpawnReason.LOAD, false), entity -> {
                loaded.add(entity);
                return entity;
            });
            if (loaded.isEmpty()) {
                problems.add(name + ": an entity does not load: " + nbt.getStringOr("id", "?"));
            }
            level.addLegacyChunkEntities(loaded.stream());
        }
    }

    /** The entities of ours in the fixture's place against {@code expected}, as two lists of descriptions. */
    private static void compareEntities(ServerLevel level, String when, Vec3i size, List<JsonObject> expected, List<String> problems) {
        List<String> was = expected.stream().map(JsonObject::toString).sorted().toList();
        List<String> now = entities(level, size).stream().map(JsonObject::toString).sorted().toList();
        if (!was.equals(now)) {
            List<String> gone = new ArrayList<>(was);
            now.forEach(gone::remove);
            List<String> come = new ArrayList<>(now);
            was.forEach(come::remove);
            problems.add(when + ": entities were " + was.size() + ", now " + now.size() + "; not found " + gone + "; found instead " + come);
        }
    }

    /**
     * What a place from fixture {@code name} is meant to be now, where a version changed it on purpose - each change named
     * here, with its reason, and the fix for old worlds that makes it so.
     */
    /** The machines that show pipe ports since 1.3 (fluid/PipePorts), and on which sides. */
    private static final Map<String, String[]> PORTED = ported();

    private static Map<String, String[]> ported() {
        String[] all = {"down", "east", "north", "south", "up", "west"};
        String[] low = {"down", "east", "north", "south", "west"};
        String[] round = {"east", "north", "south", "west"};
        Map<String, String[]> out = new java.util.HashMap<>();
        for (String name : new String[] {"electric_furnace", "electrolytic_cell", "generator", "retort", "mercury_tank", "experience_tank"}) {
            out.put(Nookworks.MODID + ":" + name, all);
        }
        for (String name : new String[] {"diesel_generator", "distiller", "water_pump", "stirring_tank"}) {
            out.put(Nookworks.MODID + ":" + name, low);
        }
        out.put(Nookworks.MODID + ":copper_tank", round);
        return out;
    }

    static JsonObject intended(String name, JsonObject was) {
        JsonObject now = was.deepCopy();
        String state = now.get("state").getAsString();
        if (name.equals("0_4_0") && state.startsWith(Nookworks.MODID + ":dice[")) {
            // 0.5.0: a die is drawn from its block entity (where it lies, how it is turned); one from before had none,
            // and is given it as its chunk loads (block/DiceUpgrade)
            now.addProperty("entity", Nookworks.MODID + ":dice");
        }
        if (state.matches(Nookworks.MODID + ":[a-z_]+_desk\\[waterlogged=(true|false)]")) {
            // 0.5.1: desks side by side join into one (furniture/DeskBlock), a side for each way; one from before stood
            // alone, and loads joined on no side - as it stood - until a desk beside it is set down or taken away
            String water = state.substring(state.indexOf('[') + 1, state.length() - 1);
            now.addProperty("state", state.substring(0, state.indexOf('[')) + "[east=false,north=false,south=false," + water + ",west=false]");
        }
        if (state.equals(Nookworks.MODID + ":item_warp_receiver") && !now.has("entity")) {
            // 1.0: an Item Warp Receiver keeps who set it down, for its owner's senders only (block/entity/
            // ItemWarpReceiverBlockEntity); one from before had no block entity, and is given one of no owner as its chunk
            // loads - and with no owner, every sender of no owner sends to it as before
            now.addProperty("entity", Nookworks.MODID + ":item_warp_receiver");
        }
        String furniture = now.get("state").getAsString();
        String[] apart = furniture.matches(Nookworks.MODID + ":[a-z_]+_desk\\[.*") ? new String[] {"east_apart", "north_apart", "south_apart", "west_apart"}
                : furniture.matches(Nookworks.MODID + ":[a-z_]+_(bench|wall_shelf)\\[.*") ? new String[] {"left_apart", "right_apart"} : new String[0];
        if (apart.length > 0 && !furniture.contains("_apart=")) {
            // 1.2.0: benches, desks and wall shelves are kept apart from the one beside them by a Wrench (DeskBlock,
            // BenchBlock, WallShelfBlock: *_apart); one from before was kept apart from nothing, and loads so
            TreeMap<String, String> properties = new TreeMap<>();
            for (String pair : furniture.substring(furniture.indexOf('[') + 1, furniture.length() - 1).split(",")) {
                properties.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
            }
            for (String key : apart) {
                properties.put(key, "false");
            }
            StringBuilder out = new StringBuilder(furniture.substring(0, furniture.indexOf('[') + 1));
            properties.forEach((key, value) -> out.append(out.charAt(out.length() - 1) == '[' ? "" : ",").append(key).append('=').append(value));
            now.addProperty("state", out.append(']').toString());
        }
        if (state.startsWith(Nookworks.MODID + ":oil_press[") && !state.contains("port_")) {
            // 1.3: an Oil Press shows a copper port where a Copper Pipe joins its side (oil/OilPressBlock, port_*); one
            // from before loads with none, and is set right as its chunk loads, by the pipes beside it (none here)
            now.addProperty("state", state.substring(0, state.length() - 1) + ",port_east=false,port_north=false,port_south=false,port_west=false]");
        }
        String machine = now.get("state").getAsString();
        String block = machine.contains("[") ? machine.substring(0, machine.indexOf('[')) : machine;
        String[] ports = PORTED.getOrDefault(block, new String[0]);
        if (ports.length > 0 && !machine.contains("port_")) {
            // 1.3: the machines whose panels are set back a pixel show a port where a pipe of ours joins them
            // (fluid/PipePorts, port_*), so the pipe does not stand off them in the air; one from before loads with
            // none, and is set right as its chunk loads, by the pipes beside it (none here)
            TreeMap<String, String> properties = new TreeMap<>();
            if (machine.contains("[")) {
                for (String pair : machine.substring(machine.indexOf('[') + 1, machine.length() - 1).split(",")) {
                    properties.put(pair.substring(0, pair.indexOf('=')), pair.substring(pair.indexOf('=') + 1));
                }
            }
            for (String side : ports) {
                properties.put("port_" + side, "false");
            }
            StringBuilder out = new StringBuilder(block + "[");
            properties.forEach((key, value) -> out.append(out.charAt(out.length() - 1) == '[' ? "" : ",").append(key).append('=').append(value));
            now.addProperty("state", out.append(']').toString());
        }
        if (state.equals(Nookworks.MODID + ":filament_bulb")) {
            // 1.0.1: a Filament Bulb switches off and on by hand and by redstone (rope/FilamentBulbBlock, LIT and
            // POWERED); one from before had no switch, and loads on, as it was, having seen no signal
            now.addProperty("state", Nookworks.MODID + ":filament_bulb[lit=true,powered=false]");
        }
        return now;
    }

    private static @Nullable JsonObject expectations(String name) {
        String path = "/data/" + Nookworks.MODID + "/structure/compat/" + name + ".json";
        try (var in = SaveCompat.class.getResourceAsStream(path)) {
            return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            return null;
        }
    }

    // ---- what a place holds ----

    /** The block, its state, and the items, fluids and energy in it, as a player would find them. */
    static JsonObject place(ServerLevel level, BlockPos pos) {
        JsonObject out = new JsonObject();
        BlockState state = level.getBlockState(pos);
        out.addProperty("state", BlockStateParser.serialize(state));
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return out;
        }
        out.addProperty("entity", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString());
        TreeMap<String, Long> items = new TreeMap<>();
        if (entity instanceof dev.nookworks.decor.PlacedBooksBlockEntity pile) {
            for (int i = 0; i < pile.count(); i++) {
                items.merge(BuiltInRegistries.ITEM.getKey(pile.book(i).getItem()).toString(), 1L, Long::sum);
            }
        } else if (entity instanceof dev.nookworks.decor.BookendBlockEntity bookend) {
            for (int i = 0; i < bookend.count(); i++) {
                items.merge(BuiltInRegistries.ITEM.getKey(bookend.book(i).getItem()).toString(), 1L, Long::sum);
            }
        } else if (entity instanceof Container container) {
            items.putAll(contents(container));
        } else {
            ResourceHandler<ItemResource> handler = firstOf(level, pos, Capabilities.Item.BLOCK);
            if (handler != null) {
                for (int i = 0; i < handler.size(); i++) {
                    ItemResource resource = handler.getResource(i);
                    if (!resource.isEmpty()) {
                        items.merge(BuiltInRegistries.ITEM.getKey(resource.getItem()).toString(), handler.getAmountAsLong(i), Long::sum);
                    }
                }
            }
        }
        if (!items.isEmpty()) {
            out.add("items", counts(items));
        }
        ResourceHandler<FluidResource> fluids = firstOf(level, pos, Capabilities.Fluid.BLOCK);
        if (fluids != null) {
            TreeMap<String, Long> amounts = new TreeMap<>();
            for (int i = 0; i < fluids.size(); i++) {
                FluidResource resource = fluids.getResource(i);
                if (!resource.isEmpty()) {
                    amounts.merge(BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString(), fluids.getAmountAsLong(i), Long::sum);
                }
            }
            if (!amounts.isEmpty()) {
                out.add("fluids", counts(amounts));
            }
        }
        EnergyHandler energy = firstOf(level, pos, Capabilities.Energy.BLOCK);
        if (energy != null && energy.getAmountAsLong() > 0) {
            out.addProperty("energy", energy.getAmountAsLong());
        }
        if (entity instanceof FluidPipeBlockEntity pipe) {
            // the sides a pipe pulls from, kept in its block entity: one saved before pipes could pull pulls from none
            StringBuilder sides = new StringBuilder();
            for (Direction side : Direction.values()) {
                if (pipe.pullsFrom(side)) {
                    sides.append(sides.isEmpty() ? "" : ",").append(side.getSerializedName());
                }
            }
            if (!sides.isEmpty()) {
                out.addProperty("pulling", sides.toString());
            }
        }
        if (entity instanceof DriveWheelBlockEntity wheel && !wheel.belts().isEmpty()) {
            // each belt, in order (the first rides on one side of the wheel, the second on the other): the step to the
            // wheel at its far end, what it is and what it took - and whether that wheel is the one it was tied to
            JsonArray belts = new JsonArray();
            for (DriveWheelBlockEntity.Belt belt : wheel.belts()) {
                BlockPos step = belt.other().subtract(pos);
                boolean same = level.getBlockEntity(belt.other()) instanceof DriveWheelBlockEntity far
                        && (belt.farWheel() == 0 || belt.farWheel() == far.wheelId());
                belts.add(step.getX() + "," + step.getY() + "," + step.getZ() + " " + belt.kind().getSerializedName() + " "
                        + BuiltInRegistries.ITEM.getKey(belt.item()) + " " + belt.count() + (same ? "" : " (not the wheel it was tied to)"));
            }
            out.add("belts", belts);
        }
        return out;
    }

    private static TreeMap<String, Long> contents(Container container) {
        TreeMap<String, Long> items = new TreeMap<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), (long) stack.getCount(), Long::sum);
            }
        }
        return items;
    }

    private static JsonArray counts(TreeMap<String, Long> counts) {
        JsonArray array = new JsonArray();
        counts.forEach((id, n) -> array.add(id + " " + n));
        return array;
    }

    /** A capability of the block at {@code pos}: its own (no side) if it has one, else the first side's that does. */
    private static <T> @Nullable T firstOf(ServerLevel level, BlockPos pos,
            net.neoforged.neoforge.capabilities.BlockCapability<T, @Nullable Direction> capability) {
        T found = level.getCapability(capability, pos, null);
        for (Direction side : Direction.values()) {
            if (found != null) {
                break;
            }
            found = level.getCapability(capability, pos, side);
        }
        return found;
    }

    // ---- what an entity is ----

    /** The entities of ours in a fixture's place of {@code size}, each as {@link #entity} tells it. */
    private static List<JsonObject> entities(ServerLevel level, Vec3i size) {
        return level.getEntitiesOfClass(Entity.class, box(size), e -> !e.isRemoved() && ours(e.getType())).stream()
                .map(SaveCompat::entity).toList();
    }

    private static boolean ours(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(Nookworks.MODID);
    }

    /**
     * An entity of ours as it is now: its kind, where it is and which way it faces (from the fixture's corner, to a
     * hundredth of a block and a tenth of a degree), and what its save holds that matters - every place in it as a step
     * from the corner too.
     */
    static JsonObject entity(Entity entity) {
        JsonObject out = new JsonObject();
        out.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        Vec3 at = entity.position().subtract(Vec3.atLowerCornerOf(AT));
        out.addProperty("at", round(at.x, 100) + "," + round(at.y, 100) + "," + round(at.z, 100));
        out.addProperty("yaw", round(Mth.wrapDegrees(entity.getYRot()), 10));
        if (entity instanceof RopeKnotEntity knot) {
            out.addProperty("post", rel(knot.getPos()));
            out.add("spans", rels(knot.spans()));
            out.add("chains", rels(knot.chains()));
        }
        if (entity instanceof HungItemEntity hung) {
            out.addProperty("item", item(entity, hung.item()));
            out.addProperty("facing", round(hung.yaw(), 10));
            if (hung.spanFrom() != null && hung.spanTo() != null) {
                out.addProperty("span", rel(hung.spanFrom()) + " to " + rel(hung.spanTo()) + " at " + round(hung.along(), 1000));
            }
            Vec3 point = hung.hangPoint().subtract(Vec3.atLowerCornerOf(AT));
            out.addProperty("hangs", round(point.x, 100) + "," + round(point.y, 100) + "," + round(point.z, 100));
            if (hung.litAt() != null) {
                out.addProperty("light", rel(hung.litAt()));
            }
        }
        if (entity instanceof HungBannerEntity banner) {
            out.addProperty("slope", round(banner.slope(), 1000));
        }
        if (entity instanceof PosterEntity poster) {
            out.addProperty("post", rel(poster.getPos()));
            out.addProperty("facing", poster.getDirection().getSerializedName());
            out.addProperty("design", String.valueOf(poster.variantId()));
            out.addProperty("box", box(poster.getBoundingBox()));
        }
        if (entity instanceof LabelEntity label) {
            out.addProperty("post", rel(label.getPos()));
            out.addProperty("facing", label.getDirection().getSerializedName());
            out.addProperty("shows", item(entity, label.shows()));
            out.addProperty("text", label.text());
            out.addProperty("up", label.up());
            // where on its face it sits (across, up, how far back the face stands) and its size
            out.addProperty("box", box(label.getBoundingBox()));
        }
        if (entity instanceof Container container) {
            JsonArray items = new JsonArray();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    items.add(slot + ": " + stack.getCount() + " " + item(entity, stack));
                }
            }
            if (!items.isEmpty()) {
                out.add("items", items);
            }
        }
        return out;
    }

    /**
     * An item as kept: its id, and what its components say beyond the item's own (a banner's patterns, a head's owner, a
     * name), as its save codec writes them.
     */
    private static String item(Entity entity, ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return id;
        }
        return id + " " + DataComponentPatch.CODEC.encodeStart(entity.registryAccess().createSerializationContext(JsonOps.INSTANCE), patch)
                .result().map(JsonElement::toString).orElse("(components not written)");
    }

    /** A box from the fixture's corner, to a thousandth of a block. */
    private static String box(AABB box) {
        AABB step = box.move(-AT.getX(), -AT.getY(), -AT.getZ());
        return round(step.minX, 1000) + "," + round(step.minY, 1000) + "," + round(step.minZ, 1000) + " to "
                + round(step.maxX, 1000) + "," + round(step.maxY, 1000) + "," + round(step.maxZ, 1000);
    }

    private static String rel(BlockPos pos) {
        BlockPos step = pos.subtract(AT);
        return step.getX() + "," + step.getY() + "," + step.getZ();
    }

    private static JsonArray rels(List<BlockPos> positions) {
        JsonArray array = new JsonArray();
        positions.forEach(pos -> array.add(rel(pos)));
        return array;
    }

    /** {@code value} to the nearest 1/{@code per}, as a number with no -0. */
    private static double round(double value, int per) {
        return Math.round(value * per) / (double) per + 0.0;
    }

    /** The place {@code "x,y,z"} from the fixture's corner. */
    private static BlockPos at(String key) {
        String[] xyz = key.split(",");
        return AT.offset(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
    }

    // ---- making a fixture ----

    /**
     * Every block of ours, set down, filled and kept, and the scene of entities beside them: the fixture of the version
     * that runs it. The scene is set first and given a few ticks (glow berries light the tick after they are hung); the
     * grid is set after, so no machine in it works on what it was given before it is kept.
     */
    private static void capture(GameTestHelper helper, Path dir, String name) {
        ServerLevel level = helper.getLevel();
        List<Block> blocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Nookworks.MODID))
                .filter(block -> block.asItem() != Items.AIR || block.defaultBlockState().hasBlockEntity())
                .toList();
        int rows = (blocks.size() + PER_ROW - 1) / PER_ROW;
        int sceneZ = rows * 3 + SCENE_GAP;
        Vec3i size = new Vec3i(Math.max(PER_ROW * 2, SCENE.getX()), Math.max(4, SCENE.getY()), sceneZ + SCENE.getZ());
        load(level, AT, size);
        force(level, AT, size, true);
        AfterTest.undo(helper, () -> force(level, AT, size, false));
        BlockPos scene = AT.offset(0, 0, sceneZ);
        whenReady(helper, AT, size, System.nanoTime() + WAIT_NANOS, () -> {
            clearAll(level, AT, size);
            setScene(helper, level, scene);
            // glow berries light the tick after they are hung, a bulb at its first look round
            helper.runAfterDelay(25, () -> keep(helper, level, blocks, size, scene, dir, name));
        });
    }

    /** The grid set down and filled beside the scene, and the fixture kept. */
    private static void keep(GameTestHelper helper, ServerLevel level, List<Block> blocks, Vec3i size, BlockPos scene, Path dir, String name) {
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = AT.offset((i % PER_ROW) * 2, 1, (i / PER_ROW) * 3);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            BlockState state = blocks.get(i).defaultBlockState();
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                level.setBlock(pos.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                placed.add(pos.above());
            }
            placed.add(pos);
        }
        for (BlockPos pos : placed) {
            fill(level, pos);
        }
        TreeMap<String, JsonObject> places = new TreeMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(AT, AT.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Nookworks.MODID)) {
                places.put(rel(pos.immutable()), place(level, pos.immutable()));
            }
        }
        level.addFreshEntity(new SeatEntity(level, chair(scene), SeatEntity.SEAT_HEIGHT));
        List<JsonObject> entities = new ArrayList<>(entities(level, size));
        entities.sort(java.util.Comparator.comparing(JsonObject::toString));
        StructureTemplateManager manager = level.getStructureTemplateManager();
        StructureTemplate template = manager.getOrCreate(Nookworks.id("compat/capture"));
        template.fillFromWorld(level, AT, size, true, List.of());
        JsonObject json = new JsonObject();
        json.addProperty("version", name.replace('_', '.'));
        json.addProperty("blocks", blocks.size());
        JsonObject placesJson = new JsonObject();
        places.forEach(placesJson::add);
        json.add("places", placesJson);
        JsonObject sceneJson = new JsonObject();
        sceneJson.addProperty("from", rel(scene));
        sceneJson.addProperty("to", rel(scene.offset(SCENE.getX() - 1, SCENE.getY() - 1, SCENE.getZ() - 1)));
        json.add("scene", sceneJson);
        JsonArray entitiesJson = new JsonArray();
        entities.forEach(entitiesJson::add);
        json.add("entities", entitiesJson);
        try {
            Files.createDirectories(dir);
            StructureTemplateManager.save(dir.resolve(name + ".nbt"), template, false);
            Files.writeString(dir.resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write the fixture to " + dir, e);
        }
        clearAll(level, AT, size);
        check(helper, entities.size() == 20, "the scene holds 20 entities of ours (four knots, seven hung things, three posters, three labels, a seat, two boats): " + entities);
        Nookworks.LOGGER.info("save compat fixture {}: {} blocks, {} places, {} entities, written to {}", name, blocks.size(),
                places.size(), entities.size(), dir);
        helper.succeed();
    }

    /**
     * The entities of ours a world keeps, set up from {@code s} (the scene's corner) as a player would leave them - see
     * the class's notes. Everything in it is checked as it is made, so a fixture is never kept missing part of it.
     */
    private static void setScene(GameTestHelper helper, ServerLevel level, BlockPos s) {
        ServerPlayer player = MockPlayers.inLevel(helper); // in creative: ties without paying
        Vec3 south = Vec3.atCenterOf(s.offset(6, 2, 7)); // where a player hanging things stood: banners and heads face them
        player.snapTo(south.x, south.y, south.z); // the line a player pays out is looked for round them

        // a Thin Rope between two fence posts: a banner, a bulb, a bottle and a head on it
        BlockPos ropeA = s.offset(1, 4, 1);
        BlockPos ropeB = s.offset(11, 4, 1);
        // a Thin Chain between two more: a head and glow berries
        BlockPos chainA = s.offset(1, 4, 4);
        BlockPos chainB = s.offset(11, 4, 4);
        for (BlockPos post : List.of(ropeA, ropeB, chainA, chainB)) {
            level.setBlockAndUpdate(post.atY(s.getY()), Blocks.STONE.defaultBlockState());
            for (int y = 1; y <= 4; y++) {
                level.setBlockAndUpdate(post.atY(s.getY() + y), Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        ItemStack rope = new ItemStack(ModItems.THIN_ROPE.get(), 64);
        RopeSpans.tie(level, player, ropeA, rope);
        RopeSpans.tie(level, player, ropeB, rope);
        ItemStack chain = new ItemStack(ModRope.THIN_CHAIN.get(), 64);
        RopeSpans.tie(level, player, chainA, chain);
        RopeSpans.tie(level, player, chainB, chain);
        RopeKnotEntity ropeKnot = RopeKnotEntity.at(level, ropeA);
        RopeKnotEntity chainKnot = RopeKnotEntity.at(level, chainA);
        check(helper, ropeKnot != null && ropeKnot.ties(ropeB) && chainKnot != null && chainKnot.ties(chainB)
                && chainKnot.chains().contains(chainB), "the rope and the chain are strung");
        level.addFreshEntity(HungItemEntity.onSpan(level, ropeA, ropeB, 0.15, patternedBanner(level), south));
        level.addFreshEntity(HungItemEntity.onSpan(level, ropeA, ropeB, 0.38, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get())));
        level.addFreshEntity(HungItemEntity.onSpan(level, ropeA, ropeB, 0.6, new ItemStack(Items.GLASS_BOTTLE)));
        level.addFreshEntity(HungItemEntity.onSpan(level, ropeA, ropeB, 0.82, ownedHead(), south));
        level.addFreshEntity(HungItemEntity.onSpan(level, chainA, chainB, 0.3, new ItemStack(Items.CREEPER_HEAD), south));
        level.addFreshEntity(RopeTests.bearing(HungItemEntity.onSpan(level, chainA, chainB, 0.7, new ItemStack(Items.GLOW_BERRIES))));

        // a Thin Rope let down from a ceiling, a honey bottle under its end
        BlockPos ropeEnd = s.offset(14, 4, 1);
        level.setBlockAndUpdate(ropeEnd.above(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(ropeEnd, ModBlocks.THIN_ROPE.get().defaultBlockState().setValue(ThinRopeBlock.ANCHORED, true));
        level.addFreshEntity(HungItemEntity.underRope(level, ropeEnd, new ItemStack(Items.HONEY_BOTTLE), 30.0F));

        // a poster on a wall: the smallest design
        for (BlockPos stone : BlockPos.betweenClosed(s.offset(14, 1, 5), s.offset(16, 3, 5))) {
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        }
        PosterEntity poster = null;
        for (Identifier design : Posters.ids(level.registryAccess())) {
            PosterEntity tried = new PosterEntity(level, s.offset(15, 2, 4), Direction.NORTH, design);
            if (tried.survives()) {
                poster = tried;
                break;
            }
        }
        check(helper, poster != null, "a poster goes on the wall");
        level.addFreshEntity(poster);
        // printed photos, two blocks by two and two by one (an even size sits between two blocks), on a wall behind
        for (BlockPos stone : BlockPos.betweenClosed(s.offset(13, 1, 7), s.offset(18, 3, 7))) {
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        }
        PosterEntity square = new PosterEntity(level, s.offset(17, 1, 6), Direction.NORTH, PhotoPrints.id(42, new Photo.Size(2, 2)));
        PosterEntity wide = new PosterEntity(level, s.offset(14, 2, 6), Direction.NORTH, PhotoPrints.id(43, new Photo.Size(2, 1)));
        check(helper, square.survives() && wide.survives(), "the printed photos go on the wall");
        level.addFreshEntity(square);
        level.addFreshEntity(wide);

        // a filled crate with a picture label on its front and a letter label on its side, and a chest with a label of a
        // patterned banner on its front (which stands a pixel back from the edge of its square) - each off the middle
        BlockPos crate = s.offset(19, 1, 1);
        BlockPos chest = s.offset(17, 1, 1);
        for (BlockPos b : List.of(crate, chest)) {
            level.setBlockAndUpdate(b.below(), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(crate, ModBlocks.CRATE.get().defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        fill(level, crate);
        LabelEntity picture = LabelItem.place(level, crate, Direction.NORTH,
                new Vec3(crate.getX() + 0.75, crate.getY() + 0.7, crate.getZ()), new ItemStack(Items.IRON_INGOT), false);
        LabelEntity letters = LabelItem.place(level, crate, Direction.EAST,
                new Vec3(crate.getX() + 1, crate.getY() + 0.3, crate.getZ() + 0.3), new ItemStack(Items.PAPER), false);
        LabelEntity onChest = LabelItem.place(level, chest, Direction.NORTH,
                new Vec3(chest.getX() + 0.3, chest.getY() + 0.35, chest.getZ() + 1 / 16.0), patternedBanner(level), false);
        check(helper, picture != null && letters != null && onChest != null, "the labels go on the crate and the chest");
        letters.setText("NOOK");
        level.addFreshEntity(picture);
        level.addFreshEntity(letters);
        level.addFreshEntity(onChest);

        // a chair, for the seat left on it (set as the fixture is kept: with no one in it, it goes on its first tick)
        BlockPos chair = chair(s);
        level.setBlockAndUpdate(chair.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(chair, ModWoods.CHAIRS.get("oak").get().defaultBlockState());

        // a Dawnwood boat and a filled chest boat, each on a floor
        for (BlockPos floor : BlockPos.betweenClosed(s.offset(22, 0, 0), s.offset(24, 0, 6))) {
            level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        }
        Entity boat = ModEntities.DAWNWOOD_BOAT.get().create(level, EntitySpawnReason.COMMAND);
        Entity chestBoat = ModEntities.DAWNWOOD_CHEST_BOAT.get().create(level, EntitySpawnReason.COMMAND);
        check(helper, boat != null && chestBoat instanceof Container, "the boats are made");
        boat.snapTo(s.getX() + 23.5, s.getY() + 1, s.getZ() + 1.5, 90.0F, 0.0F);
        chestBoat.snapTo(s.getX() + 23.5, s.getY() + 1, s.getZ() + 5.5, 0.0F, 0.0F);
        Container hold = (Container) chestBoat;
        hold.setItem(0, new ItemStack(Items.COD, 5));
        ItemStack named = new ItemStack(Items.OAK_LOG, 12);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Nook logs"));
        hold.setItem(4, named);
        hold.setItem(26, new ItemStack(ModItems.THIN_ROPE.get(), 3));
        level.addFreshEntity(boat);
        level.addFreshEntity(chestBoat);

        // a Motor's wheel belted on to a second with rope, the second on to a third with chain
        BlockPos motor = s.offset(27, 2, 0);
        BlockPos w1 = s.offset(27, 2, 1);
        BlockPos w2 = s.offset(32, 2, 1);
        BlockPos w3 = s.offset(32, 6, 1);
        level.setBlockAndUpdate(motor, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.SOUTH));
        fill(level, motor);
        for (BlockPos w : List.of(w1, w2, w3)) {
            level.setBlockAndUpdate(w, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.SOUTH));
        }
        var why = Belts.tie(level, (DriveWheelBlockEntity) level.getBlockEntity(w1), (DriveWheelBlockEntity) level.getBlockEntity(w2),
                BeltKind.ROPE, null, new ItemStack(ModItems.ROPE.get()));
        check(helper, why == null, "the rope belt is tied: " + (why == null ? "" : why.getString()));
        why = Belts.tie(level, (DriveWheelBlockEntity) level.getBlockEntity(w2), (DriveWheelBlockEntity) level.getBlockEntity(w3),
                BeltKind.CHAIN, null, new ItemStack(ModRope.THIN_CHAIN.get()));
        check(helper, why == null, "the chain belt is tied: " + (why == null ? "" : why.getString()));
    }

    /** A red banner with a white top stripe and a black cross, as from the loom. */
    private static ItemStack patternedBanner(ServerLevel level) {
        var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        ItemStack banner = new ItemStack(Items.BANNER.red());
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.WHITE)
                .add(patterns.getOrThrow(BannerPatterns.CROSS), DyeColor.BLACK).build());
        return banner;
    }

    /** A player head with its owner (made up, and whole: nothing to look up). */
    private static ItemStack ownedHead() {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(
                new GameProfile(UUID.fromString("6e6f6f6b-776f-726b-7300-000000000001"), "Nookling")));
        return head;
    }

    private static BlockPos chair(BlockPos scene) {
        return scene.offset(19, 1, 5);
    }

    /** Things put in what takes them: a few kinds of item, a fluid, energy - each by the block's own rules. */
    private static void fill(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return;
        }
        List<Item> samples = samples();
        // books laid down or stood in a Bookend are no container: two books each, one of them named (1.1)
        if (entity instanceof dev.nookworks.decor.PlacedBooksBlockEntity pile) {
            pile.add(new ItemStack(Items.ENCHANTED_BOOK), 0.0F, false, level.getRandom());
            pile.add(new ItemStack(Items.BOOK).copy(), 0.0F, false, level.getRandom());
            level.setBlock(pos, level.getBlockState(pos).setValue(dev.nookworks.decor.PlacedBooksBlock.BOOKS, pile.count()),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            return;
        }
        if (entity instanceof dev.nookworks.decor.BookendBlockEntity bookend) {
            bookend.add(new ItemStack(Items.WRITABLE_BOOK));
            bookend.add(new ItemStack(Items.BOOK));
            level.setBlock(pos, level.getBlockState(pos).setValue(dev.nookworks.decor.BookendBlock.BOOKS, bookend.count()),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            return;
        }
        if (entity instanceof Container container) {
            int filled = 0;
            for (int slot = 0; slot < container.getContainerSize() && filled < 3; slot++) {
                for (int k = 0; k < samples.size(); k++) {
                    ItemStack stack = new ItemStack(samples.get((k + slot) % samples.size()), 3 + slot % 9);
                    stack.setCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
                    if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, stack)) {
                        container.setItem(slot, stack);
                        filled++;
                        break;
                    }
                }
            }
        } else {
            ResourceHandler<ItemResource> handler = firstOf(level, pos, Capabilities.Item.BLOCK);
            if (handler != null) {
                int kinds = 0;
                for (Item item : samples) {
                    if (kinds >= 3) {
                        break;
                    }
                    ItemStack stack = new ItemStack(item, Math.min(13, item.getDefaultMaxStackSize()));
                    if (ItemUtil.insertItemReturnRemaining(handler, stack, false, null).getCount() < stack.getCount()) {
                        kinds++;
                    }
                }
            }
        }
        ResourceHandler<FluidResource> fluids = firstOf(level, pos, Capabilities.Fluid.BLOCK);
        if (fluids != null) {
            for (Fluid fluid : sampleFluids()) {
                try (Transaction tx = Transaction.openRoot()) {
                    if (fluids.insert(FluidResource.of(fluid), 750, tx) > 0) {
                        tx.commit();
                        break;
                    }
                }
            }
        }
        EnergyHandler energy = firstOf(level, pos, Capabilities.Energy.BLOCK);
        if (energy != null) {
            try (Transaction tx = Transaction.openRoot()) {
                energy.insert(5_000, tx);
                tx.commit();
            }
        }
        entity.setChanged();
    }

    /** What is offered: vanilla's common things and some of ours, so most machines take one. */
    private static List<Item> samples() {
        List<Item> items = new ArrayList<>(List.of(Items.COBBLESTONE, Items.IRON_INGOT, Items.COAL, Items.WHEAT, Items.PAPER,
                Items.GOLD_INGOT, Items.REDSTONE, Items.GLASS_BOTTLE, Items.DIRT, Items.GRAVEL, Items.SAND, Items.OAK_LOG,
                Items.EMERALD, Items.BOOK, Items.BONE_MEAL, Items.POTATO, Items.PORKCHOP, Items.EGG, Items.SUGAR, Items.BREAD,
                Items.DYE.pick(net.minecraft.world.item.DyeColor.WHITE), Items.DYE.pick(net.minecraft.world.item.DyeColor.CYAN),
                Items.DYE.pick(net.minecraft.world.item.DyeColor.MAGENTA), Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW),
                Items.DYE.pick(net.minecraft.world.item.DyeColor.BLACK), Items.COPPER_INGOT,
                Items.RAW_IRON, Items.GOLD_NUGGET, Items.SWEET_BERRIES, Items.KELP, Items.COD, Items.STICK));
        for (String id : List.of("brown_rice", "white_rice", "flour", "crude_salt", "sulfur_dust", "caustic_soda", "brass_ingot",
                "zinc_ingot", "wood_chips", "fine_paper", "corn_kernels", "onion", "brass_mesh", "vegetable_oil_bottle", "cinnabar",
                "iron_amalgam", "label", "thin_rope")) {
            BuiltInRegistries.ITEM.getOptional(Nookworks.id(id)).ifPresent(items::add);
        }
        return items;
    }

    private static List<Fluid> sampleFluids() {
        List<Fluid> fluids = new ArrayList<>(List.of(Fluids.WATER, Fluids.LAVA));
        BuiltInRegistries.FLUID.stream()
                .filter(f -> BuiltInRegistries.FLUID.getKey(f).getNamespace().equals(Nookworks.MODID) && f.isSource(f.defaultFluidState()))
                .forEach(fluids::add);
        return fluids;
    }

    // ---- the plot ----

    private static AABB box(Vec3i size) {
        return AABB.encapsulatingFullBlocks(AT, AT.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1)).inflate(1);
    }

    private static void load(ServerLevel level, BlockPos at, Vec3i size) {
        for (int cx = at.getX() >> 4; cx <= (at.getX() + size.getX()) >> 4; cx++) {
            for (int cz = at.getZ() >> 4; cz <= (at.getZ() + size.getZ()) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /**
     * {@code then} once the plot is {@link #ready}, looked at each tick. The wait is in real time, up to a minute: alone
     * (-PonlyTests=save_compat), the test server runs its ticks as fast as it can, thousands a second, far ahead of the
     * chunk work that gets the plot ticking - which no count of ticks waited for. Each tick waited sleeps a little, so
     * the ticks it takes stay under {@link #WAIT_TICKS}.
     */
    private static void whenReady(GameTestHelper helper, BlockPos at, Vec3i size, long deadline, Runnable then) {
        if (ready(helper.getLevel(), at, size)) {
            then.run();
            return;
        }
        check(helper, System.nanoTime() < deadline, "the fixtures' plot did not load and start ticking in a minute");
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        helper.runAfterDelay(1, () -> whenReady(helper, at, size, deadline, then));
    }

    /**
     * Whether the plot's chunks are ticking and their entities loaded (they load a few ticks after the blocks): an entity
     * set down before is not found.
     */
    private static boolean ready(ServerLevel level, BlockPos at, Vec3i size) {
        for (int cx = at.getX() >> 4; cx <= (at.getX() + size.getX()) >> 4; cx++) {
            for (int cz = at.getZ() >> 4; cz <= (at.getZ() + size.getZ()) >> 4; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.pack(cx, cz)) || !level.isPositionEntityTicking(new BlockPos(cx << 4, at.getY(), cz << 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The plot's chunks kept loaded and ticking, or let go. */
    private static void force(ServerLevel level, BlockPos at, Vec3i size, boolean on) {
        for (int cx = at.getX() >> 4; cx <= (at.getX() + size.getX()) >> 4; cx++) {
            for (int cz = at.getZ() >> 4; cz <= (at.getZ() + size.getZ()) >> 4; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    /** Everything in the plot gone: entities (but players), then blocks, then anything they dropped. */
    private static void clearAll(ServerLevel level, BlockPos at, Vec3i size) {
        AABB box = box(size);
        level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player)).forEach(Entity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(at, at.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            clear(level, pos);
        }
        level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player)).forEach(Entity::discard);
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            level.removeBlockEntity(pos); // nothing spilt
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
        }
    }
}
