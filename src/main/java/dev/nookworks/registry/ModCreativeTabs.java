package dev.nookworks.registry;

import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import dev.nookworks.label.LabelItem;
import dev.nookworks.mixin.CreativeModeTabAccessor;
import dev.nookworks.poster.PosterItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Four tabs, by what a player comes for: building and furnishing a home, the kitchen and the fields, the workshop -
 * tools, machines, pipes and what they make - and the posters and labels the Printing Press prints (there are many).
 * Within a tab, things go in groups as a player looks for them; a new item goes into its group, not onto the end.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Nookworks.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUILDING = CREATIVE_MODE_TABS.register("building",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nookworks.building"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModWoods.CHAIR_ITEMS.get("oak").get().getDefaultInstance())
                    .displayItems(Features.shown((parameters, output) -> {
                        // the Dawnwood set, supports, ropes, loose ground
                        ModWoods.DAWNWOOD_ITEMS.forEach(item -> output.accept(item.get()));
                        output.accept(ModWoods.DAWNWOOD_BOAT.get());
                        output.accept(ModWoods.DAWNWOOD_CHEST_BOAT.get());
                        ModWoods.SUPPORT_ITEMS.values().forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.ROPE.get());
                        output.accept(ModItems.THIN_ROPE.get());
                        output.accept(ModRope.THIN_CHAIN.get());
                        output.accept(ModRope.HOOK_ITEM.get());
                        output.accept(ModRope.FILAMENT_BULB_ITEM.get());
                        output.accept(ModItems.DEEPSLATE_GRAVEL.get());
                        output.accept(ModItems.NETHER_GRIT.get());
                        output.accept(ModItems.END_GRAVEL.get());
                        output.accept(ModItems.STONY_DIRT.get());
                        output.accept(ModItems.PEBBLES.get());
                        // furniture, light, pots, the odd thing for the table
                        ModWoods.CHAIR_ITEMS.forEach((wood, chair) -> {
                            output.accept(chair.get());
                            output.accept(ModWoods.STOOL_ITEMS.get(wood).get());
                            output.accept(ModWoods.DESK_ITEMS.get(wood).get());
                            output.accept(ModWoods.WALL_SHELF_ITEMS.get(wood).get());
                        });
                        output.accept(ModItems.SCONCE.get());
                        output.accept(ModItems.OIL_LAMP.get());
                        output.accept(ModItems.BOOKEND.get());
                        output.accept(ModItems.UNLIT_LANTERN.get());
                        output.accept(ModItems.UNLIT_SOUL_LANTERN.get());
                        ModItems.UNLIT_COPPER_LANTERNS.values().forEach(lantern -> output.accept(lantern.get()));
                        output.accept(ModItems.DARK_MATTER.get());
                        output.accept(GlazedPots.ITEM.get());
                        output.accept(GlazedPots.HANGING_GREEN_ITEM.get());
                        output.accept(GlazedPots.HANGING_PINK_ITEM.get());
                        output.accept(ModItems.CRATE.get());
                        output.accept(ModItems.IRON_WORKBENCH.get());
                        output.accept(ModItems.BRASS_WORKBENCH.get());
                        output.accept(ModItems.ELEVATOR.get());
                        output.accept(ModItems.DICE.get());
                        output.accept(ModItems.FIREPROOF_COATING.get());
                    }))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> KITCHEN = CREATIVE_MODE_TABS.register("kitchen",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nookworks.kitchen"))
                    .withTabsBefore(BUILDING.key())
                    // a bowl of rice, or with the kitchen switched off (features), what the tab still holds
                    .icon(() -> (Features.enabled("kitchen") ? ModCooking.COOKED_RICE.get() : ModOil.OIL_PRESS_ITEM.get())
                            .getDefaultInstance())
                    .displayItems(Features.shown((parameters, output) -> {
                        // seeds and soil, and what the fields want
                        output.accept(ModWoods.RARE_SEED.get());
                        output.accept(ModCrops.BROWN_RICE.get());
                        output.accept(ModCrops.WHITE_RICE.get());
                        output.accept(ModCrops.RICE_BRAN.get());
                        output.accept(ModKitchen.RICE_BALE_ITEM.get());
                        output.accept(ModKitchen.CORN_KERNELS.get());
                        output.accept(ModKitchen.CORN.get());
                        output.accept(ModKitchen.ONION.get());
                        output.accept(ModOil.SUNFLOWER_SEEDS.get());
                        output.accept(ModCrops.END_SEED.get());
                        output.accept(ModItems.FERTILE_SOIL.get());
                        output.accept(ModOil.OIL_CAKE.get());
                        output.accept(ModOil.WOOD_VINEGAR.get());
                        // the hand-worked machines of the kitchen, its stove and its air
                        output.accept(ModCrops.RICE_POLISHER_ITEM.get());
                        output.accept(ModKitchen.HAND_QUERN_ITEM.get());
                        output.accept(ModKitchen.NOODLE_MAKER_ITEM.get());
                        output.accept(ModOil.OIL_PRESS_ITEM.get());
                        output.accept(ModCooking.GRILL_ITEM.get());
                        output.accept(ModCooking.IRON_POT_ITEM.get());
                        output.accept(ModCooking.NUKADOKO_ITEM.get());
                        output.accept(ModKitchen.WOODEN_TRAY_ITEM.get());
                        output.accept(ModVentilation.RANGE_HOOD_ITEM.get());
                        output.accept(ModVentilation.VENT_FAN_ITEM.get());
                        output.accept(ModVentilation.DUCT_ITEM.get());
                        output.accept(ModVentilation.CHIMNEY_ITEM.get());
                        // what the kitchen takes in and makes
                        output.accept(ModOil.VEGETABLE_OIL_BOTTLE.get());
                        output.accept(ModOil.VEGETABLE_OIL_BUCKET.get());
                        output.accept(ModCooking.FLOUR.get());
                        output.accept(ModCooking.BREADCRUMBS.get());
                        output.accept(ModCooking.BREADED_PORK.get());
                        output.accept(ModKitchen.BREAD_DOUGH.get());
                        output.accept(ModKitchen.RISEN_DOUGH.get());
                        output.accept(ModKitchen.UDON_DOUGH.get());
                        output.accept(ModKitchen.RAW_UDON.get());
                        output.accept(ModKitchen.PANCAKE_BATTER.get());
                        output.accept(ModCooking.COOKED_RICE.get());
                        output.accept(ModCooking.SALT_ONIGIRI.get());
                        output.accept(ModCooking.SALMON_ONIGIRI.get());
                        output.accept(ModCooking.VEGETABLE_SOUP.get());
                        output.accept(ModCooking.FRIED_RICE.get());
                        output.accept(ModCooking.STIR_FRIED_VEGETABLES.get());
                        output.accept(ModCooking.FRENCH_FRIES.get());
                        output.accept(ModCooking.KARAAGE.get());
                        output.accept(ModCooking.FRIED_FISH.get());
                        output.accept(ModCooking.TONKATSU.get());
                        output.accept(ModRope.DRIED_FISH.get());
                        output.accept(ModKitchen.UDON.get());
                        output.accept(ModKitchen.CREAM_STEW.get());
                        output.accept(ModKitchen.CORN_POTAGE.get());
                        output.accept(ModKitchen.GRILLED_CORN.get());
                        output.accept(ModKitchen.DONUT.get());
                        output.accept(ModKitchen.PANCAKE.get());
                        output.accept(ModCooking.FRIED_EGG.get());
                        output.accept(ModCooking.BOILED_EGG.get());
                        output.accept(ModCooking.TAMAGOYAKI.get());
                        output.accept(ModCooking.CARROT_NUKAZUKE.get());
                        output.accept(ModCooking.BEETROOT_NUKAZUKE.get());
                        output.accept(ModOil.SOAP.get());
                    }))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WORKSHOP = CREATIVE_MODE_TABS.register("workshop",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nookworks.workshop"))
                    .withTabsBefore(KITCHEN.key())
                    .icon(() -> ModItems.GENERATOR.get().getDefaultInstance())
                    .displayItems(Features.shown((parameters, output) -> {
                        // tools and gear
                        output.accept(ModItems.STONE_HAMMER.get());
                        output.accept(ModItems.COPPER_HAMMER.get());
                        output.accept(ModItems.IRON_HAMMER.get());
                        output.accept(ModItems.GOLDEN_HAMMER.get());
                        output.accept(ModItems.DIAMOND_HAMMER.get());
                        output.accept(ModItems.NETHERITE_HAMMER.get());
                        output.accept(ModItems.VELARIUM_SWORD.get());
                        output.accept(ModItems.VELARIUM_PICKAXE.get());
                        output.accept(ModItems.VELARIUM_AXE.get());
                        output.accept(ModItems.VELARIUM_SHOVEL.get());
                        output.accept(ModItems.VELARIUM_HOE.get());
                        output.accept(ModItems.VELARIUM_HAMMER.get());
                        output.accept(ModItems.VELARIUM_HELMET.get());
                        output.accept(ModItems.VELARIUM_CHESTPLATE.get());
                        output.accept(ModItems.VELARIUM_LEGGINGS.get());
                        output.accept(ModItems.VELARIUM_BOOTS.get());
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModItems.BUILDING_WAND.get());
                        output.accept(ModItems.MAGNET.get());
                        output.accept(ModItems.ADVANCED_MAGNET.get());
                        output.accept(ModOil.OIL_CAN.get());
                        output.accept(ModItems.MOB_BOTTLE.get());
                        output.accept(ModItems.LODE_PEARL.get());
                        output.accept(ModItems.NOOK_DOWSING_ROD.get());
                        output.accept(ModItems.WOOL_SHEARS.get());
                        output.accept(ModItems.CAMERA.get());
                        // machines and power
                        output.accept(ModItems.MACHINE_CHASSIS.get());
                        output.accept(ModItems.GENERATOR.get());
                        output.accept(ModItems.DIESEL_GENERATOR.get());
                        output.accept(ModItems.CREATIVE_GENERATOR.get()); // creative mode's: no recipe, placed in creative only
                        output.accept(ModItems.ENERGY_CABLE.get());
                        output.accept(ModOil.MOTOR_ITEM.get());
                        output.accept(ModOil.DRIVE_WHEEL_ITEM.get());
                        output.accept(ModItems.CRUSHER.get());
                        output.accept(ModRecycling.COMPRESSOR_ITEM.get());
                        output.accept(ModRecycling.BALER.get());
                        output.accept(ModRecycling.RECYCLER_ITEM.get());
                        output.accept(ModRecycling.SCRAP.get());
                        ModRecycling.BUILDING_ITEMS.values().forEach(item -> output.accept(item.get()));
                        output.accept(ModRecycling.PATCHED_SHEET_METAL_ITEM.get());
                        output.accept(ModItems.SIFTER.get());
                        output.accept(ModItems.BRASS_MESH.get());
                        output.accept(ModItems.STONE_MAKER.get());
                        output.accept(ModItems.ELECTRIC_FURNACE.get());
                        output.accept(ModItems.PLANTER.get());
                        output.accept(ModItems.LASER.get());
                        output.accept(ModItems.WATER_PUMP.get());
                        output.accept(ModItems.RETORT.get());
                        output.accept(ModItems.STIRRING_TANK.get());
                        output.accept(ModItems.ELECTROLYTIC_CELL.get());
                        output.accept(ModItems.DISTILLER.get());
                        output.accept(ModItems.DISTILLATION_COLUMN.get());
                        output.accept(ModItems.CONDENSER.get());
                        output.accept(ModItems.PRINTING_PRESS.get());
                        output.accept(ModBarter.BARTER_TERMINAL_ITEM.get());
                        output.accept(ModTrading.TRADING_COUNTER_ITEM.get());
                        output.accept(ModItems.SPAWN_EGG_BREWER.get());
                        output.accept(ModItems.REPAIR_BOX.get());
                        output.accept(ModItems.TRASH_CAN.get());
                        output.accept(ModItems.SPAWNER_LINK_SENDER.get());
                        output.accept(ModItems.SPAWNER_LINK_RECEIVER.get());
                        output.accept(ModItems.SPAWNER_CONTROLLER.get());
                        // pipes, tanks, moving things and keeping them, redstone
                        output.accept(ModItems.COPPER_PIPE.get());
                        output.accept(ModItems.SIGHT_GLASS.get());
                        output.accept(ModItems.VALVE.get());
                        output.accept(ModItems.COPPER_TANK.get());
                        output.accept(ModItems.MERCURY_PIPE.get());
                        output.accept(ModItems.MERCURY_TANK.get());
                        output.accept(ModItems.EXPERIENCE_PIPE.get());
                        output.accept(ModItems.EXPERIENCE_TANK.get());
                        output.accept(ModItems.ABSORPTION_PAD.get());
                        output.accept(ModItems.ITEM_SORTER.get());
                        output.accept(ModItems.PRECISION_SORTER.get());
                        output.accept(ModItems.GOLD_SORTER_GEAR.get());
                        output.accept(ModItems.DIAMOND_SORTER_GEAR.get());
                        output.accept(ModItems.NETHERITE_SORTER_GEAR.get());
                        output.accept(ModItems.VELARIUM_SORTER_GEAR.get());
                        output.accept(ModItems.ITEM_WARP_SENDER.get());
                        output.accept(ModItems.ITEM_WARP_RECEIVER.get());
                        output.accept(ModItems.LINE_WARP_SENDER.get());
                        output.accept(ModItems.LINE_WARP_RECEIVER.get());
                        output.accept(ModTravel.DAEMON_DOOR_ITEM.get());
                        output.accept(ModTravel.DAEMON_PANEL_ITEM.get());
                        output.accept(ModTravel.IF_MIRROR_ITEM.get());
                        output.accept(ModItems.IRON_STORAGE.get());
                        output.accept(ModItems.GOLD_STORAGE.get());
                        output.accept(ModItems.DIAMOND_STORAGE.get());
                        output.accept(ModItems.NETHERITE_STORAGE.get());
                        output.accept(ModItems.VELARIUM_STORAGE.get());
                        output.accept(ModItems.IRON_STORAGE_UPGRADE.get());
                        output.accept(ModItems.GOLD_STORAGE_UPGRADE.get());
                        output.accept(ModItems.DIAMOND_STORAGE_UPGRADE.get());
                        output.accept(ModItems.NETHERITE_STORAGE_UPGRADE.get());
                        output.accept(ModItems.VELARIUM_STORAGE_UPGRADE.get());
                        output.accept(ModItems.MAGNET_CHEST.get());
                        output.accept(ModItems.HUB_STONE.get());
                        output.accept(ModItems.NESTED_TIMER.get());
                        // materials and liquids
                        output.accept(ModItems.RAW_ZINC.get());
                        output.accept(ModItems.ZINC_INGOT.get());
                        output.accept(ModItems.BRASS_INGOT.get());
                        output.accept(ModItems.VELARIUM_NUGGET.get());
                        output.accept(ModItems.VELARIUM_INGOT.get());
                        output.accept(ModItems.RED_HOT_VELARIUM_INGOT.get());
                        output.accept(ModItems.TEMPERED_VELARIUM_INGOT.get());
                        output.accept(ModItems.REINFORCED_ROD.get());
                        output.accept(ModItems.VELARIUM_BLOCK.get());
                        output.accept(ModItems.SULFUR_DUST.get());
                        output.accept(ModItems.IRON_AMALGAM.get());
                        output.accept(ModItems.GOLD_AMALGAM.get());
                        output.accept(ModItems.COPPER_AMALGAM.get());
                        output.accept(ModItems.TRACE_AMALGAM.get());
                        output.accept(ModItems.CRUDE_SALT.get());
                        output.accept(ModItems.SALT.get());
                        output.accept(ModItems.CAUSTIC_SODA.get());
                        output.accept(ModItems.WOOD_CHIPS.get());
                        output.accept(ModItems.WOOD_PULP.get());
                        output.accept(ModItems.FINE_PAPER.get());
                        output.accept(ModItems.CHARRED_LUMP.get());
                        output.accept(ModOil.METHANOL.get());
                        output.accept(ModOil.BIODIESEL_BOTTLE.get());
                        output.accept(ModOil.BIODIESEL_BUCKET.get());
                        output.accept(ModItems.MERCURY_BUCKET.get());
                        output.accept(ModItems.EXPERIENCE_BUCKET.get());
                        output.accept(ModItems.LIQUID_EXPERIENCE_BOTTLE.get());
                    }))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> POSTERS = CREATIVE_MODE_TABS.register("posters",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nookworks.posters"))
                    .withTabsBefore(WORKSHOP.key())
                    .icon(() -> {
                        ItemStack poster = new ItemStack(ModItems.POSTER.get());
                        poster.set(ModDataComponents.POSTER.get(), Nookworks.id("safety_cross"));
                        return poster;
                    })
                    .displayItems(Features.shown((parameters, output) -> {
                        output.accept(LabelItem.sample());
                        PosterItem.allDesigns(parameters.holders()).forEach(output::accept);
                    }))
                    .build());

    /** Every tab of ours, in the order the game shows them. */
    public static List<DeferredHolder<CreativeModeTab, CreativeModeTab>> all() {
        return List.of(BUILDING, KITCHEN, WORKSHOP, POSTERS);
    }

    /**
     * The parts of the mod a world has are known (on joining it, {@link dev.nookworks.feature.WorldFeatures}): the
     * Kitchen tab picks its icon again, as it was drawn for the last world. The tabs' contents are built again anyway,
     * for each world's registries.
     */
    public static void partsKnown() {
        ((CreativeModeTabAccessor) KITCHEN.get()).nookworks$setIconItemStack(null);
    }

    private ModCreativeTabs() {}
}
