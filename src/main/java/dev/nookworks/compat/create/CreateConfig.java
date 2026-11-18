package dev.nookworks.compat.create;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The Create part's numbers, a server setting of each world (nookworks-create-server.toml, beside Nookworks' own), for a
 * pack that weighs Create's stress otherwise. The defaults and how they tie together: tools/port1211/design_create.md.
 * Read when used, so a change counts at once (the load is worked out again each tick).
 */
public final class CreateConfig {
    public static final ModConfigSpec SPEC;

    /** Stress impact of each hand-worked machine a Create shaft turns, in su per RPM. */
    public static final ModConfigSpec.DoubleValue OIL_PRESS_IMPACT;
    public static final ModConfigSpec.DoubleValue RICE_POLISHER_IMPACT;
    public static final ModConfigSpec.DoubleValue HAND_QUERN_IMPACT;
    public static final ModConfigSpec.DoubleValue NOODLE_MAKER_IMPACT;
    /** The same for another mod's machine worked by a handle (an add-on's), unless it registers its own. */
    public static final ModConfigSpec.DoubleValue OTHER_MACHINE_IMPACT;
    /** What a Nookworks Motor gives a Create shaft against its axle, in su per RPM at its 30 RPM: its whole budget of eight machines. */
    public static final ModConfigSpec.DoubleValue MOTOR_CAPACITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Create's rotation and Nookworks' machines (a Create shaft set straight against them). Stress in Create's units (su).",
                "One turn of a machine's handle is a quarter round of the shaft: N RPM gives N/15 turns a second. Our machines are",
                "hand-sized, turned with one hand: 2 su/RPM each (Create's Low, half its Millstone), so a Hand Crank (256 su at 32 RPM)",
                "turns four and a Water Wheel (256 su at 8 RPM) a whole Drive Wheel line of eight.")
                .translation(key("create")).push("create");
        builder.comment("Stress impact of each machine a Create shaft turns, in su per RPM (Create's Millstone: 4, Encased Fan: 2).");
        OIL_PRESS_IMPACT = builder.translation(key("create.oilPressImpact")).defineInRange("oilPressImpact", 2.0, 0.0, 1024.0);
        RICE_POLISHER_IMPACT = builder.translation(key("create.ricePolisherImpact")).defineInRange("ricePolisherImpact", 2.0, 0.0, 1024.0);
        HAND_QUERN_IMPACT = builder.translation(key("create.handQuernImpact")).defineInRange("handQuernImpact", 2.0, 0.0, 1024.0);
        NOODLE_MAKER_IMPACT = builder.translation(key("create.noodleMakerImpact")).defineInRange("noodleMakerImpact", 2.0, 0.0, 1024.0);
        builder.comment("The same for another mod's machine worked by a handle, unless that mod gives Create a figure of its own.");
        OTHER_MACHINE_IMPACT = builder.translation(key("create.otherMachineImpact")).defineInRange("otherMachineImpact", 2.0, 0.0, 1024.0);
        builder.comment("Stress capacity a Nookworks Motor gives a Create shaft set against its axle, in su per RPM at its 30 RPM:",
                "its eight machines' worth (8 x 2 su/RPM = 480 su), each machine of ours on its belt line taking one eighth. It pays",
                "20 FE/t for every eighth of this drawn.");
        MOTOR_CAPACITY = builder.translation(key("create.motorCapacity")).defineInRange("motorCapacity", 16.0, 0.0, 4096.0);
        builder.pop();
        SPEC = builder.build();
    }

    private CreateConfig() {}

    /**
     * A setting's name in the settings screen (its tooltip is this with ".tooltip"), as NookworksConfig names its own:
     * without one NeoForge's screen looks for "nookworks.configuration." and the setting's bare name, leaving its section
     * out, and showed these as raw keys with no tooltip (seen in the game, 2026-11-17).
     */
    private static String key(String path) {
        return "nookworks.configuration." + path;
    }

    /** A setting's value, or its default before the world's settings are loaded (a client's tooltip, a test). */
    static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
