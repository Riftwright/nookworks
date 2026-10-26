package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.block.MotorBlock;
import dev.nookworks.cooking.HandMachineBlockEntity;
import dev.nookworks.cooking.HandQuernBlock;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveNetwork;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.Drive;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HungItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The user's request of 2026-10-10: the Hand Quern made over (a frame like the Oil Press's, its upper stone turned by a
 * spindle) so a Motor turns it from above or below - never from a side - while the side machines are never turned from
 * above or below; and Drive Wheels with taut belts of Rope or Thin Chain, so one Motor turns several machines.
 */
final class R11DriveTests {
    private R11DriveTests() {}

    /** The tests that set a Hand Quern or a Rice Polisher down: both are the kitchen's. */
    static final String[] NEED_KITCHEN = {"machines_are_turned_only_from_the_sides_they_are_driven_from",
            "a_belted_wheel_turns_the_quern_from_above_but_not_a_polisher_from_above"};

    static void register() {
        NookworksGameTests.test(NEED_KITCHEN[0], 80, R11DriveTests::drivenFromTheirOwnSides);
        NookworksGameTests.test(NEED_KITCHEN[1], 80, R11DriveTests::wheelTurnsTheQuernFromAbove);
        NookworksGameTests.test("one_motor_turns_two_presses_by_a_belt", 120, R11DriveTests::oneMotorTwoPresses);
        NookworksGameTests.test("one_motor_turns_eight_machines_at_most", 120, R11DriveTests::eightMachinesAtMost);
        NookworksGameTests.test("belts_are_tied_only_where_they_run_true", R11DriveTests::beltsRunTrue);
        NookworksGameTests.test("a_belt_takes_two_rope_a_block_and_gives_them_back", R11DriveTests::beltPaidAndGivenBack);
        NookworksGameTests.test("a_belt_snaps_when_something_is_set_in_its_way", 100, R11DriveTests::beltSnaps);
        NookworksGameTests.test("a_block_is_not_set_down_in_a_belts_way", R11DriveTests::noBlockInABeltsWay);
        NookworksGameTests.test("a_wheel_turned_out_of_line_lets_its_belt_fall", 20, R11DriveTests::turnedOutOfLine);
        NookworksGameTests.test("a_belt_paid_to_the_last_rope_in_hand_gives_it_all_back", R11DriveTests::paidToTheLast);
        NookworksGameTests.test("a_belt_paid_from_two_stacks_takes_from_both", R11DriveTests::paidFromTwoStacks);
        NookworksGameTests.test("wheels_end_to_end_on_one_axle_turn_as_one", 80, R11DriveTests::wheelsOnOneAxle);
        NookworksGameTests.test("a_belt_whose_far_wheel_went_while_away_is_given_back_once", 60, R11DriveTests::farWheelWentWhileAway);
        // the r11 fix check: a wheel let go of before its own check, after the far end went while it was away
        NookworksGameTests.test("an_owner_broken_after_its_far_wheel_went_gives_the_belt_back", R11DriveTests::ownerBrokenAfterFarWent);
        NookworksGameTests.test("a_belt_taken_off_after_its_owner_went_gives_nothing_more", R11DriveTests::takenOffAfterOwnerWent);
        NookworksGameTests.test("a_started_belt_lapses_after_half_a_minute", 700, R11DriveTests::startedBeltLapses);
        NookworksGameTests.test("a_thing_hung_on_a_span_tells_clients_its_span", R11DriveTests::hungThingsSpanSeen);
        NookworksGameTests.test("a_broken_wheel_drops_its_belt_and_frees_the_far_end", R11DriveTests::brokenWheelDropsItsBelt);
    }

    // ---- helpers ----

