package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.block.OilLampBlock;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.feature.WoodVinegar;
import dev.nookworks.furniture.SeatEntity;
import dev.nookworks.item.WoolShearsItem;
import dev.nookworks.label.LabelEntity;
import dev.nookworks.label.LabelItem;
import dev.nookworks.machine.CrankMotion;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.oil.OilPressBlock;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.poster.PosterVariant;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModWoods;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The things added to the kitchen, the fields and the house: the Oil Lamp, and those after it. */
final class FeatureTests {
    static void register() {
        NookworksGameTests.test("oil_lamp_burns_its_oil_down_and_goes_out", FeatureTests::oilLampBurnsDown);
        NookworksGameTests.test("flour_coats_what_is_fried", FeatureTests::flourCoatsWhatIsFried);
        NookworksGameTests.test("wood_vinegar_keeps_rabbits_off_a_field", FeatureTests::woodVinegarKeepsRabbitsOff);
        NookworksGameTests.test("archaeology_gives_nothing_of_ours", FeatureTests::archaeologyGivesNothingOfOurs);
        NookworksGameTests.test("labels_print_stick_on_a_chest_and_come_off", FeatureTests::labels);
        NookworksGameTests.test("letter_labels_are_typed_printed_and_stuck", FeatureTests::letterLabels);
        NookworksGameTests.test("dawnwood_has_signs_a_shelf_and_boats", FeatureTests::dawnwoodSignsShelfBoats);
        NookworksGameTests.test("a_handle_goes_round_a_quarter_each_turn", FeatureTests::handleGoesRound);
        NookworksGameTests.test("motor_shaft_runs_up_and_down", FeatureTests::motorShaftRunsUpAndDown);
        NookworksGameTests.test("the_press_tray_shows_its_oil", FeatureTests::pressTrayShowsOil);
        NookworksGameTests.test("advancements_are_earned_as_a_player_would", FeatureTests::advancementsEarned);
    }

