package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.cooking.ElectricStoveBlock;
import dev.nookworks.cooking.ElectricStoveBlockEntity;
import dev.nookworks.machine.MachineAlerts;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PlacedBowlsBlock;
import dev.nookworks.cooking.PlacedBowlsBlockEntity;
import dev.nookworks.cooking.PlateBlockEntity;
import dev.nookworks.cooking.Plating;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.cooking.Serving;
import dev.nookworks.cooking.TrayBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The table laid (the user, 2026-11-14): dishes in bowls set straight down; fried rice served on a plate; a plate and a
 * tray holding as much of each food as that food allows; a tray lifted with its food and set down again; an Electric
 * Stove under the pot. Through all of it no plate, bowl or dish is made or lost.
 */
final class TableSettingTests {
    private TableSettingTests() {}

    static final String[] NEED_KITCHEN = {"bowls_are_set_straight_down_two_to_a_block", "a_plate_takes_no_bowl_but_fried_rice_is_served_on_it",
            "a_plate_in_hand_serves_from_the_frying_pan", "a_dish_on_a_plate_is_one_thing_with_its_plate",
            "a_plate_holds_as_many_as_its_food_allows", "a_tray_gives_each_food_its_room", "a_hopper_lays_cookies_together_on_a_tray",
            "a_tray_is_lifted_with_its_food_and_set_down_again", "an_electric_stove_heats_a_pot_while_it_has_power",
            "a_plate_goes_on_a_tray_with_its_food", "a_doughnut_tower_is_as_tall_as_it_stands",
            "a_pot_off_the_heat_waits_where_it_was"};

    static void register() {
        NookworksGameTests.test(NEED_KITCHEN[0], TableSettingTests::bowls);
        NookworksGameTests.test(NEED_KITCHEN[1], TableSettingTests::plating);
        NookworksGameTests.test(NEED_KITCHEN[2], 400, TableSettingTests::fromThePan);
        NookworksGameTests.test(NEED_KITCHEN[3], TableSettingTests::platedIsOne);
        NookworksGameTests.test(NEED_KITCHEN[4], TableSettingTests::plateCounts);
        NookworksGameTests.test(NEED_KITCHEN[5], TableSettingTests::trayRooms);
        NookworksGameTests.test(NEED_KITCHEN[6], TableSettingTests::trayHopper);
        NookworksGameTests.test(NEED_KITCHEN[7], TableSettingTests::trayLifted);
        NookworksGameTests.test(NEED_KITCHEN[8], 600, TableSettingTests::stove);
        NookworksGameTests.test(NEED_KITCHEN[9], TableSettingTests::platesOnTrays);
        NookworksGameTests.test(NEED_KITCHEN[10], TableSettingTests::towerShape);
        NookworksGameTests.test(NEED_KITCHEN[11], 600, TableSettingTests::waitsOffTheHeat);
    }

