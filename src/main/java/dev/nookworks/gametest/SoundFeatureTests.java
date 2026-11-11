package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.NookworksConfig;
import dev.nookworks.block.MachineBellBlock;
import dev.nookworks.fluid.WaterPumpBlockEntity;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.MachineAlerts;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.WorkSounds;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HungItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;

/**
 * The sounds and the bell of 2026-11-10 (the user: 「できそうなことはぜんぶやっていいよ」, of the sound session's survey): the
 * machines' work sounds capped a second; a machine's stop heard once it has settled, once, and once for a line that stops
 * together; machines at work as vibrations when the server asks; the Machine Bell ringing and signalling for a stopped
 * machine but never to it; the Wind Chime hanging, and chiming more in rain and storm, less under a roof.
 */
final class SoundFeatureTests {
    private SoundFeatureTests() {}

    static void register() {
        test("machine_work_sounds_start_no_more_than_three_a_second", SoundFeatureTests::workSoundCap);
        test("a_machine_stop_is_heard_once_it_has_settled_and_once_only", SoundFeatureTests::stopCue);
        test("a_machine_is_watched_for_its_stop_from_its_load", SoundFeatureTests::watched);
        test("machine_vibrations_reach_a_sculk_sensor_when_the_server_asks", 100, SoundFeatureTests::vibrations);
        test("machine_bell_rings_and_signals_for_a_stopped_machine_but_never_to_it", 300, SoundFeatureTests::bell);
        test("a_machine_with_a_screen_of_its_own_is_watched_and_rung_for", 200, SoundFeatureTests::ownScreen);
        test("a_machine_bell_found_ringing_rings_on_without_a_second_ring", 200, SoundFeatureTests::bellLoaded);
        test("a_water_pump_with_its_tank_full_is_no_stop_to_see_to", SoundFeatureTests::pumpFull);
        test("a_wind_chime_hangs_and_chimes_more_in_rain_and_storm", SoundFeatureTests::windChime);
    }

    private static void workSoundCap(GameTestHelper helper) {
        WorkSounds.reset();
        long t = 1_000;
        check(helper, WorkSounds.mayStart(t) && WorkSounds.mayStart(t + 1) && WorkSounds.mayStart(t + 2), "three may start in a second");
        check(helper, !WorkSounds.mayStart(t + 3) && !WorkSounds.mayStart(t + 19), "a fourth waits");
        check(helper, WorkSounds.mayStart(t + 20), "a second after the first, one more may");
        // another world joined since, its clock far behind: the starts counted in the last are no reason to wait
        WorkSounds.reset();
        long old = 3_000_000;
        check(helper, WorkSounds.mayStart(old) && WorkSounds.mayStart(old) && WorkSounds.mayStart(old), "three in the old world");
        check(helper, WorkSounds.mayStart(100), "and one at once in a world whose clock is behind it");
        // on the server the client settings are not loaded: as made
        check(helper, WorkSounds.chance(0.1F) == 0.1F && WorkSounds.volume() == 1.0F, "as made where nothing is set");
        WorkSounds.reset();
        helper.succeed();
    }

    private static void stopCue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MachineAlerts.forgetRecent();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        MachineAlerts.Watch w = new MachineAlerts.Watch();
        check(helper, !MachineAlerts.look(level, at, MachineStatus.RUNNING, w, 0, false), "running: nothing");
        check(helper, !MachineAlerts.look(level, at, MachineStatus.OUTPUT_BLOCKED, w, 10, false), "just stopped: not yet");
        check(helper, !MachineAlerts.look(level, at, MachineStatus.OUTPUT_BLOCKED, w, 40, false), "stopped half a second ago... not yet");
        check(helper, MachineAlerts.look(level, at, MachineStatus.OUTPUT_BLOCKED, w, 50, false), "settled two seconds: the cue");
        check(helper, !MachineAlerts.look(level, at, MachineStatus.OUTPUT_BLOCKED, w, 100, false), "and only once");
        MachineAlerts.look(level, at, MachineStatus.RUNNING, w, 110, false);
        MachineAlerts.look(level, at, MachineStatus.NO_POWER, w, 120, false);
        check(helper, !MachineAlerts.look(level, at, MachineStatus.NO_POWER, w, 170, false), "stopped again soon after: no cue within thirty seconds");

