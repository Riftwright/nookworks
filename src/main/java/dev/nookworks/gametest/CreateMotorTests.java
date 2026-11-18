package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;

import dev.nookworks.compat.create.CreateDrives;
import dev.nookworks.compat.create.CreateEnds;
import dev.nookworks.compat.create.CreateGoggles;
import dev.nookworks.compat.create.MotorShaftBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * A Nookworks Motor driving Create's blocks where they stand, nothing in between and nothing of Create's replaced
 * (tools/port1211/design_create.md, "Ends and trains": giving) - the figures, the price, claims and saving, as the
 * earlier rounds' tests pinned them, on Create's own shaft now - and the review's mends of the Create -> ours accounting
 * (r23: a belt's far pulley, a powered clutch, one block reaching a machine twice, the client's figure). Every kind of
 * Create part against every end: CreateEndsTests. Registered only with Create (from CreateTests).
 */
// (With the add-on Nook Technology a Motor turns its shaft the way the add-on sets it, clockwise seen from its axle's
// end - the hook ForeignDrives#motorRpm; at30 below asks the way round only without it.)
final class CreateMotorTests {
    private CreateMotorTests() {}

    static void register() {
        NookworksGameTests.test("create_motor_turns_a_shaft_and_a_millstone", 300, CreateMotorTests::turnsAMillstone);
        NookworksGameTests.test("create_motor_without_power_or_held_by_redstone_stops_the_shaft", 120, CreateMotorTests::stopsUnpaid);
        NookworksGameTests.test("create_motor_broken_leaves_the_shaft_still", 100, CreateMotorTests::motorBroken);
        NookworksGameTests.test("create_motor_shaft_broken_drops_one_shaft", 100, CreateMotorTests::shaftBroken);
        NookworksGameTests.test("create_motor_overstressed_says_so", 120, CreateMotorTests::overstressed);
        NookworksGameTests.test("create_motor_through_a_shaft_costs_what_it_costs_itself", 200, CreateMotorTests::pressCostsTheSame);
        NookworksGameTests.test("create_motor_shares_its_budget_with_its_line", 120, CreateMotorTests::sharedBudget);
        NookworksGameTests.test("create_motor_line_is_never_turned_by_create", 120, CreateMotorTests::motorLineKeepsItsSense);
        NookworksGameTests.test("create_motor_follows_a_network_turning_the_other_way", 100, CreateMotorTests::followsTheNetwork);
        NookworksGameTests.test("create_motor_two_on_one_shaft_one_turns_it", 150, CreateMotorTests::twoMotorsOneShaft);
        NookworksGameTests.test("create_motor_two_on_one_network_add_up", 100, CreateMotorTests::twoMotorsAddUp);
        NookworksGameTests.test("create_motor_shaft_reloaded_turns_on", 150, CreateMotorTests::reloaded);
        NookworksGameTests.test("create_motor_goggles_and_item_figures", 100, CreateMotorTests::gogglesAndFigures);
        NookworksGameTests.test("create_motor_takes_no_shaft_in_anothers_claim", 100, CreateMotorTests::claimEdge);
        NookworksGameTests.test("create_belt_far_pulley_weighs_its_machines", 120, CreateMotorTests::beltFarPulley);
        NookworksGameTests.test("create_powered_clutch_takes_off_the_load", 140, CreateMotorTests::clutchCutsTheLoad);
        NookworksGameTests.test("create_block_reaching_a_machine_twice_turns_it_once", 160, CreateMotorTests::reachedTwice);
        NookworksGameTests.test("create_client_reads_the_servers_load", 100, CreateMotorTests::clientFigure);
    }

    // ---- helpers ----

    /** Create's network capacity at the shaft, in su (each source's capacity times its own speed). */
    static float capacity(KineticBlockEntity kinetic) {
        return kinetic.getOrCreateNetwork().calculateCapacity();
    }

    /**
     * A Motor's shaft at its 30 RPM. The way round is Nookworks' own (+30 for these tests' Motors) and is asked - unless
     * the add-on Nook Technology is installed, which turns a Motor the way it sets it (ForeignDrives#motorRpm): then
     * only the size is asked here, and the add-on's own tests ask the way.
     */
    static boolean at30(float speed) {
        return net.neoforged.fml.ModList.get().isLoaded("nooktech") ? Math.abs(speed) == 30.0F : speed == 30.0F;
    }