    /**
     * A cutlet part fried on an Electric Stove that runs out of power waits where it was, and goes on when the power is
     * back: it started again from nothing (1.7 review).
     */
    private static void waitsOffTheHeat(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), ModCooking.ELECTRIC_STOVE.get());
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var stove = helper.getBlockEntity(pos.below(), ElectricStoveBlockEntity.class);
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.OIL, 1_000);
        check(helper, Move.insert(pot.itemHandler(), ItemResource.of(ModCooking.BREADED_PORK.get()), 1) == 1, "a cutlet goes in");
        stove.energy().set(ElectricStoveBlockEntity.CAPACITY);
        int[] before = {0};
        helper.runAfterDelay(80, () -> {
            before[0] = pot.progress();
            check(helper, before[0] > 0 && pot.dish().isEmpty(), "part fried: " + before[0]);
            stove.energy().set(0);
            helper.runAfterDelay(60, () -> {
                int held = pot.progress();
                check(helper, held >= before[0] && held <= before[0] + 5,
                        "out of power it waits where it was: " + before[0] + " then " + held);
                stove.energy().set(ElectricStoveBlockEntity.CAPACITY);
                helper.succeedWhen(() -> check(helper, pot.dish().is(ModCooking.TONKATSU.get()), "and goes on to the cutlet: " + pot.dish()));
            });
        });
    }

    /**
     * What is on a Plate is as tall as it stands, to stand on and to click (the user, 2026-11-14, play-testing: a
     * doughnut tower had no shape - 「埋まってしまう」 - and clicks at it went through it): an empty plate is a plate's
     * height, a doughnut stands on it, six of them a tower nearly the block's height; and the tower is solid to walk into.
     */
    private static void towerShape(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.OAK_PLANKS);
        helper.setBlock(at, ModKitchen.PLATE.get());
        var plate = helper.getBlockEntity(at, PlateBlockEntity.class);
        BlockPos abs = helper.absolutePos(at);
        var level = helper.getLevel();
        java.util.function.DoubleSupplier top = () -> level.getBlockState(abs).getShape(level, abs).max(Direction.Axis.Y) * 16;
        java.util.function.DoubleSupplier solid = () -> level.getBlockState(abs).getCollisionShape(level, abs).max(Direction.Axis.Y) * 16;
        check(helper, top.getAsDouble() <= 1.6, "an empty plate is a plate's height: " + top.getAsDouble());
        ServerPlayer player = player(helper, new ArrayList<>());
        ItemStack doughnuts = new ItemStack(ModKitchen.DONUT.get(), 6);
        use(helper, player, at, Direction.UP, doughnuts);
        double one = top.getAsDouble();
        check(helper, one > 2.5 && one < 4.5, "one doughnut stands on it: " + one);
        for (int i = 0; i < 5; i++) {
            use(helper, player, at, Direction.UP, doughnuts);
        }
        check(helper, plate.food().getCount() == 6, "six doughnuts: " + plate.food());
        double six = top.getAsDouble();
        check(helper, six > 12 && six <= 16, "six are a tower, as tall as drawn: " + six);
        check(helper, solid.getAsDouble() == six, "and as solid: " + solid.getAsDouble());
        // a look at the tower's side, high up, meets it - where it went through to what was behind
        Vec3 high = Vec3.atLowerCornerOf(abs).add(0.5, 10.0 / 16, 0.18);
        var hit = level.getBlockState(abs).getShape(level, abs).clip(high.add(0, 0, -1), high.add(0, 0, 1), abs);
        check(helper, hit != null, "a line at the tower's middle height meets its shape");

        // a cake goes on a plate too, whole, and stands as tall as it is drawn (the user, the same evening:
        // 「ケーキだけは皿に載せられない、トレイに置こうとするとアイコンになっちゃうの、悲しいな…」)
        BlockPos cakeAt = new BlockPos(3, 2, 1);
        helper.setBlock(cakeAt.below(), Blocks.OAK_PLANKS);
        helper.setBlock(cakeAt, ModKitchen.PLATE.get());
        var cakePlate = helper.getBlockEntity(cakeAt, PlateBlockEntity.class);
        ItemStack cake = new ItemStack(Items.CAKE, 2);
        use(helper, player, cakeAt, Direction.UP, cake);
        check(helper, cakePlate.food().is(Items.CAKE) && cakePlate.food().getCount() == 1 && cake.getCount() == 1,
                "a cake on the plate, one: " + cakePlate.food());
        BlockPos cakeAbs = helper.absolutePos(cakeAt);
        double cakeTop = level.getBlockState(cakeAbs).getShape(level, cakeAbs).max(Direction.Axis.Y) * 16;
        check(helper, cakeTop > 5 && cakeTop < 8, "as tall as the cake: " + cakeTop);
        check(helper, TrayBlockEntity.fits(new ItemStack(Items.CAKE)) && Serving.of(new ItemStack(Items.CAKE)).room() == Serving.Room.HALF,
                "and on a tray it takes half the board");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, List<Component> said) {
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, said);
        Vec3 near = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.snapTo(near.x, near.y, near.z);
        return player;
    }

    /** A click of {@code held} (left in the hand afterwards) on {@code pos}'s {@code face}, as the game makes it. */
    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos at = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(at).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, at, false));
    }

    private static int has(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static List<ItemEntity> dropped(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2));
    }

    /**
     * Sneaking, a bowl of soup is set down on the block clicked, a bowl of rice beside it, and no third; not without
     * sneaking (it is eaten), nor on a block's side. An empty hand takes the last one back, and the last of all takes the
     * block away. A Suspicious Stew stays the stew it was.
     */
    private static void bowls(GameTestHelper helper) {
        BlockPos table = new BlockPos(1, 1, 1);
        BlockPos at = table.above();
        helper.setBlock(table, Blocks.OAK_PLANKS);
        ServerPlayer player = player(helper, new ArrayList<>());
        ItemStack soup = new ItemStack(ModCooking.VEGETABLE_SOUP.get(), 3);
        use(helper, player, table, Direction.UP, soup);
        check(helper, helper.getBlockState(at).isAir(), "not without sneaking");
        player.setShiftKeyDown(true);
        use(helper, player, table, Direction.NORTH, soup);
        check(helper, helper.getBlockState(at).isAir() && soup.getCount() == 3, "not on a block's side");
        use(helper, player, table, Direction.UP, soup);
        check(helper, helper.getBlockState(at).is(ModKitchen.PLACED_BOWLS.get()) && soup.getCount() == 2, "set down on top: " + helper.getBlockState(at));
        ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW);
        stew.set(DataComponents.CUSTOM_NAME, Component.literal("odd"));
        use(helper, player, table, Direction.UP, stew);
        BlockState two = helper.getBlockState(at);
        check(helper, stew.isEmpty() && two.getValue(PlacedBowlsBlock.BOWLS) == 2, "a second beside it: " + two);
        use(helper, player, table, Direction.UP, soup);
        use(helper, player, at, Direction.NORTH, soup);
        check(helper, soup.getCount() == 2 && helper.getBlockState(at).getValue(PlacedBowlsBlock.BOWLS) == 2, "no third");
        var bowls = helper.getBlockEntity(at, PlacedBowlsBlockEntity.class);
        check(helper, bowls.bowl(1).is(Items.SUSPICIOUS_STEW) && bowls.bowl(1).has(DataComponents.CUSTOM_NAME), "the stew as it was set down");
        check(helper, !PlacedBowlsBlock.setDown(helper.getLevel(), helper.absolutePos(table), Direction.UP, player, new ItemStack(ModCooking.TONKATSU.get())),
                "a cutlet has no dish of its own: not on the bare table");

        player.setShiftKeyDown(false);
        player.getInventory().clearContent();
        use(helper, player, at, Direction.NORTH, ItemStack.EMPTY);
        check(helper, has(player, Items.SUSPICIOUS_STEW) == 1 && helper.getBlockState(at).getValue(PlacedBowlsBlock.BOWLS) == 1
                && player.getMainHandItem().isEmpty(), "an empty hand takes the last one, into the inventory");
        use(helper, player, at, Direction.NORTH, ItemStack.EMPTY);
        check(helper, has(player, ModCooking.VEGETABLE_SOUP.get()) == 1 && helper.getBlockState(at).isAir(), "and the first, and the block is gone");
        check(helper, dropped(helper, at).isEmpty(), "nothing was dropped on the way");
        helper.succeed();
    }

    /**
     * A Plate set on the table takes no bowl of soup and says how a bowl is set down; a bowl of fried rice is served onto
     * it and the bowl handed back; taken up by hand, dish and plate go as one thing and the table is bare.
     */
    private static void plating(GameTestHelper helper) {
        BlockPos table = new BlockPos(1, 1, 1);
        BlockPos at = table.above();
        helper.setBlock(table, Blocks.OAK_PLANKS);
        helper.setBlock(at, ModKitchen.PLATE.get());
        var plate = helper.getBlockEntity(at, PlateBlockEntity.class);
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        ItemStack soup = new ItemStack(ModCooking.VEGETABLE_SOUP.get());
        use(helper, player, at, Direction.UP, soup);
        check(helper, soup.getCount() == 1 && plate.isEmpty() && said.size() == 1, "a bowl of soup goes on no plate, and it is said: " + said);
        check(helper, !PlateBlockEntity.fits(new ItemStack(Items.MUSHROOM_STEW)) && TrayBlockEntity.fits(new ItemStack(Items.MUSHROOM_STEW)),
                "vanilla's stews too; a tray still takes them");

        ItemStack rice = new ItemStack(ModCooking.FRIED_RICE.get(), 2);
        use(helper, player, at, Direction.UP, rice);
        check(helper, plate.food().is(ModKitchen.PLATED_FRIED_RICE.get()) && plate.food().getCount() == 1 && plate.plated(), "served on the plate: " + plate.food());
        check(helper, rice.getCount() == 1 && has(player, Items.BOWL) == 1, "one bowl of it used, its bowl handed back");
        use(helper, player, at, Direction.UP, rice);
        check(helper, rice.getCount() == 1 && said.size() == 2, "a plate served takes no second dish, and says so: " + said);

        player.getInventory().clearContent();
        use(helper, player, at, Direction.UP, ItemStack.EMPTY);
        check(helper, has(player, ModKitchen.PLATED_FRIED_RICE.get()) == 1 && helper.getBlockState(at).isAir(), "taken up plate and all");
        check(helper, has(player, ModKitchen.PLATE_ITEM.get()) == 0 && dropped(helper, at).isEmpty(), "and no plate besides");
        ItemStack eaten = Plating.emptied(new ItemStack(ModKitchen.PLATED_FRIED_RICE.get()));
        check(helper, eaten.is(ModKitchen.PLATE_ITEM.get()), "eaten, it leaves its Plate: " + eaten);
        check(helper, Plating.of(new ItemStack(ModCooking.STIR_FRIED_VEGETABLES.get())).is(ModKitchen.PLATED_STIR_FRIED_VEGETABLES.get())
                && Plating.of(new ItemStack(ModCooking.VEGETABLE_SOUP.get())).isEmpty(), "stir-fried vegetables too; a soup stays in its bowl");
        helper.succeed();
    }

    /**
     * A bowl of rice and an egg fried in the pan, and a Plate in hand: the fried rice comes out on the plate, one plate
     * used; the bowl its rice came in is still in the pan for the next empty hand. A Plate lifted with food on it is not
     * served onto: it keeps its food, and the dish stays in the pan.
     */
    private static void fromThePan(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, ModCooking.FRYING_PAN.get());
        var pan = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        pan.addLiquid(PotRecipe.Liquid.OIL, 250);
        check(helper, Move.insert(pan.itemHandler(), ItemResource.of(ModCooking.COOKED_RICE.get()), 1) == 1
                && Move.insert(pan.itemHandler(), ItemResource.of(Items.EGG), 1) == 1, "rice and an egg go in");
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        helper.succeedWhen(() -> {
            check(helper, pan.dish().is(ModCooking.FRIED_RICE.get()), "fried rice is done");
            ItemStack laden = new ItemStack(ModKitchen.PLATE_ITEM.get());
            Plating.setOnPlate(laden, new ItemStack(Items.COOKIE, 3));
            use(helper, player, pos, Direction.UP, laden);
            ItemStack held = player.getMainHandItem();
            check(helper, Plating.isPlate(held) && Plating.onPlate(held).is(Items.COOKIE) && Plating.onPlate(held).getCount() == 3,
                    "a Plate with cookies on it keeps them: " + held + " " + Plating.onPlate(held));
            check(helper, pan.dish().is(ModCooking.FRIED_RICE.get()) && has(player, ModKitchen.PLATED_FRIED_RICE.get()) == 0,
                    "and the fried rice stays in the pan");
            check(helper, said.stream().anyMatch(c -> c.getString().equals(Component.translatable("message.nookworks.plate.taken").getString())),
                    "told the plate has a dish on it: " + said);
            player.getInventory().clearContent();
            ItemStack plates = new ItemStack(ModKitchen.PLATE_ITEM.get(), 2);
            use(helper, player, pos, Direction.UP, plates);
            check(helper, has(player, ModKitchen.PLATED_FRIED_RICE.get()) == 1 && has(player, ModKitchen.PLATE_ITEM.get()) == 1,
                    "served onto one of the plates: " + player.getInventory().getNonEquipmentItems());
            check(helper, pan.dish().isEmpty() && helper.getBlockState(pos.above()).isAir(), "the pan's dish is out, and no plate was set down on the pan");
            List<ItemStack> freed = pan.takeFreed();
            check(helper, freed.size() == 1 && freed.getFirst().is(Items.BOWL), "the rice's bowl is still in the pan: " + freed);
        });
    }

    /**
     * A dish on a plate set down (sneaking) is a Plate block with the dish on it; knocked off the table it drops as the
     * one item, with no Plate beside it. Used on an empty Plate already on the table, its own plate is handed back.
     */
    private static void platedIsOne(GameTestHelper helper) {
        BlockPos table = new BlockPos(1, 1, 1);
        BlockPos at = table.above();
        helper.setBlock(table, Blocks.OAK_PLANKS);
        ServerPlayer player = player(helper, new ArrayList<>());
        ItemStack dish = new ItemStack(ModKitchen.PLATED_STIR_FRIED_VEGETABLES.get(), 2);
        player.setShiftKeyDown(true);
        use(helper, player, table, Direction.UP, dish);
        check(helper, helper.getBlockState(at).is(ModKitchen.PLATE.get()) && dish.getCount() == 1, "set down with its plate: " + helper.getBlockState(at));
        var plate = helper.getBlockEntity(at, PlateBlockEntity.class);
        check(helper, plate.food().is(ModKitchen.PLATED_STIR_FRIED_VEGETABLES.get()) && plate.plated(), "the dish is on it");
        helper.getLevel().destroyBlock(helper.absolutePos(at), true, player);
        List<ItemEntity> drops = dropped(helper, at);
        check(helper, drops.size() == 1 && drops.getFirst().getItem().is(ModKitchen.PLATED_STIR_FRIED_VEGETABLES.get())
                && drops.getFirst().getItem().getCount() == 1, "knocked off, one item - the dish on its plate - and no Plate beside it: "
                        + drops.stream().map(ItemEntity::getItem).toList());
        drops.forEach(ItemEntity::discard);
        // Broken by a player's own hand the block goes first and its drops are asked for after (1.7 review): still the one item
        player.setShiftKeyDown(true);
        use(helper, player, table, Direction.UP, dish);
        check(helper, helper.getBlockEntity(at, PlateBlockEntity.class).plated(), "set down again");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.destroyBlock(helper.absolutePos(at));
        List<ItemEntity> byHand = dropped(helper, at);
        check(helper, helper.getBlockState(at).isAir() && byHand.size() == 1
                && byHand.getFirst().getItem().is(ModKitchen.PLATED_STIR_FRIED_VEGETABLES.get()),
                "broken by hand, the dish on its plate and no Plate beside it: " + byHand.stream().map(ItemEntity::getItem).toList());
        byHand.forEach(ItemEntity::discard);
        dish.setCount(1);

        player.setShiftKeyDown(false);
        helper.setBlock(at, ModKitchen.PLATE.get());
        player.getInventory().clearContent();
        use(helper, player, at, Direction.UP, dish);
        var second = helper.getBlockEntity(at, PlateBlockEntity.class);
        check(helper, second.plated() && dish.isEmpty() && has(player, ModKitchen.PLATE_ITEM.get()) == 1,
                "slid onto a plate already there, its own plate handed back");
        helper.succeed();
    }

    /** Five cookies to a plate and no sixth, nor an apple beside them; taken off one at a time; sneaking, the plate is lifted with the rest. */
    private static void plateCounts(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.OAK_PLANKS);
        helper.setBlock(at, ModKitchen.PLATE.get());
        var plate = helper.getBlockEntity(at, PlateBlockEntity.class);
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        ItemStack cookies = new ItemStack(Items.COOKIE, 8);
        for (int i = 0; i < 6; i++) {
            use(helper, player, at, Direction.UP, cookies);
        }
        check(helper, plate.food().is(Items.COOKIE) && plate.food().getCount() == 5 && cookies.getCount() == 3, "five cookies, no sixth: " + plate.food());
        check(helper, said.size() == 1 && plate.isFull(), "and the sixth is told the plate holds no more: " + said);
        check(helper, !plate.takes(new ItemStack(Items.APPLE)), "one kind to a plate");
        player.getInventory().clearContent();
        use(helper, player, at, Direction.UP, ItemStack.EMPTY);
        check(helper, has(player, Items.COOKIE) == 1 && plate.food().getCount() == 4, "an empty hand takes one");
        player.setShiftKeyDown(true);
        use(helper, player, at, Direction.UP, ItemStack.EMPTY);
        ItemStack lifted = player.getMainHandItem();
        check(helper, Plating.isPlate(lifted) && Plating.onPlate(lifted).is(Items.COOKIE) && Plating.onPlate(lifted).getCount() == 4
                && helper.getBlockState(at).isAir(), "sneaking, the plate comes up with the four left on it: " + lifted);
        player.setShiftKeyDown(false);
        check(helper, Serving.of(new ItemStack(Items.APPLE)).plate() == 2 && Serving.of(new ItemStack(ModCooking.SALT_ONIGIRI.get())).plate() == 3
                && Serving.of(new ItemStack(ModCooking.TONKATSU.get())).plate() == 1 && Serving.of(new ItemStack(Items.ROTTEN_FLESH)).plate() == 1,
                "two apples, three onigiri, one cutlet; one of whatever is not listed");
        check(helper, Serving.of(new ItemStack(ModKitchen.DONUT.get())).plate() == 6, "six doughnuts in a tower");
        check(helper, Serving.of(new ItemStack(Items.COOKIE)).leaning() && !Serving.of(new ItemStack(Items.APPLE)).leaning(),
                "cookies lean one on the next on a plate; apples lie side by side");
        helper.succeed();
    }

    private static TrayBlockEntity tray(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.OAK_PLANKS);
        helper.setBlock(at, ModKitchen.WOODEN_TRAY.get());
        return helper.getBlockEntity(at, TrayBlockEntity.class);
    }

    private static boolean on(GameTestHelper helper, TrayBlockEntity tray, Item item) {
        return tray.place(helper.getLevel(), null, new ItemStack(item));
    }

    /**
     * A fish takes half a tray: two and no third, or one with a bowl of rice and a bowl of soup before it. Cookies lie
     * four to a quarter, sixteen to a tray. A dish on its own plate takes a quarter, as a plate does. Four bowls still go
     * on, as ever.
     */
    private static void trayRooms(GameTestHelper helper) {
        var two = tray(helper, new BlockPos(0, 2, 0));
        check(helper, on(helper, two, Items.SALMON) && on(helper, two, Items.SALMON), "two fish");
        check(helper, !on(helper, two, Items.SALMON) && !on(helper, two, ModCooking.COOKED_RICE.get()) && !two.isFull(),
                "and nothing more, with two slots still empty: " + two.items());

        var meal = tray(helper, new BlockPos(2, 2, 0));
        check(helper, on(helper, meal, ModCooking.COOKED_RICE.get()) && on(helper, meal, ModCooking.VEGETABLE_SOUP.get())
                && on(helper, meal, Items.SALMON), "rice, soup and a fish");
        check(helper, !on(helper, meal, Items.APPLE), "and the board is full");
        float[][] places = Serving.trayPlaces(meal.items());
        check(helper, places[2][0] == Serving.MIDDLE && places[2][1] == Serving.BACK, "the fish lies across the back");
        check(helper, places[0][1] == Serving.FRONT && places[1][1] == Serving.FRONT && places[0][0] == Serving.LEFT && places[1][0] == Serving.RIGHT,
                "the rice and the soup before it, left and right");

        var sheet = tray(helper, new BlockPos(0, 2, 2));
        int cookies = 0;
        while (cookies < 20 && on(helper, sheet, Items.COOKIE)) {
            cookies++;
        }
        check(helper, cookies == 16 && sheet.items().stream().allMatch(stack -> stack.is(Items.COOKIE) && stack.getCount() == 4),
                "sixteen cookies, four to a quarter: " + sheet.items());
        ItemStack one = sheet.take();
        check(helper, one.getCount() == 1 && sheet.items().get(3).getCount() == 3, "taken off one at a time");

        var alone = tray(helper, new BlockPos(2, 2, 2));
        check(helper, on(helper, alone, ModKitchen.PLATED_FRIED_RICE.get()) && on(helper, alone, ModCooking.VEGETABLE_SOUP.get())
                && on(helper, alone, ModCooking.COOKED_RICE.get()), "a dish on its own plate shares the tray with the bowls");
        var bowls = tray(helper, new BlockPos(1, 2, 1));
        check(helper, on(helper, bowls, ModCooking.COOKED_RICE.get()) && on(helper, bowls, Items.MUSHROOM_STEW)
                && on(helper, bowls, ModKitchen.UDON.get()) && on(helper, bowls, ModCooking.VEGETABLE_SOUP.get()) && bowls.isFull(), "four bowls, as ever");
        check(helper, !on(helper, bowls, ModKitchen.PLATED_FRIED_RICE.get()), "and no plate on top of them");
        helper.succeed();
    }

    /** As a hopper sees the tray: cookies go in four to a slot, sixteen in all; a cutlet into an empty tray, and no third. */
    private static void trayHopper(GameTestHelper helper) {
        var sheet = tray(helper, new BlockPos(0, 2, 0));
        check(helper, Move.insert(sheet.itemHandler(), ItemResource.of(Items.COOKIE), 40) == 16, "sixteen cookies go in: " + sheet.items());
        check(helper, sheet.items().stream().allMatch(stack -> stack.getCount() == 4), "four to a slot");
        check(helper, Move.extract(sheet.itemHandler(), ItemResource.of(Items.COOKIE), 5) == 5
                && sheet.items().stream().mapToInt(ItemStack::getCount).sum() == 11, "and come out by the piece");
        var two = tray(helper, new BlockPos(2, 2, 0));
        check(helper, Move.insert(two.itemHandler(), ItemResource.of(Items.SALMON), 5) == 2, "two fish go in, no third: " + two.items());
        check(helper, Move.insert(two.itemHandler(), ItemResource.of(ModCooking.TONKATSU.get()), 1) == 0
                && Move.insert(tray(helper, new BlockPos(0, 2, 2)).itemHandler(), ItemResource.of(ModCooking.TONKATSU.get()), 1) == 0,
                "a hopper lays no cutlet on the bare board");
        helper.succeed();
    }

    /**
     * Sneaking, an empty hand lifts a tray with all that is on it: the block is gone, nothing drops, and the hand holds
     * the tray's item with the food on it, one to a stack. Set down again, everything is where it was.
     */
    private static void trayLifted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(1, 2, 1);
        var tray = tray(helper, at);
        on(helper, tray, ModCooking.COOKED_RICE.get());
        on(helper, tray, ModCooking.VEGETABLE_SOUP.get());
        for (int i = 0; i < 3; i++) {
            on(helper, tray, Items.COOKIE);
        }
        on(helper, tray, ModKitchen.BREAD_DOUGH.get());
        List<ItemStack> before = tray.items().stream().map(ItemStack::copy).toList();
        ServerPlayer player = player(helper, new ArrayList<>());
        player.getInventory().clearContent();
        player.setShiftKeyDown(true);
        use(helper, player, at, Direction.UP, ItemStack.EMPTY);
        ItemStack lifted = player.getMainHandItem();
        check(helper, helper.getBlockState(at).isAir() && dropped(helper, at).isEmpty(), "the tray is off the table, and nothing fell");
        check(helper, lifted.is(ModKitchen.WOODEN_TRAY_ITEM.get()) && lifted.getMaxStackSize() == 1, "in the hand, one to a stack: " + lifted);
        ItemContainerContents kept = lifted.get(DataComponents.CONTAINER);
        check(helper, kept != null && kept.nonEmptyItemCopyStream().mapToInt(ItemStack::getCount).sum() == 6, "with its six things on it");
        check(helper, new ItemStack(ModKitchen.WOODEN_TRAY_ITEM.get()).getMaxStackSize() == 64, "an empty tray stacks as ever");

        BlockPos to = new BlockPos(1, 1, 0);
        helper.setBlock(to, Blocks.OAK_PLANKS);
        use(helper, player, to, Direction.UP, lifted);
        var down = helper.getBlockEntity(to.above(), TrayBlockEntity.class);
        check(helper, lifted.isEmpty() && down.items().size() == before.size(), "set down again");
        for (int slot = 0; slot < before.size(); slot++) {
            check(helper, ItemStack.matches(before.get(slot), down.items().get(slot)), "slot " + slot + " as it was: " + down.items().get(slot));
        }
        down.riseBy(level, level.getGameTime());
        down.riseBy(level, level.getGameTime() + TrayBlockEntity.RISE_TICKS);
        check(helper, down.items().get(3).is(ModKitchen.RISEN_DOUGH.get()), "and the dough rises afresh where it was set down");
        check(helper, down.components().isEmpty(), "the block keeps nothing of the item it came from: " + down.components());

        var empty = tray(helper, new BlockPos(0, 2, 2));
        player.getInventory().clearContent();
        use(helper, player, new BlockPos(0, 2, 2), Direction.UP, ItemStack.EMPTY);
        check(helper, helper.getBlockState(new BlockPos(0, 2, 2)).is(ModKitchen.WOODEN_TRAY.get()) && empty.isEmpty(), "an empty tray is not lifted");
        helper.succeed();
    }

    /**
     * A cutlet is not laid on a tray's bare board: the tray says a plate is wanted. A Plate with a cutlet on it is lifted
     * from the table by a sneaking empty hand, plate and all, and goes on the tray so; or a Plate is set on the tray and
     * the cutlet on that. It comes off the tray as the plate with its food, and set down on a table it is a served plate
     * again. Lifted, the tray keeps its plates and what is on them.
     */
    private static void platesOnTrays(GameTestHelper helper) {
        BlockPos table = new BlockPos(1, 1, 1);
        BlockPos at = table.above();
        helper.setBlock(table, Blocks.OAK_PLANKS);
        helper.setBlock(at, ModKitchen.PLATE.get());
        List<Component> said = new ArrayList<>();
        ServerPlayer player = player(helper, said);
        use(helper, player, at, Direction.UP, new ItemStack(ModCooking.TONKATSU.get()));
        check(helper, helper.getBlockEntity(at, PlateBlockEntity.class).food().is(ModCooking.TONKATSU.get()), "a cutlet served on a plate");
        player.getInventory().clearContent();
        player.setShiftKeyDown(true);
        use(helper, player, at, Direction.UP, ItemStack.EMPTY);
        ItemStack lifted = player.getMainHandItem();
        check(helper, helper.getBlockState(at).isAir() && dropped(helper, at).isEmpty(), "the plate is off the table, and nothing fell");
        check(helper, Plating.isPlate(lifted) && Plating.onPlate(lifted).is(ModCooking.TONKATSU.get()) && lifted.getMaxStackSize() == 1,
                "in the hand with its cutlet, one to a stack: " + lifted);
        player.setShiftKeyDown(false);

        BlockPos trayAt = new BlockPos(1, 2, 0);
        var tray = tray(helper, trayAt);
        ItemStack katsu = new ItemStack(ModCooking.TONKATSU.get(), 2);
        use(helper, player, trayAt, Direction.UP, katsu);
        check(helper, katsu.getCount() == 2 && tray.isEmpty() && said.size() == 1, "a cutlet goes on no bare board, and it is said: " + said);
        use(helper, player, trayAt, Direction.UP, lifted);
        check(helper, lifted.isEmpty() && Plating.onPlate(tray.items().get(0)).is(ModCooking.TONKATSU.get()), "on its plate it goes on: " + tray.items());
        use(helper, player, trayAt, Direction.UP, new ItemStack(ModKitchen.PLATE_ITEM.get()));
        check(helper, Plating.isPlate(tray.items().get(1)) && Plating.onPlate(tray.items().get(1)).isEmpty()
                && helper.getBlockState(trayAt.above()).isAir(), "an empty plate is set on the tray, not on top of it");
        use(helper, player, trayAt, Direction.UP, katsu);
        check(helper, katsu.getCount() == 1 && Plating.onPlate(tray.items().get(1)).is(ModCooking.TONKATSU.get()), "and the cutlet on that plate");
        use(helper, player, trayAt, Direction.UP, katsu);
        check(helper, katsu.getCount() == 1 && said.size() == 2, "a plate takes one cutlet: the next wants a plate of its own");
        check(helper, on(helper, tray, ModCooking.COOKED_RICE.get()) && on(helper, tray, ModCooking.VEGETABLE_SOUP.get()) && tray.isFull(),
                "rice and soup beside the two plates: a meal for two");
        check(helper, Serving.packs(tray.items()), "all with room on the board");
        float[][] laid = Serving.trayPlaces(tray.items());
        check(helper, laid[0][1] == Serving.BACK && laid[1][1] == Serving.BACK && laid[2][1] == Serving.FRONT && laid[3][1] == Serving.FRONT,
                "the plates behind, the bowls before them");
        var one = tray(helper, new BlockPos(0, 2, 2));
        ItemStack served = new ItemStack(ModKitchen.PLATE_ITEM.get());
        Plating.setOnPlate(served, new ItemStack(ModCooking.KARAAGE.get()));
        check(helper, on(helper, one, ModKitchen.PLATE_ITEM.get()) && on(helper, one, ModCooking.COOKED_RICE.get())
                && on(helper, one, ModCooking.VEGETABLE_SOUP.get()), "a plate set on first, then rice and soup");
        float[][] meal = Serving.trayPlaces(one.items());
        check(helper, meal[0][0] == Serving.MIDDLE && meal[0][1] == Serving.BACK && meal[1][1] == Serving.FRONT && meal[2][1] == Serving.FRONT,
                "the one plate across the back, the rice and the soup before it, whichever went on first");

        ItemStack laden = tray.lift();
        ItemContainerContents kept = laden.get(DataComponents.CONTAINER);
        check(helper, kept != null && kept.nonEmptyItemCopyStream().filter(Plating::isPlate)
                .filter(stack -> Plating.onPlate(stack).is(ModCooking.TONKATSU.get())).count() == 2, "lifted, the tray keeps its plates and their cutlets");
        helper.setBlock(trayAt, Blocks.AIR);
        use(helper, player, new BlockPos(1, 1, 0), Direction.UP, laden);
        var again = helper.getBlockEntity(trayAt, TrayBlockEntity.class);
        player.getInventory().clearContent();
        use(helper, player, trayAt, Direction.UP, ItemStack.EMPTY);
        use(helper, player, trayAt, Direction.UP, ItemStack.EMPTY);
        use(helper, player, trayAt, Direction.UP, ItemStack.EMPTY);
        ItemStack off = player.getInventory().getNonEquipmentItems().stream().filter(Plating::isPlate).findFirst().orElse(ItemStack.EMPTY);
        check(helper, Plating.onPlate(off).is(ModCooking.TONKATSU.get()) && again.items().stream().filter(stack -> !stack.isEmpty()).count() == 1,
                "an empty hand takes a plate off with its cutlet: " + off);
        use(helper, player, table, Direction.UP, off);
        check(helper, off.isEmpty() && helper.getBlockEntity(at, PlateBlockEntity.class).food().is(ModCooking.TONKATSU.get()),
                "set down on the table, it is a served plate again");
        check(helper, helper.getBlockEntity(at, PlateBlockEntity.class).components().isEmpty(), "and the block keeps nothing of the item");
        helper.succeed();
    }

    /**
     * A pot of oil with a breaded cutlet on an Electric Stove: nothing cooks while it has no power, and its ring is
     * dark; with power the ring glows and the cutlet fries; done, the ring goes dark again and the power is left alone.
     * Out of power under a pot that wants heat it is stopped for a reason to see to, as a machine is - so its stop is
     * heard and a Machine Bell beside it rings (the sound audit, 2026-11-14: it went dark in silence); heating, and with
     * the dish waiting, it is not.
     */
    private static void stove(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), ModCooking.ELECTRIC_STOVE.get());
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var stove = helper.getBlockEntity(pos.below(), ElectricStoveBlockEntity.class);
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.OIL, 1_000);
        check(helper, Move.insert(pot.itemHandler(), ItemResource.of(ModCooking.BREADED_PORK.get()), 1) == 1, "a cutlet goes in");
        int[] step = {0};
        int[] left = {0};
        helper.runAfterDelay(40, () -> {
            check(helper, pot.percent() <= 0 && pot.dish().isEmpty() && !helper.getBlockState(pos.below()).getValue(ElectricStoveBlock.LIT),
                    "no power: the ring is dark and nothing cooks (" + pot.percent() + "%)");
            check(helper, MachineAlerts.isMachine(stove) && MachineAlerts.watched(stove), "the stove is watched as a machine is");
            check(helper, MachineAlerts.statusOf(stove) == MachineStatus.NO_POWER && MachineAlerts.stopped(stove),
                    "out of power under a pot that wants heat: a stop to see to, not " + MachineAlerts.statusOf(stove));
            stove.energy().set(ElectricStoveBlockEntity.CAPACITY);
            step[0] = 1;
        });
        helper.succeedWhen(() -> {
            check(helper, step[0] >= 1, "waiting for the first look");
            boolean lit = helper.getBlockState(pos.below()).getValue(ElectricStoveBlock.LIT);
            if (step[0] == 1 && lit && pot.percent() > 0) {
                check(helper, MachineAlerts.statusOf(stove) == MachineStatus.RUNNING && !MachineAlerts.stopped(stove), "heating: running");
                step[0] = 2; // heating
            } else if (step[0] == 2 && pot.dish().is(ModCooking.TONKATSU.get()) && !lit) {
                left[0] = (int) stove.energy().getAmountAsLong();
                check(helper, left[0] < ElectricStoveBlockEntity.CAPACITY && left[0] > 0, "it cost power: " + left[0] + " FE left");
                step[0] = 3;
                helper.runAfterDelay(20, () -> {
                    check(helper, stove.energy().getAmountAsLong() == left[0], "and costs none with the dish waiting");
                    check(helper, MachineAlerts.statusOf(stove) == MachineStatus.IDLE && !MachineAlerts.stopped(stove),
                            "the dish waiting is no stop to see to: " + MachineAlerts.statusOf(stove));
                    step[0] = 4;
                });
            }
            check(helper, step[0] == 4, "the cutlet fried on the stove: at step " + step[0] + ", dish " + pot.dish() + ", lit " + lit);
        });
    }
}
