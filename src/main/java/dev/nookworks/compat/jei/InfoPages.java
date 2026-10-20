package dev.nookworks.compat.jei;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import dev.nookworks.NookworksConfig;
import dev.nookworks.feature.Features;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.registry.GlazedPots;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRecycling;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModTrading;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.registry.ModVentilation;
import dev.nookworks.registry.ModWoods;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The information pages JEI shows on our things, and on the few of vanilla's our features change. They are kept
 * apart from the plugin, which needs JEI, so a test can read them and see every page is written in both languages.
 */
public final class InfoPages {
    /** Every page: the item it is shown on, and its text, worded for the parts of the mod that are on. */
    public static void each(BiConsumer<Item, Component> page) {
        each(page, Features::enabled);
    }

    /**
     * Every page, worded for the parts {@code on} says are on (by name, the parts they need counted). A page that names
     * a thing of a part switched off - Soap's Iron Pot, the Electric Furnace's Experience Pipe - reads as its
     * {@code .without_<part>} text instead, and the Spawner's, about nothing but the hammers, is left out with them. The
     * plugin drops the pages on a switched-off part's own items. Public for tests.
     */
    public static void each(BiConsumer<Item, Component> page, Predicate<String> on) {
        info(page, ModItems.IRON_WORKBENCH.get(), "workbench");
        info(page, ModItems.BRASS_WORKBENCH.get(), "workbench");
        info(page, ModItems.LIQUID_EXPERIENCE_BOTTLE.get(), "liquid_experience_bottle");
        info(page, ModItems.ELECTROLYTIC_CELL.get(), "electrolytic_cell");
        info(page, ModItems.MERCURY_BUCKET.get(), "mercury");
        // where the whole mercury chain starts, which nothing else in the game says: the sulfur caves
        info(page, Items.CINNABAR, "cinnabar");
        info(page, Items.SULFUR, "cinnabar");
        info(page, ModItems.NOOK_DOWSING_ROD.get(), "nook_dowsing_rod");
        info(page, ModItems.WOOL_SHEARS.get(), "wool_shears");
        info(page, ModItems.CAMERA.get(), "camera");
        info(page, ModItems.MERCURY_TANK.get(), "mercury");
        info(page, ModItems.MERCURY_PIPE.get(), "mercury");
        info(page, ModItems.DISTILLER.get(), "distiller");
        info(page, ModItems.DISTILLATION_COLUMN.get(), "distiller");
        info(page, ModItems.CONDENSER.get(), "distiller");
        info(page, ModItems.VELARIUM_NUGGET.get(), "velarium_nugget");
        info(page, ModItems.RED_HOT_VELARIUM_INGOT.get(), "red_hot_velarium_ingot");
        info(page, ModItems.TEMPERED_VELARIUM_INGOT.get(), "tempered_velarium_ingot");
        info(page, ModItems.REINFORCED_ROD.get(), "reinforced_rod");
        info(page, ModItems.BRASS_INGOT.get(), "brass");
        info(page, ModItems.MACHINE_CHASSIS.get(), "machine_chassis");
        info(page, ModRecycling.COMPRESSOR_ITEM.get(), "compressor");
        info(page, ModRecycling.RECYCLER_ITEM.get(), "recycler");
        info(page, ModRecycling.BALER.get(), "baler");
        info(page, ModRecycling.SCRAP.get(), "scrap");
        ModRecycling.BUILDING_ITEMS.forEach((name, item) -> {
            if (ModRecycling.SCRAP_BALES.containsKey(name) || ModRecycling.CORRUGATED_SHEETS.containsKey(name)) {
                info(page, item.get(), "rusting_scrap");
            }
            if (name.endsWith("corrugated_sheet")) {
                info(page, item.get(), "corrugated_sheet");
            }
        });
        info(page, ModItems.CAUSTIC_SODA.get(), "caustic_soda");
        info(page, ModItems.FINE_PAPER.get(), "fine_paper");
        info(page, ModItems.PRINTING_PRESS.get(), "printing_press");
        info(page, ModItems.LABEL.get(), "label");
        info(page, ModItems.EXPERIENCE_BUCKET.get(), "liquid_experience");
        info(page, ModItems.EXPERIENCE_TANK.get(), "liquid_experience");
        info(page, ModItems.EXPERIENCE_TANK.get(), "experience_tank");
        info(page, ModItems.EXPERIENCE_PIPE.get(), "liquid_experience");
        info(page, ModItems.EXPERIENCE_PIPE.get(), "experience_pipe");
        info(page, ModItems.ABSORPTION_PAD.get(), "absorption_pad");
        info(page, ModItems.REPAIR_BOX.get(), "repair_box");
        info(page, ModItems.TRASH_CAN.get(), "trash_can");
        info(page, ModItems.WRENCH.get(), "wrench");
        info(page, ModItems.FIREPROOF_COATING.get(), "fireproof_coating");
        info(page, ModItems.CRATE.get(), "crate");
        info(page, ModItems.MAGNET.get(), "magnet");
        info(page, ModItems.ADVANCED_MAGNET.get(), "magnet");
        for (var hammer : List.of(ModItems.STONE_HAMMER, ModItems.COPPER_HAMMER, ModItems.IRON_HAMMER, ModItems.GOLDEN_HAMMER,
                ModItems.DIAMOND_HAMMER, ModItems.NETHERITE_HAMMER, ModItems.VELARIUM_HAMMER)) {
            info(page, hammer.get(), "hammer");
        }
        info(page, ModItems.ENERGY_CABLE.get(), "energy_cable");
        info(page, ModItems.BUILDING_WAND.get(), "building_wand");
        info(page, ModItems.ITEM_SORTER.get(), "item_sorter");
        info(page, ModItems.PRECISION_SORTER.get(), "precision_sorter");
        for (var gear : List.of(ModItems.GOLD_SORTER_GEAR, ModItems.DIAMOND_SORTER_GEAR, ModItems.NETHERITE_SORTER_GEAR, ModItems.VELARIUM_SORTER_GEAR)) {
            info(page, gear.get(), "sorter_gear");
        }
        // The page names the hand-breaking limit, which is a server setting: JEI may be reading it before the
        // server's copy has arrived, and then the default is the best there is.
        int breakLimit;
        try {
            breakLimit = NookworksConfig.STORAGE_BREAK_LIMIT.getAsInt();
        } catch (IllegalStateException notLoaded) {
            breakLimit = NookworksConfig.STORAGE_BREAK_LIMIT.getDefault();
        }
        for (Item barrel : List.of(ModItems.IRON_STORAGE.get(), ModItems.GOLD_STORAGE.get(), ModItems.DIAMOND_STORAGE.get(),
                ModItems.NETHERITE_STORAGE.get(), ModItems.VELARIUM_STORAGE.get())) {
            page.accept(barrel, Component.translatable("jei.nookworks.info.storage_barrel", breakLimit));
        }

        info(page, ModItems.STONE_MAKER.get(), "stone_maker");
        info(page, ModItems.ELECTRIC_FURNACE.get(), "electric_furnace", on, "liquid_experience");
        info(page, ModItems.GENERATOR.get(), "generator");
        info(page, ModItems.DIESEL_GENERATOR.get(), "diesel_generator");
        info(page, ModItems.CREATIVE_GENERATOR.get(), "creative_generator");
        info(page, ModItems.MOB_BOTTLE.get(), "mob_bottle");
        info(page, ModBarter.BARTER_TERMINAL_ITEM.get(), "barter_terminal");
        info(page, ModTrading.TRADING_COUNTER_ITEM.get(), "trading_counter");
        info(page, ModItems.SPAWNER_LINK_SENDER.get(), "spawner_control");
        info(page, ModItems.SPAWNER_LINK_RECEIVER.get(), "spawner_control");
        info(page, ModItems.SPAWNER_CONTROLLER.get(), "spawner_control");
        if (on.test("hammers")) {
            info(page, Items.SPAWNER, "spawner_pickup"); // a Silk Touch hammer's, and nothing else picks a spawner up
        }
        info(page, ModItems.ELEVATOR.get(), "elevator");
        info(page, ModItems.ITEM_WARP_SENDER.get(), "item_warp");
        info(page, ModItems.ITEM_WARP_RECEIVER.get(), "item_warp");
        info(page, ModItems.LINE_WARP_SENDER.get(), "line_warp");
        info(page, ModItems.LINE_WARP_RECEIVER.get(), "line_warp");
        info(page, ModItems.MAGNET_CHEST.get(), "magnet_chest");
        info(page, ModItems.FERTILE_SOIL.get(), "nourished_soil");
        info(page, ModItems.LASER.get(), "laser");
        info(page, ModItems.PLANTER.get(), "planter");
        info(page, ModItems.DICE.get(), "dice");
        info(page, ModItems.PEBBLES.get(), "pebbles"); // that they lie four to a place is not plain from the item
        info(page, GlazedPots.HANGING_GREEN_ITEM.get(), "hanging_planter");
        info(page, GlazedPots.HANGING_PINK_ITEM.get(), "hanging_planter");
        info(page, ModItems.ROPE.get(), "rope");
        info(page, ModItems.THIN_ROPE.get(), "thin_rope");
        info(page, ModRope.THIN_CHAIN.get(), "thin_chain");
        info(page, ModRope.FILAMENT_BULB_ITEM.get(), "filament_bulb");
        info(page, ModRope.HOOK_ITEM.get(), "hook");
        info(page, ModRope.DRIED_FISH.get(), "dried_fish");
        info(page, ModItems.WATER_PUMP.get(), "water_pump");
        info(page, ModItems.NESTED_TIMER.get(), "nested_timer");
        info(page, ModCrops.BROWN_RICE.get(), "rice");
        info(page, ModCrops.RICE_POLISHER_ITEM.get(), "rice_polisher");
        info(page, ModKitchen.HAND_QUERN_ITEM.get(), "hand_quern");
        info(page, ModKitchen.NOODLE_MAKER_ITEM.get(), "noodle_maker");
        info(page, ModKitchen.BREAD_DOUGH.get(), "bread_dough");
        info(page, ModKitchen.WOODEN_TRAY_ITEM.get(), "wooden_tray");
        info(page, ModKitchen.RICE_BALE_ITEM.get(), "rice_bale");
        info(page, ModKitchen.CORN_KERNELS.get(), "corn_kernels");
        info(page, ModKitchen.CORN.get(), "corn");
        info(page, ModKitchen.ONION.get(), "onion");
        info(page, ModVentilation.VENT_FAN_ITEM.get(), "ventilation_fan", on, "barter_terminal");
        info(page, ModVentilation.RANGE_HOOD_ITEM.get(), "range_hood");
        info(page, ModVentilation.DUCT_ITEM.get(), "duct", on, "barter_terminal");
        info(page, ModVentilation.CHIMNEY_ITEM.get(), "chimney");
        info(page, ModItems.IRON_STORAGE_UPGRADE.get(), "storage_upgrade");
        info(page, ModItems.GOLD_STORAGE_UPGRADE.get(), "storage_upgrade");
        info(page, ModItems.DIAMOND_STORAGE_UPGRADE.get(), "storage_upgrade");
        info(page, ModItems.NETHERITE_STORAGE_UPGRADE.get(), "storage_upgrade");
        info(page, ModItems.VELARIUM_STORAGE_UPGRADE.get(), "storage_upgrade");
        info(page, ModCrops.RICE_BRAN.get(), "rice_bran");
        info(page, ModCooking.GRILL_ITEM.get(), "grill");
        info(page, ModCooking.IRON_POT_ITEM.get(), "iron_pot");
        info(page, ModCooking.NUKADOKO_ITEM.get(), "nukadoko");
        info(page, ModItems.COPPER_PIPE.get(), "copper_pipe");
        info(page, ModItems.SIGHT_GLASS.get(), "sight_glass");
        info(page, ModItems.VALVE.get(), "valve");
        info(page, ModItems.COPPER_TANK.get(), "copper_tank");
        info(page, ModItems.OIL_LAMP.get(), "oil_lamp");
        info(page, ModItems.BOOKEND.get(), "bookend");
        // books are laid down sneaking, which nothing in the game says (the bottles have their page): the r18 review
        info(page, Items.BOOK, "placed_book");
        info(page, Items.ENCHANTED_BOOK, "placed_book");
        info(page, Items.WRITTEN_BOOK, "placed_book");
        info(page, Items.WRITABLE_BOOK, "placed_book");
        info(page, ModItems.UNLIT_LANTERN.get(), "unlit_lantern");
        info(page, ModItems.UNLIT_SOUL_LANTERN.get(), "unlit_lantern");
        ModItems.UNLIT_COPPER_LANTERNS.values().forEach(lantern -> info(page, lantern.get(), "unlit_lantern"));
        info(page, Items.GLASS_BOTTLE, "placed_bottle");
        info(page, Items.HONEY_BOTTLE, "placed_bottle");
        info(page, ModCooking.FLOUR.get(), "flour");
        info(page, ModCooking.BREADCRUMBS.get(), "breadcrumbs");
        info(page, ModCooking.BREADED_PORK.get(), "breaded_pork");
        info(page, ModOil.OIL_PRESS_ITEM.get(), "oil_press", on, "kitchen");
        info(page, ModOil.MOTOR_ITEM.get(), "motor");
        info(page, ModOil.DRIVE_WHEEL_ITEM.get(), "drive_wheel");
        info(page, ModOil.SUNFLOWER_SEEDS.get(), "sunflower_seeds");
        info(page, ModCrops.END_SEED.get(), "end_seed");
        info(page, ModOil.OIL_CAKE.get(), "oil_cake");
        info(page, ModOil.OIL_CAN.get(), "oil_can");
        info(page, ModOil.SOAP.get(), "soap", on, "kitchen"); // without the pot, made in the crafting grid
        info(page, ModOil.WOOD_VINEGAR.get(), "wood_vinegar");
        info(page, ModOil.METHANOL.get(), "methanol", on, "kitchen");
        info(page, ModOil.BIODIESEL_BOTTLE.get(), "biodiesel_bottle", on, "kitchen");
        info(page, ModItems.RETORT.get(), "retort_experience", on, "liquid_experience");
        info(page, ModItems.SIFTER.get(), "sifter");
        info(page, ModItems.BRASS_MESH.get(), "brass_mesh");
        info(page, ModItems.LODE_PEARL.get(), "lode_pearl");
        info(page, ModItems.DARK_MATTER.get(), "dark_matter");
        info(page, ModTravel.DAEMON_DOOR_ITEM.get(), "daemon_door");
        info(page, ModTravel.DAEMON_PANEL_ITEM.get(), "daemon_door");
        info(page, ModTravel.IF_MIRROR_ITEM.get(), "if_mirror");
        info(page, ModItems.CHARRED_LUMP.get(), "charred_lump");
        info(page, Items.VINE, "vine");
        info(page, ModWoods.RARE_SEED.get(), "rare_seed");
        ModWoods.SUPPORT_ITEMS.values().forEach(support -> info(page, support.get(), "support"));
        ModWoods.CHAIR_ITEMS.values().forEach(chair -> info(page, chair.get(), "chair"));
        ModWoods.STOOL_ITEMS.values().forEach(stool -> info(page, stool.get(), "stool"));
        ModWoods.WALL_SHELF_ITEMS.values().forEach(shelf -> info(page, shelf.get(), "wall_shelf"));
    }

