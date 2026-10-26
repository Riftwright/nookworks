package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.settleLight;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.compat.jei.InfoPages;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.crop.CornBlock;
import dev.nookworks.crop.RiceBlock;
import dev.nookworks.crop.RicePaniclesBlock;
import dev.nookworks.feature.Features;
import dev.nookworks.feature.WoodVinegar;
import dev.nookworks.furniture.SeatEntity;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModVentilation;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * The r7 review's findings on the kitchen, the data and the core: a chair refused, the Iron Pot's hand-outs, Wood
 * Vinegar on tall corn, our crops and NeoForge's crop events, JEI's pages and the config screen for a pack that
 * switches parts off.
 */
final class R7KitchenDataCoreTests {
    private R7KitchenDataCoreTests() {}

    static void register() {
        NookworksGameTests.test("a_refused_chair_sets_nothing_down_against_it", R7KitchenDataCoreTests::aRefusedChairSetsNothingDown);
        NookworksGameTests.test("iron_pot_hands_out_stacks_a_hand_can_hold", R7KitchenDataCoreTests::ironPotHandsOutWholeStacks);
        NookworksGameTests.test("wood_vinegar_works_on_the_top_of_tall_corn", R7KitchenDataCoreTests::woodVinegarOnTallCorn);
        NookworksGameTests.test("corn_and_rice_grow_by_the_crop_events", R7KitchenDataCoreTests::cropsGrowByTheCropEvents);
        NookworksGameTests.test("jei_pages_read_without_a_switched_off_part", R7KitchenDataCoreTests::jeiPagesWithoutAPart);
        NookworksGameTests.test("a_parts_tooltip_names_what_goes_with_it", R7KitchenDataCoreTests::partTooltipNamesItsDependents);
        NookworksGameTests.test("every_part_has_its_line_on_the_create_world_screen", R7KitchenDataCoreTests::everyPartHasItsLine);
        NookworksGameTests.test("conflicting_mod_lists_can_be_added_to", R7KitchenDataCoreTests::conflictListsCanBeAddedTo);
    }

