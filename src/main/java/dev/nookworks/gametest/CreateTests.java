package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.nookworks.block.MotorBlock;
import dev.nookworks.compat.create.CreateCompat;
import dev.nookworks.compat.create.CreateDrives;
import dev.nookworks.compat.create.CreateGoggles;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.Drive;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Create's rotation straight into Nookworks' machines (the 1.21.1 port's one new feature; tools/port1211/design_create.md),
 * registered only when Create is loaded: a Creative Motor and Create's shafts are the Create side, set against our
 * machines and Drive Wheels with nothing in between.
 */
final class CreateTests {
    private CreateTests() {}

    static void register() {
        NookworksGameTests.test("create_shaft_turns_a_press_set_against_it", 100, CreateTests::shaftTurnsAPress);
        NookworksGameTests.test("create_shaft_turns_faster_at_more_rpm", 200, CreateTests::fasterAtMoreRpm);
        NookworksGameTests.test("create_shaft_on_a_wheel_counts_each_machine_on_its_belts", 100, CreateTests::countsMachinesOnBelts);
        NookworksGameTests.test("create_shaft_works_turned_the_other_way", 100, CreateTests::reversed);
        NookworksGameTests.test("create_shaft_turns_a_quern_from_above", 100, CreateTests::quernFromAbove);
        NookworksGameTests.test("create_shaft_leaves_a_motors_machine_to_the_motor", 120, CreateTests::motorKeepsItsMachine);
        NookworksGameTests.test("create_overstress_stops_the_machines", 100, CreateTests::overstress);
        NookworksGameTests.test("create_gearbox_turns_a_machine_on_each_side", 100, CreateTests::gearboxTurnsTwo);
        NookworksGameTests.test("create_shaft_taken_away_lets_the_machine_go", 120, CreateTests::shaftTakenAway);
        NookworksGameTests.test("create_machine_taken_away_takes_its_stress", 120, CreateTests::machineTakenAway);
        NookworksGameTests.test("create_goggles_read_a_machine_and_its_shaft", 100, CreateTests::goggles);
        NookworksGameTests.test("create_stress_figures_are_registered", CreateTests::stressFigures);
        NookworksGameTests.test("create_chute_carries_from_our_crate_into_the_crusher", 200, CreateTests::chuteCarries);
        NookworksGameTests.test("create_shaft_join_models_are_whole", CreateTests::joinModels);
        CreateMotorTests.register(); // a Motor driving Create's blocks where they stand: the figures, the price, claims, saving
        CreateEndsTests.register(); // every Create part against every end of ours, the owner's scenes, what must not happen
        CreateRelayTests.register(); // what an add-on's trains ask of ForeignDrives
    }

    // ---- helpers ----

    /** A block of Create's by its id (Create's own block entries are Registrate's, which these tests do not compile against). */
    static net.minecraft.world.level.block.Block createBlock(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", id));
    }

    /** A Creative Motor at {@code pos} pointing {@code facing}, turning at {@code rpm} (its own scroll value). */
    static CreativeMotorBlockEntity creativeMotor(GameTestHelper helper, BlockPos pos, Direction facing, int rpm) {
        helper.setBlock(pos, createBlock("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, facing));
        CreativeMotorBlockEntity motor = helper.getBlockEntity(pos, CreativeMotorBlockEntity.class);
        motor.generatedSpeed.setValue(rpm);
        return motor;
    }

    static KineticBlockEntity shaft(GameTestHelper helper, BlockPos pos, Direction.Axis axis) {
        helper.setBlock(pos, createBlock("shaft").defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, axis));
        return helper.getBlockEntity(pos, KineticBlockEntity.class);
    }

    /** Sets a block as a player's placing would: its look worked out from its neighbours (a machine's drive side). */
    static void place(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.Block block) {
        helper.setBlock(pos, block);
        BlockPos at = helper.absolutePos(pos);
        var level = helper.getLevel();
        level.setBlock(at, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(at), level, at),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
    }

    static OilPressBlockEntity press(GameTestHelper helper, BlockPos pos) {
        place(helper, pos, ModOil.OIL_PRESS.get());
        OilPressBlockEntity press = helper.getBlockEntity(pos, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get(), 64));
        return press;
    }

    /** Turns a press has had: those toward its seed now, and two for each seed's oil in its tray. */
    static int turns(OilPressBlockEntity press) {
        return press.turns() + press.stored() / 60 * 2;
    }

    private static RicePolisherBlockEntity polisher(GameTestHelper helper, BlockPos pos) {
        place(helper, pos, ModCrops.RICE_POLISHER.get());
        RicePolisherBlockEntity polisher = helper.getBlockEntity(pos, RicePolisherBlockEntity.class);
        polisher.setItem(RicePolisherBlockEntity.INPUT, new ItemStack(ModCrops.BROWN_RICE.get(), 64));
        return polisher;
    }

    /** Turns a polisher has had: three a grain gone, and those toward the next. */
    private static int turns(RicePolisherBlockEntity polisher) {
        return (64 - polisher.getItem(RicePolisherBlockEntity.INPUT).getCount()) * RicePolisherBlockEntity.TURNS + polisher.turns();
    }

    static MotorBlockEntity motor(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, facing));
        MotorBlockEntity motor = helper.getBlockEntity(pos, MotorBlockEntity.class);
        motor.energy().set((int) motor.energy().getCapacityAsLong());
        return motor;
    }

    static DriveWheelBlockEntity wheel(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, facing));
        return helper.getBlockEntity(pos, DriveWheelBlockEntity.class);
    }

    static void belt(GameTestHelper helper, DriveWheelBlockEntity a, DriveWheelBlockEntity b) {
        check(helper, Belts.tie(helper.getLevel(), a, b, BeltKind.ROPE, null, new ItemStack(ModItems.ROPE.get())) == null, "the wheels are belted");
    }

    /** What the shaft's network carries, in su (each member's figure times its speed). */
    static float networkStress(KineticBlockEntity kinetic) {
        return kinetic.getOrCreateNetwork().calculateStress();
    }

    // ---- tests ----

    /**
     * Creative Motor - shaft - Oil Press, nothing between: the press shows its axle on the shaft's side, turns, puts 2 su
     * per RPM on the shaft (64 su at 32 RPM, counted by Create's own network through the hook), and stands still once
     * the shaft does.
     */
    private static void shaftTurnsAPress(GameTestHelper helper) {
        OilPressBlockEntity press = press(helper, new BlockPos(3, 1, 3));
        CreativeMotorBlockEntity source = creativeMotor(helper, new BlockPos(1, 1, 3), Direction.EAST, 32);
        KineticBlockEntity shaft = shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, Math.abs(shaft.getSpeed()) == 32, "the shaft turns: " + shaft.getSpeed());
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).getValue(Drive.PROPERTY) == Drive.WEST,
                    "the press shows its axle on the shaft's side");
            check(helper, turns(press) >= 2, "the press has been turned: " + turns(press));
            check(helper, CreateDrives.stressHooked(), "Create's stress figure takes our machines in (the hook went in)");
            check(helper, shaft.calculateStressApplied() == 2.0F, "the press weighs 2 su/RPM on the shaft: " + shaft.calculateStressApplied());
            check(helper, networkStress(shaft) == 2.0F * 32, "the network carries 64 su: " + networkStress(shaft));
            source.generatedSpeed.setValue(0);
        }).thenExecuteAfter(10, () -> at[0] = turns(press)).thenExecuteAfter(30, () -> {
            check(helper, shaft.getSpeed() == 0.0F && turns(press) == at[0], "stopped with the shaft: " + at[0] + " then " + turns(press));
        }).thenSucceed();
    }

    /** Twice the RPM, twice the turns: 30 RPM gives two a second (the Motor's pace), 60 four. */
    private static void fasterAtMoreRpm(GameTestHelper helper) {
        creativeMotor(helper, new BlockPos(1, 1, 1), Direction.EAST, 30);
        shaft(helper, new BlockPos(2, 1, 1), Direction.Axis.X);
        RicePolisherBlockEntity slow = polisher(helper, new BlockPos(3, 1, 1));
        creativeMotor(helper, new BlockPos(1, 1, 5), Direction.EAST, 60);
        shaft(helper, new BlockPos(2, 1, 5), Direction.Axis.X);
        RicePolisherBlockEntity fast = polisher(helper, new BlockPos(3, 1, 5));
        int[] start = new int[2];
        helper.startSequence().thenExecuteAfter(20, () -> {
            start[0] = turns(slow);
            start[1] = turns(fast);
        }).thenExecuteAfter(100, () -> {
            int a = turns(slow) - start[0];
            int b = turns(fast) - start[1];
            float ticks = MachineTuning.time(MotorBlockEntity.TICKS_PER_TURN) / (float) MotorBlockEntity.TICKS_PER_TURN;
            check(helper, Math.abs(a - 10 / ticks) <= 1, "30 RPM: ten turns in five seconds, as a Motor gives: " + a);
            check(helper, Math.abs(b - 20 / ticks) <= 1, "60 RPM: twenty: " + b);
        }).thenSucceed();
    }

    /**
     * A shaft on a Drive Wheel's axle, the wheel belted to another, presses on both wheels' axle lines: three machines,
     * 6 su/RPM on the shaft, all turned, and the far wheel going round.
     */
    private static void countsMachinesOnBelts(GameTestHelper helper) {
        creativeMotor(helper, new BlockPos(0, 1, 1), Direction.EAST, 32);
        KineticBlockEntity shaft = shaft(helper, new BlockPos(1, 1, 1), Direction.Axis.X);
        DriveWheelBlockEntity near = wheel(helper, new BlockPos(2, 1, 1), Direction.EAST);
        DriveWheelBlockEntity far = wheel(helper, new BlockPos(2, 1, 5), Direction.EAST);
        belt(helper, near, far);
        List<OilPressBlockEntity> presses = new ArrayList<>(List.of(press(helper, new BlockPos(3, 1, 1)),
                press(helper, new BlockPos(3, 1, 5)), press(helper, new BlockPos(1, 1, 5))));
        helper.startSequence().thenExecuteAfter(50, () -> {
            check(helper, shaft.calculateStressApplied() == 6.0F, "three machines at 2 su/RPM: " + shaft.calculateStressApplied());
            check(helper, networkStress(shaft) == 6.0F * 32, "the network carries 192 su: " + networkStress(shaft));
            for (OilPressBlockEntity press : presses) {
                check(helper, turns(press) >= 2, "every press on the belt line turned: " + turns(press) + " at " + press.getBlockPos());
            }
            check(helper, helper.getBlockState(new BlockPos(2, 1, 5)).getValue(DriveWheelBlock.TURNING), "the far wheel goes round");
        }).thenSucceed();
    }

    /** A shaft turning the other way: the press works all the same. */
    private static void reversed(GameTestHelper helper) {
        creativeMotor(helper, new BlockPos(1, 1, 3), Direction.EAST, -32);
        KineticBlockEntity shaft = shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        OilPressBlockEntity press = press(helper, new BlockPos(3, 1, 3));
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, shaft.getSpeed() < 0, "the shaft turns backwards: " + shaft.getSpeed());
            check(helper, turns(press) >= 2, "the press works turned backwards: " + turns(press));
        }).thenSucceed();
    }

    /** An upright shaft onto a Hand Quern turns it by its spindle, as a Motor above does. */
    private static void quernFromAbove(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), ModKitchen.HAND_QUERN.get());
        var quern = helper.getBlockEntity(new BlockPos(3, 1, 3), dev.nookworks.cooking.HandMachineBlockEntity.class);
        quern.setItem(dev.nookworks.cooking.HandMachineBlockEntity.INPUT, new ItemStack(net.minecraft.world.item.Items.WHEAT, 16));
        shaft(helper, new BlockPos(3, 2, 3), Direction.Axis.Y);
        creativeMotor(helper, new BlockPos(3, 3, 3), Direction.DOWN, 32);
        helper.startSequence().thenExecuteAfter(50, () -> {
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).getValue(Drive.PROPERTY) == Drive.UP, "the quern shows its coupling above");
            check(helper, quern.turns() > 0 || quern.getItem(dev.nookworks.cooking.HandMachineBlockEntity.INPUT).getCount() < 16,
                    "the quern is turned from above");
        }).thenSucceed();
    }

    /**
     * A press between a running Motor and a turning shaft: one drive a machine - the Motor's side shows, the Motor keeps
     * it, and the shaft carries no stress for it.
     */
    private static void motorKeepsItsMachine(GameTestHelper helper) {
        press(helper, new BlockPos(3, 1, 3));
        MotorBlockEntity motor = motor(helper, new BlockPos(3, 1, 2), Direction.SOUTH);
        KineticBlockEntity shaft = shaft(helper, new BlockPos(3, 1, 4), Direction.Axis.Z);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, motor.guiStatus() == MachineStatus.RUNNING, "the Motor turns the press");
            creativeMotor(helper, new BlockPos(3, 1, 5), Direction.NORTH, 32);
        }).thenExecuteAfter(45, () -> {
            check(helper, shaft.getSpeed() != 0.0F, "Create's shaft turns");
            check(helper, motor.guiStatus() == MachineStatus.RUNNING, "the Motor keeps the press: " + motor.guiStatus());
            check(helper, shaft.calculateStressApplied() == 0.0F, "no stress on the shaft for the Motor's press: " + shaft.calculateStressApplied());
        }).thenSucceed();
    }

    /** A press weighing more than the Creative Motor can carry: the network is overstressed, as Create's are, and the press stands still. */
    private static void overstress(GameTestHelper helper) {
        creativeMotor(helper, new BlockPos(1, 1, 3), Direction.EAST, 32);
        KineticBlockEntity shaft = shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        OilPressBlockEntity press = press(helper, new BlockPos(3, 1, 3));
        CreateDrives.impactForTest(press, 20_000.0F); // past the Creative Motor's 16384 su/RPM
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, shaft.isOverStressed() && shaft.getSpeed() == 0.0F, "overstressed, the shaft still: " + shaft.getSpeed());
            at[0] = turns(press);
        }).thenExecuteAfter(30, () -> {
            check(helper, turns(press) == at[0], "the press stands still while it is overstressed");
        }).thenSucceed();
    }

    /** A gearbox between two presses, a Creative Motor on a third side: both presses turn, 4 su/RPM on the gearbox. */
    private static void gearboxTurnsTwo(GameTestHelper helper) {
        OilPressBlockEntity west = press(helper, new BlockPos(2, 1, 3));
        OilPressBlockEntity east = press(helper, new BlockPos(4, 1, 3));
        helper.setBlock(new BlockPos(3, 1, 3), createBlock("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        KineticBlockEntity gearbox = helper.getBlockEntity(new BlockPos(3, 1, 3), KineticBlockEntity.class);
        creativeMotor(helper, new BlockPos(3, 1, 2), Direction.SOUTH, 32);
        helper.startSequence().thenExecuteAfter(50, () -> {
            check(helper, turns(west) >= 2 && turns(east) >= 2, "both presses turn: " + turns(west) + ", " + turns(east));
            check(helper, gearbox.calculateStressApplied() == 4.0F, "two machines on the gearbox: " + gearbox.calculateStressApplied());
        }).thenSucceed();
    }

    /** The shaft broken: the press shows no drive, stops, and the shaft's load is forgotten. */
    private static void shaftTakenAway(GameTestHelper helper) {
        creativeMotor(helper, new BlockPos(1, 1, 3), Direction.EAST, 32);
        KineticBlockEntity shaft = shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        OilPressBlockEntity press = press(helper, new BlockPos(3, 1, 3));
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, CreateDrives.tracks(shaft), "the shaft's load is kept while it turns the press");
            helper.setBlock(new BlockPos(2, 1, 3), Blocks.AIR);
        }).thenExecuteAfter(5, () -> {
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).getValue(Drive.PROPERTY) == Drive.NONE, "no drive shown");
            check(helper, !CreateDrives.tracks(shaft), "the shaft gone, its load is let go");
            at[0] = turns(press);
        }).thenExecuteAfter(30, () -> check(helper, turns(press) == at[0], "the press stands still")).thenSucceed();
    }

    /** The press broken: the shaft's stress goes back to its own (nothing), and the network's with it. */
    private static void machineTakenAway(GameTestHelper helper) {
        creativeMotor(helper, new BlockPos(1, 1, 3), Direction.EAST, 32);
        KineticBlockEntity shaft = shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        press(helper, new BlockPos(3, 1, 3));
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, networkStress(shaft) == 64.0F, "64 su with the press: " + networkStress(shaft));
            helper.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);
        }).thenExecuteAfter(5, () -> {
            check(helper, shaft.calculateStressApplied() == 0.0F && networkStress(shaft) == 0.0F,
                    "nothing on the shaft without the press: " + networkStress(shaft));
            check(helper, !CreateDrives.tracks(shaft), "and its load is let go");
        }).thenSucceed();
    }

    /** The goggles' lines on a press a shaft turns, and nothing on one no shaft turns. */
    private static void goggles(GameTestHelper helper) {
        creativeMotor(helper, new BlockPos(1, 1, 3), Direction.EAST, 32);
        shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        OilPressBlockEntity press = press(helper, new BlockPos(3, 1, 3));
        OilPressBlockEntity idle = press(helper, new BlockPos(3, 1, 5));
        helper.startSequence().thenExecuteAfter(30, () -> {
            CreateGoggles.Facts facts = CreateGoggles.facts(press);
            check(helper, facts != null && Math.abs(facts.rpm()) == 32.0F && facts.impactPerRpm() == 2.0F,
                    "the goggles read 32 RPM and 2 su/RPM (64 su) off the press: " + facts);
            Object asGoggles = press;
            check(helper, asGoggles instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
                    "our block entity is one Create's goggles read (the mixin went in)");
            check(helper, CreateGoggles.facts(idle) == null, "a press no shaft turns has nothing to say");
        }).thenSucceed();
    }

    /**
     * The flange coupling where a Create shaft meets our machines (client ShaftJoins, models/compat/create): one model
     * for every part, each element with a texture that is there - ours or vanilla's (named, never shipped).
     */
    private static void joinModels(GameTestHelper helper) {
        int found = 0;
        for (String look : java.util.List.of("join")) {
            for (String part : java.util.List.of("oil_press", "rice_polisher", "noodle_maker", "hand_quern_up", "hand_quern_down", "drive_wheel", "motor")) {
                String path = "assets/nookworks/models/compat/create/" + look + "_" + part + ".json";
                var in = CreateTests.class.getResourceAsStream("/" + path);
                check(helper, in != null, path + " is there");
                com.google.gson.JsonObject model;
                try (var reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                    model = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                var textures = model.getAsJsonObject("textures");
                check(helper, model.getAsJsonArray("elements").size() > 0, path + " draws something");
                for (var texture : textures.entrySet()) {
                    var id = net.minecraft.resources.ResourceLocation.parse(texture.getValue().getAsString());
                    String png = "assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png";
                    check(helper, !AssetAudit.vanillaAssetsVisible() && id.getNamespace().equals("minecraft") || AssetAudit.has(png),
                            path + " names " + png + ", which is not there");
                }
                for (var element : model.getAsJsonArray("elements")) {
                    for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                        String ref = face.getValue().getAsJsonObject().get("texture").getAsString().substring(1);
                        check(helper, textures.has(ref), path + " has a face drawn with #" + ref + ", which it does not name");
                    }
                }
                found++;
            }
        }
        check(helper, found == 7, "seven join models: " + found);
        helper.succeed();
    }

    /**
     * Create's table holds our figures: 2 su/RPM each machine, which Create's own tooltip calls Low (under its
     * mediumStressImpact, 4), and a Hand Crank's capacity turns four of them at its speed.
     */
    private static void stressFigures(GameTestHelper helper) {
        double medium = com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.mediumStressImpact.get();
        for (var block : CreateCompat.machineBlocks()) {
            double impact = BlockStressValues.getImpact(block);
            check(helper, impact == 2.0, block + " weighs 2 su/RPM: " + impact);
            check(helper, impact < medium, block + " is Low in Create's tooltip (under " + medium + ")");
        }
        // Create keeps a source's capacity per RPM of its own speed (a Hand Crank: 8, at 32 RPM, 256 su), so what it turns
        // at that speed is its capacity over our impact
        var crank = createBlock("hand_crank");
        var rpm = BlockStressValues.RPM.get(crank);
        double perRpm = BlockStressValues.getCapacity(crank);
        check(helper, rpm != null, "Create gives the Hand Crank a speed");
        int turned = (int) Math.floor(perRpm * rpm.value() / (2.0 * rpm.value()));
        check(helper, turned == 4, "a Hand Crank (" + perRpm * rpm.value() + " su at " + rpm.value() + " RPM) turns four machines: " + turned);
        // a Water Wheel turns a whole Drive Wheel line (eight machines, DriveNetwork's most) at its own speed
        var wheel = createBlock("water_wheel");
        var wheelRpm = BlockStressValues.RPM.get(wheel);
        check(helper, wheelRpm != null && BlockStressValues.getCapacity(wheel) / 2.0 >= 8,
                "a Water Wheel turns a line of eight: " + BlockStressValues.getCapacity(wheel) + " su/RPM at " + wheelRpm);
        helper.succeed();
    }

    /**
     * Create's Chute between a Crate of ours above and a Crusher below: it draws the Crate's cobblestone out and feeds
     * the Crusher with it - Create's logistics through the port's bridge from NeoForge 21.1's item handlers to ours.
     */
    private static void chuteCarries(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), dev.nookworks.registry.ModBlocks.CRUSHER.get());
        helper.setBlock(new BlockPos(3, 2, 3), createBlock("chute"));
        helper.setBlock(new BlockPos(3, 3, 3), dev.nookworks.registry.ModBlocks.CRATE.get());
        var crate = helper.getBlockEntity(new BlockPos(3, 3, 3), dev.nookworks.block.entity.CrateBlockEntity.class);
        crate.setItem(0, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 16));
        var crusher = helper.getBlockEntity(new BlockPos(3, 1, 3), dev.nookworks.machine.CrusherBlockEntity.class);
        helper.succeedWhen(() -> {
            int inCrusher = 0;
            for (int i = 0; i < crusher.getContainerSize(); i++) {
                if (crusher.getItem(i).is(net.minecraft.world.item.Items.COBBLESTONE)) {
                    inCrusher += crusher.getItem(i).getCount();
                }
            }
            check(helper, inCrusher > 0 && crate.getItem(0).getCount() < 16,
                    "the chute took from the crate (" + crate.getItem(0).getCount() + " left) into the crusher (" + inCrusher + ")");
        });
    }
}
