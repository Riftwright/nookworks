package dev.nookworks.registry;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The sounds of our own. Each is defined in assets/nookworks/sounds.json: most play files computed for the mod
 * (tools/sounds/gen_sounds.py, several variants each), a few the user picked from CC0 packs (make_sounds.py), and the
 * rest one of vanilla's by its event (so no sound file of Mojang's is carried) at a volume and pitch of its own, where
 * vanilla's is the thing itself - a chain, leather, an orb. A resource pack can replace any. Each has a subtitle in
 * every language.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Nookworks.MODID);

    // what the machines sound of now and then while they work (MachineBlock#workSound), as vanilla's furnace crackles
    public static final Supplier<SoundEvent> GENERATOR_RUN = event("block.generator.run");
    public static final Supplier<SoundEvent> DIESEL_GENERATOR_RUN = event("block.diesel_generator.run");
    public static final Supplier<SoundEvent> CRUSHER_RUN = event("block.crusher.run");
    public static final Supplier<SoundEvent> STONE_MAKER_RUN = event("block.stone_maker.run");
    public static final Supplier<SoundEvent> ELECTRIC_FURNACE_RUN = event("block.electric_furnace.run");
    public static final Supplier<SoundEvent> LASER_RUN = event("block.laser.run");
    public static final Supplier<SoundEvent> RETORT_RUN = event("block.retort.run");
    public static final Supplier<SoundEvent> STIRRING_TANK_RUN = event("block.stirring_tank.run");
    public static final Supplier<SoundEvent> DISTILLER_RUN = event("block.distiller.run");
    public static final Supplier<SoundEvent> ELECTROLYTIC_CELL_RUN = event("block.electrolytic_cell.run");
    public static final Supplier<SoundEvent> WATER_PUMP_RUN = event("block.water_pump.run");
    public static final Supplier<SoundEvent> PRINTING_PRESS_RUN = event("block.printing_press.run");
    public static final Supplier<SoundEvent> COMPRESSOR_RUN = event("block.compressor.run");
    public static final Supplier<SoundEvent> RECYCLER_RUN = event("block.recycler.run");
    public static final Supplier<SoundEvent> BARTER_TERMINAL_RUN = event("block.barter_terminal.run");
    /** A Ventilation Fan's hum, now and then while it runs. */
    public static final Supplier<SoundEvent> VENTILATION_FAN_RUN = event("block.ventilation_fan.run");
    /** A Rice Polisher's crank turned: the grain rubbing on itself. */
    public static final Supplier<SoundEvent> RICE_POLISHER_TURN = event("block.rice_polisher.turn");
    /** A Repair Box taking a step of wear off what it mends. */
    public static final Supplier<SoundEvent> REPAIR_BOX_MEND = event("block.repair_box.mend");
    /** A Repair Box done with an item: its last wear taken off, a light ring of the anvil. */
    public static final Supplier<SoundEvent> REPAIR_BOX_DONE = event("block.repair_box.done");
    /** A die thrown, or knocked rolling: vanilla's bone block knock, with a subtitle of its own. */
    public static final Supplier<SoundEvent> DICE_ROLL = event("block.dice.roll");
    /** Thin Chain tied to a post, let go of or cut, come down as its post goes, and rattled by something going through it. */
    public static final Supplier<SoundEvent> THIN_CHAIN_TIE = event("entity.thin_chain.tie");
    public static final Supplier<SoundEvent> THIN_CHAIN_UNTIE = event("entity.thin_chain.untie");
    public static final Supplier<SoundEvent> THIN_CHAIN_BREAK = event("entity.thin_chain.break");
    public static final Supplier<SoundEvent> THIN_CHAIN_RATTLE = event("entity.thin_chain.rattle");
    /** A Camera's shutter: a brass camera's heavy clack. */
    public static final Supplier<SoundEvent> CAMERA_SHUTTER = event("item.camera.shutter");
    /**
     * A Motor running, a Drive Wheel turning, a Spawner Controller at work, now and then each; a Trash Can taking
     * something in, and the lava in it. The Motor's and the Trash Can's are cut from Kenney's CC0 packs
     * (tools/sounds/make_sounds.py), the wheel's and the controller's computed (tools/sounds/gen_sounds.py).
     */
    public static final Supplier<SoundEvent> MOTOR_RUN = event("block.motor.run");
    public static final Supplier<SoundEvent> DRIVE_WHEEL_TURN = event("block.drive_wheel.turn");
    public static final Supplier<SoundEvent> SPAWNER_CONTROLLER_RUN = event("block.spawner_controller.run");
    public static final Supplier<SoundEvent> TRASH_CAN_SWALLOW = event("block.trash_can.swallow");
    public static final Supplier<SoundEvent> TRASH_CAN_BURN = event("block.trash_can.burn");
    /** An Absorption Pad drinking from whoever stands on it: an orb picked up, the other way round - low and soft. */
    public static final Supplier<SoundEvent> ABSORPTION_PAD_DRAIN = event("block.absorption_pad.drain");
    /** A Camera brought up to the eye, and let down: its strap and its leather, as a spyglass is heard drawn out. */
    public static final Supplier<SoundEvent> CAMERA_RAISE = event("item.camera.raise");
    public static final Supplier<SoundEvent> CAMERA_LOWER = event("item.camera.lower");
    /**
     * Sounds computed for the mod (tools/sounds/gen_sounds.py), where vanilla's were borrowed in the code itself: a Hand
     * Quern's stone taken round, an Oil Press screwed down a turn, an Iron Pot frying and boiling, fat on a Grill's
     * coals, the Wrench's ratchet, the Oil Can's tin bottom.
     */
    public static final Supplier<SoundEvent> HAND_QUERN_TURN = event("block.hand_quern.turn");
    public static final Supplier<SoundEvent> OIL_PRESS_TURN = event("block.oil_press.turn");
    public static final Supplier<SoundEvent> IRON_POT_FRY = event("block.iron_pot.fry");
    public static final Supplier<SoundEvent> IRON_POT_BOIL = event("block.iron_pot.boil");
    public static final Supplier<SoundEvent> GRILL_SIZZLE = event("block.grill.sizzle");
    public static final Supplier<SoundEvent> WRENCH_TURN = event("item.wrench.turn");
    public static final Supplier<SoundEvent> OIL_CAN_SQUIRT = event("item.oil_can.squirt");
    /**
     * Sounds where there were none (the user, 2026-11-10: 「できそうなことはぜんぶやっていいよ」, of the sound session's survey):
     * a machine come to a stop it should be seen to (MachineAlerts), the Machine Bell ringing, a sorter sending something
     * on, sitting down on our seats, a Wind Chime in the wind. Each plays a vanilla sound as a stand-in (sounds.json)
     * until one made for it is heard and chosen by the user.
     */
    public static final Supplier<SoundEvent> MACHINE_STOP = event("block.machine.stop");
    public static final Supplier<SoundEvent> MACHINE_BELL_RING = event("block.machine_bell.ring");
    public static final Supplier<SoundEvent> SORTER_SORT = event("block.sorter.sort");
    public static final Supplier<SoundEvent> SEAT_SIT = event("block.seat.sit");
    public static final Supplier<SoundEvent> WIND_CHIME_CHIME = event("block.wind_chime.chime");

    private ModSounds() {}

    private static Supplier<SoundEvent> event(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Nookworks.id(name)));
    }
}