    private static void motor(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, facing));
        MotorBlockEntity motor = helper.getBlockEntity(pos, MotorBlockEntity.class);
        motor.energy().set((int) motor.energy().getCapacityAsLong());
    }

    private static DriveWheelBlockEntity wheel(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, facing));
        return helper.getBlockEntity(pos, DriveWheelBlockEntity.class);
    }

    private static OilPressBlockEntity press(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModOil.OIL_PRESS.get());
        OilPressBlockEntity press = helper.getBlockEntity(pos, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get(), 64));
        return press;
    }

    /** Whether a press has been turned at all: part way through a seed, or oil in its tray. */
    private static boolean worked(OilPressBlockEntity press) {
        return press.turns() > 0 || press.stored() > 0;
    }

    /** A polisher: part way through a grain, or a grain gone (it was given sixteen). */
    private static boolean worked(RicePolisherBlockEntity polisher) {
        return polisher.turns() > 0 || polisher.getItem(RicePolisherBlockEntity.INPUT).getCount() < 16;
    }

    private static boolean worked(HandMachineBlockEntity machine) {
        return machine.turns() > 0 || !machine.getItem(HandMachineBlockEntity.OUTPUT).isEmpty();
    }

    /** Ties a belt with no one paying for it (a player-less tie still keeps what it would cost, to give back). */
    private static @Nullable Component tie(GameTestHelper helper, DriveWheelBlockEntity a, DriveWheelBlockEntity b) {
        return Belts.tie(helper.getLevel(), a, b, BeltKind.ROPE, null, new ItemStack(ModItems.ROPE.get()));
    }

    private static boolean refused(@Nullable Component why, String key) {
        return why != null && why.getContents() instanceof TranslatableContents t && t.getKey().equals("block.nookworks.drive_wheel." + key);
    }

    private static String said(@Nullable Component why) {
        return why == null ? "tied" : why.getString();
    }

    /** How many of {@code item} lie dropped within {@code box} (relative to the test). */
    private static int dropped(GameTestHelper helper, AABB box, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.absoluteAABB(box))
                .stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(helper.absoluteVec(Vec3.atCenterOf(pos)), Direction.UP, helper.absolutePos(pos), false);
    }

    // ---- the tests ----

    /**
     * A Motor turns the Hand Quern set on it or hung over it, by its spindle, and not one set against its side; and it
     * turns a Rice Polisher from a side, but not from above. Where a machine is not driven from a side, it shows no axle
     * out to the motor there either.
     */
    private static void drivenFromTheirOwnSides(GameTestHelper helper) {
        BlockPos above = new BlockPos(1, 2, 1);
        BlockPos below = new BlockPos(3, 2, 1);
        BlockPos side = new BlockPos(5, 2, 3);
        List<HandQuernBlock.Entity> querns = new ArrayList<>();
        for (BlockPos pos : List.of(above, below, side)) {
            helper.setBlock(pos, ModKitchen.HAND_QUERN.get());
            HandQuernBlock.Entity quern = helper.getBlockEntity(pos, HandQuernBlock.Entity.class);
            quern.setItem(HandMachineBlockEntity.INPUT, new ItemStack(Items.WHEAT, 16));
            querns.add(quern);
        }
        BlockPos polisherAbove = new BlockPos(1, 2, 5);
        BlockPos polisherSide = new BlockPos(4, 2, 5);
        List<RicePolisherBlockEntity> polishers = new ArrayList<>();
        for (BlockPos pos : List.of(polisherAbove, polisherSide)) {
            helper.setBlock(pos, ModCrops.RICE_POLISHER.get());
            RicePolisherBlockEntity polisher = helper.getBlockEntity(pos, RicePolisherBlockEntity.class);
            polisher.setItem(RicePolisherBlockEntity.INPUT, new ItemStack(ModCrops.BROWN_RICE.get(), 16));
            polishers.add(polisher);
        }
        motor(helper, above.above(), Direction.DOWN);
        motor(helper, below.below(), Direction.UP);
        motor(helper, side.west(), Direction.EAST);
        motor(helper, polisherAbove.above(), Direction.DOWN);
        motor(helper, polisherSide.west(), Direction.EAST);
        check(helper, helper.getBlockState(above).getValue(Drive.PROPERTY) == Drive.UP
                && helper.getBlockState(below).getValue(Drive.PROPERTY) == Drive.DOWN, "the quern meets a motor above it and one below");
        check(helper, helper.getBlockState(side).getValue(Drive.PROPERTY) == Drive.NONE, "but not one at its side");
        check(helper, helper.getBlockState(polisherAbove).getValue(Drive.PROPERTY) == Drive.NONE
                && helper.getBlockState(polisherSide).getValue(Drive.PROPERTY) == Drive.WEST, "the polisher meets one at its side, not above");
        int full = helper.getBlockEntity(side.west(), MotorBlockEntity.class).energyStored();
        helper.runAfterDelay(40, () -> {
            check(helper, worked(querns.get(0)) && worked(querns.get(1)), "the quern is turned from above and from below");
            check(helper, !worked(querns.get(2)), "and not from its side");
            check(helper, worked(polishers.get(1)) && !worked(polishers.get(0)), "the polisher is turned from its side, not from above");
            for (BlockPos idle : List.of(side.west(), polisherAbove.above())) {
                MotorBlockEntity motor = helper.getBlockEntity(idle, MotorBlockEntity.class);
                int spent = full - motor.energyStored();
                check(helper, spent > 0 && spent <= MachineTuning.energy(MotorBlockEntity.IDLE_FE_PER_TICK) * 40 && motor.guiStatus() == MachineStatus.IDLING,
                        "a motor that turns nothing idles on its own small power: " + spent + " FE, " + motor.guiStatus());
            }
            helper.succeed();
        });
    }

    /**
     * A belted wheel hung over the Hand Quern turns it, as a motor there would, and the quern shows the drive coming
     * from above; a Rice Polisher under the motor's own wheel - driven from above - is left alone.
     */
    private static void wheelTurnsTheQuernFromAbove(GameTestHelper helper) {
        BlockPos quernPos = new BlockPos(1, 2, 1);
        BlockPos polisherPos = new BlockPos(4, 2, 1);
        helper.setBlock(quernPos, ModKitchen.HAND_QUERN.get());
        helper.setBlock(polisherPos, ModCrops.RICE_POLISHER.get());
        HandQuernBlock.Entity quern = helper.getBlockEntity(quernPos, HandQuernBlock.Entity.class);
        quern.setItem(HandMachineBlockEntity.INPUT, new ItemStack(Items.WHEAT, 16));
        RicePolisherBlockEntity polisher = helper.getBlockEntity(polisherPos, RicePolisherBlockEntity.class);
        polisher.setItem(RicePolisherBlockEntity.INPUT, new ItemStack(ModCrops.BROWN_RICE.get(), 16));
        DriveWheelBlockEntity overQuern = wheel(helper, quernPos.above(), Direction.DOWN);
        DriveWheelBlockEntity ofMotor = wheel(helper, polisherPos.above(), Direction.DOWN);
        motor(helper, polisherPos.above(2), Direction.DOWN);
        check(helper, tie(helper, ofMotor, overQuern) == null, "the two upright wheels are belted");
        check(helper, helper.getBlockState(quernPos).getValue(Drive.PROPERTY) == Drive.UP, "the quern shows the drive above it");
        check(helper, helper.getBlockState(polisherPos).getValue(Drive.PROPERTY) == Drive.NONE, "the polisher none");
        helper.runAfterDelay(40, () -> {
            check(helper, worked(quern), "the quern is turned by the wheel over it");
            check(helper, !worked(polisher), "the polisher under the motor's wheel is not");
            check(helper, helper.getBlockState(quernPos.above()).getValue(DriveWheelBlock.TURNING), "and the quern's wheel goes round");
            helper.succeed();
        });
    }

    /**
     * A Motor with a Drive Wheel on its shaft turns the press on that wheel's far side and, by a rope belt to another
     * wheel, a second press: both as fast as one motor turns one, for 20 FE/t each. The wheels go round while it runs,
     * and stop when it does.
     */
    private static void oneMotorTwoPresses(GameTestHelper helper) {
        OilPressBlockEntity near = press(helper, new BlockPos(1, 2, 3));
        OilPressBlockEntity far = press(helper, new BlockPos(4, 2, 3));
        DriveWheelBlockEntity first = wheel(helper, new BlockPos(1, 2, 2), Direction.SOUTH);
        DriveWheelBlockEntity second = wheel(helper, new BlockPos(4, 2, 2), Direction.SOUTH);
        BlockPos motorPos = new BlockPos(1, 2, 1);
        motor(helper, motorPos, Direction.SOUTH);
        MotorBlockEntity motor = helper.getBlockEntity(motorPos, MotorBlockEntity.class);
        check(helper, tie(helper, first, second) == null, "the wheels are belted");
        check(helper, helper.getBlockState(new BlockPos(4, 2, 3)).getValue(Drive.PROPERTY) == Drive.NORTH,
                "the far press shows the drive on its wheel's side");
        int cost = MachineTuning.energy(MotorBlockEntity.FE_PER_TICK);
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(30, () -> {
            int a = near.turns() + near.stored() / 60 * 2;
            int b = far.turns() + far.stored() / 60 * 2;
            check(helper, a >= 2 && a == b, "both presses turned alike, as one motor turns one: " + a + " and " + b);
            check(helper, helper.getBlockState(new BlockPos(1, 2, 2)).getValue(DriveWheelBlock.TURNING)
                    && helper.getBlockState(new BlockPos(4, 2, 2)).getValue(DriveWheelBlock.TURNING), "both wheels go round");
            at[0] = motor.energyStored();
        }).thenExecuteAfter(10, () -> {
            int spent = at[0] - motor.energyStored();
            check(helper, spent == 2 * cost * 10, "two machines at work cost twice: " + spent + " FE in ten ticks");
            motor.energy().set(0);
        }).thenExecuteAfter(MotorBlockEntity.LAMP_HOLD + 10, () -> {
            // (the motor's coil, and its shaft, stay lit a second after it last turned: the wheels with them)
            check(helper, !helper.getBlockState(new BlockPos(4, 2, 2)).getValue(DriveWheelBlock.TURNING), "out of power, the wheels stop");
        }).thenSucceed();
    }

    /**
     * A line of belted wheels with presses on both ends of their axles - ten in all - is turned eight at a time at
     * most, the nearest first, for eight motors' worth of power.
     */
    private static void eightMachinesAtMost(GameTestHelper helper) {
        List<OilPressBlockEntity> presses = new ArrayList<>();
        for (int x = 0; x <= 6; x += 2) {
            presses.add(press(helper, new BlockPos(x, 2, 1)));
            presses.add(press(helper, new BlockPos(x, 2, 3)));
        }
        OilPressBlockEntity farA = press(helper, new BlockPos(6, 4, 1));
        OilPressBlockEntity farB = press(helper, new BlockPos(6, 4, 3));
        List<DriveWheelBlockEntity> line = new ArrayList<>();
        DriveWheelBlockEntity ofMotor = wheel(helper, new BlockPos(0, 4, 2), Direction.SOUTH);
        for (int x = 0; x <= 6; x += 2) {
            line.add(wheel(helper, new BlockPos(x, 2, 2), Direction.SOUTH));
        }
        DriveWheelBlockEntity last = wheel(helper, new BlockPos(6, 4, 2), Direction.SOUTH);
        BlockPos motorPos = new BlockPos(0, 4, 1);
        motor(helper, motorPos, Direction.SOUTH);
        MotorBlockEntity motor = helper.getBlockEntity(motorPos, MotorBlockEntity.class);
        check(helper, tie(helper, ofMotor, line.get(0)) == null, "the motor's wheel is belted down to the line");
        for (int i = 0; i + 1 < line.size(); i++) {
            check(helper, tie(helper, line.get(i), line.get(i + 1)) == null, "the line is belted");
        }
        check(helper, tie(helper, line.get(line.size() - 1), last) == null, "and up to the last wheel");
        int cost = MachineTuning.energy(MotorBlockEntity.FE_PER_TICK);
        int full = (int) motor.energy().getCapacityAsLong();
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, presses.stream().allMatch(R11DriveTests::worked), "the eight nearest presses are turned");
            check(helper, !worked(farA) && !worked(farB), "the ninth and tenth are not");
            motor.energy().set(full);
            at[0] = full;
        }).thenExecuteAfter(10, () -> {
            int spent = at[0] - motor.energyStored();
            check(helper, spent == DriveNetwork.MOST_MACHINES * cost * 10, "eight machines' power: " + spent + " FE in ten ticks");
        }).thenSucceed();
    }

    /**
     * A belt is tied only between wheels that turn the same way, stand in one plane, are near enough for the belt's kind
     * and have nothing solid between them - a carpet under the belt is no matter, a slab it would run through is - and
     * only twice on a wheel. What a belt took is kept at both ends, and kept through a save.
     */
    private static void beltsRunTrue(GameTestHelper helper) {
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1), Direction.NORTH);
        DriveWheelBlockEntity across = wheel(helper, new BlockPos(1, 2, 4), Direction.EAST);
        DriveWheelBlockEntity askew = wheel(helper, new BlockPos(4, 2, 3), Direction.NORTH);
        Component why = tie(helper, a, across);
        check(helper, refused(why, "not_parallel"), "wheels on crossed axles: " + said(why));
        why = tie(helper, a, askew);
        check(helper, refused(why, "not_in_line"), "wheels in different planes: " + said(why));

        DriveWheelBlockEntity low = wheel(helper, new BlockPos(0, 1, 6), Direction.NORTH);
        DriveWheelBlockEntity high = wheel(helper, new BlockPos(6, 8, 6), Direction.NORTH);
        why = tie(helper, low, high);
        check(helper, refused(why, "too_long"), "a rope belt over 8 blocks: " + said(why));
        ItemStack chain = new ItemStack(ModRope.THIN_CHAIN.get());
        check(helper, BeltKind.of(chain) == BeltKind.CHAIN && BeltKind.of(new ItemStack(ModItems.ROPE.get())) == BeltKind.ROPE
                && BeltKind.of(new ItemStack(ModItems.THIN_ROPE.get())) == null, "Rope and Thin Chain make belts; Thin Rope does not");
        why = Belts.tie(helper.getLevel(), low, high, BeltKind.CHAIN, null, chain);
        check(helper, why == null && low.belts().get(0).item() == ModRope.THIN_CHAIN.get(), "Thin Chain reaches 9 blocks: " + said(why));

        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1), Direction.SOUTH);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        why = tie(helper, a, b);
        check(helper, refused(why, "in_the_way"), "a slab the lower run would go through: " + said(why));
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.MOSS_CARPET);
        why = tie(helper, a, b);
        check(helper, why == null, "a moss carpet under it is no matter: " + said(why));
        why = tie(helper, b, a);
        check(helper, refused(why, "already"), "tied twice: " + said(why));
        check(helper, a.belts().size() == 1 && a.belts().get(0).count() == 8 && b.belts().get(0).count() == 8,
                "four blocks of belt took eight rope, kept at both ends");

        DriveWheelBlockEntity c = wheel(helper, new BlockPos(1, 4, 1), Direction.NORTH);
        DriveWheelBlockEntity d = wheel(helper, new BlockPos(3, 4, 1), Direction.NORTH);
        check(helper, tie(helper, a, c) == null, "a second belt on the wheel");
        why = tie(helper, a, d);
        check(helper, refused(why, "full"), "but no third: " + said(why));

        try (var reporter = new ProblemReporter.ScopedCollector(a.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            a.saveWithoutMetadata(output);
            var copy = new DriveWheelBlockEntity(a.getBlockPos(), a.getBlockState());
            copy.loadWithComponents(TagValueInput.create(reporter, helper.getLevel().registryAccess(), output.buildResult()));
            check(helper, copy.belts().equals(a.belts()), "saved and loaded, its belts are kept: " + copy.belts());
        }
        helper.succeed();
    }

    /**
     * Tied by a player in survival, a four-block belt takes eight Rope from them, or is refused with nothing taken when
     * they have too few; sneak-used with an empty hand, either wheel gives all eight back and lets the other go.
     */
    private static void beltPaidAndGivenBack(GameTestHelper helper) {
        BlockPos aPos = new BlockPos(1, 2, 1);
        BlockPos bPos = new BlockPos(5, 2, 1);
        DriveWheelBlockEntity a = wheel(helper, aPos, Direction.NORTH);
        DriveWheelBlockEntity b = wheel(helper, bPos, Direction.NORTH);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 2.5)));
        Item rope = ModItems.ROPE.get();
        var level = helper.getLevel();

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 3));
        helper.getBlockState(aPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, aPos));
        helper.getBlockState(bPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, bPos));
        check(helper, a.belts().isEmpty() && player.getInventory().countItem(rope) == 3, "three rope are too few: nothing tied, nothing taken");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 10));
        helper.getBlockState(aPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, aPos));
        check(helper, a.belts().isEmpty(), "used on the first wheel, the belt is only started");
        helper.getBlockState(bPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, bPos));
        check(helper, a.has(helper.absolutePos(bPos)) && b.has(helper.absolutePos(aPos)), "and on the second, tied");
        check(helper, player.getInventory().countItem(rope) == 2, "taking eight of the ten: " + player.getInventory().countItem(rope));

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.getBlockState(bPos).useWithoutItem(level, player, hit(helper, bPos));
        check(helper, a.belts().isEmpty() && b.belts().isEmpty(), "taken off from either end, both wheels let it go");
        check(helper, player.getInventory().countItem(rope) == 8, "and the eight come back: " + player.getInventory().countItem(rope));
        helper.succeed();
    }

    /**
     * A player cannot set a block down in a belt's way - it is taken back up, and stays in their hand - though a carpet
     * under it is let be; nor a block on the far side of a wheel, out of the way.
     */
    private static void noBlockInABeltsWay(GameTestHelper helper) {
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1), Direction.NORTH);
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1), Direction.NORTH);
        check(helper, tie(helper, a, b) == null, "tied");
        BlockPos under = new BlockPos(3, 1, 1);
        helper.setBlock(under, Blocks.STONE);
        helper.setBlock(new BlockPos(6, 1, 1), Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        var level = helper.getLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, under)));
        check(helper, helper.getBlockState(under.above()).isAir() && player.getMainHandItem().getCount() == 4,
                "a stone set in the belt's way is taken back: " + helper.getBlockState(under.above()));
        check(helper, a.belts().size() == 1, "and the belt stays");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MOSS_CARPET, 4));
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, under)));
        check(helper, helper.getBlockState(under.above()).is(Blocks.MOSS_CARPET), "a carpet under it is let be");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, new BlockPos(6, 1, 1))));
        check(helper, helper.getBlockState(new BlockPos(6, 2, 1)).is(Blocks.STONE), "beyond the wheel, out of its way, a stone is set down");
        helper.succeed();
    }

    /** A block set in a belt's way since it was tied - by a piston, say - snaps it, and its rope falls there. */
    private static void beltSnaps(GameTestHelper helper) {
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1), Direction.NORTH);
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1), Direction.NORTH);
        check(helper, tie(helper, a, b) == null, "tied");
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.STONE);
        helper.succeedWhen(() -> {
            check(helper, a.belts().isEmpty() && b.belts().isEmpty(), "the belt snaps");
            int rope = dropped(helper, new AABB(1, 1, 0, 6, 4, 3), ModItems.ROPE.get());
            check(helper, rope == 8, "its eight rope fall: " + rope);
        });
    }

    /** A belted wheel turned another way - as the Wrench turns it - lets its belt fall at once, rope and all. */
    private static void turnedOutOfLine(GameTestHelper helper) {
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1), Direction.NORTH);
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1), Direction.NORTH);
        check(helper, tie(helper, a, b) == null, "tied");
        BlockPos turnedPos = new BlockPos(5, 2, 1);
        helper.setBlock(turnedPos, helper.getBlockState(turnedPos).setValue(DriveWheelBlock.FACING, Direction.EAST));
        check(helper, helper.getBlockEntity(turnedPos, DriveWheelBlockEntity.class) == b, "the wheel is the same one, turned");
        helper.succeedWhen(() -> {
            check(helper, a.belts().isEmpty() && b.belts().isEmpty(), "the belt comes off");
            int rope = dropped(helper, new AABB(1, 1, 0, 6, 4, 3), ModItems.ROPE.get());
            check(helper, rope == 8, "and its eight rope fall: " + rope);
        });
    }

    /** Exactly the eight rope a four-block belt takes, in hand: tied, it keeps them as rope (the emptied hand read as air). */
    private static void paidToTheLast(GameTestHelper helper) {
        BlockPos aPos = new BlockPos(1, 2, 1);
        BlockPos bPos = new BlockPos(5, 2, 1);
        DriveWheelBlockEntity a = wheel(helper, aPos, Direction.NORTH);
        wheel(helper, bPos, Direction.NORTH);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 2.5)));
        Item rope = ModItems.ROPE.get();
        var level = helper.getLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 8));
        helper.getBlockState(aPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, aPos));
        helper.getBlockState(bPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, bPos));
        check(helper, a.belts().size() == 1 && a.belts().get(0).item() == rope && player.getMainHandItem().isEmpty(),
                "tied with the hand's last eight, the belt is of rope: " + a.belts());
        player.setShiftKeyDown(true);
        helper.getBlockState(aPos).useWithoutItem(level, player, hit(helper, aPos));
        check(helper, player.getInventory().countItem(rope) == 8, "taken off, all eight back: " + player.getInventory().countItem(rope));
        helper.succeed();
    }

    /** Three rope in hand and ten more in the pack: a four-block belt takes the three and five of the ten. */
    private static void paidFromTwoStacks(GameTestHelper helper) {
        BlockPos aPos = new BlockPos(1, 2, 1);
        BlockPos bPos = new BlockPos(5, 2, 1);
        DriveWheelBlockEntity a = wheel(helper, aPos, Direction.NORTH);
        wheel(helper, bPos, Direction.NORTH);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 2.5)));
        Item rope = ModItems.ROPE.get();
        var level = helper.getLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 3));
        player.getInventory().setItem(20, new ItemStack(rope, 10));
        helper.getBlockState(aPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, aPos));
        helper.getBlockState(bPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, bPos));
        int left = player.getInventory().countItem(rope);
        check(helper, a.belts().size() == 1 && a.belts().get(0).item() == rope && a.belts().get(0).count() == 8 && left == 5,
                "13 rope, 8 taken, 5 left: " + left + ", the belt " + a.belts());
        helper.succeed();
    }

    /**
     * A wheel set against another, end to end on its axle, turns with it as one: the motor's wheel turns the one beside
     * it, and that one's belt turns a press.
     */
    private static void wheelsOnOneAxle(GameTestHelper helper) {
        OilPressBlockEntity press = press(helper, new BlockPos(4, 2, 4));
        wheel(helper, new BlockPos(1, 2, 2), Direction.SOUTH);
        DriveWheelBlockEntity beside = wheel(helper, new BlockPos(1, 2, 3), Direction.SOUTH);
        DriveWheelBlockEntity far = wheel(helper, new BlockPos(4, 2, 3), Direction.SOUTH);
        motor(helper, new BlockPos(1, 2, 1), Direction.SOUTH);
        check(helper, tie(helper, beside, far) == null, "the wheel beside the motor's is belted to the press's");
        helper.runAfterDelay(40, () -> {
            check(helper, worked(press), "the press is turned through the two wheels on one axle");
            check(helper, helper.getBlockState(new BlockPos(1, 2, 3)).getValue(DriveWheelBlock.TURNING), "and the second wheel goes round");
            helper.succeed();
        });
    }

    /**
     * A wheel gone while the other end of its belt was away (its area not loaded - here, taken without a word): the
     * end that owns the belt gives its rope back when it finds the wheel gone, and the other end does not, so it comes
     * back once either way.
     */
    private static void farWheelWentWhileAway(GameTestHelper helper) {
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1), Direction.NORTH);
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1), Direction.NORTH);
        DriveWheelBlockEntity c = wheel(helper, new BlockPos(1, 2, 5), Direction.NORTH);
        DriveWheelBlockEntity d = wheel(helper, new BlockPos(5, 2, 5), Direction.NORTH);
        check(helper, tie(helper, a, b) == null && tie(helper, c, d) == null, "tied");
        // in each pair, the wheel taken is the one that does not own the belt in the first, the owner in the second
        DriveWheelBlockEntity notOwner = a.owns(b.getBlockPos()) ? b : a;
        DriveWheelBlockEntity owner = c.owns(d.getBlockPos()) ? c : d;
        DriveWheelBlockEntity left = owner == c ? d : c;
        var level = helper.getLevel();
        int quietly = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        level.setBlock(notOwner.getBlockPos(), Blocks.AIR.defaultBlockState(), quietly);
        level.setBlock(owner.getBlockPos(), Blocks.AIR.defaultBlockState(), quietly);
        DriveWheelBlockEntity ownerLeft = notOwner == a ? b : a;
        helper.succeedWhen(() -> {
            check(helper, ownerLeft.belts().isEmpty() && left.belts().isEmpty(), "both wheels left let go");
            int first = dropped(helper, new AABB(0, 1, 0, 7, 4, 3), ModItems.ROPE.get());
            int second = dropped(helper, new AABB(0, 1, 4, 7, 4, 7), ModItems.ROPE.get());
            check(helper, first == 8, "the owner left gives the eight back: " + first);
            check(helper, second == 0, "the other end left does not (the owner gave them back as it went): " + second);
        });
    }

    /** Belted wheels, and of the two, the one that owns the belt and the other. */
    private record Pair(DriveWheelBlockEntity owner, DriveWheelBlockEntity other) {}

    private static Pair belted(GameTestHelper helper) {
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1), Direction.NORTH);
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1), Direction.NORTH);
        check(helper, tie(helper, a, b) == null, "tied");
        return a.owns(b.getBlockPos()) ? new Pair(a, b) : new Pair(b, a);
    }

    /** Taken away without a word, as when its area was not loaded: what it does on going is not done. */
    private static void goneWhileAway(GameTestHelper helper, DriveWheelBlockEntity wheel) {
        helper.getLevel().setBlock(wheel.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
    }

    /**
     * The other end broken while the owner was away gave nothing back (the owner does); the owner, broken before its
     * own check has found that, gives the belt's rope back as it goes.
     */
    private static void ownerBrokenAfterFarWent(GameTestHelper helper) {
        Pair pair = belted(helper);
        goneWhileAway(helper, pair.other());
        helper.getLevel().destroyBlock(pair.owner().getBlockPos(), true);
        int rope = dropped(helper, new AABB(0, 1, 0, 7, 4, 3), ModItems.ROPE.get());
        check(helper, rope == 8, "the eight rope come back once: " + rope);
        helper.succeed();
    }

    /**
     * The owner gone while the other end was away gave the rope back as it went; the other end, taken off before its own
     * check has found that, gives nothing more.
     */
    private static void takenOffAfterOwnerWent(GameTestHelper helper) {
        Pair pair = belted(helper);
        goneWhileAway(helper, pair.owner());
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 2.5)));
        player.setShiftKeyDown(true);
        BlockPos at = pair.other().getBlockPos().equals(helper.absolutePos(new BlockPos(1, 2, 1))) ? new BlockPos(1, 2, 1) : new BlockPos(5, 2, 1);
        helper.getBlockState(at).useWithoutItem(helper.getLevel(), player, hit(helper, at));
        check(helper, pair.other().belts().isEmpty() && player.getInventory().countItem(ModItems.ROPE.get()) == 0,
                "the belt is let go of, and no second eight rope: " + player.getInventory().countItem(ModItems.ROPE.get()));
        helper.succeed();
    }

    /**
     * A belt started on a wheel and left half a minute is not tied off from there: the next wheel used starts one,
     * taking nothing; used on the first again then, it is tied.
     */
    private static void startedBeltLapses(GameTestHelper helper) {
        BlockPos aPos = new BlockPos(1, 2, 1);
        BlockPos bPos = new BlockPos(5, 2, 1);
        DriveWheelBlockEntity a = wheel(helper, aPos, Direction.NORTH);
        DriveWheelBlockEntity b = wheel(helper, bPos, Direction.NORTH);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 2, 2.5)));
        Item rope = ModItems.ROPE.get();
        var level = helper.getLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(rope, 20));
        helper.getBlockState(aPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, aPos));
        helper.runAfterDelay(610, () -> {
            helper.getBlockState(bPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, bPos));
            check(helper, a.belts().isEmpty() && b.belts().isEmpty() && player.getInventory().countItem(rope) == 20,
                    "half a minute on, the next wheel only starts a belt");
            helper.getBlockState(aPos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit(helper, aPos));
            check(helper, a.has(helper.absolutePos(bPos)) && player.getInventory().countItem(rope) == 12,
                    "and the first, used again, ties it off: " + player.getInventory().countItem(rope) + " rope left");
            helper.succeed();
        });
    }

    /**
     * A thing hung on a span tells clients which span (as it is hung, and loaded again): they swing it with that chain
     * alone, and know it from nowhere else.
     */
    private static void hungThingsSpanSeen(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos from = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos to = helper.absolutePos(new BlockPos(5, 2, 1));
        HungItemEntity hung = HungItemEntity.onSpan(level, from, to, 0.5, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, from.equals(hung.spanFromSeen()) && to.equals(hung.spanToSeen()), "hung, its span is sent: " + hung.spanFromSeen());
        try (var reporter = new ProblemReporter.ScopedCollector(hung.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            hung.saveWithoutId(output);
            HungItemEntity copy = new HungItemEntity(ModRope.HUNG_ITEM.get(), level);
            copy.load(TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
            check(helper, from.equals(copy.spanFromSeen()) && to.equals(copy.spanToSeen()), "and loaded again: " + copy.spanFromSeen());
        }
        helper.succeed();
    }

    /** A wheel broken drops its belt's rope with itself, and the wheel at the other end lets go of the belt. */
    private static void brokenWheelDropsItsBelt(GameTestHelper helper) {
        DriveWheelBlockEntity a = wheel(helper, new BlockPos(1, 2, 1), Direction.NORTH);
        DriveWheelBlockEntity b = wheel(helper, new BlockPos(5, 2, 1), Direction.NORTH);
        check(helper, tie(helper, a, b) == null, "tied");
        helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(5, 2, 1)), true);
        check(helper, a.belts().isEmpty(), "the far wheel lets go");
        int rope = dropped(helper, new AABB(4, 1, 0, 7, 4, 3), ModItems.ROPE.get());
        int wheels = dropped(helper, new AABB(4, 1, 0, 7, 4, 3), ModOil.DRIVE_WHEEL_ITEM.get());
        check(helper, rope == 8 && wheels == 1, "the rope and the wheel fall where it was: " + rope + " rope, " + wheels + " wheel");
        helper.succeed();
    }
}
