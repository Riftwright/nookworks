package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;
import java.util.Locale;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.nookworks.compat.create.CreateCompat;
import dev.nookworks.compat.create.CreateDrives;
import dev.nookworks.compat.create.CreateEnds;
import dev.nookworks.compat.create.MotorShaftBlock;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.Drive;
import dev.nookworks.machine.ForeignDrives;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Our turning parts in Create's own network (tools/port1211/design_create.md, "Ends and trains"), tried as a Create
 * player would set things down: every kind of Create part ({@link Part}) against every end of Nookworks' own (a Motor's
 * axle, an axle end of a Motor's wheel line, a hand machine's drive side, an axle end of a wheel line with no Motor)
 * in every sense (giving, taking, passing on into the part and out of it) - the design's table, a test a cell, each with
 * its control row - then the owner's scenes, Create's own tools on what we touch, and what must never happen.
 * Registered only with Create (from CreateTests).
 */
final class CreateEndsTests {
    private CreateEndsTests() {}

    /** Create's parts with a shaft end, as the brief lists them. */
    enum Part {
        SHAFT, COGWHEEL, LARGE_COGWHEEL, GEARBOX, ENCASED_SHAFT, CLUTCH, GEARSHIFT, BELT_PULLEY, MACHINE
    }

    static void register() {
        for (Part part : Part.values()) {
            String name = part.name().toLowerCase(Locale.ROOT);
            NookworksGameTests.test("create_ends_motor_axle_drives_a_" + name, 120, helper -> givingAxle(helper, part));
            NookworksGameTests.test("create_ends_motor_wheel_drives_a_" + name, 120, helper -> givingWheel(helper, part));
            NookworksGameTests.test("create_ends_machine_takes_from_a_" + name, 120, helper -> takingMachine(helper, part));
            NookworksGameTests.test("create_ends_wheel_takes_from_a_" + name, 120, helper -> takingWheel(helper, part));
            NookworksGameTests.test("create_ends_wheel_passes_on_into_a_" + name, 120, helper -> passingInto(helper, part));
            NookworksGameTests.test("create_ends_wheel_passes_on_out_of_a_" + name, 120, helper -> passingOutOf(helper, part));
        }
        // the owner's scenes (Nookworks' own: 1, 3, 5)
        NookworksGameTests.test("create_scene1_creates_belt_goes_on_a_shaft_a_motor_drives_and_carries", 200, CreateEndsTests::sceneBelt);
        NookworksGameTests.test("create_scene3_large_cogwheel_on_the_axle_turns_a_cog_meshed_with_it", 100, CreateEndsTests::sceneLargeCog);
        NookworksGameTests.test("create_scene5_motor_shaft_wheel_shaft_turns_the_far_shaft", 120, CreateEndsTests::sceneWheel);
        // Create's own tools on what we touch
        NookworksGameTests.test("create_tools_casing_put_on_a_driven_shaft_keeps_it_turning", 120, CreateEndsTests::casing);
        NookworksGameTests.test("create_tools_wrench_turns_and_picks_up_a_driven_shaft", 160, CreateEndsTests::wrench);
        NookworksGameTests.test("create_tools_cogwheel_set_on_a_driven_shaft_turns", 100, CreateEndsTests::cogOnShaft);
        NookworksGameTests.test("create_tools_driven_shaft_broken_and_set_again", 140, CreateEndsTests::shaftSetAgain);
        NookworksGameTests.test("create_tools_wheel_between_two_shafts_broken_and_set_again", 160, CreateEndsTests::wheelSetAgain);
        // what must not happen
        NookworksGameTests.test("create_never_two_networks_against_each_other_are_joined_or_broken", 200, CreateEndsTests::against);
        NookworksGameTests.test("create_never_a_source_set_against_a_motors_network_is_popped", 160, CreateEndsTests::sourceSetAgainst);
        NookworksGameTests.test("create_never_a_shaft_joining_a_motors_network_to_another_is_popped", 160, CreateEndsTests::shaftBetween);
        NookworksGameTests.test("create_never_a_create_source_started_against_a_motors_network_is_popped", 160, CreateEndsTests::sourceStartedAgainst);
        NookworksGameTests.test("create_never_a_source_started_against_a_network_joined_through_a_wheel_is_popped", 200, CreateEndsTests::sourceBeyondWheel);
        NookworksGameTests.test("create_never_a_shaft_joining_a_wheels_far_side_to_another_network_is_popped", 200, CreateEndsTests::shaftBeyondWheel);
        NookworksGameTests.test("create_never_a_shaft_set_between_a_motor_and_a_network_turning_the_other_way_is_popped", 160, CreateEndsTests::shaftAtAxle);
        NookworksGameTests.test("create_never_two_sources_set_at_once_at_a_wheels_two_ends_pop_a_shaft", 160, CreateEndsTests::setAtOnce);
        NookworksGameTests.test("create_never_flickers_over_a_minute", 1400, CreateEndsTests::noFlicker);
        NookworksGameTests.test("create_never_turns_on_unpaid_when_loaded_without_its_motor", 100, CreateEndsTests::loadedWithoutMotor);
        NookworksGameTests.test("create_never_overstress_on_one_side_of_a_wheel_alone_and_it_recovers", 200, CreateEndsTests::overstressBothSides);
        NookworksGameTests.test("create_never_free_a_press_beyond_a_wheel_costs_what_it_costs", 200, CreateEndsTests::pressBeyondWheel);
        NookworksGameTests.test("create_never_a_redstone_clock_on_the_motor_pops_the_shaft", 400, CreateEndsTests::redstoneClock);
        // edges
        NookworksGameTests.test("create_ends_gearbox_across_a_belted_line_turns_from_the_face_it_is_met_at", 120, CreateEndsTests::gearboxAcrossBelt);
        NookworksGameTests.test("create_ends_wheel_line_across_its_belt_passes_on", 160, CreateEndsTests::acrossBelt);
        NookworksGameTests.test("create_ends_motor_adds_to_a_create_generator_and_gives_none_to_a_still_one", 120, CreateEndsTests::generatorAtAxle);
        NookworksGameTests.test("create_ends_old_motor_shaft_is_creates_plain_shaft_again", 100, CreateEndsTests::migration);
        NookworksGameTests.test("create_ends_goggles_show_generator_stats_on_a_driven_block", 100, CreateEndsTests::goggles);
    }

    // ---- helpers ----

    private static final Direction.Axis X = Direction.Axis.X;

    private static Block create(String id) {
        return CreateTests.createBlock(id);
    }

    /** Create's block of {@code part} (a belt's pulley is Create's belt block). */
    private static Block blockOf(Part part) {
        return create(switch (part) {
            case SHAFT -> "shaft";
            case COGWHEEL -> "cogwheel";
            case LARGE_COGWHEEL -> "large_cogwheel";
            case GEARBOX -> "gearbox";
            case ENCASED_SHAFT -> "andesite_encased_shaft";
            case CLUTCH -> "clutch";
            case GEARSHIFT -> "gearshift";
            case BELT_PULLEY -> "belt";
            case MACHINE -> "mechanical_press";
        });
    }

    /**
     * {@code part} set at {@code pos} with shaft ends east and west: a Gearbox upright (its four sides its shafts), a
     * belt between a shaft there and one two blocks south (the block at {@code pos} its pulley), a Mechanical Press
     * with its shaft through east and west.
     */
    private static KineticBlockEntity part(GameTestHelper helper, Part part, BlockPos pos) {
        switch (part) {
            case GEARBOX -> helper.setBlock(pos, blockOf(part).defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
            case BELT_PULLEY -> {
                CreateTests.shaft(helper, pos, X);
                CreateTests.shaft(helper, pos.south(2), X);
                BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(pos), helper.absolutePos(pos.south(2)));
            }
            case MACHINE -> helper.setBlock(pos, blockOf(part).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            default -> helper.setBlock(pos, blockOf(part).defaultBlockState().setValue(BlockStateProperties.AXIS, X));
        }
        check(helper, helper.getBlockState(pos).getBlock() == blockOf(part), "set: " + helper.getBlockState(pos));
        return helper.getBlockEntity(pos, KineticBlockEntity.class);
    }

    /** What a turn coming in at the part's west end makes of a shaft at its east end: a Gearbox turns it back. */
    private static float through(Part part) {
        return part == Part.GEARBOX ? -1.0F : 1.0F;
    }

    private static KineticBlockEntity kinetic(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, KineticBlockEntity.class);
    }

    private static boolean sameNetwork(KineticBlockEntity a, KineticBlockEntity b) {
        return a.network != null && a.network.equals(b.network);
    }