    /**
     * A few advancements, earned the way a player earns them: one piece of Velarium gear (not all ten at once, as the
     * criterion once asked), sitting down on a chair, bottling a pig, and planting rice (ours, or Farmer's Delight's,
     * which the Sifter gives instead when it is installed: the advancement asks for a block of #nookworks:rice_crops).
     */
    private static void advancementsEarned(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var player = MockPlayers.inLevel(helper);
        Predicate<String> done = id -> {
            var holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath(Nookworks.MODID, id));
            return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
        };
        ItemStack sword = new ItemStack(ModItems.VELARIUM_SWORD.get());
        player.getInventory().add(sword.copy()); // (add empties the stack it is given)
        CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), sword);
        check(helper, done.test("velarium_gear"), "one Velarium sword is enough");

        BlockPos chair = new BlockPos(1, 2, 1);
        helper.setBlock(chair, ModWoods.CHAIRS.get("oak").get());
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 2.5)));
        SeatEntity.sit(level, helper.absolutePos(chair), player);
        check(helper, done.test("seat"), "sitting on a chair: " + player.getVehicle());
        player.stopRiding();

        var pig = helper.spawn(EntityTypes.PIG, new BlockPos(3, 2, 3));
        pig.setHealth(pig.getMaxHealth() / 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        // a click with the bottle on what it does not take (a boat boarded, a frame) is no catch
        CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(player, player.getMainHandItem(), pig);
        check(helper, !done.test("mob_bottle"), "a bottle clicked on a mob it does not take is not bottling it");
        MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, pig);
        check(helper, done.test("mob_bottle"), "bottling a pig");

        // the Fine Shears' advancement: a sheep shorn, not any click with them in hand
        var sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(1, 2, 5));
        ItemStack shears = new ItemStack(ModItems.WOOL_SHEARS.get());
        CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(player, shears, sheep);
        check(helper, !done.test("wool_shears"), "the shears clicked on a sheep left woolly are no shearing");
        WoolShearsItem.shear(level, sheep, player, shears);
        CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(player, shears, sheep);
        check(helper, done.test("wool_shears"), "a sheep shorn with the Fine Shears");

        BlockPos paddy = new BlockPos(5, 2, 5);
        helper.setBlock(paddy, ModCrops.RICE_CROP.get());
        check(helper, helper.getBlockState(paddy).is(TagKey.create(Registries.BLOCK,
                Nookworks.id("rice_crops"))), "our rice is in the tag");
        CriteriaTriggers.PLACED_BLOCK.trigger(player, helper.absolutePos(paddy),
                new ItemStack(ModCrops.BROWN_RICE.get()));
        check(helper, done.test("rice"), "planting rice");
        helper.succeed();
    }

    /** The Oil Press's tray shows the oil standing in it: none when empty, some after a few seeds, plenty past half. */
    private static void pressTrayShowsOil(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModOil.OIL_PRESS.get());
        var press = helper.getBlockEntity(pos, OilPressBlockEntity.class);
        var oil = OilPressBlock.OIL;
        check(helper, helper.getBlockState(pos).getValue(oil) == 0, "a new press is dry");
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get(), 64));
        for (int i = 0; i < 8; i++) {
            press.turn(helper.getLevel());
        }
        check(helper, press.stored() > 0 && helper.getBlockState(pos).getValue(oil) == 1, "some oil in: " + press.stored());
        while (press.stored() < OilPressBlockEntity.TANK / 2 && !press.getItem(OilPressBlockEntity.INPUT).isEmpty()) {
            press.turn(helper.getLevel());
        }
        check(helper, helper.getBlockState(pos).getValue(oil) == 2, "plenty past half: " + press.stored());
        press.pour(press.stored());
        check(helper, press.stored() == 0 && helper.getBlockState(pos).getValue(oil) == 0, "poured out, dry again");
        helper.succeed();
    }

    /**
     * A handle worked once goes a quarter round, easing to a stop over four ticks; worked again it goes on from there.
     * Both hand-worked machines answer the turn's block event.
     */
    private static void handleGoesRound(GameTestHelper helper) {
        var motion = new CrankMotion();
        check(helper, motion.degrees(100, 0) == 0, "never worked, it stands");
        motion.turned(100);
        check(helper, motion.degrees(100, 0) == 0 && motion.degrees(102, 0) == 45 && motion.degrees(104, 0) == 90
                && motion.degrees(200, 0) == 90, "a quarter turn over four ticks: " + motion.degrees(102, 0));
        motion.turned(104);
        check(helper, motion.degrees(108, 0) == 180, "and on from there");
        BlockPos press = new BlockPos(1, 2, 1);
        BlockPos polisher = new BlockPos(3, 2, 1);
        helper.setBlock(press, ModOil.OIL_PRESS.get());
        helper.setBlock(polisher, ModCrops.RICE_POLISHER.get());
        check(helper, helper.getBlockEntity(press, OilPressBlockEntity.class).triggerEvent(CrankMotion.EVENT, 0)
                && helper.getBlockEntity(polisher, RicePolisherBlockEntity.class).triggerEvent(CrankMotion.EVENT, 0),
                "both take the turn's event");
        helper.succeed();
    }

    /**
     * A Motor's shaft, as the client turns it: running, it runs up to a quarter turn a motor's turn and goes round at
     * that; stopped, it runs down to a stand within a second, without going back. The machine it drives turns its axle
     * and handle by the same angle (CrankRenderer), so they go round as one.
     */
    private static void motorShaftRunsUpAndDown(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModOil.MOTOR.get());
        var motor = helper.getBlockEntity(pos, MotorBlockEntity.class);
        var level = helper.getLevel();
        var running = helper.getBlockState(pos).setValue(MotorBlock.POWERED, true);
        var stopped = running.setValue(MotorBlock.POWERED, false);
        check(helper, motor.shaftDegrees(1) == 0, "it stands until it runs");
        for (int i = 0; i < 40; i++) {
            MotorBlockEntity.clientTick(level, helper.absolutePos(pos), running, motor);
        }
        float a = motor.shaftDegrees(1);
        MotorBlockEntity.clientTick(level, helper.absolutePos(pos), running, motor);
        float b = motor.shaftDegrees(1);
        float step = ((b - a) % 360 + 360) % 360;
        check(helper, Math.abs(step - MotorBlockEntity.DEGREES_PER_TICK) < 0.01F,
                "run up, a quarter turn a motor's turn: " + step + " a tick");
        float last = b;
        boolean back = false;
        for (int i = 0; i < 20; i++) {
            MotorBlockEntity.clientTick(level, helper.absolutePos(pos), stopped, motor);
            float now = motor.shaftDegrees(1);
            back |= ((now - last) % 360 + 360) % 360 > 180;
            last = now;
        }
        MotorBlockEntity.clientTick(level, helper.absolutePos(pos), stopped, motor);
        check(helper, motor.shaftDegrees(1) == last && !back, "stopped, it runs down to a stand and never turns back");
        helper.succeed();
    }

    /**
     * Dawnwood's signs, hanging signs and shelf get vanilla's block entities (the types were told to take them - without
     * that the game refuses to make one), and its boats come back as their own items and burn as boats do.
     */
    private static void dawnwoodSignsShelfBoats(GameTestHelper helper) {
        var woods = List.of(ModWoods.DAWNWOOD_SIGN, ModWoods.DAWNWOOD_WALL_SIGN,
                ModWoods.DAWNWOOD_HANGING_SIGN, ModWoods.DAWNWOOD_WALL_HANGING_SIGN,
                ModWoods.DAWNWOOD_SHELF);
        int x = 0;
        for (var block : woods) {
            BlockPos pos = new BlockPos(x, 2, 1);
            helper.setBlock(pos.above(), Blocks.OAK_PLANKS); // for the hanging sign to hang from
            helper.setBlock(pos.north(), Blocks.OAK_PLANKS); // and the wall ones to stand against
            helper.setBlock(pos, block.get().defaultBlockState());
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
            check(helper, entity != null && entity.getType().isValid(helper.getBlockState(pos)), block.getId() + " has its block entity");
            x += 2;
        }
        var level = helper.getLevel();
        var boat = ModEntities.DAWNWOOD_BOAT.get().create(level, EntitySpawnReason.COMMAND);
        var chestBoat = ModEntities.DAWNWOOD_CHEST_BOAT.get().create(level, EntitySpawnReason.COMMAND);
        check(helper, boat != null && boat.getPickResult().is(ModWoods.DAWNWOOD_BOAT.get())
                && chestBoat != null && chestBoat.getPickResult().is(ModWoods.DAWNWOOD_CHEST_BOAT.get()), "boats are their own items");
        check(helper, new ItemStack(ModWoods.DAWNWOOD_BOAT.get()).has(DataComponents.COOKING_FUEL)
                && new ItemStack(ModWoods.DAWNWOOD_SHELF.get()).has(DataComponents.COOKING_FUEL), "and burn");
        helper.succeed();
    }

    /**
     * The press prints eight labels of its sample from a sheet and a drop of each ink, keeping the sample, and prints
     * what a label shows when the sample is a label. A label pressed on a chest's front lies on the chest (a pixel back
     * from the edge of its square), stays on the chest's face however near the edge it is pressed, will not overlap
     * another, keeps its place through a save, and drops as the label it was the tick the chest goes.
     */
    private static void labels(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pressPos = new BlockPos(1, 2, 1);
        helper.setBlock(pressPos, ModBlocks.PRINTING_PRESS.get());
        var press = helper.getBlockEntity(pressPos, PrintingPressBlockEntity.class);
        press.energy().set((int) press.energy().getCapacityAsLong());
        press.setItem(PrintingPressBlockEntity.PAPER, new ItemStack(ModItems.FINE_PAPER.get(), 2));
        for (int i = 0; i < PosterVariant.PROCESS_INKS.size(); i++) {
            press.setItem(PrintingPressBlockEntity.FIRST_INK + i,
                    new ItemStack(PosterVariant.PROCESS_INKS.get(i), 2));
        }
        press.select(PrintingPressBlockEntity.LABEL);
        check(helper, !press.canPrintLabels() && PrintingPressBlockEntity.labelsOf(press, "").isEmpty(),
                "no sample and no letters: nothing to print (not eight labels of air)");
        press.setItem(PrintingPressBlockEntity.SAMPLE, new ItemStack(Items.COBBLESTONE));
        Runnable print = () -> {
            press.setRunning(true);
            for (int i = 0; i < PrintingPressBlockEntity.TICKS + 2; i++) {
                PrintingPressBlockEntity.serverTick(level, helper.absolutePos(pressPos), helper.getBlockState(pressPos), press);
            }
        };
        print.run();
        ItemStack out = press.getItem(PrintingPressBlockEntity.OUTPUT);
        check(helper, out.is(ModItems.LABEL.get()) && out.getCount() == 8
                && LabelItem.shows(out).is(Items.COBBLESTONE), "eight cobblestone labels: " + out);
        check(helper, press.getItem(PrintingPressBlockEntity.SAMPLE).is(Items.COBBLESTONE)
                && press.getItem(PrintingPressBlockEntity.PAPER).getCount() == 1, "the sample stays, a sheet is used");
        check(helper, !press.canPlaceItemThroughFace(PrintingPressBlockEntity.SAMPLE, new ItemStack(Items.DIRT), Direction.UP),
                "no hopper sets the sample");
        press.setItem(PrintingPressBlockEntity.OUTPUT, ItemStack.EMPTY);
        press.setItem(PrintingPressBlockEntity.SAMPLE, LabelItem.of(new ItemStack(Items.CHEST)));
        print.run();
        out = press.getItem(PrintingPressBlockEntity.OUTPUT);
        check(helper, out.getCount() == 8 && LabelItem.shows(out).is(Items.CHEST), "a label as the sample: " + out);

        BlockPos chest = new BlockPos(3, 2, 3);
        helper.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        BlockPos at = helper.absolutePos(chest);
        // pressed on the chest's front, far out to the east and the top
        Vec3 hit = new Vec3(at.getX() + 0.95, at.getY() + 0.8, at.getZ() + 15 / 16.0);
        var label = LabelItem.place(level, at, Direction.SOUTH, hit, new ItemStack(Items.IRON_INGOT));
        check(helper, label != null, "a label goes on a chest");
        var box = label.getBoundingBox();
        check(helper, Math.abs(box.minZ - (at.getZ() + 15 / 16.0)) < 1.0E-6, "it lies on the chest, not the square: " + box);
        check(helper, box.maxX <= at.getX() + 15 / 16.0 + 1.0E-6 && box.maxY <= at.getY() + 14 / 16.0 + 1.0E-6,
                "it stays on the chest's face: " + box);
        level.addFreshEntity(label);
        check(helper, LabelItem.place(level, at, Direction.SOUTH, hit, new ItemStack(Items.GOLD_INGOT)) == null,
                "no label over another");
        check(helper, LabelItem.place(level, at, Direction.SOUTH,
                new Vec3(at.getX() + 0.2, at.getY() + 0.2, at.getZ() + 15 / 16.0), new ItemStack(Items.GOLD_INGOT)) != null,
                "one beside it is fine");

        try (var reporter = new ProblemReporter.ScopedCollector(label.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            label.saveWithoutId(output);
            var copy = new LabelEntity(ModEntities.LABEL.get(), level);
            copy.load(TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
            check(helper, copy.getPos().equals(at) && copy.getDirection() == Direction.SOUTH && copy.shows().is(Items.IRON_INGOT)
                    && copy.getBoundingBox().distanceToSqr(box.getCenter()) < 1.0E-9 && copy.getBoundingBox().getSize() - box.getSize() < 1.0E-9,
                    "saved and loaded: " + copy.getBoundingBox() + " was " + box);
        }

        // By hand: the label goes on, and the chest does not open instead.
        BlockPos other = new BlockPos(5, 2, 3);
        helper.setBlock(other, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        BlockPos otherAt = helper.absolutePos(other);
        var player = MockPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, LabelItem.of(new ItemStack(Items.WHEAT)));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(otherAt.getX() + 0.5, otherAt.getY() + 0.3, otherAt.getZ() + 15 / 16.0), Direction.SOUTH, otherAt, false));
        check(helper, player.containerMenu == player.inventoryMenu, "the chest did not open");
        check(helper, level.getEntitiesOfClass(LabelEntity.class, new AABB(otherAt).inflate(0.2))
                .stream().anyMatch(l -> l.shows().is(Items.WHEAT)), "the label went on");

        helper.setBlock(chest, Blocks.AIR);
        helper.runAfterDelay(5, () -> { // a label looks behind it every fourth tick
            check(helper, label.isRemoved(), "the chest gone, the label comes off within a moment");
            var drops = helper.getEntities(EntityTypes.ITEM, chest, 2.0);
            check(helper, drops.stream().anyMatch(item -> LabelItem.shows(item.getItem()).is(Items.IRON_INGOT)),
                    "and drops as the label it was");
            helper.succeed();
        });
    }

    /**
     * Letters typed into the press's screen come a button a letter, only A-Z, a-z, 0-9 and spaces, eight at most, and
     * go back to the screen through the menu's data. Typed, they are printed instead of the sample. A letter label goes
     * on by hand, keeps its letters through a save, and drops with them.
     */
    private static void letterLabels(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pressPos = new BlockPos(1, 2, 1);
        helper.setBlock(pressPos, ModBlocks.PRINTING_PRESS.get());
        var press = helper.getBlockEntity(pressPos, PrintingPressBlockEntity.class);
        press.energy().set((int) press.energy().getCapacityAsLong());
        press.setItem(PrintingPressBlockEntity.PAPER, new ItemStack(ModItems.FINE_PAPER.get(), 2));
        for (int i = 0; i < PosterVariant.PROCESS_INKS.size(); i++) {
            press.setItem(PrintingPressBlockEntity.FIRST_INK + i,
                    new ItemStack(PosterVariant.PROCESS_INKS.get(i), 2));
        }
        press.setItem(PrintingPressBlockEntity.SAMPLE, new ItemStack(Items.COBBLESTONE));
        press.select(PrintingPressBlockEntity.LABEL);

        var player = MockPlayers.inLevel(helper);
        var menu = (PrintingPressMenu) press.createMenu(1, player.getInventory(), player);
        check(helper, menu.clickMenuButton(player, PrintingPressMenu.BUTTON_TEXT_CLEAR), "clear");
        for (char c : "Ore  9!xyzW".toCharArray()) {
            menu.clickMenuButton(player, PrintingPressMenu.letterButton(c));
        }
        check(helper, press.labelText().equals("Ore  9xy"), "only letters, digits and spaces, eight at most: '" + press.labelText() + "'");
        check(helper, menu.labelText().equals("Ore  9xy"), "the screen reads it back from the menu's data: '" + menu.labelText() + "'");
        menu.clickMenuButton(player, PrintingPressMenu.BUTTON_TEXT_CLEAR);
        for (char c : "Ores ".toCharArray()) {
            menu.clickMenuButton(player, PrintingPressMenu.letterButton(c));
        }
        check(helper, press.labelText().equals("Ores"), "a space at the end is not printed: '" + press.labelText() + "'");

        press.setRunning(true);
        for (int i = 0; i < PrintingPressBlockEntity.TICKS + 2; i++) {
            PrintingPressBlockEntity.serverTick(level, helper.absolutePos(pressPos), helper.getBlockState(pressPos), press);
        }
        ItemStack out = press.getItem(PrintingPressBlockEntity.OUTPUT);
        check(helper, out.getCount() == 8 && LabelItem.text(out).equals("Ores")
                && LabelItem.shows(out).isEmpty(), "eight labels saying Ores, not cobblestone: " + out);
        check(helper, out.getHoverName().getString().contains("Ores"), "named for what it says: " + out.getHoverName().getString());
        for (int i = 0; i < PosterVariant.PROCESS_INKS.size(); i++) {
            Item ink = PosterVariant.PROCESS_INKS.get(i);
            int left = press.getItem(PrintingPressBlockEntity.FIRST_INK + i).getCount();
            check(helper, left == (ink == Items.DYE.pick(DyeColor.BLACK) ? 1 : 2),
                    "letters take a drop of black and no other ink: " + ink + " left " + left);
        }
        check(helper, LabelItem.of(out).is(ModItems.LABEL.get())
                && LabelItem.text(LabelItem.of(out)).equals("Ores"), "a letter label as a sample prints its letters");
        check(helper, LabelItem.ofText("!!").isEmpty(), "nothing printable, no label");

        try (var reporter = new ProblemReporter.ScopedCollector(press.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            press.saveWithoutMetadata(output);
            var copy = new PrintingPressBlockEntity(press.getBlockPos(), press.getBlockState());
            copy.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
            check(helper, copy.labelText().equals("Ores"), "the press keeps its letters through a save");
        }

        BlockPos chest = new BlockPos(3, 2, 3);
        helper.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        BlockPos at = helper.absolutePos(chest);
        player.setItemInHand(InteractionHand.MAIN_HAND, out.copyWithCount(1));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 15 / 16.0), Direction.SOUTH, at, false));
        var stuck = level.getEntitiesOfClass(LabelEntity.class, new AABB(at).inflate(0.2));
        check(helper, stuck.size() == 1 && stuck.get(0).text().equals("Ores"), "the letter label went on: " + stuck);
        var label = stuck.get(0);
        try (var reporter = new ProblemReporter.ScopedCollector(label.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            label.saveWithoutId(output);
            var copy = new LabelEntity(ModEntities.LABEL.get(), level);
            copy.load(TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
            check(helper, copy.text().equals("Ores"), "saved and loaded: " + copy.text());
        }
        helper.setBlock(chest, Blocks.AIR);
        helper.runAfterDelay(5, () -> {
            var drops = helper.getEntities(EntityTypes.ITEM, chest, 2.0);
            check(helper, drops.stream().anyMatch(item -> LabelItem.text(item.getItem()).equals("Ores")),
                    "it drops with its letters");
            helper.succeed();
        });
    }

    /**
     * The Crusher grinds wheat to flour and crumbles bread; in the pot's oil, flour doubles the karaage and fried fish,
     * and a porkchop in flour, egg and breadcrumbs makes tonkatsu. Chicken alone still fries, one to one.
     */
    private static void flourCoatsWhatIsFried(GameTestHelper helper) {
        var wheat = CrushingRecipe.of(new ItemStack(Items.WHEAT));
        check(helper, wheat != null && wheat.output().is(ModCooking.FLOUR.get()), "wheat grinds to flour");
        var bread = CrushingRecipe.of(new ItemStack(Items.BREAD));
        check(helper, bread != null && bread.output().is(ModCooking.BREADCRUMBS.get()) && bread.output().getCount() == 2,
                "a loaf crumbles to two breadcrumbs");
        var registries = helper.getLevel();
        var oil = PotRecipe.Liquid.OIL;
        var plain = PotRecipe.find(registries, List.of(new ItemStack(Items.CHICKEN)), oil, 1_000);
        check(helper, plain != null && plain.value().result().stack(1).is(ModCooking.KARAAGE.get())
                && plain.value().result().count() == 1, "chicken alone: one karaage");
        var coated = PotRecipe.find(registries, List.of(new ItemStack(Items.CHICKEN),
                new ItemStack(ModCooking.FLOUR.get())), oil, 1_000);
        check(helper, coated != null && coated.value().result().count() == 2, "in flour: two");
        var fish = PotRecipe.find(registries, List.of(new ItemStack(Items.COD),
                new ItemStack(ModCooking.FLOUR.get())), oil, 1_000);
        check(helper, fish != null && fish.value().result().stack(1).is(ModCooking.FRIED_FISH.get())
                && fish.value().result().count() == 2, "cod in flour: two fried fish");
        // tonkatsu is coated at the bench and only fried in the pot (the user, 2026-10-12)
        var level = helper.getLevel();
        var coating = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, List.of(new ItemStack(Items.PORKCHOP),
                new ItemStack(ModCooking.FLOUR.get()), new ItemStack(Items.EGG), new ItemStack(ModCooking.BREADCRUMBS.get())));
        ItemStack breaded = level.getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, coating, level)
                .map(recipe -> recipe.value().assemble(coating)).orElse(ItemStack.EMPTY);
        check(helper, breaded.is(ModCooking.BREADED_PORK.get()) && breaded.getCount() == 2, "a porkchop coats into two Breaded Pork: " + breaded);
        var katsu = PotRecipe.find(registries, List.of(new ItemStack(ModCooking.BREADED_PORK.get())), oil, 1_000);
        check(helper, katsu != null && katsu.value().result().stack(1).is(ModCooking.TONKATSU.get())
                && katsu.value().result().count() == 1, "each fries into a tonkatsu");
        check(helper, PotRecipe.find(registries, List.of(new ItemStack(Items.PORKCHOP), new ItemStack(ModCooking.FLOUR.get()),
                new ItemStack(Items.EGG), new ItemStack(ModCooking.BREADCRUMBS.get())), oil, 1_000) == null,
                "the coat's makings no longer go into the oil themselves");
        helper.succeed();
    }

    /**
     * A bottle used on a field treats the farmland in the 5x5 square about it and comes back empty; a rabbit looking
     * for carrots to raid passes over a treated bed, not an untreated one past the edge of it.
     */
    private static void woodVinegarKeepsRabbitsOff(GameTestHelper helper) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
            }
        }
        BlockPos treated = new BlockPos(2, 1, 2);
        BlockPos beyond = new BlockPos(6, 1, 2); // four squares out: past the 5x5
        for (BlockPos soil : List.of(treated, beyond)) {
            helper.setBlock(soil.above(), Blocks.CARROTS.defaultBlockState().setValue(CarrotBlock.AGE, 7));
        }
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bottle = new ItemStack(ModOil.WOOD_VINEGAR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        bottle.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hitTop(helper, treated.above())));
        check(helper, player.getMainHandItem().is(Items.GLASS_BOTTLE), "the bottle comes back empty");
        var level = helper.getLevel();
        check(helper, WoodVinegar.isTreated(level, helper.absolutePos(treated)), "the bed under the carrot is treated");
        check(helper, WoodVinegar.isTreated(level, helper.absolutePos(new BlockPos(0, 1, 0))), "and the corner of the 5x5");
        check(helper, !WoodVinegar.isTreated(level, helper.absolutePos(beyond)), "not past it");
        var rabbit = helper.spawn(EntityTypes.RABBIT, new BlockPos(4, 2, 2));
        try {
            Object raid = null;
            for (var wrapped : rabbit.goalSelector.getAvailableGoals()) {
                if (wrapped.getGoal().getClass().getSimpleName().equals("RaidGardenGoal")) {
                    raid = wrapped.getGoal();
                }
            }
            check(helper, raid != null, "the rabbit has its garden goal: " + rabbit.goalSelector.getAvailableGoals().stream()
                    .map(g -> g.getGoal().getClass().getName()).toList());
            Field wants = raid.getClass().getDeclaredField("wantsToRaid");
            wants.setAccessible(true);
            Method valid = raid.getClass().getDeclaredMethod("isValidTarget", LevelReader.class, BlockPos.class);
            valid.setAccessible(true);
            wants.setBoolean(raid, true);
            check(helper, !(boolean) valid.invoke(raid, level, helper.absolutePos(treated)), "the rabbit will not go for the treated bed");
            wants.setBoolean(raid, true);
            check(helper, (boolean) valid.invoke(raid, level, helper.absolutePos(beyond)), "it would for the one past the edge");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        helper.succeed();
    }

    /**
     * Nothing of ours is dug up: vanilla's archaeology keeps its own finds (the user, 2026-10-02). The Rare Seed came out
     * of the trail ruins' suspicious gravel now and then; it no longer does, nor does anything else of ours, from any of
     * the archaeology tables - every roll is still a single item.
     */
    private static void archaeologyGivesNothingOfOurs(GameTestHelper helper) {
        var level = helper.getLevel();
        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                .withParameter(LootContextParams.THIS_ENTITY, helper.makeMockPlayer(GameType.SURVIVAL))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.BRUSH))
                .withLuck(0).create(LootContextParamSets.ARCHAEOLOGY);
        var tables = level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds()
                .filter(key -> key.identifier().getNamespace().equals("minecraft") && key.identifier().getPath().startsWith("archaeology/"))
                .toList();
        check(helper, tables.size() >= 5, "the archaeology tables are there: " + tables);
        for (var id : tables) {
            var table = level.getServer().reloadableRegistries().getLootTable(id);
            for (int i = 0; i < 400; i++) {
                var loot = table.getRandomItems(params, level.getRandom().nextLong());
                check(helper, loot.size() <= 1, id + ": one item a brushing, got " + loot.size());
                check(helper, loot.stream().noneMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Nookworks.MODID)),
                        id + " gave " + loot);
            }
        }
        helper.succeed();
    }

    private static BlockHitResult hitTop(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
    }

    /**
     * Oil poured in a bottle at a time, up to four; lit, each bottle's worth burns down in its turn, and the dish goes
     * out when it is dry. It needs something to stand on.
     */
    private static void oilLampBurnsDown(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.OIL_LAMP.get());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 2; i++) {
            ItemStack bottle = new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
            helper.getBlockState(pos).useItemOn(bottle, helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, pos));
        }
        check(helper, helper.getBlockState(pos).getValue(OilLampBlock.OIL) == 2, "two bottles in");
        check(helper, player.getMainHandItem().is(Items.GLASS_BOTTLE), "and the bottle comes back empty");
        helper.setBlock(pos, helper.getBlockState(pos).setValue(OilLampBlock.LIT, true));
        check(helper, helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(pos), ModBlocks.OIL_LAMP.get()),
                "lit, it starts to burn down");
        for (int step = 0; step < OilLampBlock.STEPS; step++) {
            helper.getBlockState(pos).tick(helper.getLevel(), helper.absolutePos(pos), helper.getLevel().getRandom());
        }
        check(helper, helper.getBlockState(pos).getValue(OilLampBlock.OIL) == 1 && helper.getBlockState(pos).getValue(OilLampBlock.LIT),
                "a bottle burnt, still alight");
        for (int step = 0; step < OilLampBlock.STEPS; step++) {
            helper.getBlockState(pos).tick(helper.getLevel(), helper.absolutePos(pos), helper.getLevel().getRandom());
        }
        check(helper, helper.getBlockState(pos).getValue(OilLampBlock.OIL) == 0 && !helper.getBlockState(pos).getValue(OilLampBlock.LIT),
                "dry, it goes out");
        helper.setBlock(pos.below(), Blocks.AIR);
        check(helper, helper.getBlockState(pos).isAir(), "with nothing under it, it falls");
        helper.succeed();
    }
}