        MachineAlerts.forgetRecent();
        MachineAlerts.Watch idle = new MachineAlerts.Watch();
        MachineAlerts.look(level, at, MachineStatus.IDLE, idle, 1_000, false);
        MachineAlerts.look(level, at, MachineStatus.OUTPUT_BLOCKED, idle, 1_010, false);
        check(helper, !MachineAlerts.look(level, at, MachineStatus.OUTPUT_BLOCKED, idle, 1_100, false), "it was not running: nothing");

        MachineAlerts.Watch held = new MachineAlerts.Watch();
        MachineAlerts.look(level, at, MachineStatus.RUNNING, held, 2_000, false);
        MachineAlerts.look(level, at, MachineStatus.REDSTONE, held, 2_010, false);
        check(helper, !MachineAlerts.look(level, at, MachineStatus.REDSTONE, held, 2_100, false), "held by redstone, as meant: nothing");

        MachineAlerts.Watch fuel = new MachineAlerts.Watch();
        MachineAlerts.look(level, at, MachineStatus.RUNNING, fuel, 3_000, false);
        MachineAlerts.look(level, at, MachineStatus.NO_FUEL, fuel, 3_010, false);
        check(helper, MachineAlerts.look(level, at, MachineStatus.NO_FUEL, fuel, 3_050, false), "out of fuel is heard though its tone is grey");