    private static UseOnContext click(GameTestHelper helper, Player player, BlockPos pos, Direction face) {
        BlockPos at = helper.absolutePos(pos);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), face, at, false));
    }

    private static Player holding(GameTestHelper helper, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static int drops(GameTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(7, 6, 7).inflate(1);
        int count = 0;
        for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) {
            count += drop.getItem().getCount();
        }
        return count;
    }

    // ---- the table: giving ----

    /** A block driven where it stands: Create's own block still, a source of its network at the Motor's 30 RPM with 480 su. */
    private static void checkDriven(GameTestHelper helper, Part part, BlockPos pos, BlockPos beyond) {
        BlockState state = helper.getBlockState(pos);
        check(helper, state.getBlock() == blockOf(part), "Create's own block still: " + state);
        KineticBlockEntity driven = kinetic(helper, pos);
        check(helper, CreateMotorTests.at30(driven.getSpeed()), "it turns at the Motor's 30 RPM: " + driven.getSpeed());
        check(helper, CreateMotorTests.generating(driven), "a source of its network where it stands");
        check(helper, CreateMotorTests.capacity(driven) == 480.0F, "with the Motor's 480 su: " + CreateMotorTests.capacity(driven));
        check(helper, !driven.isOverStressed(), "not overstressed");
        KineticBlockEntity far = kinetic(helper, beyond);
        check(helper, far.getSpeed() == driven.getSpeed() * through(part), "Create's own rules carry it on to the shaft beyond: "
                + far.getSpeed() + " of " + driven.getSpeed());
    }

    /** Powers the Clutch or Gearshift at {@code pos}; afterwards the shaft {@code beyond} it stands (Clutch) or turns back (Gearshift). */
    private static Runnable thenThrown(GameTestHelper helper, Part part, BlockPos pos, BlockPos beyond, float[] went) {
        return () -> {
            if (part != Part.CLUTCH && part != Part.GEARSHIFT) {
                return;
            }
            KineticBlockEntity far = kinetic(helper, beyond);
            float wanted = part == Part.CLUTCH ? 0.0F : -went[0];
            check(helper, far.getSpeed() == wanted, "powered, Create's own " + part + " rule holds beyond it: " + far.getSpeed() + ", was " + went[0]);
            check(helper, helper.getBlockState(pos).getBlock() == blockOf(part) && helper.getBlockState(beyond).getBlock() == create("shaft"),
                    "nothing broken");
        };
    }

    private static void power(GameTestHelper helper, Part part, BlockPos pos, BlockPos beyond, float[] went) {
        if (part == Part.CLUTCH || part == Part.GEARSHIFT) {
            went[0] = kinetic(helper, beyond).getSpeed();
            helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        }
    }

    /** A powered Motor, {@code part} straight against its axle, a shaft beyond; the control: the same against a Motor with no power. */
    private static void givingAxle(GameTestHelper helper, Part part) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 0), Direction.EAST);
        BlockPos pos = new BlockPos(2, 1, 0);
        BlockPos beyond = new BlockPos(3, 1, 0);
        part(helper, part, pos);
        CreateTests.shaft(helper, beyond, X);
        MotorBlockEntity dead = CreateTests.motor(helper, new BlockPos(1, 1, 4), Direction.EAST);
        dead.energy().set(0);
        part(helper, part, new BlockPos(2, 1, 4));
        float[] went = new float[1];
        helper.startSequence().thenExecuteAfter(40, () -> {
            checkDriven(helper, part, pos, beyond);
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "the Motor says it turns Create: " + motor.guiStatus());
            KineticBlockEntity still = kinetic(helper, new BlockPos(2, 1, 4));
            check(helper, still.getSpeed() == 0.0F && !CreateMotorTests.generating(still), "against a Motor with no power it stands: " + still.getSpeed());
            power(helper, part, pos, beyond, went);
        }).thenExecuteAfter(10, thenThrown(helper, part, pos, beyond, went)).thenSucceed();
    }

    /** A powered Motor, a Drive Wheel on its axle, {@code part} at the wheel's far axle end, a shaft beyond; the control with no power. */
    private static void givingWheel(GameTestHelper helper, Part part) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(0, 1, 0), Direction.EAST);
        CreateTests.wheel(helper, new BlockPos(1, 1, 0), Direction.EAST);
        BlockPos pos = new BlockPos(2, 1, 0);
        BlockPos beyond = new BlockPos(3, 1, 0);
        part(helper, part, pos);
        CreateTests.shaft(helper, beyond, X);
        MotorBlockEntity dead = CreateTests.motor(helper, new BlockPos(0, 1, 4), Direction.EAST);
        dead.energy().set(0);
        CreateTests.wheel(helper, new BlockPos(1, 1, 4), Direction.EAST);
        part(helper, part, new BlockPos(2, 1, 4));
        float[] went = new float[1];
        helper.startSequence().thenExecuteAfter(40, () -> {
            checkDriven(helper, part, pos, beyond);
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "the Motor says it turns Create: " + motor.guiStatus());
            check(helper, helper.getBlockState(new BlockPos(1, 1, 0)).getValue(DriveWheelBlock.TURNING), "its wheel goes round");
            KineticBlockEntity still = kinetic(helper, new BlockPos(2, 1, 4));
            check(helper, still.getSpeed() == 0.0F && !CreateMotorTests.generating(still), "with no power it stands: " + still.getSpeed());
            power(helper, part, pos, beyond, went);
        }).thenExecuteAfter(10, thenThrown(helper, part, pos, beyond, went)).thenSucceed();
    }

    // ---- the table: taking ----

    /** A Creative Motor, {@code part}, an Oil Press straight against the part's far shaft end; the control: the motor at 0 RPM. */
    private static void takingMachine(GameTestHelper helper, Part part) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 0), Direction.EAST, 32);
        KineticBlockEntity turning = part(helper, part, new BlockPos(1, 1, 0));
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(2, 1, 0));
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 4), Direction.EAST, 0);
        part(helper, part, new BlockPos(1, 1, 4));
        OilPressBlockEntity still = CreateTests.press(helper, new BlockPos(2, 1, 4));
        float own = turning.calculateStressApplied(); // the part's own impact, before the press weighs on it
        helper.startSequence().thenExecuteAfter(50, () -> {
            check(helper, helper.getBlockState(new BlockPos(2, 1, 0)).getValue(Drive.PROPERTY) == Drive.WEST, "the press shows its axle toward the part");
            check(helper, CreateTests.turns(press) >= 2, "the part turns the press: " + CreateTests.turns(press));
            KineticBlockEntity holder = kinetic(helper, new BlockPos(1, 1, 0));
            check(helper, CreateTests.networkStress(holder) >= own * 32.0F + 64.0F, "the press weighs its 2 su/RPM on Create's network: "
                    + CreateTests.networkStress(holder));
            check(helper, CreateTests.turns(still) == 0, "a part standing still turns nothing: " + CreateTests.turns(still));
        }).thenSucceed();
    }

    /** A Creative Motor, {@code part}, a Drive Wheel on the part's far shaft end, an Oil Press on the wheel's far axle end; the control at 0 RPM. */
    private static void takingWheel(GameTestHelper helper, Part part) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 0), Direction.EAST, 32);
        part(helper, part, new BlockPos(1, 1, 0));
        CreateTests.wheel(helper, new BlockPos(2, 1, 0), Direction.EAST);
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(3, 1, 0));
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 4), Direction.EAST, 0);
        part(helper, part, new BlockPos(1, 1, 4));
        CreateTests.wheel(helper, new BlockPos(2, 1, 4), Direction.EAST);
        OilPressBlockEntity still = CreateTests.press(helper, new BlockPos(3, 1, 4));
        helper.startSequence().thenExecuteAfter(50, () -> {
            check(helper, helper.getBlockState(new BlockPos(2, 1, 0)).getValue(DriveWheelBlock.TURNING), "the wheel goes round");
            check(helper, CreateTests.turns(press) >= 2, "and turns the press on its axle: " + CreateTests.turns(press));
            check(helper, !helper.getBlockState(new BlockPos(2, 1, 4)).getValue(DriveWheelBlock.TURNING) && CreateTests.turns(still) == 0,
                    "a part standing still turns nothing");
        }).thenSucceed();
    }

    // ---- the table: passing on ----

    /**
     * A Creative Motor, a shaft, a Drive Wheel, then {@code part} at the wheel's far axle end and a shaft beyond it: the
     * part turns, one Create network with the motor's side. The control: a stone in the wheel's place.
     */
    private static void passingInto(GameTestHelper helper, Part part) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 0), Direction.EAST, 32);
        KineticBlockEntity near = CreateTests.shaft(helper, new BlockPos(1, 1, 0), X);
        CreateTests.wheel(helper, new BlockPos(2, 1, 0), Direction.EAST);
        BlockPos pos = new BlockPos(3, 1, 0);
        BlockPos beyond = new BlockPos(4, 1, 0);
        part(helper, part, pos);
        CreateTests.shaft(helper, beyond, X);
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 4), Direction.EAST, 32);
        CreateTests.shaft(helper, new BlockPos(1, 1, 4), X);
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.STONE);
        part(helper, part, new BlockPos(3, 1, 4));
        float[] went = new float[1];
        helper.startSequence().thenExecuteAfter(40, () -> {
            KineticBlockEntity passed = kinetic(helper, pos);
            check(helper, helper.getBlockState(pos).getBlock() == blockOf(part), "Create's own block still");
            check(helper, passed.getSpeed() == 32.0F, "the wheel passes the turning on into it: " + passed.getSpeed());
            check(helper, sameNetwork(near, passed), "one Create network: " + near.network + " and " + passed.network);
            check(helper, !CreateMotorTests.generating(passed), "no source of ours in it");
            check(helper, CreateMotorTests.capacity(passed) == 16384.0F * 32.0F, "the motor's capacity is this side's too: " + CreateMotorTests.capacity(passed));
            check(helper, kinetic(helper, beyond).getSpeed() == 32.0F * through(part), "and on to the shaft beyond: " + kinetic(helper, beyond).getSpeed());
            check(helper, helper.getBlockState(new BlockPos(2, 1, 0)).getValue(DriveWheelBlock.TURNING), "the wheel turns with them");
            check(helper, kinetic(helper, new BlockPos(3, 1, 4)).getSpeed() == 0.0F, "with a stone for the wheel nothing passes");
            power(helper, part, pos, beyond, went);
        }).thenExecuteAfter(10, thenThrown(helper, part, pos, beyond, went)).thenSucceed();
    }

    /**
     * A Creative Motor, {@code part}, a Drive Wheel on the part's far shaft end, a shaft at the wheel's far axle end: the
     * shaft turns as Create's own rules bring the turning out of the part. The control: a stone in the wheel's place.
     */
    private static void passingOutOf(GameTestHelper helper, Part part) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 0), Direction.EAST, 32);
        BlockPos pos = new BlockPos(1, 1, 0);
        KineticBlockEntity from = part(helper, part, pos);
        CreateTests.wheel(helper, new BlockPos(2, 1, 0), Direction.EAST);
        BlockPos beyond = new BlockPos(3, 1, 0);
        CreateTests.shaft(helper, beyond, X);
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 4), Direction.EAST, 32);
        part(helper, part, new BlockPos(1, 1, 4));
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.STONE);
        CreateTests.shaft(helper, new BlockPos(3, 1, 4), X);
        float[] went = new float[1];
        helper.startSequence().thenExecuteAfter(40, () -> {
            KineticBlockEntity far = kinetic(helper, beyond);
            check(helper, far.getSpeed() == 32.0F * through(part), "the wheel passes the turning on out of it: " + far.getSpeed());
            check(helper, sameNetwork(kinetic(helper, pos), far), "one Create network");
            check(helper, helper.getBlockState(new BlockPos(2, 1, 0)).getValue(DriveWheelBlock.TURNING), "the wheel turns with them");
            check(helper, kinetic(helper, new BlockPos(3, 1, 4)).getSpeed() == 0.0F, "with a stone for the wheel nothing passes");
            check(helper, from.getBlockState().getBlock() == blockOf(part) || part == Part.BELT_PULLEY, "Create's own block still");
            power(helper, part, pos, beyond, went);
        }).thenExecuteAfter(10, thenThrown(helper, part, pos, beyond, went)).thenSucceed();
    }

    // ---- the owner's scenes ----

    /**
     * Scene 1: a powered Motor, a Create Shaft against its axle, another shaft three blocks off; Create's Mechanical Belt
     * is put on the two by a player's two clicks (it was refused: the shaft was no longer Create's). The belt runs, the
     * Motor now driving its pulley where it stands, and carries an item.
     */
    private static void sceneBelt(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
        BlockPos first = new BlockPos(2, 1, 1);
        BlockPos second = new BlockPos(2, 1, 4);
        CreateTests.shaft(helper, first, X);
        CreateTests.shaft(helper, second, X);
        float[] at = new float[1];
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, CreateMotorTests.at30(CreateMotorTests.driven(helper, first).getSpeed()), "the Motor turns Create's own shaft");
            ItemStack belt = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "belt_connector")));
            Player player = holding(helper, belt);
            check(helper, belt.useOn(click(helper, player, second, Direction.UP)).consumesAction(), "Create's belt takes the far shaft");
            check(helper, belt.useOn(click(helper, player, first, Direction.UP)).consumesAction(), "and the shaft the Motor drives");
            check(helper, helper.getBlockState(first).getBlock() == create("belt") && helper.getBlockState(new BlockPos(2, 1, 2)).getBlock() == create("belt"),
                    "the belt is laid: " + helper.getBlockState(first));
        }).thenExecuteAfter(20, () -> {
            BeltBlockEntity pulley = helper.getBlockEntity(first, BeltBlockEntity.class);
            check(helper, CreateMotorTests.at30(pulley.getSpeed()) && CreateMotorTests.generating(pulley), "the Motor drives the belt's pulley: " + pulley.getSpeed());
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "and says so: " + motor.guiStatus());
            BeltBlockEntity controller = pulley.getControllerBE();
            TransportedItemStack carried = new TransportedItemStack(new ItemStack(Items.IRON_INGOT));
            carried.beltPosition = 1.5F;
            carried.prevBeltPosition = 1.5F;
            controller.getInventory().addItem(carried);
            at[0] = 1.5F;
        }).thenExecuteAfter(16, () -> {
            BeltBlockEntity controller = helper.getBlockEntity(first, BeltBlockEntity.class).getControllerBE();
            List<TransportedItemStack> carried = controller.getInventory().getTransportedItems();
            boolean moved = carried.isEmpty() || Math.abs(carried.get(0).beltPosition - at[0]) >= 0.5F;
            check(helper, moved, "the belt carries the item along: " + (carried.isEmpty() ? "off its end" : carried.get(0).beltPosition));
        }).thenSucceed();
    }

    /**
     * Scene 3: a Motor with a Large Cogwheel straight against its axle: it turns (only a plain shaft was ever taken), and
     * a Cogwheel meshed with it turns twice as fast the other way, by Create's own teeth.
     */
    private static void sceneLargeCog(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(1, 2, 2), Direction.EAST);
        helper.setBlock(new BlockPos(2, 2, 2), create("large_cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, X));
        helper.setBlock(new BlockPos(2, 3, 3), create("cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, X));
        helper.startSequence().thenExecuteAfter(30, () -> {
            KineticBlockEntity large = kinetic(helper, new BlockPos(2, 2, 2));
            check(helper, helper.getBlockState(new BlockPos(2, 2, 2)).getBlock() == create("large_cogwheel"), "Create's Large Cogwheel still");
            check(helper, CreateMotorTests.at30(large.getSpeed()), "it turns at 30 RPM: " + large.getSpeed());
            KineticBlockEntity small = kinetic(helper, new BlockPos(2, 3, 3));
            check(helper, small.getSpeed() == -2.0F * large.getSpeed(), "the cog meshed with it turns twice as fast, back: " + small.getSpeed());
        }).thenSucceed();
    }

    /**
     * Scene 5: a Motor, a Create Shaft, a Drive Wheel on the shaft's line, a Create Shaft beyond the wheel's other axle
     * end: the far shaft turns (a wheel line with no Motor on it only took), in one network of 480 su, the wheel with it.
     */
    private static void sceneWheel(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 2), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        CreateTests.wheel(helper, new BlockPos(3, 1, 2), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(4, 1, 2), X);
        helper.startSequence().thenExecuteAfter(40, () -> {
            KineticBlockEntity near = CreateMotorTests.driven(helper, new BlockPos(2, 1, 2));
            KineticBlockEntity far = CreateMotorTests.driven(helper, new BlockPos(4, 1, 2));
            check(helper, CreateMotorTests.at30(near.getSpeed()) && far.getSpeed() == near.getSpeed(), "the far shaft turns with the near one: "
                    + near.getSpeed() + ", " + far.getSpeed());
            check(helper, sameNetwork(near, far) && CreateMotorTests.capacity(far) == 480.0F, "one network, the Motor's 480 su");
            check(helper, CreateMotorTests.generating(near) && !CreateMotorTests.generating(far), "the Motor's end is its one source");
            check(helper, helper.getBlockState(new BlockPos(3, 1, 2)).getValue(DriveWheelBlock.TURNING), "the wheel turns with them");
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "the Motor says it turns Create: " + motor.guiStatus());
        }).thenSucceed();
    }

    // ---- Create's own tools on what we touch ----

    /** Andesite casing put on a shaft a Motor drives, by a player's click: an Encased Shaft of Create's, turning on. */
    private static void casing(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(1, 1, 2), Direction.EAST);
        BlockPos pos = new BlockPos(2, 1, 2);
        CreateTests.shaft(helper, pos, X);
        CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, CreateMotorTests.at30(kinetic(helper, pos).getSpeed()), "turning before");
            ItemStack casing = new ItemStack(create("andesite_casing"));
            Player player = holding(helper, casing);
            BlockPos at = helper.absolutePos(pos);
            helper.getBlockState(pos).useItemOn(casing, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
            check(helper, helper.getBlockState(pos).getBlock() == create("andesite_encased_shaft"), "Create cased it: " + helper.getBlockState(pos));
        }).thenExecuteAfter(10, () -> {
            KineticBlockEntity cased = kinetic(helper, pos);
            check(helper, CreateMotorTests.at30(cased.getSpeed()) && CreateMotorTests.generating(cased), "cased, it keeps turning: " + cased.getSpeed());
            check(helper, kinetic(helper, new BlockPos(3, 1, 2)).getSpeed() == cased.getSpeed(), "and the shaft beyond with it");
            check(helper, drops(helper) == 0, "nothing dropped");
        }).thenSucceed();
    }

    /**
     * Create's Wrench on a shaft a Motor drives: a click turns it off the Motor's axis - it stands, the Motor idles; a
     * second click turns it back - it turns again; crouching, the wrench picks it up: gone, into the player's hands, and
     * the Motor idles.
     */
    private static void wrench(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 2), Direction.EAST);
        BlockPos pos = new BlockPos(2, 1, 2);
        CreateTests.shaft(helper, pos, X);
        ItemStack wrench = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "wrench")));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, CreateMotorTests.at30(kinetic(helper, pos).getSpeed()), "turning before");
            wrench.useOn(click(helper, player, pos, Direction.UP));
            check(helper, helper.getBlockState(pos).getValue(BlockStateProperties.AXIS) == Direction.Axis.Z, "the wrench turned it: " + helper.getBlockState(pos));
        }).thenExecuteAfter(10, () -> {
            KineticBlockEntity shaft = CreateMotorTests.driven(helper, pos);
            check(helper, shaft.getTheoreticalSpeed() == 0.0F && !CreateMotorTests.generating(shaft), "off the axle it stands: " + shaft.getTheoreticalSpeed());
            check(helper, motor.guiStatus() == MachineStatus.IDLING, "the Motor idles: " + motor.guiStatus());
            wrench.useOn(click(helper, player, pos, Direction.UP));
        }).thenExecuteAfter(10, () -> {
            check(helper, CreateMotorTests.at30(CreateMotorTests.driven(helper, pos).getSpeed()), "turned back, it turns again");
            player.setShiftKeyDown(true);
            wrench.useOn(click(helper, player, pos, Direction.UP));
            player.setShiftKeyDown(false);
            check(helper, helper.getBlockState(pos).isAir(), "picked up: " + helper.getBlockState(pos));
            check(helper, player.getInventory().countItem(MotorShaftBlock.plainShaft().asItem()) == 1 || drops(helper) == 1,
                    "one Create Shaft, in the player's hands");
        }).thenExecuteAfter(10, () -> check(helper, motor.guiStatus() == MachineStatus.IDLING, "the Motor idles: " + motor.guiStatus())).thenSucceed();
    }

    /** A Cogwheel set on the end of a shaft a Motor drives, by a player's click on the shaft's end: it turns with the shaft. */
    private static void cogOnShaft(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(1, 1, 2), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        helper.startSequence().thenExecuteAfter(20, () -> {
            ItemStack cog = new ItemStack(create("cogwheel"));
            Player player = holding(helper, cog);
            cog.useOn(click(helper, player, new BlockPos(2, 1, 2), Direction.EAST));
            check(helper, helper.getBlockState(new BlockPos(3, 1, 2)).getBlock() == create("cogwheel"), "the cogwheel is set: "
                    + helper.getBlockState(new BlockPos(3, 1, 2)));
        }).thenExecuteAfter(10, () -> {
            KineticBlockEntity cog = kinetic(helper, new BlockPos(3, 1, 2));
            check(helper, helper.getBlockState(new BlockPos(3, 1, 2)).getValue(BlockStateProperties.AXIS) == X, "on the shaft's line");
            check(helper, CreateMotorTests.at30(cog.getSpeed()), "it turns with the shaft: " + cog.getSpeed());
        }).thenSucceed();
    }

    /** The shaft a Motor drives broken: the Motor idles, nothing left turning; set again: it turns again. */
    private static void shaftSetAgain(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 2), Direction.EAST);
        BlockPos pos = new BlockPos(2, 1, 2);
        CreateTests.shaft(helper, pos, X);
        CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, CreateMotorTests.at30(kinetic(helper, new BlockPos(3, 1, 2)).getSpeed()), "turning before");
            helper.getLevel().destroyBlock(helper.absolutePos(pos), false);
        }).thenExecuteAfter(10, () -> {
            check(helper, kinetic(helper, new BlockPos(3, 1, 2)).getTheoreticalSpeed() == 0.0F, "the shaft beyond stands");
            check(helper, motor.guiStatus() == MachineStatus.IDLING, "the Motor idles: " + motor.guiStatus());
            CreateTests.shaft(helper, pos, X);
        }).thenExecuteAfter(10, () -> {
            check(helper, CreateMotorTests.at30(kinetic(helper, pos).getSpeed()) && CreateMotorTests.at30(kinetic(helper, new BlockPos(3, 1, 2)).getSpeed()),
                    "set again, both turn");
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "the Motor turns Create again: " + motor.guiStatus());
        }).thenSucceed();
    }

    /** A Drive Wheel between two Create shafts broken: the far shaft stands, the near one turns on; set again: joined again. */
    private static void wheelSetAgain(GameTestHelper helper) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 2), Direction.EAST, 32);
        KineticBlockEntity near = CreateTests.shaft(helper, new BlockPos(1, 1, 2), X);
        BlockPos wheel = new BlockPos(2, 1, 2);
        CreateTests.wheel(helper, wheel, Direction.EAST);
        KineticBlockEntity far = CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, far.getSpeed() == 32.0F, "passed on before: " + far.getSpeed());
            helper.getLevel().destroyBlock(helper.absolutePos(wheel), false);
        }).thenExecuteAfter(10, () -> {
            check(helper, far.getTheoreticalSpeed() == 0.0F && near.getSpeed() == 32.0F, "the wheel gone, the far shaft stands and the near one turns on: "
                    + far.getTheoreticalSpeed());
            check(helper, !sameNetwork(near, far), "two networks again");
            CreateTests.wheel(helper, wheel, Direction.EAST);
        }).thenExecuteAfter(10, () -> {
            check(helper, far.getSpeed() == 32.0F && sameNetwork(near, far), "set again, joined again: " + far.getSpeed());
            check(helper, drops(helper) == 0, "nothing dropped");
        }).thenSucceed();
    }

    // ---- what must not happen ----

    /**
     * Two Create networks that turn against each other, a Drive Wheel set between them: not joined - both turn on as they
     * did, nothing is broken or dropped, the wheel stands and says so. One of them turned the other way: joined, one
     * network, the wheel turning. The control beside it: the same wheel between two that agree from the start.
     */
    private static void against(GameTestHelper helper) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 1), Direction.EAST, 32);
        KineticBlockEntity west = CreateTests.shaft(helper, new BlockPos(1, 1, 1), X);
        KineticBlockEntity east = CreateTests.shaft(helper, new BlockPos(3, 1, 1), X);
        // (a motor facing west turns its shaft the other way about the axis for the same figure)
        CreativeMotorBlockEntity other = CreateTests.creativeMotor(helper, new BlockPos(4, 1, 1), Direction.WEST, 32);
        // the control: two that agree
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 5), Direction.EAST, 32);
        KineticBlockEntity agreeWest = CreateTests.shaft(helper, new BlockPos(1, 1, 5), X);
        KineticBlockEntity agreeEast = CreateTests.shaft(helper, new BlockPos(3, 1, 5), X);
        CreateTests.creativeMotor(helper, new BlockPos(4, 1, 5), Direction.WEST, -32);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, west.getSpeed() == 32.0F && east.getSpeed() == -32.0F, "the two turn against each other: " + west.getSpeed() + ", " + east.getSpeed());
            check(helper, agreeWest.getSpeed() == 32.0F && agreeEast.getSpeed() == 32.0F, "the control's two alike");
            CreateTests.wheel(helper, new BlockPos(2, 1, 1), Direction.EAST);
            CreateTests.wheel(helper, new BlockPos(2, 1, 5), Direction.EAST);
        }).thenExecuteAfter(40, () -> {
            check(helper, west.getSpeed() == 32.0F && east.getSpeed() == -32.0F, "each turns on as it did: " + west.getSpeed() + ", " + east.getSpeed());
            check(helper, !sameNetwork(west, east), "not joined");
            DriveWheelBlockEntity wheel = helper.getBlockEntity(new BlockPos(2, 1, 1), DriveWheelBlockEntity.class);
            check(helper, !helper.getBlockState(new BlockPos(2, 1, 1)).getValue(DriveWheelBlock.TURNING), "the wheel stands");
            CreateDrives.WheelTrain train = CreateDrives.train(helper.getLevel(), wheel);
            check(helper, train.bridge() == ForeignDrives.Bridge.STALLED, "and its line knows why: " + train.bridge());
            check(helper, drops(helper) == 0, "nothing broken or dropped");
            for (int x = 0; x <= 4; x++) {
                check(helper, !helper.getBlockState(new BlockPos(x, 1, 1)).isAir(), "every block stands: " + x);
            }
            check(helper, sameNetwork(agreeWest, agreeEast) && agreeEast.getSpeed() == 32.0F, "the two that agree are one network");
            check(helper, helper.getBlockState(new BlockPos(2, 1, 5)).getValue(DriveWheelBlock.TURNING), "their wheel turning");
            other.generatedSpeed.setValue(-32); // the east motor turned to agree
        }).thenExecuteAfter(40, () -> {
            check(helper, west.getSpeed() == 32.0F && east.getSpeed() == 32.0F && sameNetwork(west, east), "they agree now: joined: "
                    + west.getSpeed() + ", " + east.getSpeed());
            check(helper, helper.getBlockState(new BlockPos(2, 1, 1)).getValue(DriveWheelBlock.TURNING), "the wheel turns with them");
            check(helper, drops(helper) == 0, "nothing dropped");
        }).thenSucceed();
    }

    /**
     * A Motor turns a line of Create shafts; a Creative Motor is set down against the line's far end, its dial the other
     * way and slower. Nothing pops and nothing drops: the motor set down stands, the line turns on at the faster
     * source's speed, and both capacities count. (Create itself lets a motor set down so ride along; one whose dial is
     * then turned against the line is the next test.)
     */
    private static void sourceSetAgainst(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(0, 1, 2), Direction.EAST);
        KineticBlockEntity first = CreateTests.shaft(helper, new BlockPos(1, 1, 2), X);
        CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, CreateMotorTests.at30(first.getSpeed()), "the Motor turns the line: " + first.getSpeed());
            // a motor facing west turns its shaft the other way about the axis for the same figure: set against the line's way
            CreateTests.creativeMotor(helper, new BlockPos(3, 1, 2), Direction.WEST, first.getSpeed() > 0.0F ? 16 : -16);
        }).thenExecuteAfter(30, () -> {
            check(helper, helper.getBlockState(new BlockPos(3, 1, 2)).getBlock() == create("creative_motor"), "the motor set down stands: "
                    + helper.getBlockState(new BlockPos(3, 1, 2)));
            check(helper, drops(helper) == 0, "nothing dropped");
            check(helper, Math.abs(first.getSpeed()) == 30.0F, "the line turns on at the faster source's speed: " + first.getSpeed());
            check(helper, CreateMotorTests.generating(first), "the Motor's end gives");
            check(helper, CreateMotorTests.capacity(first) == 480.0F + 16384.0F * 16.0F, "both capacities: " + CreateMotorTests.capacity(first));
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "the Motor turns Create: " + motor.guiStatus());
        }).thenSucceed();
    }

    /**
     * A Creative Motor standing still (its dial at 0) on a line a Nookworks Motor turns; its dial is then set to turn the
     * other way, slower. Create alone breaks a generator turning against the network that turns it; our end stands down
     * first, the motor starts as on a still line, and our end follows: nothing pops, both capacities.
     */
    private static void sourceStartedAgainst(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(0, 1, 2), Direction.EAST);
        KineticBlockEntity first = CreateTests.shaft(helper, new BlockPos(1, 1, 2), X);
        CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        CreativeMotorBlockEntity creative = CreateTests.creativeMotor(helper, new BlockPos(3, 1, 2), Direction.WEST, 0);
        float[] went = new float[1];
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, CreateMotorTests.at30(first.getSpeed()) && creative.getSpeed() == first.getSpeed(), "the Motor turns the line, the still motor with it: "
                    + first.getSpeed() + ", " + creative.getSpeed());
            went[0] = first.getSpeed();
            creative.generatedSpeed.setValue(first.getSpeed() > 0.0F ? 16 : -16); // facing west: against the line's way
        }).thenExecuteAfter(30, () -> {
            check(helper, helper.getBlockState(new BlockPos(3, 1, 2)).getBlock() == create("creative_motor"), "the motor stands: "
                    + helper.getBlockState(new BlockPos(3, 1, 2)));
            check(helper, drops(helper) == 0, "nothing dropped");
            CreateEnds.State state = CreateEnds.state(first);
            check(helper, first.getSpeed() == -went[0], "the line turns the motor's way now, at the faster source's speed: " + first.getSpeed()
                    + " motor " + creative.getSpeed() + " generating " + creative.getGeneratedSpeed() + " its source " + creative.source
                    + " shaft's source " + first.source + " given " + (state == null ? null : state.given() + " " + state.generating()));
            check(helper, CreateMotorTests.capacity(first) == 480.0F + 16384.0F * 16.0F, "both capacities: " + CreateMotorTests.capacity(first));
        }).thenSucceed();
    }

    /**
     * A Motor's line of shafts and a Creative Motor's, turning against each other, one shaft apart; the shaft between is
     * set down. Create alone would break it; the Motor's end stands down first: the shaft stands, one network.
     */
    private static void shaftBetween(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(0, 1, 2), Direction.EAST);
        KineticBlockEntity ours = CreateTests.shaft(helper, new BlockPos(1, 1, 2), X);
        KineticBlockEntity theirs = CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        CreativeMotorBlockEntity creative = CreateTests.creativeMotor(helper, new BlockPos(4, 1, 2), Direction.WEST, 16);
        helper.startSequence().thenExecuteAfter(20, () -> {
            if (Math.signum(ours.getSpeed()) == Math.signum(theirs.getSpeed())) {
                creative.generatedSpeed.setValue(-creative.generatedSpeed.getValue());
            }
        }).thenExecuteAfter(10, () -> {
            check(helper, ours.getSpeed() != 0.0F && Math.signum(ours.getSpeed()) != Math.signum(theirs.getSpeed()), "the two lines turn against each other: "
                    + ours.getSpeed() + ", " + theirs.getSpeed());
            CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        }).thenExecuteAfter(30, () -> {
            check(helper, helper.getBlockState(new BlockPos(2, 1, 2)).getBlock() == create("shaft"), "the shaft set down stands: "
                    + helper.getBlockState(new BlockPos(2, 1, 2)));
            check(helper, drops(helper) == 0, "nothing dropped");
            check(helper, sameNetwork(ours, theirs) && Math.abs(ours.getSpeed()) == 30.0F
                    && Math.signum(ours.getSpeed()) == Math.signum(creative.getGeneratedSpeed()), "one network, turning the Creative Motor's way: " + ours.getSpeed());
            check(helper, CreateMotorTests.capacity(ours) == 480.0F + 16384.0F * 16.0F, "both capacities: " + CreateMotorTests.capacity(ours));
        }).thenSucceed();
    }

    /**
     * A Creative Motor's line, a Drive Wheel passing its turning on to a far line that ends in a second Creative Motor
     * standing still; the second motor's dial is then set to turn the far line the other way. Create alone breaks a
     * generator turning against the network that turns it - and here only the wheel's join turns it. The join is cut
     * first: the second motor stands and turns its own line its own way, the wheel stands between the two, saying so,
     * nothing drops. Its dial set to agree: joined again.
     */
    private static void sourceBeyondWheel(GameTestHelper helper) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 2), Direction.EAST, 32);
        KineticBlockEntity near = CreateTests.shaft(helper, new BlockPos(1, 1, 2), X);
        DriveWheelBlockEntity wheel = CreateTests.wheel(helper, new BlockPos(2, 1, 2), Direction.EAST);
        KineticBlockEntity far = CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        CreativeMotorBlockEntity second = CreateTests.creativeMotor(helper, new BlockPos(4, 1, 2), Direction.WEST, 0);
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, far.getSpeed() == 32.0F && second.getSpeed() == 32.0F && sameNetwork(near, second), "passed on, the still motor riding along: "
                    + far.getSpeed());
            second.generatedSpeed.setValue(32); // facing west: its shaft the other way about the axis, against the line
        }).thenExecuteAfter(30, () -> {
            check(helper, helper.getBlockState(new BlockPos(4, 1, 2)).getBlock() == create("creative_motor"), "the motor stands: "
                    + helper.getBlockState(new BlockPos(4, 1, 2)));
            check(helper, drops(helper) == 0, "nothing dropped");
            check(helper, near.getSpeed() == 32.0F && far.getSpeed() == -32.0F && !sameNetwork(near, far), "each side turns its own way, not joined: "
                    + near.getSpeed() + ", " + far.getSpeed());
            check(helper, !helper.getBlockState(new BlockPos(2, 1, 2)).getValue(DriveWheelBlock.TURNING), "the wheel stands");
            check(helper, CreateDrives.train(helper.getLevel(), wheel).bridge() == ForeignDrives.Bridge.STALLED, "and its line knows why");
            second.generatedSpeed.setValue(-32);
        }).thenExecuteAfter(40, () -> {
            check(helper, far.getSpeed() == 32.0F && sameNetwork(near, far), "they agree now: joined again: " + far.getSpeed());
            check(helper, helper.getBlockState(new BlockPos(2, 1, 2)).getValue(DriveWheelBlock.TURNING), "the wheel turns with them");
            check(helper, drops(helper) == 0, "nothing dropped");
        }).thenSucceed();
    }

    /**
     * A Creative Motor's line passed on by a Drive Wheel to a far shaft; a second Creative Motor's line turning the other
     * way ends one block short of that shaft; the shaft between is set down. Create alone would break it (two networks
     * turning it two ways - one of them only through the wheel's join). The join is cut first: the shaft stands and
     * turns with the second motor, the wheel stands between the two.
     */
    private static void shaftBeyondWheel(GameTestHelper helper) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 2), Direction.EAST, 32);
        KineticBlockEntity near = CreateTests.shaft(helper, new BlockPos(1, 1, 2), X);
        CreateTests.wheel(helper, new BlockPos(2, 1, 2), Direction.EAST);
        KineticBlockEntity far = CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        KineticBlockEntity theirs = CreateTests.shaft(helper, new BlockPos(5, 1, 2), X);
        CreateTests.creativeMotor(helper, new BlockPos(6, 1, 2), Direction.WEST, 32);
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, far.getSpeed() == 32.0F && theirs.getSpeed() == -32.0F, "the wheel's far shaft and the other line turn against each other: "
                    + far.getSpeed() + ", " + theirs.getSpeed());
            CreateTests.shaft(helper, new BlockPos(4, 1, 2), X);
        }).thenExecuteAfter(30, () -> {
            check(helper, helper.getBlockState(new BlockPos(4, 1, 2)).getBlock() == create("shaft"), "the shaft set down stands: "
                    + helper.getBlockState(new BlockPos(4, 1, 2)));
            check(helper, drops(helper) == 0, "nothing dropped");
            check(helper, far.getSpeed() == -32.0F && sameNetwork(far, theirs), "the far shaft turns with the line it was joined to: " + far.getSpeed());
            check(helper, near.getSpeed() == 32.0F && !sameNetwork(near, far), "the near side turns on, apart");
            check(helper, !helper.getBlockState(new BlockPos(2, 1, 2)).getValue(DriveWheelBlock.TURNING), "the wheel stands between them");
        }).thenSucceed();
    }

    /**
     * A powered Motor; a Creative Motor's line turning the other way ends one block short of its axle; the shaft between
     * is set down. The Motor ticks before the new shaft has been joined by Create to the line: started then, the shaft
     * would be turned against the line and broken. The Motor's end waits for the shaft's own tick, then follows the line.
     */
    private static void shaftAtAxle(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(0, 1, 2), Direction.EAST);
        KineticBlockEntity theirs = CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        // facing west, a motor turns its shaft the other way about the axis for the same figure: -32 here, against a Motor's +30
        CreateTests.creativeMotor(helper, new BlockPos(3, 1, 2), Direction.WEST, 32);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, theirs.getSpeed() == -32.0F, "the line turns: " + theirs.getSpeed());
            CreateTests.shaft(helper, new BlockPos(1, 1, 2), X);
        }).thenExecuteAfter(30, () -> {
            check(helper, helper.getBlockState(new BlockPos(1, 1, 2)).getBlock() == create("shaft"), "the shaft set down stands: "
                    + helper.getBlockState(new BlockPos(1, 1, 2)));
            check(helper, drops(helper) == 0, "nothing dropped");
            KineticBlockEntity set = kinetic(helper, new BlockPos(1, 1, 2));
            check(helper, set.getSpeed() == -32.0F && sameNetwork(set, theirs), "it turns with the line: " + set.getSpeed());
            check(helper, CreateMotorTests.generating(set) && CreateMotorTests.capacity(set) == 480.0F + 16384.0F * 32.0F,
                    "the Motor's end gives beside the line's source: " + CreateMotorTests.capacity(set));
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "the Motor turns Create: " + motor.guiStatus());
        }).thenSucceed();
    }

    /**
     * A Creative Motor and a shaft, a Drive Wheel, a shaft and a Creative Motor turning against the first, all set down
     * in one tick: the wheel sees one shaft turn before the other has been joined by Create to its motor. Joined then,
     * that shaft would be turned against its own motor and broken. The wheel waits for each shaft's own tick: nothing
     * breaks, the two are not joined, the wheel stands.
     */
    private static void setAtOnce(GameTestHelper helper) {
        // the wheel first, so that it ticks before the shafts
        CreateTests.wheel(helper, new BlockPos(3, 1, 2), Direction.EAST);
        CreateTests.creativeMotor(helper, new BlockPos(1, 1, 2), Direction.EAST, 32);
        KineticBlockEntity west = CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        KineticBlockEntity east = CreateTests.shaft(helper, new BlockPos(4, 1, 2), X);
        CreateTests.creativeMotor(helper, new BlockPos(5, 1, 2), Direction.WEST, 32);
        helper.startSequence().thenExecuteAfter(40, () -> {
            for (int x = 1; x <= 5; x++) {
                check(helper, !helper.getBlockState(new BlockPos(x, 1, 2)).isAir(), "every block stands: " + x);
            }
            check(helper, drops(helper) == 0, "nothing dropped");
            check(helper, !west.isRemoved() && !east.isRemoved() && west.getSpeed() == 32.0F && east.getSpeed() == -32.0F,
                    "each turns its own way: " + west.getSpeed() + ", " + east.getSpeed());
            check(helper, !sameNetwork(west, east) && !helper.getBlockState(new BlockPos(3, 1, 2)).getValue(DriveWheelBlock.TURNING),
                    "not joined, the wheel stands");
        }).thenSucceed();
    }

    /**
     * A minute of a Motor turning a shaft, a wheel passing it on, a shaft, a Millstone at work: the far shaft's speed
     * never comes and goes, no block's flicker tally (Create's own count of speed changes) rises, nothing drops.
     */
    private static void noFlicker(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 3), dev.nookworks.registry.ModBlocks.CREATIVE_GENERATOR.get()); // power for the whole minute
        CreateTests.motor(helper, new BlockPos(1, 1, 2), Direction.EAST);
        KineticBlockEntity near = CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        CreateTests.wheel(helper, new BlockPos(3, 1, 2), Direction.EAST);
        KineticBlockEntity far = CreateTests.shaft(helper, new BlockPos(4, 1, 2), X);
        helper.setBlock(new BlockPos(5, 1, 2), create("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        helper.setBlock(new BlockPos(5, 2, 2), create("millstone"));
        com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity millstone = helper.getBlockEntity(new BlockPos(5, 2, 2),
                com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity.class);
        millstone.inputInv.setStackInSlot(0, new ItemStack(Items.WHEAT, 64));
        int[] changes = new int[1];
        float[] last = new float[1];
        boolean[] watching = new boolean[1];
        java.util.function.Consumer<net.neoforged.neoforge.event.tick.LevelTickEvent.Post> each = event -> {
            if (event.getLevel() == helper.getLevel() && watching[0]) {
                if (far.getSpeed() != last[0]) {
                    changes[0]++;
                    last[0] = far.getSpeed();
                }
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.LevelTickEvent.Post.class, each);
        AfterTest.undo(helper, () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(each));
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, CreateMotorTests.at30(far.getSpeed()), "turning: " + far.getSpeed());
            last[0] = far.getSpeed();
            watching[0] = true;
        }).thenExecuteAfter(1200, () -> {
            check(helper, changes[0] == 0, "the far shaft's speed never changed in a minute: " + changes[0] + " changes");
            check(helper, near.getFlickerScore() == 0 && far.getFlickerScore() == 0, "no flicker counted: " + near.getFlickerScore() + ", " + far.getFlickerScore());
            check(helper, millstone.inputInv.getStackInSlot(0).getCount() < 64, "the Millstone ground on");
            check(helper, drops(helper) == 0, "nothing dropped");
        }).thenSucceed();
    }

    /**
     * A driven shaft's data set down again with no Motor beside it (as a chunk loaded whose Motor's chunk is not): it comes
     * back turning, a source - and with no word from a Motor it is stopped within a few ticks: nobody pays, nothing turns.
     */
    private static void loadedWithoutMotor(GameTestHelper helper) {
        BlockPos motorPos = new BlockPos(1, 1, 2);
        BlockPos pos = new BlockPos(2, 1, 2);
        CreateTests.motor(helper, motorPos, Direction.EAST);
        CreateTests.shaft(helper, pos, X);
        CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        var registries = helper.getLevel().registryAccess();
        helper.startSequence().thenExecuteAfter(20, () -> {
            KineticBlockEntity shaft = CreateMotorTests.driven(helper, pos);
            check(helper, CreateMotorTests.at30(shaft.getSpeed()), "turning before");
            CompoundTag tag = shaft.saveWithFullMetadata(registries);
            BlockState state = helper.getBlockState(pos);
            helper.setBlock(motorPos, Blocks.AIR);
            KineticBlockEntity again = CreateMotorTests.reload(helper, pos, state, tag);
            check(helper, CreateMotorTests.generating(again) && CreateMotorTests.at30(again.getTheoreticalSpeed()), "loaded, a source as it was saved");
        }).thenExecuteAfter(8, () -> {
            KineticBlockEntity shaft = CreateMotorTests.driven(helper, pos);
            check(helper, shaft.getTheoreticalSpeed() == 0.0F && !CreateMotorTests.generating(shaft) && !shaft.hasSource(),
                    "no Motor says so: stopped, " + shaft.getTheoreticalSpeed());
            check(helper, kinetic(helper, new BlockPos(3, 1, 2)).getTheoreticalSpeed() == 0.0F, "and what it turned");
        }).thenSucceed();
    }

    /**
     * A Motor, a shaft, a wheel, a shaft, an Oil Press weighing far more than the Motor gives: overstressed on both sides
     * of the wheel at once (one network), the press still, the Motor saying so. The press made light again: both sides
     * turn, the press works.
     */
    private static void overstressBothSides(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(0, 1, 2), Direction.EAST);
        KineticBlockEntity near = CreateTests.shaft(helper, new BlockPos(1, 1, 2), X);
        CreateTests.wheel(helper, new BlockPos(2, 1, 2), Direction.EAST);
        KineticBlockEntity far = CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(4, 1, 2));
        CreateDrives.impactForTest(press, 100.0F); // 3000 su at 30 RPM
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(50, () -> {
            check(helper, near.isOverStressed() && far.isOverStressed(), "overstressed on both sides: " + near.isOverStressed() + ", " + far.isOverStressed());
            check(helper, near.getSpeed() == 0.0F && far.getSpeed() == 0.0F, "both stand");
            check(helper, motor.guiStatus() == MachineStatus.OVERSTRESSED, "the Motor says why: " + motor.guiStatus());
            at[0] = CreateTests.turns(press);
        }).thenExecuteAfter(30, () -> {
            check(helper, CreateTests.turns(press) == at[0], "the press stands still");
            CreateDrives.impactForTest(press, 2.0F);
        }).thenExecuteAfter(60, () -> {
            check(helper, !near.isOverStressed() && !far.isOverStressed() && CreateMotorTests.at30(far.getSpeed()), "light again, both turn: " + far.getSpeed());
            check(helper, CreateTests.turns(press) > at[0], "and the press works");
        }).thenSucceed();
    }

    /**
     * Motor - shaft - wheel - shaft - Oil Press beside Motor - Oil Press: the press beyond the wheel costs the Motor what
     * turning a press itself costs, 20 FE/t for its 60 su, and never less.
     */
    private static void pressBeyondWheel(GameTestHelper helper) {
        MotorBlockEntity via = CreateTests.motor(helper, new BlockPos(0, 1, 1), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(1, 1, 1), X);
        CreateTests.wheel(helper, new BlockPos(2, 1, 1), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(3, 1, 1), X);
        OilPressBlockEntity far = CreateTests.press(helper, new BlockPos(4, 1, 1));
        MotorBlockEntity direct = CreateTests.motor(helper, new BlockPos(2, 1, 5), Direction.EAST);
        OilPressBlockEntity near = CreateTests.press(helper, new BlockPos(3, 1, 5));
        int[] start = new int[2];
        helper.startSequence().thenExecuteAfter(40, () -> {
            via.energy().set(10_000);
            direct.energy().set(10_000);
            start[0] = CreateTests.turns(far);
            start[1] = CreateTests.turns(near);
        }).thenExecuteAfter(100, () -> {
            int a = CreateTests.turns(far) - start[0];
            int b = CreateTests.turns(near) - start[1];
            check(helper, a >= 2 && Math.abs(a - b) <= 1, "as fast beyond the wheel as straight: " + a + " and " + b);
            double paidVia = 10_000 - via.energyStored();
            double paidDirect = 10_000 - direct.energyStored();
            check(helper, Math.abs(paidVia - paidDirect) <= CreateMotorTests.fe(20) * 2 + 1, "the same FE: " + paidVia + " beyond the wheel, " + paidDirect + " straight");
            check(helper, paidVia >= paidDirect - 1, "never less");
        }).thenSucceed();
    }

    /**
     * A fast redstone clock on the Motor (on and off every two ticks) for fifteen seconds: Create breaks a block whose
     * speed comes and goes too often, so the Motor's end waits its tally out - the shaft is never broken.
     */
    private static void redstoneClock(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(1, 1, 2), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        int[] tick = new int[1];
        boolean[] run = {true};
        java.util.function.Consumer<net.neoforged.neoforge.event.tick.LevelTickEvent.Pre> each = event -> {
            if (event.getLevel() == helper.getLevel() && run[0]) {
                helper.setBlock(new BlockPos(1, 2, 2), tick[0]++ / 2 % 2 == 0 ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre.class, each);
        AfterTest.undo(helper, () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(each));
        helper.startSequence().thenExecuteAfter(300, () -> {
            run[0] = false;
            helper.setBlock(new BlockPos(1, 2, 2), Blocks.AIR);
            check(helper, helper.getBlockState(new BlockPos(2, 1, 2)).getBlock() == create("shaft") && helper.getBlockState(new BlockPos(3, 1, 2)).getBlock() == create("shaft"),
                    "the shafts stand through the clock");
            check(helper, drops(helper) == 0, "nothing dropped");
        }).thenExecuteAfter(80, () -> check(helper, CreateMotorTests.at30(kinetic(helper, new BlockPos(3, 1, 2)).getSpeed()),
                "the clock gone, the line turns again: " + kinetic(helper, new BlockPos(3, 1, 2)).getSpeed())).thenSucceed();
    }

    // ---- edges ----

    /**
     * A Create Gearbox at the far end of a belted wheel line, its source three blocks off to one side: Create would take
     * its turning to enter at the nearest side to that place (north), wrongly; it enters at the face the wheel meets it
     * at (west), and its other shafts turn by Create's own rule from there: straight through turned back, the side
     * shaft on the west's side of its axis turned back, the other on. And for how it is drawn: what a client is sent,
     * and that the client's two hooks find their places in Create's classes ({@link #clientHooks}).
     */
    private static void gearboxAcrossBelt(GameTestHelper helper) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 0), Direction.EAST, 32);
        CreateTests.shaft(helper, new BlockPos(1, 1, 0), X);
        DriveWheelBlockEntity a = CreateTests.wheel(helper, new BlockPos(2, 1, 0), Direction.EAST);
        DriveWheelBlockEntity b = CreateTests.wheel(helper, new BlockPos(2, 1, 4), Direction.EAST);
        CreateTests.belt(helper, a, b);
        BlockPos box = new BlockPos(3, 1, 4);
        helper.setBlock(box, create("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        KineticBlockEntity east = CreateTests.shaft(helper, new BlockPos(4, 1, 4), X);
        KineticBlockEntity north = CreateTests.shaft(helper, new BlockPos(3, 1, 3), Direction.Axis.Z);
        KineticBlockEntity south = CreateTests.shaft(helper, new BlockPos(3, 1, 5), Direction.Axis.Z);
        helper.startSequence().thenExecuteAfter(40, () -> {
            KineticBlockEntity gearbox = kinetic(helper, box);
            check(helper, gearbox.getSpeed() == 32.0F, "the Gearbox turns across the belt: " + gearbox.getSpeed());
            check(helper, gearbox instanceof com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity halves
                    && halves.getSourceFacing() == Direction.WEST, "its turning enters at the face the wheel meets it at");
            check(helper, east.getSpeed() == -32.0F, "straight through, turned back: " + east.getSpeed());
            check(helper, north.getSpeed() == -32.0F && south.getSpeed() == 32.0F, "the side shafts by Create's rule from the west: "
                    + north.getSpeed() + ", " + south.getSpeed());
            // what a client is sent: the face, for the Gearbox's look
            CompoundTag sent = gearbox.getUpdateTag(helper.getLevel().registryAccess());
            check(helper, sent.contains("nookworks_entry") && sent.getInt("nookworks_entry") == Direction.WEST.get3DDataValue(), "a client is told the face: " + sent);
            KineticBlockEntity copy = (KineticBlockEntity) gearbox.getType().create(gearbox.getBlockPos(), gearbox.getBlockState());
            copy.readClient(sent, helper.getLevel().registryAccess());
            check(helper, ((com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity) copy).getSourceFacing() == Direction.WEST,
                    "and its copy draws from it");
            clientHooks(helper);
        }).thenSucceed();
    }

    /** Two belted wheels, a Create shaft on one, a Create shaft on the other: the turning crosses the rope belt; the belt cut, it stops. */
    private static void acrossBelt(GameTestHelper helper) {
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 0), Direction.EAST, 32);
        KineticBlockEntity near = CreateTests.shaft(helper, new BlockPos(1, 1, 0), X);
        DriveWheelBlockEntity a = CreateTests.wheel(helper, new BlockPos(2, 1, 0), Direction.EAST);
        DriveWheelBlockEntity b = CreateTests.wheel(helper, new BlockPos(2, 1, 4), Direction.EAST);
        CreateTests.belt(helper, a, b);
        KineticBlockEntity far = CreateTests.shaft(helper, new BlockPos(3, 1, 4), X);
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, far.getSpeed() == 32.0F && sameNetwork(near, far), "across the belt, one network: " + far.getSpeed());
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE); // a block in the belt's way snaps it (the wheel looks once a second)
        }).thenExecuteAfter(60, () -> {
            check(helper, a.belts().isEmpty(), "the belt snapped");
            check(helper, far.getTheoreticalSpeed() == 0.0F && near.getSpeed() == 32.0F, "the belt cut, the far shaft stands: " + far.getTheoreticalSpeed());
        }).thenSucceed();
    }

    /**
     * A Motor's axle straight against a Creative Motor's shaft: Create's own generator, whose speed is its own. Running,
     * it has the Motor's 480 su added and keeps its speed; standing still, nothing can be given through it and the Motor
     * idles.
     */
    private static void generatorAtAxle(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
        CreativeMotorBlockEntity running = CreateTests.creativeMotor(helper, new BlockPos(2, 1, 1), Direction.WEST, 16);
        MotorBlockEntity idle = CreateTests.motor(helper, new BlockPos(1, 1, 5), Direction.EAST);
        CreativeMotorBlockEntity still = CreateTests.creativeMotor(helper, new BlockPos(2, 1, 5), Direction.WEST, 0);
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, Math.abs(running.getSpeed()) == 16.0F, "its speed is its own: " + running.getSpeed());
            check(helper, CreateMotorTests.capacity(running) == 16384.0F * 16.0F + 480.0F, "the Motor's 480 su added: " + CreateMotorTests.capacity(running));
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "the Motor says it turns Create: " + motor.guiStatus());
            check(helper, still.getSpeed() == 0.0F && !CreateMotorTests.generating(still), "a generator standing still is given nothing");
            check(helper, idle.guiStatus() == MachineStatus.IDLING, "and its Motor idles: " + idle.guiStatus());
        }).thenSucceed();
    }

    /**
     * A world's old {@code nookworks:motor_shaft} (the first rounds' swapped shaft), one dry on a Motor's axle, one
     * waterlogged and alone: each is Create's plain shaft again at its first tick, axis and water kept, and the one on
     * the axle is driven where it stands.
     */
    private static void migration(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.SOUTH);
        BlockPos onAxle = new BlockPos(1, 1, 2);
        BlockPos alone = new BlockPos(4, 1, 4);
        BlockState old = CreateCompat.MOTOR_SHAFT.get().defaultBlockState();
        helper.setBlock(onAxle, old.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        helper.setBlock(alone, old.setValue(BlockStateProperties.AXIS, Direction.Axis.Y).setValue(BlockStateProperties.WATERLOGGED, true));
        helper.startSequence().thenExecuteAfter(20, () -> {
            BlockState first = helper.getBlockState(onAxle);
            check(helper, first.getBlock() == MotorShaftBlock.plainShaft() && first.getValue(BlockStateProperties.AXIS) == Direction.Axis.Z,
                    "Create's plain shaft again, its axis kept: " + first);
            check(helper, CreateMotorTests.at30(kinetic(helper, onAxle).getSpeed()), "and the Motor drives it where it stands");
            BlockState second = helper.getBlockState(alone);
            check(helper, second.getBlock() == MotorShaftBlock.plainShaft() && second.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y
                    && second.getValue(BlockStateProperties.WATERLOGGED), "the other too, its water kept: " + second);
            check(helper, drops(helper) == 0, "nothing dropped");
        }).thenSucceed();
    }

    /**
     * Engineer's Goggles on a shaft a Motor drives, by what a client's copy of it is sent (the lines themselves are drawn
     * on a client: seen in the game): that it is driven, with 480 su, and what the Motor draws; a plain shaft beyond is
     * sent none of it.
     */
    /**
     * The client's two hooks (how a Gearbox is drawn when turned through a train) are never loaded by the test server,
     * and a player's client leaves a hook that finds no place out without a word. So each one's place is looked up here
     * in Create's class, read as bytes: the method the hook names, and in it the call it names. (They named
     * Direction.getNearest(III) at first, where Create calls (FFF): neither ever applied, and no run said so.) Part of
     * the Gearbox's test above, not a test of its own.
     */
    private static void clientHooks(GameTestHelper helper) {
        String[][] hooks = {
                {"dev/nookworks/mixin/compat/create/GearboxVisualMixin", "com/simibubi/create/content/kinetics/gearbox/GearboxVisual"},
                {"dev/nookworks/mixin/compat/create/GearboxRendererMixin", "com/simibubi/create/content/kinetics/gearbox/GearboxRenderer"}};
        for (String[] hook : hooks) {
            org.objectweb.asm.tree.ClassNode mixin = classBytes(helper, hook[0]);
            org.objectweb.asm.tree.ClassNode target = classBytes(helper, hook[1]);
            int injectors = 0;
            for (org.objectweb.asm.tree.MethodNode callback : mixin.methods) {
                List<org.objectweb.asm.tree.AnnotationNode> annotations = new java.util.ArrayList<>();
                if (callback.visibleAnnotations != null) {
                    annotations.addAll(callback.visibleAnnotations);
                }
                if (callback.invisibleAnnotations != null) {
                    annotations.addAll(callback.invisibleAnnotations);
                }
                for (org.objectweb.asm.tree.AnnotationNode annotation : annotations) {
                    Object methods = annotationValue(annotation, "method");
                    Object ats = annotationValue(annotation, "at");
                    if (!(methods instanceof List<?> names) || ats == null) {
                        continue;
                    }
                    injectors++;
                    List<?> places = ats instanceof List<?> list ? list : List.of(ats);
                    for (Object name : names) {
                        org.objectweb.asm.tree.MethodNode method = null;
                        for (org.objectweb.asm.tree.MethodNode candidate : target.methods) {
                            if ((candidate.name + candidate.desc).equals(name)) {
                                method = candidate;
                            }
                        }
                        check(helper, method != null, hook[1] + " has the method " + name);
                        for (Object place : places) {
                            Object call = annotationValue((org.objectweb.asm.tree.AnnotationNode) place, "target");
                            int found = 0;
                            for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions) {
                                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode invoke
                                        && ("L" + invoke.owner + ";" + invoke.name + invoke.desc).equals(call)) {
                                    found++;
                                }
                            }
                            check(helper, found == 1, hook[1] + "." + name + " calls " + call + " once: " + found);
                        }
                    }
                }
            }
            check(helper, injectors == 1, hook[0] + " has its one hook: " + injectors);
        }
    }

    private static org.objectweb.asm.tree.ClassNode classBytes(GameTestHelper helper, String name) {
        try (java.io.InputStream in = CreateEndsTests.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(helper, in != null, "the class " + name + " is there to read");
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(in).accept(node, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            return node;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object annotationValue(org.objectweb.asm.tree.AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (key.equals(annotation.values.get(i))) {
                    return annotation.values.get(i + 1);
                }
            }
        }
        return null;
    }

    private static void goggles(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(1, 1, 2), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 2), X);
        CreateTests.shaft(helper, new BlockPos(3, 1, 2), X);
        CreateTests.press(helper, new BlockPos(4, 1, 2));
        helper.startSequence().thenExecuteAfter(40, () -> {
            var registries = helper.getLevel().registryAccess();
            KineticBlockEntity driven = CreateMotorTests.driven(helper, new BlockPos(2, 1, 2));
            KineticBlockEntity copy = (KineticBlockEntity) driven.getType().create(driven.getBlockPos(), driven.getBlockState());
            copy.readClient(driven.getUpdateTag(registries), registries);
            CreateEnds.State state = CreateEnds.state(copy);
            check(helper, state != null && state.generating() && CreateEnds.givenSu(state) == 480.0F, "a client knows it is driven with 480 su");
            check(helper, Math.abs(state.drawnSu() - 60.0F) < 0.6F && Math.abs(state.motorFe() - CreateMotorTests.fe(20)) < 0.1F,
                    "and what the Motor draws for the press's 60 su: " + state.drawnSu() + " su, " + state.motorFe() + " FE/t");
            KineticBlockEntity plain = CreateMotorTests.driven(helper, new BlockPos(3, 1, 2));
            KineticBlockEntity plainCopy = (KineticBlockEntity) plain.getType().create(plain.getBlockPos(), plain.getBlockState());
            plainCopy.readClient(plain.getUpdateTag(registries), registries);
            CreateEnds.State none = CreateEnds.state(plainCopy);
            check(helper, none == null || !none.generating(), "a shaft beyond is no source: its goggles show none of it");
            Object asGoggles = driven;
            check(helper, asGoggles instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation, "Create's shaft answers the goggles");
        }).thenSucceed();
    }
}
