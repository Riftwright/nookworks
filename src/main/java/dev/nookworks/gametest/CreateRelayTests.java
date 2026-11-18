package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.nookworks.machine.ForeignDrives;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * What an add-on's trains ask of ForeignDrives (Relay, Hook#taken, Hook#bridge / #join; the add-on Nook Technology's
 * own tests try them with its lines). Here a test's own relay: chains along an axis, at the places a test names only,
 * pass the turning on as if absent. Registered only with Create (from CreateTests).
 */
final class CreateRelayTests {
    private CreateRelayTests() {}

    static void register() {
        NookworksGameTests.test("create_relay_carries_a_shaft_into_a_press", 100, CreateRelayTests::carries);
        NookworksGameTests.test("create_relay_motor_speed_reaches_its_shaft", 100, CreateRelayTests::motorSpeed);
        NookworksGameTests.test("create_relay_join_makes_two_networks_one", 200, CreateRelayTests::bridge);
        NookworksGameTests.test("create_relay_part_at_a_wheels_axle_weighs_on_its_shaft", 100, CreateRelayTests::wheelEnd);
        NookworksGameTests.test("create_relay_motor_with_a_fixed_way_stands_by_against_a_network", 200, CreateRelayTests::fixedWay);
        NookworksGameTests.test("create_relay_join_never_breaks_a_shaft_closing_a_loop", 160, CreateRelayTests::joined);
    }

    /** A test's relay: the chains at {@code places}, end to end on their axis, as a line; {@code motorRpm} for one Motor. */
    private static final class Chains implements ForeignDrives.Relay {
        final Set<BlockPos> places = new HashSet<>();
        @Nullable BlockPos motor;
        float motorRpm = Float.NaN;
        /** What the chains make of the turning (an add-on's Reversing Gear: -1). */
        int sign = 1;
        /** Where a part of the relay's weighs on the drive turning it (an add-on's belt run on a wheel's drum), su/RPM. */
        final java.util.Map<BlockPos, Float> loads = new java.util.HashMap<>();

        @Override
        public float load(net.minecraft.world.level.Level level, BlockPos at, Direction side, BlockPos drive) {
            return this.loads.getOrDefault(at, 0.0F);
        }

        @Override
        public ForeignDrives.@Nullable Far through(BlockGetter level, BlockPos at, Direction from, boolean taking) {
            if (!this.chain(level, at, from)) {
                return null;
            }
            Direction out = from.getOpposite();
            BlockPos last = at;
            while (this.chain(level, last.relative(out), from)) {
                last = last.relative(out);
            }
            return new ForeignDrives.Far(last.relative(out), out, this.sign);
        }

        private boolean chain(BlockGetter level, BlockPos at, Direction along) {
            BlockState state = level.getBlockState(at);
            return this.places.contains(at) && state.is(Blocks.CHAIN) && state.getValue(ChainBlock.AXIS) == along.getAxis();
        }

        @Override
        public float motorRpm(MotorBlockEntity motor) {
            if (motor.getBlockPos().equals(this.motor)) {
                return this.motorRpm;
            }
            // one whose way is fixed: +30 about its axis, said here so the test reads the same beside an add-on that gives
            // Motors a way of its own (this relay is asked first)
            return this.fixed.contains(motor.getBlockPos()) ? 30.0F : Float.NaN;
        }

        /** Motors whose way an add-on fixes (its Forward and Reverse): +30 RPM about the axis. */
        final Set<BlockPos> fixed = new HashSet<>();

        @Override
        public boolean motorFixed(MotorBlockEntity motor) {
            return this.fixed.contains(motor.getBlockPos());
        }
    }

    private static Chains relay(GameTestHelper helper) {
        Chains chains = new Chains();
        ForeignDrives.addRelayFirst(chains); // before an add-on's own, where one is installed too
        AfterTest.undo(helper, () -> ForeignDrives.removeRelay(chains));
        return chains;
    }