    private static void info(BiConsumer<Item, Component> page, Item item, String key) {
        info(page, item, key, key);
    }

    /** The page {@code key}, which names a thing of {@code part}: with that part off, in its words for that. */
    private static void info(BiConsumer<Item, Component> page, Item item, String key, Predicate<String> on, String part) {
        info(page, item, key, on.test(part) ? key : key + ".without_" + part);
    }

    /** The page {@code key}, in the words of {@code wording} (its own, or those for a part switched off). */
    private static void info(BiConsumer<Item, Component> page, Item item, String key, String wording) {
        Component text = Component.translatable("jei.nookworks.info." + wording);
        if (TUNED_PAGES.contains(key) && MachineTuning.tuned()) {
            // the page's figures are the defaults: a server that sets them otherwise says by how much
            text = text.copy().append("\n\n").append(Component.translatable("jei.nookworks.info.tuned",
                    factor(NookworksConfig.MACHINE_ENERGY), factor(NookworksConfig.MACHINE_TIME),
                    factor(NookworksConfig.GENERATOR_OUTPUT)));
        }
        page.accept(item, text);
    }

    /** The pages that give a machine's power or pace in figures. */
    private static final Set<String> TUNED_PAGES = Set.of("stone_maker", "generator", "diesel_generator", "laser", "planter",
            "electric_furnace", "electrolytic_cell", "water_pump", "motor", "spawner_control", "compressor", "recycler");

    private static String factor(ModConfigSpec.DoubleValue value) {
        double f = MachineTuning.factor(value);
        return f == Math.rint(f) ? Integer.toString((int) f) : Double.toString(f);
    }

    private InfoPages() {}
}
