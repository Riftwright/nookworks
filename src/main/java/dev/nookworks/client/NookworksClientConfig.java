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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static String key(String path) {
        return Nookworks.MODID + ".configuration.inventory." + path;
    }

    private NookworksClientConfig() {}
}