    private static void chains(GameTestHelper helper, Chains relay, Direction.Axis axis, BlockPos... at) {
        for (BlockPos pos : at) {
            helper.setBlock(pos, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, axis));
            if (relay != null) {
                relay.places.add(helper.absolutePos(pos));
            }
        }
    }

    /** An Oil Press whose look names its drive on its west (as the add-on's DriveMixin makes it for a line ending there). */
    private static OilPressBlockEntity pressDrivenFromWest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, dev.nookworks.registry.ModOil.OIL_PRESS.get().defaultBlockState()
                .setValue(dev.nookworks.machine.Drive.PROPERTY, dev.nookworks.machine.Drive.WEST));
        OilPressBlockEntity press = helper.getBlockEntity(pos, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.INPUT, new net.minecraft.world.item.ItemStack(dev.nookworks.registry.ModOil.SUNFLOWER_SEEDS.get(), 64));
        return press;
    }

    /**
     * Creative Motor, shaft, two relaying chains, Oil Press: the press turns as if the shaft stood against it and weighs
     * its 2 su/RPM on the shaft. The same with chains no relay names: the press stands, the shaft carries nothing.
     */
    private static void carries(GameTestHelper helper) {
        Chains relay = relay(helper);
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 1), Direction.EAST, 32);
        KineticBlockEntity shaft = CreateTests.shaft(helper, new BlockPos(1, 1, 1), Direction.Axis.X);
        chains(helper, relay, Direction.Axis.X, new BlockPos(2, 1, 1), new BlockPos(3, 1, 1));
        OilPressBlockEntity press = pressDrivenFromWest(helper, new BlockPos(4, 1, 1));
        // the control: the same, but the chains are no relay's
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 5), Direction.EAST, 32);
        KineticBlockEntity plain = CreateTests.shaft(helper, new BlockPos(1, 1, 5), Direction.Axis.X);
        chains(helper, null, Direction.Axis.X, new BlockPos(2, 1, 5), new BlockPos(3, 1, 5));
        OilPressBlockEntity still = pressDrivenFromWest(helper, new BlockPos(4, 1, 5));
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, CreateTests.turns(press) >= 2, "through the relay, the shaft turns the press: " + CreateTests.turns(press));
            check(helper, shaft.calculateStressApplied() == 2.0F, "and carries its 2 su/RPM: " + shaft.calculateStressApplied());
            check(helper, CreateTests.turns(still) == 0, "chains no relay names pass nothing: " + CreateTests.turns(still));
            check(helper, plain.calculateStressApplied() == 0.0F, "nor weigh: " + plain.calculateStressApplied());
        }).thenSucceed();
    }

    /** A relay's word on a Motor's speed (an add-on's speed gear): the shaft it drives turns at 60 RPM with 16 su/RPM, 960 su. */
    private static void motorSpeed(GameTestHelper helper) {
        Chains relay = relay(helper);
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 3), Direction.EAST);
        relay.motor = motor.getBlockPos();
        relay.motorRpm = 60.0F;
        CreateTests.shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        MotorBlockEntity bare = CreateTests.motor(helper, new BlockPos(1, 1, 5), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 5), Direction.Axis.X);
        helper.startSequence().thenExecuteAfter(30, () -> {
            KineticBlockEntity fast = CreateMotorTests.driven(helper, new BlockPos(2, 1, 3));
            check(helper, fast.getSpeed() == 60.0F, "the relay's speed: " + fast.getSpeed());
            check(helper, fast.getOrCreateNetwork().calculateCapacity() == 960.0F, "16 su/RPM at 60 RPM: " + fast.getOrCreateNetwork().calculateCapacity());
            KineticBlockEntity slow = CreateMotorTests.driven(helper, new BlockPos(2, 1, 5));
            check(helper, Math.abs(slow.getSpeed()) == 30.0F, "the bare Motor's 30 RPM: " + slow.getSpeed() + " " + bare.getBlockPos());
        }).thenSucceed();
    }

    /**
     * A Motor whose way is fixed (an add-on's Forward or Reverse; here +30 RPM about the axis). Front row: a Create line
     * already turns the other way when the Motor is set against it - its end gives nothing and stands by, the Motor
     * saying so, the line turning on, nothing broken. Back row: the Motor turns the line first, then a Creative Motor on
     * it is started the other way - Create alone breaks such a motor; our end stands down, the motor stands and turns the
     * line its way, and the Motor stands by. The motor stopped again: the Motor's end turns the line once more.
     */
    private static void fixedWay(GameTestHelper helper) {
        Chains relay = relay(helper);
        // front: the line turns at -16 about the axis (a motor facing west gives its shaft the other way for the same figure)
        CreateTests.creativeMotor(helper, new BlockPos(3, 1, 1), Direction.WEST, 16);
        KineticBlockEntity line = CreateTests.shaft(helper, new BlockPos(2, 1, 1), Direction.Axis.X);
        // back: a motor standing still on the Motor's line
        com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity later = CreateTests.creativeMotor(helper, new BlockPos(3, 1, 5), Direction.WEST, 0);
        KineticBlockEntity own = CreateTests.shaft(helper, new BlockPos(2, 1, 5), Direction.Axis.X);
        MotorBlockEntity[] motors = new MotorBlockEntity[2];
        motors[1] = CreateTests.motor(helper, new BlockPos(1, 1, 5), Direction.EAST);
        relay.fixed.add(motors[1].getBlockPos());
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, line.getSpeed() == -16.0F, "the front line turns: " + line.getSpeed());
            motors[0] = CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
            relay.fixed.add(motors[0].getBlockPos());
            check(helper, own.getSpeed() == 30.0F && later.getSpeed() == 30.0F, "the back Motor turns its line, the still motor with it: " + own.getSpeed());
            later.generatedSpeed.setValue(16); // against the line, slower
        }).thenExecuteAfter(40, () -> {
            check(helper, line.getSpeed() == -16.0F && !CreateMotorTests.generating(line), "front: the line turns on its own way, nothing given: " + line.getSpeed());
            check(helper, CreateMotorTests.capacity(line) == 16384.0F * 16.0F, "its capacity the motor's alone: " + CreateMotorTests.capacity(line));
            check(helper, motors[0].guiStatus() == dev.nookworks.machine.MachineStatus.CREATE_AGAINST, "the Motor says it stands by: " + motors[0].guiStatus());
            check(helper, motors[0].standsAgainst(), "and tells an add-on so (it may turn the Motor round)");
            check(helper, helper.getBlockState(new BlockPos(3, 1, 5)).getBlock() == CreateTests.createBlock("creative_motor"), "back: the motor started against the line stands: "
                    + helper.getBlockState(new BlockPos(3, 1, 5)));
            check(helper, own.getSpeed() == -16.0F && !CreateMotorTests.generating(own), "and turns the line its way, our end standing by: " + own.getSpeed());
            check(helper, motors[1].guiStatus() == dev.nookworks.machine.MachineStatus.CREATE_AGAINST, "the Motor says so: " + motors[1].guiStatus());
            for (int z : new int[] {1, 5}) {
                for (int x = 1; x <= 3; x++) {
                    check(helper, !helper.getBlockState(new BlockPos(x, 1, z)).isAir(), "nothing broken: " + x + ", " + z);
                }
            }
            later.generatedSpeed.setValue(0);
        }).thenExecuteAfter(40, () -> {
            check(helper, own.getSpeed() == 30.0F && CreateMotorTests.generating(own), "the motor stopped, the Motor's end turns the line again: " + own.getSpeed());
            check(helper, !motors[1].standsAgainst() && motors[0].standsAgainst(), "giving, it stands against nothing; the front one still does");
        }).thenSucceed();
    }

    /**
     * A part of a relay at a Drive Wheel's far axle end (an add-on's belt run with its drum there) weighs what the relay
     * says on the Create shaft turning the wheel, beside the wheel line's machines (none here): 3 su/RPM. The same with
     * nothing the relay prices at the wheel (the control row): nothing.
     */
    private static void wheelEnd(GameTestHelper helper) {
        Chains relay = relay(helper);
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 1), Direction.EAST, 32);
        KineticBlockEntity shaft = CreateTests.shaft(helper, new BlockPos(1, 1, 1), Direction.Axis.X);
        helper.setBlock(new BlockPos(2, 1, 1), dev.nookworks.registry.ModOil.DRIVE_WHEEL.get().defaultBlockState()
                .setValue(dev.nookworks.drive.DriveWheelBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.BARREL);
        relay.loads.put(helper.absolutePos(new BlockPos(3, 1, 1)), 3.0F);
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 4), Direction.EAST, 32);
        KineticBlockEntity plain = CreateTests.shaft(helper, new BlockPos(1, 1, 4), Direction.Axis.X);
        helper.setBlock(new BlockPos(2, 1, 4), dev.nookworks.registry.ModOil.DRIVE_WHEEL.get().defaultBlockState()
                .setValue(dev.nookworks.drive.DriveWheelBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.BARREL);
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, shaft.calculateStressApplied() == 3.0F, "the part at the wheel's axle weighs 3 su/RPM: " + shaft.calculateStressApplied());
            check(helper, CreateTests.networkStress(shaft) == 96.0F, "96 su at 32 RPM: " + CreateTests.networkStress(shaft));
            check(helper, plain.calculateStressApplied() == 0.0F, "nothing priced there, nothing: " + plain.calculateStressApplied());
        }).thenSucceed();
    }

    /**
     * A train joined from a Creative Motor's Gearbox A to a plain shaft B, turning B against what Create's own shafts
     * would make of it (the chains reversing): B's network is led round by Create's shafts back toward A's Gearbox, one
     * shaft short; once the train has joined them, that shaft is set, closing the loop. The train's join is cut before
     * Create rules (CreateEnds#beforeJoin), so the shaft set down is not broken by Create's rule for a loop turning two
     * ways, nor anything else; nothing is dropped, and the train then finds its two ends turned against each other by
     * Create's own shafts: it stands.
     */
    private static void joined(GameTestHelper helper) {
        Chains relay = relay(helper);
        relay.sign = -1;
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 1), Direction.EAST, 32);
        BlockState gearbox = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "gearbox")).defaultBlockState()
                .setValue(com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.AXIS, Direction.Axis.Y);
        helper.setBlock(new BlockPos(1, 1, 1), gearbox);
        chains(helper, relay, Direction.Axis.X, new BlockPos(2, 1, 1), new BlockPos(3, 1, 1));
        CreateTests.shaft(helper, new BlockPos(4, 1, 1), Direction.Axis.X);
        helper.setBlock(new BlockPos(5, 1, 1), gearbox);
        CreateTests.shaft(helper, new BlockPos(5, 1, 2), Direction.Axis.Z);
        CreateTests.shaft(helper, new BlockPos(5, 1, 3), Direction.Axis.Z);
        helper.setBlock(new BlockPos(5, 1, 4), gearbox);
        for (int x = 2; x <= 4; x++) {
            CreateTests.shaft(helper, new BlockPos(x, 1, 4), Direction.Axis.X);
        }
        helper.setBlock(new BlockPos(1, 1, 4), gearbox);
        CreateTests.shaft(helper, new BlockPos(1, 1, 3), Direction.Axis.Z);
        BlockPos gap = new BlockPos(1, 1, 2);
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos bAt = helper.absolutePos(new BlockPos(4, 1, 1));
        ForeignDrives.Bridge[] said = {ForeignDrives.Bridge.NONE};
        float[] went = new float[1];
        java.util.function.Consumer<LevelTickEvent.Pre> each = event -> {
            if (event.getLevel() == helper.getLevel()) {
                said[0] = ForeignDrives.bridge(helper.getLevel(), at, Direction.WEST, bAt, Direction.EAST, -1);
            }
        };
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Pre.class, each);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(each));
        helper.startSequence().thenExecuteAfter(40, () -> {
            KineticBlockEntity before = CreateMotorTests.driven(helper, new BlockPos(4, 1, 1));
            check(helper, before.getSpeed() != 0.0F && said[0] == ForeignDrives.Bridge.A_TO_B, "B turns through the train before the loop closes: "
                    + before.getSpeed() + " " + said[0]);
            went[0] = before.getSpeed();
            CreateTests.shaft(helper, gap, Direction.Axis.Z);
        }).thenExecuteAfter(40, () -> {
            check(helper, com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.isShaft(helper.getBlockState(gap)),
                    "the shaft set down stands: " + helper.getBlockState(gap));
            KineticBlockEntity after = CreateMotorTests.driven(helper, new BlockPos(4, 1, 1));
            check(helper, after.getSpeed() == -went[0], "B turns as Create's own shafts turn it now, the other way: " + after.getSpeed() + " was " + went[0]);
            for (int x = 1; x <= 5; x++) {
                for (int z = 1; z <= 4; z++) {
                    boolean ring = x == 1 || x == 5 || z == 1 || z == 4;
                    boolean chain = z == 1 && (x == 2 || x == 3);
                    check(helper, !ring || chain || helper.getBlockEntity(new BlockPos(x, 1, z), KineticBlockEntity.class).getSpeed() != 0.0F,
                            "every block of the ring stands and turns: " + x + ", " + z);
                }
            }
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(7, 4, 7);
            check(helper, helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).isEmpty(), "nothing dropped");
            check(helper, said[0] == ForeignDrives.Bridge.STALLED, "its two ends turned against each other: it stands: " + said[0]);
        }).thenSucceed();
    }

    /**
     * Create at both ends of a train: a Creative Motor's shaft A, chains, a still plain shaft B with an Oil Press beyond
     * it. Joined each tick: B stays Create's plain shaft and turns at A's 32 RPM, the press turns, and the two sides are
     * one Create network (one id, the press's 64 su counted once, the motor's capacity on both sides). No longer asked
     * for: the join is cut, B stands while A turns on. Asked for again: joined again. The source stopped: both still.
     */
    private static void bridge(GameTestHelper helper) {
        relay(helper);
        CreativeMotorBlockEntity source = CreateTests.creativeMotor(helper, new BlockPos(0, 1, 2), Direction.EAST, 32);
        KineticBlockEntity a = CreateTests.shaft(helper, new BlockPos(1, 1, 2), Direction.Axis.X);
        chains(helper, null, Direction.Axis.X, new BlockPos(2, 1, 2), new BlockPos(3, 1, 2));
        CreateTests.shaft(helper, new BlockPos(4, 1, 2), Direction.Axis.X);
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(5, 1, 2));
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos bAt = helper.absolutePos(new BlockPos(4, 1, 2));
        ForeignDrives.Bridge[] said = {ForeignDrives.Bridge.NONE};
        boolean[] asking = {true};
        java.util.function.Consumer<LevelTickEvent.Pre> each = event -> {
            if (event.getLevel() == helper.getLevel() && asking[0]) {
                said[0] = ForeignDrives.bridge(helper.getLevel(), at, Direction.WEST, bAt, Direction.EAST, 1);
            }
        };
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Pre.class, each);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(each));
        int[] turns = new int[1];
        helper.startSequence().thenExecuteAfter(60, () -> {
            KineticBlockEntity b = CreateMotorTests.driven(helper, new BlockPos(4, 1, 2));
            check(helper, b.getSpeed() == 32.0F, "B turns at A's 32 RPM: " + b.getSpeed());
            check(helper, said[0] == ForeignDrives.Bridge.A_TO_B, "the turning comes in at A: " + said[0]);
            check(helper, CreateTests.turns(press) >= 2, "the press beyond B turns: " + CreateTests.turns(press));
            check(helper, a.network != null && a.network.equals(b.network), "one Create network: " + a.network + " and " + b.network);
            check(helper, CreateTests.networkStress(a) == 64.0F, "the press's 64 su, once: " + CreateTests.networkStress(a));
            check(helper, b.getOrCreateNetwork().calculateCapacity() == 16384.0F * 32.0F, "the motor's capacity is B's side's too: "
                    + b.getOrCreateNetwork().calculateCapacity());
            check(helper, !CreateMotorTests.generating(b), "B is no source: nothing of ours gives capacity");
            asking[0] = false;
        }).thenExecuteAfter(10, () -> {
            KineticBlockEntity b = CreateMotorTests.driven(helper, new BlockPos(4, 1, 2));
            check(helper, b.getTheoreticalSpeed() == 0.0F && a.getSpeed() == 32.0F, "no train asks: cut, B stands, A turns on: " + b.getTheoreticalSpeed());
            check(helper, CreateTests.networkStress(a) == 0.0F, "and the press weighs on A no more: " + CreateTests.networkStress(a));
            turns[0] = CreateTests.turns(press);
            asking[0] = true;
        }).thenExecuteAfter(30, () -> {
            KineticBlockEntity b = CreateMotorTests.driven(helper, new BlockPos(4, 1, 2));
            check(helper, b.getSpeed() == 32.0F && CreateTests.turns(press) > turns[0], "asked again, joined again: " + b.getSpeed());
            source.generatedSpeed.setValue(0);
        }).thenExecuteAfter(10, () -> {
            KineticBlockEntity b = CreateMotorTests.driven(helper, new BlockPos(4, 1, 2));
            check(helper, b.getSpeed() == 0.0F && a.getSpeed() == 0.0F, "the source still, both still: " + b.getSpeed());
        }).thenSucceed();
    }
}
