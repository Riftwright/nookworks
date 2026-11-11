package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.storage.SortOrder;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player client settings (not synced). */
public final class NookworksClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue WHEEL_TRANSFER = BUILDER
            .comment("Scroll over an item in a container screen to move items one at a time.")
            .translation(key("wheelTransfer")).define("wheelTransfer", true);
    public static final ModConfigSpec.BooleanValue INVERT_WHEEL = BUILDER
            .comment("Swap the scroll directions: scroll up sends, scroll down pulls.")
            .translation(key("invertWheel")).define("invertWheel", false);
    public static final ModConfigSpec.BooleanValue SHIFT_DRAG = BUILDER
            .comment("Hold Shift and drag with the left button to quick-move every slot you pass over.")
            .translation(key("shiftDrag")).define("shiftDrag", true);
    public static final ModConfigSpec.BooleanValue CHEST_SORT = BUILDER
            .comment("A Sort button on vanilla's chests, large chests, barrels, Ender Chests and shulker boxes, as on the Storage Barrels.",
                    "Not added when an inventory sorting mod (Inventory Profiles Next, Inventory Sorter, Inventory Tweaks, Quark) is installed.")
            .translation(key("chestSort")).define("chestSort", true);
    public static final ModConfigSpec.EnumValue<SortOrder> CHEST_SORT_ORDER = BUILDER
            .comment("The order that button sorts in; the button beside it changes it.")
            .translation(key("chestSortOrder")).defineEnum("chestSortOrder", SortOrder.ID, SortOrder.ID, SortOrder.COUNT, SortOrder.MOD);
    public static final ModConfigSpec.BooleanValue CHEST_SORT_REVERSED = BUILDER
            .comment("Sort the chests in that order turned round: fewest first, the game's order from its end, the mods Z-A.",
                    "The button between the order's and Sort switches it; choosing another order puts it back.")
            .translation(key("chestSortReversed")).define("chestSortReversed", false);
    public static final ModConfigSpec.BooleanValue ENGLISH_SEARCH = BUILDER
            .comment("With JEI installed and the game in a language other than English, JEI's search finds items by their English names",
                    "too (\"sword\" finds every sword), and its item list shows the English name under \"Search Aliases\".")
            .translation(key("jeiEnglishNames")).define("jeiEnglishNames", true);

    public static final ModConfigSpec.DoubleValue SQUINT_ZOOM = BUILDER
            .comment("How many times nearer the view comes while the Squint key is held. Kept gentle: the spyglass brings it ten times nearer.")
            .translation(Nookworks.MODID + ".configuration.squint.zoom").defineInRange("squintZoom", 2.0, 1.25, 4.0);
    public static final ModConfigSpec.BooleanValue SQUINT_HAND = BUILDER
            .comment("While squinting, raise the hand that holds nothing, or the off hand, to the brow: it shows at the top of the view.")
            .translation(Nookworks.MODID + ".configuration.squint.hand").define("squintHand", true);

    // the sounds of machines (the user, 2026-11-10, of the sound session's survey: the Blocks slider was the only way down)
    public static final ModConfigSpec.IntValue MACHINE_SOUND_VOLUME = BUILDER
            .comment("How loud the sounds machines make now and then as they work, in percent of how they are made (0 turns them off).",
                    "Nookworks' machines, and an add-on's that plays its work sounds the same way.")
            .translation(sound("machineVolume")).defineInRange("machineSoundVolume", 100, 0, 100);
    public static final ModConfigSpec.IntValue MACHINE_SOUND_FREQUENCY = BUILDER
            .comment("How often each machine makes its work sound, in percent of the usual: 25 is a quarter as often, 200 twice.",
                    "However many machines stand near, no more than three of their sounds start in any one second.")
            .translation(sound("machineFrequency")).defineInRange("machineSoundFrequency", 100, 25, 200);
    public static final ModConfigSpec.BooleanValue MACHINE_STOP_CUE = BUILDER
            .comment("A short sound from a machine near you that has stopped for a reason to see to (out of fuel or power, its output",
                    "blocked, a tank full...): once, after it has stood two seconds stopped.")
            .translation(sound("machineStopCue")).define("machineStopCue", true);

    // the Standing Mirror under a shader pack (the user, 2026-11-11: the pack's look in the mirror); a switch, as the
    // packs differ and not all can be tried
    public static final ModConfigSpec.BooleanValue MIRROR_PACK_LIGHT = BUILDER
            .comment("With a shader pack (Iris), a Standing Mirror's reflection is lit as the pack lights the room around it - as",
                    "dark, as warm - measured from the pack itself. Off: lit as the game lights it without shaders.",
                    "The pack's shadows, sky and water do not show in the mirror either way.")
            .translation(Nookworks.MODID + ".configuration.mirror.packLight").define("mirrorPackLight", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static String sound(String path) {
        return Nookworks.MODID + ".configuration.sounds." + path;
    }

    private static String key(String path) {
        return Nookworks.MODID + ".configuration.inventory." + path;
    }

    private NookworksClientConfig() {}
}