    /** The shaft at {@code pos}: Create's own plain shaft still (nothing of Create's is replaced), whatever drives it. */
    static KineticBlockEntity driven(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        check(helper, state.getBlock() == MotorShaftBlock.plainShaft(), "the shaft at " + pos + " is Create's plain shaft still: " + state);
        return helper.getBlockEntity(pos, KineticBlockEntity.class);
    }

    /**
     * The block entity at {@code pos} taken away and set again from {@code saved}, as a chunk unloaded and loaded does it:
     * a new block entity read from the data whole. (Set by {@code setBlock} and then loaded into, Create takes a kinetic
     * block for one a contraption moved, and reads none of its turning.)
     */
    static KineticBlockEntity reload(GameTestHelper helper, BlockPos pos, BlockState state, CompoundTag saved) {
        var registries = helper.getLevel().registryAccess();
        BlockPos at = helper.absolutePos(pos);
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, state);
        net.minecraft.world.level.block.entity.BlockEntity loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(at, state, saved, registries);
        check(helper, loaded instanceof KineticBlockEntity, "its data loads: " + saved);
        helper.getLevel().setBlockEntity(loaded);
        return (KineticBlockEntity) loaded;
    }

    /** Whether a Motor's end gives the block turning now (it is a source of its network where it stands). */
    static boolean generating(KineticBlockEntity kinetic) {
        CreateEnds.State state = CreateEnds.state(kinetic);
        return state != null && state.generating();
    }

    /** The capacity a Motor's end gives the block, su per RPM (0 while it gives none). */
    static float givenPerRpm(KineticBlockEntity kinetic) {
        CreateEnds.State state = CreateEnds.state(kinetic);
        return state != null && state.generating() ? state.capacityPerRpm() : 0.0F;
    }

    static double fe(double perTick) {
        return MachineTuning.workEnergyExact(1) * perTick;
    }

    /** A stand-in claim mod over the blocks {@code inside}: only {@code owners} may break them. Gone when the test ends. */
    private static void claim(GameTestHelper helper, java.util.Set<BlockPos> inside, java.util.Set<java.util.UUID> owners) {
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> stand = event -> {
            if (inside.contains(event.getPos()) && (event.getPlayer() == null || !owners.contains(event.getPlayer().getUUID()))) {
                event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class, stand);
        AfterTest.undo(helper, () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(stand));
    }

    /**
     * A stranger's Motor outside a claim, its axle against a plain shaft inside it: the shaft stays Create's and still,
     * and the Motor pays no more than turning on its own (set there, it started whatever stood on the claim owner's
     * shafts). The claim's owner's own Motor beside it, against their shaft, turns it.
     */
    private static void claimEdge(GameTestHelper helper) {
        java.util.UUID claimant = java.util.UUID.randomUUID();
        java.util.UUID stranger = java.util.UUID.randomUUID();
        BlockPos theirShaft = new BlockPos(1, 2, 1);
        BlockPos ownShaft = new BlockPos(3, 2, 1);
        claim(helper, java.util.Set.of(helper.absolutePos(theirShaft), helper.absolutePos(ownShaft)), java.util.Set.of(claimant));
        CreateTests.shaft(helper, theirShaft, Direction.Axis.Y);
        CreateTests.shaft(helper, ownShaft, Direction.Axis.Y);
        MotorBlockEntity theirs = CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.UP);
        theirs.setOwner(stranger);
        MotorBlockEntity own = CreateTests.motor(helper, new BlockPos(3, 1, 1), Direction.UP);
        own.setOwner(claimant);
        int had = theirs.energyStored();
        helper.runAfterDelay(40, () -> {
            BlockState inside = helper.getBlockState(theirShaft);
            check(helper, com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.isShaft(inside),
                    "the claimed shaft against a stranger's Motor is Create's plain shaft: " + inside);
            KineticBlockEntity still = helper.getBlockEntity(theirShaft, KineticBlockEntity.class);
            check(helper, still.getSpeed() == 0.0F && !generating(still), "and stands still, not driven: " + still.getSpeed());
            double spent = had - theirs.energyStored();
            check(helper, spent <= fe(MotorBlockEntity.IDLE_FE_PER_TICK) * 40 + 1, "the stranger's Motor pays for turning on its own only: " + spent);
            KineticBlockEntity turned = driven(helper, ownShaft);
            check(helper, at30(turned.getSpeed()), "the claim's owner's own Motor turns their shaft: " + turned.getSpeed());
            helper.succeed();
        });
    }

    // ---- the Motor turning Create ----

    /**
     * A powered Motor pointing up, a plain shaft on its axle, a Millstone on the shaft: the shaft - Create's own still -
     * turns at 30 RPM with 480 su, the Millstone grinds, and the Motor pays by what is drawn: 4 su/RPM x 30 = 120 su,
     * 40 FE/t.
     */
    private static void turnsAMillstone(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(2, 1, 2), Direction.UP);
        CreateTests.shaft(helper, new BlockPos(2, 2, 2), Direction.Axis.Y);
        helper.setBlock(new BlockPos(2, 3, 2), CreateTests.createBlock("millstone"));
        MillstoneBlockEntity millstone = helper.getBlockEntity(new BlockPos(2, 3, 2), MillstoneBlockEntity.class);
        millstone.inputInv.setStackInSlot(0, new ItemStack(Items.WHEAT, 8));
        int[] energy = new int[1];
        helper.startSequence().thenExecuteAfter(30, () -> {
            KineticBlockEntity shaft = driven(helper, new BlockPos(2, 2, 2));
            check(helper, CreateEnds.hooked(), "Create asks our hooks (they went in)");
            check(helper, at30(shaft.getSpeed()) && generating(shaft), "the shaft turns at 30 RPM, a source of its network: " + shaft.getSpeed());
            check(helper, capacity(shaft) == 480.0F, "480 su: " + capacity(shaft));
            check(helper, CreateTests.networkStress(shaft) == 120.0F, "the Millstone draws 120 su: " + CreateTests.networkStress(shaft));
            check(helper, motor.guiStatus() == MachineStatus.TURNING_CREATE, "the Motor says it turns Create: " + motor.guiStatus());
            motor.energy().set(10_000);
            energy[0] = motor.energyStored();
        }).thenExecuteAfter(40, () -> {
            double paid = energy[0] - motor.energyStored();
            check(helper, Math.abs(paid - fe(40) * 40) <= fe(40) + 1, "40 FE/t for 120 su: " + paid + " in 40 ticks");
        }).thenExecuteAfter(150, () -> {
            check(helper, !millstone.outputInv.getStackInSlot(0).isEmpty() || millstone.inputInv.getStackInSlot(0).getCount() < 8,
                    "the Millstone ground some wheat");
        }).thenSucceed();
    }

    /** No FE: the shaft stands still, nothing given. Held by redstone: still at once and nothing paid; let go, it turns. */
    private static void stopsUnpaid(GameTestHelper helper) {
        MotorBlockEntity empty = CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
        empty.energy().set(0);
        CreateTests.shaft(helper, new BlockPos(2, 1, 1), Direction.Axis.X);
        MotorBlockEntity held = CreateTests.motor(helper, new BlockPos(1, 1, 5), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 5), Direction.Axis.X);
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(20, () -> {
            KineticBlockEntity still = driven(helper, new BlockPos(2, 1, 1));
            check(helper, still.getSpeed() == 0.0F && !generating(still), "no FE: the shaft stands still");
            check(helper, empty.guiStatus() == MachineStatus.NO_POWER, "the Motor says it has no power: " + empty.guiStatus());
            check(helper, at30(driven(helper, new BlockPos(2, 1, 5)).getSpeed()), "powered, the other turns");
            helper.setBlock(new BlockPos(1, 2, 5), Blocks.REDSTONE_BLOCK);
        }).thenExecuteAfter(3, () -> {
            check(helper, driven(helper, new BlockPos(2, 1, 5)).getSpeed() == 0.0F, "held by redstone, it stops at once");
            check(helper, held.guiStatus() == MachineStatus.STOPPED, "the Motor says it is stopped: " + held.guiStatus());
            at[0] = held.energyStored();
        }).thenExecuteAfter(20, () -> {
            check(helper, held.energyStored() == at[0], "nothing paid while held: " + at[0] + " then " + held.energyStored());
            helper.setBlock(new BlockPos(1, 2, 5), Blocks.AIR);
        }).thenExecuteAfter(5, () -> {
            check(helper, at30(driven(helper, new BlockPos(2, 1, 5)).getSpeed()), "let go, it turns again");
        }).thenSucceed();
    }

    /** The Motor broken: the shaft - Create's plain shaft all along, its axis as set - stands still within a few ticks, nobody paying. */
    private static void motorBroken(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(3, 1, 1), Direction.SOUTH);
        CreateTests.shaft(helper, new BlockPos(3, 1, 2), Direction.Axis.Z);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, at30(driven(helper, new BlockPos(3, 1, 2)).getSpeed()), "turning before");
            helper.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
        }).thenExecuteAfter(5, () -> {
            BlockState state = helper.getBlockState(new BlockPos(3, 1, 2));
            check(helper, state.getBlock() == MotorShaftBlock.plainShaft(), "Create's plain shaft: " + state);
            check(helper, state.getValue(RotatedPillarKineticBlock.AXIS) == Direction.Axis.Z, "its axis as set");
            KineticBlockEntity shaft = driven(helper, new BlockPos(3, 1, 2));
            check(helper, shaft.getTheoreticalSpeed() == 0.0F && !generating(shaft) && !shaft.hasSource(),
                    "still, a source no more, turned from nowhere: " + shaft.getTheoreticalSpeed());
        }).thenSucceed();
    }

    /** The shaft a Motor drives, broken, drops one Create shaft and nothing else; picked, it is Create's shaft. */
    private static void shaftBroken(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(1, 1, 3), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        helper.startSequence().thenExecuteAfter(20, () -> {
            KineticBlockEntity shaft = driven(helper, new BlockPos(2, 1, 3));
            BlockPos at = helper.absolutePos(new BlockPos(2, 1, 3));
            check(helper, shaft.getBlockState().getCloneItemStack(new net.minecraft.world.phys.BlockHitResult(at.getCenter(), Direction.UP, at, false),
                    helper.getLevel(), at, null).is(MotorShaftBlock.plainShaft().asItem()), "picked, it is Create's shaft");
            helper.getLevel().destroyBlock(at, true);
        }).thenExecuteAfter(2, () -> {
            BlockPos at = helper.absolutePos(new BlockPos(2, 1, 3));
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1.5));
            int shafts = 0;
            int others = 0;
            for (ItemEntity drop : drops) {
                if (drop.getItem().is(MotorShaftBlock.plainShaft().asItem())) {
                    shafts += drop.getItem().getCount();
                } else {
                    others += drop.getItem().getCount();
                }
            }
            check(helper, shafts == 1 && others == 0, "one Create shaft and nothing else: " + shafts + " shafts, " + others + " other");
            drops.forEach(ItemEntity::discard);
        }).thenSucceed();
    }

    /** More drawn than 480 su: Create stops the network, the Motor says it is overstressed, the press stands still. */
    private static void overstressed(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 3), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(3, 1, 3));
        CreateDrives.impactForTest(press, 100.0F); // 3000 su at 30 RPM
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(40, () -> {
            KineticBlockEntity shaft = driven(helper, new BlockPos(2, 1, 3));
            check(helper, shaft.isOverStressed() && shaft.getSpeed() == 0.0F, "overstressed, the network stops");
            check(helper, motor.guiStatus() == MachineStatus.OVERSTRESSED, "the Motor says why: " + motor.guiStatus());
            at[0] = CreateTests.turns(press);
        }).thenExecuteAfter(30, () -> check(helper, CreateTests.turns(press) == at[0], "the press stands still")).thenSucceed();
    }

    /**
     * Motor - shaft - Oil Press beside Motor - Oil Press: the press beyond the shaft is turned as fast (two turns a second)
     * and the Motor pays 20 FE/t for its 60 su - what the Motor turning a press itself pays.
     */
    private static void pressCostsTheSame(GameTestHelper helper) {
        MotorBlockEntity viaShaft = CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 1), Direction.Axis.X);
        OilPressBlockEntity far = CreateTests.press(helper, new BlockPos(3, 1, 1));
        MotorBlockEntity direct = CreateTests.motor(helper, new BlockPos(2, 1, 5), Direction.EAST);
        OilPressBlockEntity near = CreateTests.press(helper, new BlockPos(3, 1, 5));
        int[] start = new int[4];
        helper.startSequence().thenExecuteAfter(30, () -> {
            viaShaft.energy().set(10_000);
            direct.energy().set(10_000);
            start[0] = CreateTests.turns(far);
            start[1] = CreateTests.turns(near);
        }).thenExecuteAfter(100, () -> {
            int a = CreateTests.turns(far) - start[0];
            int b = CreateTests.turns(near) - start[1];
            check(helper, Math.abs(a - b) <= 1, "as fast through the shaft as straight: " + a + " and " + b);
            double paidVia = 10_000 - viaShaft.energyStored();
            double paidDirect = 10_000 - direct.energyStored();
            check(helper, Math.abs(paidVia - paidDirect) <= fe(20) * 2 + 1, "the same FE: " + paidVia + " through the shaft, " + paidDirect + " straight");
            check(helper, paidVia >= paidDirect - 1, "never less through the shaft");
        }).thenSucceed();
    }

    /**
     * A Motor's wheel line with two presses and a Create shaft at a far wheel's axle end: the Motor drives the shaft, its
     * capacity six machines' worth (12 su/RPM, 360 su); the presses are the Motor's and weigh nothing on Create.
     */
    private static void sharedBudget(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
        DriveWheelBlockEntity a = CreateTests.wheel(helper, new BlockPos(2, 1, 1), Direction.EAST);
        DriveWheelBlockEntity b = CreateTests.wheel(helper, new BlockPos(2, 1, 5), Direction.EAST);
        CreateTests.belt(helper, a, b);
        OilPressBlockEntity first = CreateTests.press(helper, new BlockPos(3, 1, 1));
        OilPressBlockEntity second = CreateTests.press(helper, new BlockPos(3, 1, 5));
        CreateTests.shaft(helper, new BlockPos(1, 1, 5), Direction.Axis.X);
        helper.startSequence().thenExecuteAfter(40, () -> {
            KineticBlockEntity shaft = driven(helper, new BlockPos(1, 1, 5));
            check(helper, givenPerRpm(shaft) == 12.0F && capacity(shaft) == 360.0F, "six machines' worth: " + capacity(shaft));
            check(helper, at30(shaft.getSpeed()), "turning at 30 RPM");
            check(helper, shaft.calculateStressApplied() == 0.0F, "the line's presses weigh nothing on Create");
            check(helper, CreateTests.turns(first) >= 2 && CreateTests.turns(second) >= 2, "the Motor turns both presses");
            check(helper, motor.guiStatus() == MachineStatus.RUNNING, "running: " + motor.guiStatus());
        }).thenSucceed();
    }

    /**
     * The same line, a Creative Motor beyond the shaft at 64 RPM: the network turns at Create's faster speed, the Motor's
     * shaft gives beside it, and the line stays the Motor's (its presses held by the Motor, no load on Create).
     */
    private static void motorLineKeepsItsSense(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 1), Direction.EAST);
        DriveWheelBlockEntity a = CreateTests.wheel(helper, new BlockPos(2, 1, 1), Direction.EAST);
        DriveWheelBlockEntity b = CreateTests.wheel(helper, new BlockPos(2, 1, 5), Direction.EAST);
        CreateTests.belt(helper, a, b);
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(3, 1, 1));
        CreateTests.shaft(helper, new BlockPos(1, 1, 5), Direction.Axis.X);
        CreateTests.creativeMotor(helper, new BlockPos(0, 1, 5), Direction.EAST, 64);
        helper.startSequence().thenExecuteAfter(40, () -> {
            KineticBlockEntity shaft = driven(helper, new BlockPos(1, 1, 5));
            check(helper, shaft.getSpeed() == 64.0F, "Create's faster source sets the speed: " + shaft.getSpeed());
            check(helper, generating(shaft), "the Motor's end gives beside it");
            check(helper, shaft.calculateStressApplied() == 0.0F, "the line's press weighs nothing on Create");
            check(helper, motor.guiStatus() == MachineStatus.RUNNING && CreateTests.turns(press) >= 2, "the Motor turns its press: " + motor.guiStatus());
            check(helper, !MotorBlockEntity.heldByAnother(press, helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel().getGameTime()),
                    "no Create block holds the press");
        }).thenSucceed();
    }

    /** A Create network already turning the other way meets a Motor's axle: the Motor's end follows it, adds its capacity, and nothing is broken. */
    private static void followsTheNetwork(GameTestHelper helper) {
        CreateTests.creativeMotor(helper, new BlockPos(1, 1, 3), Direction.EAST, -32);
        CreateTests.shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        CreateTests.shaft(helper, new BlockPos(3, 1, 3), Direction.Axis.X);
        helper.startSequence().thenExecuteAfter(15, () -> CreateTests.motor(helper, new BlockPos(4, 1, 3), Direction.WEST)).thenExecuteAfter(30, () -> {
            KineticBlockEntity shaft = driven(helper, new BlockPos(3, 1, 3));
            CreateEnds.State state = CreateEnds.state(shaft);
            check(helper, state != null && state.generating() && state.given() < 0.0F, "it follows the network's way: " + (state == null ? null : state.given()));
            check(helper, shaft.getSpeed() == -32.0F, "the faster source's speed: " + shaft.getSpeed());
            float creative = 16384.0F * 32.0F;
            check(helper, capacity(shaft) >= creative + 480.0F - 1.0F, "the Motor's 480 su added: " + capacity(shaft));
        }).thenSucceed();
    }

    /** Two Motors at both ends of one shaft: one turns it (480 su, not 960), the other stands by and takes over when it stops. */
    private static void twoMotorsOneShaft(GameTestHelper helper) {
        MotorBlockEntity west = CreateTests.motor(helper, new BlockPos(1, 1, 3), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        MotorBlockEntity east = CreateTests.motor(helper, new BlockPos(3, 1, 3), Direction.WEST);
        MotorBlockEntity[] holder = new MotorBlockEntity[1];
        helper.startSequence().thenExecuteAfter(30, () -> {
            KineticBlockEntity shaft = driven(helper, new BlockPos(2, 1, 3));
            check(helper, capacity(shaft) == 480.0F, "one Motor's 480 su: " + capacity(shaft));
            boolean westTurns = west.guiStatus() == MachineStatus.TURNING_CREATE;
            boolean eastTurns = east.guiStatus() == MachineStatus.TURNING_CREATE;
            check(helper, westTurns != eastTurns, "one Motor turns it: " + west.guiStatus() + ", " + east.guiStatus());
            MotorBlockEntity standing = westTurns ? east : west;
            check(helper, standing.guiStatus() == MachineStatus.ANOTHER_MOTOR, "the other stands by: " + standing.guiStatus());
            holder[0] = westTurns ? west : east;
            helper.setBlock(helper.relativePos(holder[0].getBlockPos()).above(), Blocks.REDSTONE_BLOCK);
        }).thenExecuteAfter(10, () -> {
            KineticBlockEntity shaft = driven(helper, new BlockPos(2, 1, 3));
            MotorBlockEntity other = holder[0] == west ? east : west;
            check(helper, other.guiStatus() == MachineStatus.TURNING_CREATE, "the other takes over: " + other.guiStatus());
            check(helper, Math.abs(shaft.getSpeed()) == 30.0F && capacity(shaft) == 480.0F, "still 480 su at 30 RPM");
        }).thenSucceed();
    }

    /** Two Motors on one Create line, each at its own end of it: capacities add, 960 su, each drawing half the load. */
    private static void twoMotorsAddUp(GameTestHelper helper) {
        CreateTests.motor(helper, new BlockPos(0, 1, 3), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(1, 1, 3), Direction.Axis.X);
        CreateTests.shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        CreateTests.shaft(helper, new BlockPos(3, 1, 3), Direction.Axis.X);
        CreateTests.motor(helper, new BlockPos(4, 1, 3), Direction.WEST);
        helper.startSequence().thenExecuteAfter(30, () -> {
            KineticBlockEntity a = driven(helper, new BlockPos(1, 1, 3));
            KineticBlockEntity b = driven(helper, new BlockPos(3, 1, 3));
            check(helper, generating(a) && generating(b), "both ends give");
            check(helper, capacity(a) == 960.0F, "two Motors' 960 su: " + capacity(a));
            check(helper, a.getSpeed() == b.getSpeed() && a.getSpeed() != 0.0F, "one network turning: " + a.getSpeed() + ", " + b.getSpeed());
        }).thenSucceed();
    }

    /**
     * Saved and set again mid-turn (as a chunk unloaded and loaded): what we keep is in Create's shaft's own data
     * (nookworks_drive: speed, capacity, from), and the shaft turns on at 30 RPM with 480 su.
     */
    private static void reloaded(GameTestHelper helper) {
        BlockPos motorPos = new BlockPos(1, 1, 3);
        BlockPos shaftPos = new BlockPos(2, 1, 3);
        CreateTests.motor(helper, motorPos, Direction.EAST);
        CreateTests.shaft(helper, shaftPos, Direction.Axis.X);
        var registries = helper.getLevel().registryAccess();
        CompoundTag[] tags = new CompoundTag[2];
        BlockState[] states = new BlockState[2];
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, at30(driven(helper, shaftPos).getSpeed()), "turning before");
            states[0] = helper.getBlockState(motorPos);
            states[1] = helper.getBlockState(shaftPos);
            tags[0] = helper.getBlockEntity(motorPos, MotorBlockEntity.class).saveWithoutMetadata(registries);
            tags[1] = driven(helper, shaftPos).saveWithFullMetadata(registries);
            CompoundTag kept = tags[1].getCompound("nookworks_drive");
            check(helper, at30(kept.getFloat("speed")) && kept.getFloat("capacity") == 16.0F
                    && kept.getLong("from") == helper.absolutePos(motorPos).asLong(), "its keys saved with Create's own: " + tags[1]);
            String type = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(driven(helper, shaftPos).getType()));
            check(helper, type.startsWith("create:"), "in Create's shaft's own block entity: " + type);
            helper.setBlock(shaftPos, Blocks.AIR);
            helper.setBlock(motorPos, Blocks.AIR);
            helper.setBlock(motorPos, states[0]);
            helper.getBlockEntity(motorPos, MotorBlockEntity.class).loadWithComponents(tags[0], registries);
            KineticBlockEntity again = reload(helper, shaftPos, states[1], tags[1]);
            check(helper, generating(again) && at30(again.getTheoreticalSpeed()), "loaded, it is a source at once, at its speed: " + again.getTheoreticalSpeed());
        }).thenExecuteAfter(30, () -> {
            KineticBlockEntity shaft = driven(helper, shaftPos);
            check(helper, generating(shaft) && at30(shaft.getSpeed()), "turning after: " + shaft.getSpeed() + " generating "
                    + generating(shaft) + " theoretical " + shaft.getTheoreticalSpeed() + " overstressed " + shaft.isOverStressed()
                    + " network " + shaft.hasNetwork() + " source " + shaft.hasSource() + " motor " + helper.getBlockEntity(motorPos, MotorBlockEntity.class).guiStatus());
            check(helper, capacity(shaft) == 480.0F, "480 su after: " + capacity(shaft));
        }).thenSucceed();
    }

    /** The goggles' figures on a Motor turning a shaft (speed, su drawn of 480, FE), and Create's table for its item. */
    private static void gogglesAndFigures(GameTestHelper helper) {
        MotorBlockEntity motor = CreateTests.motor(helper, new BlockPos(1, 1, 3), Direction.EAST);
        CreateTests.shaft(helper, new BlockPos(2, 1, 3), Direction.Axis.X);
        CreateTests.press(helper, new BlockPos(3, 1, 3));
        helper.startSequence().thenExecuteAfter(40, () -> {
            CreateGoggles.MotorFacts facts = CreateGoggles.motorFacts(motor);
            check(helper, facts != null && facts.rpm() == 30.0F && facts.capacitySu() == 480.0F && Math.abs(facts.drawnSu() - 60.0F) < 0.6F,
                    "the goggles read 30 RPM and 60 su of 480: " + facts);
            check(helper, Math.abs(facts.fe() - fe(20)) < 0.1F, "and 20 FE/t: " + facts);
            Object asGoggles = motor;
            check(helper, asGoggles instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation, "the Motor answers the goggles");
            var block = dev.nookworks.registry.ModOil.MOTOR.get();
            check(helper, com.simibubi.create.api.stress.BlockStressValues.getCapacity(block) == 16.0, "the Motor's capacity in Create's table: 16 su/RPM");
            var rpm = com.simibubi.create.api.stress.BlockStressValues.RPM.get(block);
            check(helper, rpm != null && rpm.value() == 30, "at 30 RPM: " + rpm);
        }).thenSucceed();
    }

    // ---- the review's mends (r23) ----

    /** A Creative Motor turns a belt; a press at the far pulley turns and weighs on the network (2 su/RPM on the belt's controller). */
    private static void beltFarPulley(GameTestHelper helper) {
        CreateTests.shaft(helper, new BlockPos(1, 1, 2), Direction.Axis.Z);
        CreateTests.shaft(helper, new BlockPos(4, 1, 2), Direction.Axis.Z);
        BeltConnectorItem.createBelts(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 2)), helper.absolutePos(new BlockPos(4, 1, 2)));
        CreateTests.creativeMotor(helper, new BlockPos(1, 1, 1), Direction.SOUTH, 32);
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(4, 1, 3));
        helper.startSequence().thenExecuteAfter(50, () -> {
            KineticBlockEntity pulley = helper.getBlockEntity(new BlockPos(4, 1, 2), KineticBlockEntity.class);
            check(helper, pulley.getSpeed() != 0.0F, "the belt turns");
            check(helper, CreateTests.turns(press) >= 2, "the press at the far pulley turns: " + CreateTests.turns(press));
            check(helper, CreateTests.networkStress(pulley) == 2.0F * 32, "and weighs 64 su on the network: " + CreateTests.networkStress(pulley));
        }).thenSucceed();
    }

    /** A clutch between a Creative Motor and a press: powered, the press stops and its stress leaves the network; let go, it is back. */
    private static void clutchCutsTheLoad(GameTestHelper helper) {
        CreateTests.creativeMotor(helper, new BlockPos(1, 1, 3), Direction.EAST, 32);
        helper.setBlock(new BlockPos(2, 1, 3), CreateTests.createBlock("clutch").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        KineticBlockEntity clutch = helper.getBlockEntity(new BlockPos(2, 1, 3), KineticBlockEntity.class);
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(3, 1, 3));
        int[] at = new int[1];
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, CreateTests.networkStress(clutch) == 64.0F, "64 su with the press: " + CreateTests.networkStress(clutch));
            helper.setBlock(new BlockPos(2, 2, 3), Blocks.REDSTONE_BLOCK);
        }).thenExecuteAfter(10, () -> {
            check(helper, CreateTests.networkStress(clutch) == 0.0F, "the clutch powered, nothing counted: " + CreateTests.networkStress(clutch));
            at[0] = CreateTests.turns(press);
        }).thenExecuteAfter(30, () -> {
            check(helper, CreateTests.turns(press) == at[0], "and the press still");
            helper.setBlock(new BlockPos(2, 2, 3), Blocks.AIR);
        }).thenExecuteAfter(10, () -> {
            check(helper, CreateTests.networkStress(clutch) == 64.0F, "let go, counted again: " + CreateTests.networkStress(clutch));
        }).thenSucceed();
    }

    /**
     * A gearbox with Drive Wheels on two of its shaft ends, both wheels on one belt line with a press: the press turns at
     * the gearbox's speed, not twice it, and weighs 2 su/RPM, not 4.
     */
    private static void reachedTwice(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), CreateTests.createBlock("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        KineticBlockEntity gearbox = helper.getBlockEntity(new BlockPos(2, 1, 2), KineticBlockEntity.class);
        CreateTests.creativeMotor(helper, new BlockPos(2, 1, 1), Direction.SOUTH, 30);
        DriveWheelBlockEntity a = CreateTests.wheel(helper, new BlockPos(1, 1, 2), Direction.WEST);
        DriveWheelBlockEntity b = CreateTests.wheel(helper, new BlockPos(3, 1, 2), Direction.EAST);
        DriveWheelBlockEntity c = CreateTests.wheel(helper, new BlockPos(1, 1, 5), Direction.WEST);
        CreateTests.wheel(helper, new BlockPos(2, 1, 5), Direction.EAST); // joins the two belted ones end to end
        DriveWheelBlockEntity d = CreateTests.wheel(helper, new BlockPos(3, 1, 5), Direction.EAST);
        CreateTests.belt(helper, a, c);
        CreateTests.belt(helper, b, d);
        OilPressBlockEntity press = CreateTests.press(helper, new BlockPos(0, 1, 5));
        int[] start = new int[1];
        helper.startSequence().thenExecuteAfter(30, () -> {
            check(helper, gearbox.calculateStressApplied() == 2.0F, "the press weighs once on the gearbox: " + gearbox.calculateStressApplied());
            start[0] = CreateTests.turns(press);
        }).thenExecuteAfter(100, () -> {
            int turns = CreateTests.turns(press) - start[0];
            float ticks = MachineTuning.time(MotorBlockEntity.TICKS_PER_TURN) / (float) MotorBlockEntity.TICKS_PER_TURN;
            check(helper, Math.abs(turns - 10 / ticks) <= 1, "30 RPM: ten turns in five seconds, not twenty: " + turns);
        }).thenSucceed();
    }

    /**
     * A press between a running Motor and a Create shaft: the server counts it on no shaft (the Motor keeps it), and a
     * client given the shaft's data reads the same, not its own recount.
     */
    private static void clientFigure(GameTestHelper helper) {
        CreateTests.press(helper, new BlockPos(3, 1, 3));
        CreateTests.motor(helper, new BlockPos(3, 1, 2), Direction.SOUTH);
        KineticBlockEntity shaft = CreateTests.shaft(helper, new BlockPos(3, 1, 4), Direction.Axis.Z);
        helper.startSequence().thenExecuteAfter(20, () -> CreateTests.creativeMotor(helper, new BlockPos(3, 1, 5), Direction.NORTH, 32))
                .thenExecuteAfter(40, () -> {
                    var registries = helper.getLevel().registryAccess();
                    check(helper, shaft.calculateStressApplied() == 0.0F, "the server counts nothing for the Motor's press");
                    KineticBlockEntity copy = (KineticBlockEntity) shaft.getType().create(shaft.getBlockPos(), shaft.getBlockState());
                    check(helper, copy != null, "a client's copy");
                    copy.readClient(shaft.getUpdateTag(registries), registries);
                    check(helper, CreateDrives.clientLoad(copy) == 0.0F, "the client reads the server's figure: " + CreateDrives.clientLoad(copy));
                }).thenSucceed();
    }
}