        // a line stopping together: one cue in a place
        MachineAlerts.forgetRecent();
        BlockPos beside = at.east(2);
        MachineAlerts.Watch a = new MachineAlerts.Watch();
        MachineAlerts.Watch b = new MachineAlerts.Watch();
        for (MachineAlerts.Watch watch : new MachineAlerts.Watch[] {a, b}) {
            MachineAlerts.look(level, at, MachineStatus.RUNNING, watch, 4_000, false);
            MachineAlerts.look(level, at, MachineStatus.NO_POWER, watch, 4_010, false);
        }
        boolean first = MachineAlerts.look(level, at, MachineStatus.NO_POWER, a, 4_050, false);
        boolean second = MachineAlerts.look(level, beside, MachineStatus.NO_POWER, b, 4_050, false);
        check(helper, first && !second, "two machines side by side stopping together: one cue (" + first + ", " + second + ")");
        MachineAlerts.forgetRecent();
        helper.succeed();
    }

    private static void watched(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.CRUSHER.get());
        // its onLoad runs as the level takes the block entity in, a tick or so on
        helper.succeedWhen(() -> check(helper, MachineAlerts.watched(helper.getBlockEntity(pos, CrusherBlockEntity.class)),
                "a Crusher set down is watched"));
    }

    private static void vibrations(GameTestHelper helper) {
        check(helper, !NookworksConfig.MACHINE_VIBRATIONS.getDefault(), "off unless the server asks: it would call the Warden");
        ServerLevel level = helper.getLevel();
        BlockPos machine = new BlockPos(1, 2, 2);
        BlockPos sensor = new BlockPos(4, 2, 2);
        helper.setBlock(machine.below(), Blocks.STONE);
        helper.setBlock(machine, ModBlocks.CRUSHER.get());
        helper.setBlock(sensor.below(), Blocks.STONE);
        helper.setBlock(sensor, Blocks.SCULK_SENSOR);
        helper.runAfterDelay(5, () -> {
            MachineAlerts.look(level, helper.absolutePos(machine), MachineStatus.RUNNING, new MachineAlerts.Watch(),
                    MachineAlerts.VIBRATION_EVERY * 1_000L, true);
            helper.succeedWhen(() -> check(helper, SculkSensorBlock.getPhase(helper.getBlockState(sensor)) == SculkSensorPhase.ACTIVE,
                    "the sensor hears the machine at work"));
        });
    }

    private static void bell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos crusher = new BlockPos(1, 2, 2);
        BlockPos bell = crusher.east();
        BlockPos wire = bell.east();
        for (BlockPos p : new BlockPos[] {crusher, bell, wire}) {
            helper.setBlock(p.below(), Blocks.STONE);
        }
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        helper.setBlock(bell, ModBlocks.MACHINE_BELL.get().defaultBlockState().setValue(MachineBellBlock.FACE, AttachFace.FLOOR)
                .setValue(MachineBellBlock.FACING, Direction.EAST));
        helper.setBlock(wire, Blocks.REDSTONE_WIRE);
        CrusherBlockEntity machine = helper.getBlockEntity(crusher, CrusherBlockEntity.class);
        machine.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 16)); // work, and no power for it
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, helper.getBlockState(bell).getValue(MachineBellBlock.POWERED),
                        "the bell has rung for a Crusher with no power: " + machine.guiStatus()))
                .thenExecute(() -> {
                    check(helper, machine.guiStatus() == MachineStatus.NO_POWER, "the Crusher stood out of power");
                    check(helper, helper.getBlockState(wire).getValue(RedstoneWireBlock.POWER) > 0, "the dust beside it has its signal");
                    check(helper, !level.hasNeighborSignal(helper.absolutePos(crusher)), "but the Crusher gets none from it");
                    machine.energy().set(CrusherBlockEntity.CAPACITY); // powered: it runs again
                })
                .thenWaitUntil(() -> check(helper, !helper.getBlockState(bell).getValue(MachineBellBlock.POWERED),
                        "running again, the bell is quiet: " + machine.guiStatus()))
                .thenExecute(() -> check(helper, helper.getBlockState(wire).getValue(RedstoneWireBlock.POWER) == 0, "and its signal gone"))
                .thenSucceed();
    }

    /**
     * A machine built on MachineBlockEntity that is no MachineInfo - as an add-on's with a screen of its own is
     * (nookTechnology's: its review r1 found no stop of theirs heard and no bell rung) - is watched from its load and
     * rung for, and gets no signal from the bell. The Water Pump stands in for one, on stone: no water to draw.
     */
    private static void ownScreen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pump = new BlockPos(1, 2, 2);
        BlockPos bell = pump.east();
        BlockPos wire = bell.east();
        for (BlockPos p : new BlockPos[] {pump, bell, wire}) {
            helper.setBlock(p.below(), Blocks.STONE);
        }
        helper.setBlock(pump, ModBlocks.WATER_PUMP.get());
        helper.setBlock(bell, ModBlocks.MACHINE_BELL.get().defaultBlockState().setValue(MachineBellBlock.FACE, AttachFace.FLOOR)
                .setValue(MachineBellBlock.FACING, Direction.EAST));
        helper.setBlock(wire, Blocks.REDSTONE_WIRE);
        WaterPumpBlockEntity machine = helper.getBlockEntity(pump, WaterPumpBlockEntity.class);
        check(helper, !(machine instanceof dev.nookworks.machine.MachineInfo), "the stand-in is no MachineInfo");
        check(helper, MachineAlerts.isMachine(machine), "but a machine to the bell and the cue");
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, MachineAlerts.watched(machine), "watched from its load"))
                .thenWaitUntil(() -> check(helper, helper.getBlockState(bell).getValue(MachineBellBlock.POWERED),
                        "the bell has rung for a pump with no water: " + machine.guiStatus()))
                .thenExecute(() -> {
                    check(helper, MachineAlerts.statusOf(machine) == MachineStatus.NO_WATER, "read through guiStatus(): "
                            + MachineAlerts.statusOf(machine));
                    check(helper, helper.getBlockState(wire).getValue(RedstoneWireBlock.POWER) > 0, "the dust beside it has its signal");
                    check(helper, !level.hasNeighborSignal(helper.absolutePos(pump)), "but the pump gets none from it");
                })
                .thenSucceed();
    }

    /**
     * A bell found ringing - the world loaded again, its chunk back: nothing noted of when its machine stopped, and the
     * machine itself saying idle until its first tick - rings on with no second ring while the machine stays stopped. It
     * let go and rang again two seconds on, and whatever its signal ran saw it go off and on at every load.
     */
    private static void bellLoaded(GameTestHelper helper) {
        BlockPos crusher = new BlockPos(1, 2, 2);
        BlockPos bell = crusher.east();
        for (BlockPos p : new BlockPos[] {crusher, bell}) {
            helper.setBlock(p.below(), Blocks.STONE);
        }
        helper.setBlock(crusher, ModBlocks.CRUSHER.get()); // idle: nothing to crush yet, as a machine just loaded says
        helper.setBlock(bell, ModBlocks.MACHINE_BELL.get().defaultBlockState().setValue(MachineBellBlock.FACE, AttachFace.FLOOR)
                .setValue(MachineBellBlock.FACING, Direction.EAST).setValue(MachineBellBlock.POWERED, true));
        CrusherBlockEntity machine = helper.getBlockEntity(crusher, CrusherBlockEntity.class);
        // after the bell's first look (ten ticks on) and before its second, the machine works out it has no power
        helper.runAfterDelay(MachineBellBlock.LOOK_EVERY + 2,
                () -> machine.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 16)));
        helper.onEachTick(() -> check(helper, helper.getBlockState(bell).getValue(MachineBellBlock.POWERED),
                "the bell rings on (tick " + helper.getTick() + ", " + machine.guiStatus() + ")"));
        helper.runAfterDelay(MachineAlerts.SETTLE + 3 * MachineBellBlock.LOOK_EVERY, () -> {
            check(helper, machine.guiStatus() == MachineStatus.NO_POWER, "the Crusher stood out of power: " + machine.guiStatus());
            helper.succeed();
        });
    }

    /**
     * A Water Pump's tank full is the line drawing less than it pumps: no stop to see to, so neither the cue nor a bell
     * takes it for one (a pump feeding a machine slowly cued and rang at every refill). Out of water it is one.
     */
    private static void pumpFull(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, ModBlocks.WATER_PUMP.get());
        WaterPumpBlockEntity pump = helper.getBlockEntity(at, WaterPumpBlockEntity.class);
        check(helper, !pump.alerts(MachineStatus.TANK_FULL), "its tank full is as meant");
        check(helper, pump.alerts(MachineStatus.NO_WATER) && pump.alerts(MachineStatus.NO_POWER), "out of water or power it is not");
        check(helper, MachineStatus.TANK_FULL.alerts(), "another machine's tank full still is");
        helper.succeed();
    }

    private static void windChime(GameTestHelper helper) {
        ItemStack chime = new ItemStack(ModRope.WIND_CHIME.get());
        check(helper, HungItemEntity.hangs(chime), "a Wind Chime hangs as a bottle does");
        float clear = HungItemEntity.windChime(true, false, false);
        float rain = HungItemEntity.windChime(true, true, false);
        float storm = HungItemEntity.windChime(true, true, true);
        float roofed = HungItemEntity.windChime(false, false, false);
        check(helper, storm > rain && rain > clear && clear > roofed && roofed > 0, "more in rain, more in a storm, less under a roof");
        ServerLevel level = helper.getLevel();
        BlockPos end = new BlockPos(2, 3, 2);
        helper.setBlock(end.above(), Blocks.STONE);
        helper.setBlock(end, ModBlocks.THIN_ROPE.get());
        HungItemEntity hung = HungItemEntity.underRope(level, helper.absolutePos(end), chime, 0.0F);
        check(helper, hung.misfit(level) == null, "room for it under the rope's end");
        helper.succeed();
    }
}