    /**
     * A chair that will not seat the player - somebody on it already, a ceiling too low - spends the click: the planks in
     * hand are not set down against it (r7 review: the server went on from the chair's PASS to the held item).
     */
    private static void aRefusedChairSetsNothingDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos taken = new BlockPos(1, 1, 3);
        BlockPos low = new BlockPos(4, 1, 3);
        helper.setBlock(taken, ModWoods.CHAIRS.get("oak").get());
        helper.setBlock(low, ModWoods.CHAIRS.get("oak").get());
        helper.setBlock(low.above(), Blocks.STONE); // a ceiling too low for even the lowest seat
        ServerPlayer sitter = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        check(helper, SeatEntity.sit(level, helper.absolutePos(taken), sitter), "the first player sits down");
        ServerPlayer clicker = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(3.5, 8, 0.5));
        clicker.snapTo(aside.x, aside.y, aside.z);
        for (BlockPos chair : List.of(taken, low)) {
            ItemStack planks = new ItemStack(Items.OAK_PLANKS, 4);
            clicker.setItemInHand(InteractionHand.MAIN_HAND, planks);
            BlockPos at = helper.absolutePos(chair);
            clicker.gameMode.useItemOn(clicker, level, planks, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.EAST, 0.375), Direction.EAST, at, false));
            String which = chair == taken ? "the taken chair" : "the chair under a low ceiling";
            check(helper, helper.getBlockState(chair.east()).isAir(), "nothing set down against " + which + ": " + helper.getBlockState(chair.east()));
            check(helper, clicker.getMainHandItem().is(Items.OAK_PLANKS) && clicker.getMainHandItem().getCount() == 4,
                    "the planks stay in hand at " + which + ": " + clicker.getMainHandItem());
            check(helper, !clicker.isPassenger(), "and nobody was seated on " + which);
        }
        helper.succeed();
    }

    /**
     * What the pot hands back that finds no room in the player's inventory is thrown in stacks a hand can hold: three milk
     * buckets taken back out of one slot (one to a stack) went as one item of two, and a hopper that caught it kept one.
     */
    private static void ironPotHandsOutWholeStacks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        IronPotBlockEntity pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        for (int i = 0; i < 3; i++) {
            pot.addIngredient(new ItemStack(Items.MILK_BUCKET));
        }
        check(helper, pot.contents().stream().anyMatch(stack -> stack.is(Items.MILK_BUCKET) && stack.getCount() == 3),
                "three milk buckets in one of the pot's slots: " + pot.contents());
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 by = helper.absoluteVec(new Vec3(3.5, 1, 1.5));
        player.snapTo(by.x, by.y, by.z);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != inventory.getSelectedSlot()) {
                inventory.setItem(slot, new ItemStack(Blocks.STONE, 64)); // full, but for the empty hand the pot wants
            }
        }
        player.setShiftKeyDown(true); // sneaking takes the last ingredient back out
        BlockPos at = helper.absolutePos(pos);
        helper.getBlockState(pos).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        check(helper, player.getMainHandItem().is(Items.MILK_BUCKET), "one bucket goes into the empty hand: " + player.getMainHandItem());
        List<ItemEntity> thrown = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4),
                item -> item.getItem().is(Items.MILK_BUCKET));
        check(helper, thrown.stream().mapToInt(item -> item.getItem().getCount()).sum() == 2, "the other two are thrown: "
                + thrown.stream().map(ItemEntity::getItem).toList());
        check(helper, thrown.stream().allMatch(item -> item.getItem().getCount() <= item.getItem().getMaxStackSize()),
                "each as a stack a hand can hold: " + thrown.stream().map(ItemEntity::getItem).toList());
        helper.succeed();
    }

    /**
     * Corn two blocks high is clicked at eye height, on its top: a bottle of Wood Vinegar used there treats the field
     * under its stalk, as on the stalk or the soil (r7 review: it did nothing).
     */
    private static void woodVinegarOnTallCorn(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
            }
        }
        BlockPos field = new BlockPos(3, 1, 3);
        BlockState corn = ModKitchen.CORN_CROP.get().defaultBlockState().setValue(CornBlock.AGE, 4);
        helper.setBlock(field.above(), corn);
        helper.setBlock(field.above(2), corn.setValue(CornBlock.HALF, DoubleBlockHalf.UPPER));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ItemStack bottle = new ItemStack(ModOil.WOOD_VINEGAR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        BlockPos top = helper.absolutePos(field.above(2));
        bottle.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(top).relative(Direction.NORTH, 0.375), Direction.NORTH, top, false)));
        check(helper, WoodVinegar.isTreated(helper.getLevel(), helper.absolutePos(field)), "the field under the corn is treated");
        check(helper, player.getMainHandItem().is(Items.GLASS_BOTTLE), "and the bottle comes back empty: " + player.getMainHandItem());
        helper.succeed();
    }

    /**
     * Corn, rice and rice's heads ask NeoForge's CropGrowEvent.Pre before they grow, and say so after with Post, as
     * vanilla's crops do: told not to, they do not grow however often they are ticked; told to, they grow at once. A
     * seasons or growth mod rules them as it rules wheat (r7 review: they grew past it).
     */
    private static void cropsGrowByTheCropEvents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corn = new BlockPos(1, 1, 1);
        helper.setBlock(corn.below(), Blocks.FARMLAND);
        helper.setBlock(corn, ModKitchen.CORN_CROP.get());
        // rice in its own water, walled in so the water stays put, on dirt in place of the floor
        BlockPos rice = new BlockPos(5, 1, 1);
        BlockPos headed = new BlockPos(5, 1, 5);
        for (BlockPos plant : List.of(rice, headed)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                helper.setBlock(plant.relative(side), Blocks.STONE);
            }
            helper.setBlock(plant.below(), Blocks.DIRT);
        }
        helper.setBlock(rice, ModCrops.RICE_CROP.get());
        helper.setBlock(headed, ModCrops.RICE_CROP.get().defaultBlockState().setValue(RiceBlock.AGE, RiceBlock.MAX_AGE));
        helper.setBlock(headed.above(), ModCrops.RICE_PANICLES.get());
        helper.runAfterDelay(5, () -> {
            List<BlockPos> plants = List.of(corn, rice, headed.above());
            for (BlockPos plant : plants) {
                settleLight(helper, plant); // the light worked out, in time and not in ticks
                BlockPos at = helper.absolutePos(plant);
                check(helper, Math.min(level.getRawBrightness(at, 0), level.getRawBrightness(at.above(), 0)) >= 9, "daylight on " + plant);
            }
            AABB here = helper.getBounds();
            CropGrowEvent.Pre.Result[] answer = {CropGrowEvent.Pre.Result.DO_NOT_GROW};
            List<BlockPos> asked = new ArrayList<>();
            List<BlockPos> told = new ArrayList<>();
            Consumer<CropGrowEvent.Pre> pre = event -> {
                if (here.contains(Vec3.atCenterOf(event.getPos()))) {
                    asked.add(event.getPos().immutable());
                    event.setResult(answer[0]);
                }
            };
            Consumer<CropGrowEvent.Post> post = event -> {
                if (here.contains(Vec3.atCenterOf(event.getPos()))) {
                    told.add(event.getPos().immutable());
                }
            };
            NeoForge.EVENT_BUS.addListener(CropGrowEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(CropGrowEvent.Post.class, post);
            try {
                Map<BlockPos, BlockState> before = new HashMap<>();
                plants.forEach(plant -> before.put(plant, helper.getBlockState(plant)));
                for (int tick = 0; tick < 300; tick++) {
                    for (BlockPos plant : plants) {
                        BlockPos at = helper.absolutePos(plant);
                        level.getBlockState(at).randomTick(level, at, level.getRandom());
                    }
                }
                for (BlockPos plant : plants) {
                    check(helper, helper.getBlockState(plant) == before.get(plant), plant + " grew though the event said no: "
                            + before.get(plant) + " -> " + helper.getBlockState(plant));
                    check(helper, asked.contains(helper.absolutePos(plant)), plant + " never asked the event");
                }
                check(helper, helper.getBlockState(rice.above()).isAir(), "the young rice did not head up");
                check(helper, told.isEmpty(), "and nothing said it grew: " + told);

                answer[0] = CropGrowEvent.Pre.Result.GROW;
                for (BlockPos plant : plants) {
                    BlockPos at = helper.absolutePos(plant);
                    level.getBlockState(at).randomTick(level, at, level.getRandom());
                }
                check(helper, helper.getBlockState(corn).getValue(CornBlock.AGE) == before.get(corn).getValue(CornBlock.AGE) + 1,
                        "told to, the corn grows a stage: " + helper.getBlockState(corn));
                check(helper, helper.getBlockState(rice).getValue(RiceBlock.AGE) == before.get(rice).getValue(RiceBlock.AGE) + 1,
                        "the rice too: " + helper.getBlockState(rice));
                check(helper, helper.getBlockState(headed.above()).getValue(RicePaniclesBlock.AGE)
                        == before.get(headed.above()).getValue(RicePaniclesBlock.AGE) + 1, "and its heads: " + helper.getBlockState(headed.above()));
                for (BlockPos plant : plants) {
                    check(helper, told.contains(helper.absolutePos(plant)), plant + " said it grew");
                }
            } finally {
                NeoForge.EVENT_BUS.unregister(pre);
                NeoForge.EVENT_BUS.unregister(post);
            }
            helper.succeed();
        });
    }

    /**
     * A JEI page that names a thing of a part the pack switched off reads otherwise: Soap is made in the crafting grid
     * without the kitchen, the Electric Furnace keeps its experience without Experience Pipes, the fan is not wanted for a
     * Barter Terminal that is not there (gone with the Mob Bottle too), and the Spawner's page about the hammers goes with
     * them. Every wording is written in every language (r7 review: the pages said what could not be done).
     */
    private static void jeiPagesWithoutAPart(GameTestHelper helper) {
        Map<Item, String> wordings = Map.of(ModOil.SOAP.get(), "soap", ModOil.METHANOL.get(), "methanol",
                ModOil.BIODIESEL_BOTTLE.get(), "biodiesel_bottle", ModOil.OIL_PRESS_ITEM.get(), "oil_press",
                ModItems.ELECTRIC_FURNACE.get(), "electric_furnace", ModItems.RETORT.get(), "retort_experience",
                ModVentilation.VENT_FAN_ITEM.get(), "ventilation_fan", ModVentilation.DUCT_ITEM.get(), "duct");
        Map<String, String> partOf = Map.of("soap", "kitchen", "methanol", "kitchen", "biodiesel_bottle", "kitchen",
                "oil_press", "kitchen", "electric_furnace", "liquid_experience", "retort_experience", "liquid_experience",
                "ventilation_fan", "barter_terminal", "duct", "barter_terminal");
        Map<Item, List<String>> all = pages(part -> true);
        for (var page : wordings.entrySet()) {
            String own = "jei.nookworks.info." + page.getValue();
            check(helper, all.getOrDefault(page.getKey(), List.of()).contains(own), "with every part on, " + own + ": " + all.get(page.getKey()));
            String part = partOf.get(page.getValue());
            Map<Item, List<String>> without = pages(name -> !name.equals(part));
            String other = own + ".without_" + part;
            check(helper, without.getOrDefault(page.getKey(), List.of()).equals(List.of(other)),
                    "with " + part + " off, " + other + ": " + without.get(page.getKey()));
        }
        // the Barter Terminal goes with the Mob Bottle, and the fan's page with it
        Map<Item, List<String>> noBottle = pages(name -> Features.enabled(name, part -> !part.equals("mob_bottle")));
        check(helper, noBottle.get(ModVentilation.VENT_FAN_ITEM.get()).equals(List.of("jei.nookworks.info.ventilation_fan.without_barter_terminal")),
                "with the Mob Bottle off, the fan's page has no Barter Terminal: " + noBottle.get(ModVentilation.VENT_FAN_ITEM.get()));
        check(helper, all.containsKey(Items.SPAWNER), "the Spawner's page is there with the hammers");
        check(helper, !pages(name -> !name.equals("hammers")).containsKey(Items.SPAWNER), "and gone without them");

        List<String> problems = new ArrayList<>();
        List<Set<String>> languages = DataTests.ALL_LANGUAGES.stream().map(DataTests::lang).toList();
        for (Predicate<String> on : List.<Predicate<String>>of(part -> true, part -> false)) {
            pages(on).values().forEach(keys -> keys.forEach(key -> {
                if (languages.stream().anyMatch(language -> !language.contains(key))) {
                    problems.add(key);
                }
            }));
        }
        check(helper, problems.isEmpty(), "pages not written in every language: " + problems);
        helper.succeed();
    }

    /** Each item's JEI pages, by their text's key, with the parts {@code on} says are on. */
    private static Map<Item, List<String>> pages(Predicate<String> on) {
        Map<Item, List<String>> pages = new HashMap<>();
        InfoPages.each((item, text) -> pages.computeIfAbsent(item, i -> new ArrayList<>()).add(key(text)), on);
        return pages;
    }

    private static String key(Component text) {
        return text.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : String.valueOf(text);
    }

    /**
     * A part's switch in the config screen says, in every language, what goes with it: switched off, a part another
     * needs takes that one along (the Mob Bottle the Barter Terminal, the Magnets the Absorption Pad), and the pack
     * author reads only the tooltip (r7 review: the Mob Bottle's did not say).
     */
    private static void partTooltipNamesItsDependents(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (String code : DataTests.ALL_LANGUAGES) {
            JsonObject lang = texts(code);
            for (var need : Features.NEEDS.entrySet()) {
                String dependent = need.getKey();
                String nameKey = Features.PARTS.containsKey(dependent) ? "nookworks.configuration.features." + dependent
                        : BuiltInRegistries.ITEM.getValue(Identifier.parse(dependent)).getDescriptionId();
                String name = lang.get(nameKey).getAsString();
                for (String part : need.getValue()) {
                    String tooltip = lang.get("nookworks.configuration.features." + part + ".tooltip").getAsString();
                    if (!tooltip.contains(name)) {
                        problems.add(code + ": " + part + "'s tooltip does not name " + name + " (" + tooltip + ")");
                    }
                }
            }
        }
        check(helper, problems.isEmpty(), String.join(" | ", problems));
        helper.succeed();
    }

    /**
     * Every part a world can switch off has its one line under its name on the Create World screen, in every language
     * (WorldFeaturesScreen): the nine parts added in 1.1 and 1.2 had none, and the screen showed the key; English lacked
     * them too, so check_lang.py, which holds the others to English, could not see it.
     */
    private static void everyPartHasItsLine(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (String code : DataTests.ALL_LANGUAGES) {
            JsonObject lang = texts(code);
            for (String part : Features.PARTS.keySet()) {
                for (String key : List.of("nookworks.world_features." + part, "nookworks.configuration.features." + part,
                        "nookworks.configuration.features." + part + ".tooltip")) {
                    if (!lang.has(key)) {
                        missing.add(code + " " + key);
                    }
                }
            }
        }
        check(helper, missing.isEmpty(), "lines missing: " + String.join(", ", missing));
        helper.succeed();
    }

    private static JsonObject texts(String code) {
        String path = "/assets/" + Nookworks.MODID + "/lang/" + code + ".json";
        try (var in = Nookworks.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /**
     * The conflicting-mods lists have an Add button in the config screen, as the timber list's tooltip promises: a list
     * defined with nothing to add has none (r7 review: an id could only go in by editing the file).
     */
    private static void conflictListsCanBeAddedTo(GameTestHelper helper) {
        Map<String, ModConfigSpec.ConfigValue<List<? extends String>>> lists = Map.of("timber", NookworksConfig.TIMBER_CONFLICTS,
                "veinMining", NookworksConfig.VEIN_CONFLICTS, "hammer", NookworksConfig.HAMMER_CONFLICTS,
                "magnet", NookworksConfig.MAGNET_CONFLICTS, "buildingWand", NookworksConfig.WAND_CONFLICTS);
        for (var list : lists.entrySet()) {
            check(helper, list.getValue().getSpec() instanceof ModConfigSpec.ListValueSpec spec && spec.getNewElementSupplier() != null,
                    list.getKey() + ".conflictingMods has an Add button");
        }
        helper.succeed();
    }
}
