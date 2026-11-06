package dev.nookworks.client;

import net.minecraft.world.item.Items;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModTrading;
import dev.nookworks.registry.ModTravel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * A line saying how to use the things whose use is not plain from their look - where the block goes, what a click
 * does - under their name, in grey, as vanilla's smithing templates have. The whole story is in JEI; this is the
 * reminder at the moment it is wanted.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class HintTooltips {
    private record Hint(Supplier<? extends Item> item, String key) {}

    private static final List<Hint> HINTS = withUnlitCopperLanterns(List.of(
            new Hint(ModItems.ELEVATOR, "elevator"),
            new Hint(ModItems.ITEM_WARP_SENDER, "item_warp_sender"),
            new Hint(ModItems.ITEM_WARP_RECEIVER, "item_warp_receiver"),
            new Hint(ModItems.HORIZONTAL_ITEM_WARP_SENDER, "horizontal_item_warp_sender"),
            new Hint(ModItems.HORIZONTAL_ITEM_WARP_RECEIVER, "horizontal_item_warp_receiver"),
            new Hint(ModItems.HUB_STONE, "hub_stone"),
            new Hint(ModItems.DICE, "dice"),
            new Hint(ModItems.WRENCH, "wrench"),
            new Hint(ModItems.NOOK_DOWSING_ROD, "nook_dowsing_rod"),
            new Hint(ModItems.WOOL_SHEARS, "wool_shears"),
            new Hint(ModItems.CAMERA, "camera"),
            new Hint(ModTrading.TRADING_COUNTER_ITEM, "trading_counter"),
            new Hint(ModTravel.DAEMON_DOOR_ITEM, "daemon_door"),
            new Hint(ModTravel.DAEMON_PANEL_ITEM, "destination_panel"),
            new Hint(ModTravel.IF_MIRROR_ITEM, "if_mirror"),
            new Hint(ModBarter.BARTER_TERMINAL_ITEM, "barter_terminal"),
            new Hint(ModItems.SPAWNER_LINK_SENDER, "spawner_link_sender"),
            new Hint(ModItems.SPAWNER_LINK_RECEIVER, "spawner_link_receiver"),
            new Hint(ModItems.SPAWNER_CONTROLLER, "spawner_controller"),
            new Hint(() -> Items.LODESTONE, "lodestone"),
            new Hint(ModItems.BUILDING_WAND, "building_wand"),
            new Hint(ModItems.LIQUID_EXPERIENCE_BOTTLE, "liquid_experience_bottle"),
            new Hint(ModItems.ABSORPTION_PAD, "absorption_pad"),
            new Hint(ModItems.NESTED_TIMER, "nested_timer"),
            new Hint(ModItems.SIFTER, "sifter"),
            new Hint(ModRope.HOOK_ITEM, "hook"),
            new Hint(ModRope.THIN_CHAIN, "thin_chain"),
            new Hint(ModItems.UNLIT_LANTERN, "unlit_lantern"),
            new Hint(ModItems.UNLIT_SOUL_LANTERN, "unlit_lantern"),
            new Hint(ModOil.DRIVE_WHEEL_ITEM, "drive_wheel"),
            new Hint(ModOil.OIL_CAKE, "oil_cake"),
            new Hint(ModItems.TRASH_CAN, "trash_can"),
            new Hint(ModItems.CREATIVE_GENERATOR, "creative_generator")));

    private static List<Hint> withUnlitCopperLanterns(List<Hint> hints) {
        return Stream.concat(hints.stream(), ModItems.UNLIT_COPPER_LANTERNS.values().stream()
                .map(lantern -> new Hint(lantern, "unlit_lantern"))).toList();
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        // a campfire item while campfires are set down unlit (r20: nothing in the game said why a new one is out)
        if (dev.nookworks.feature.UnlitCampfires.unlit(event.getItemStack())) {
            event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                    Component.translatable("tooltip.nookworks.hint.unlit_campfire").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (Hint hint : HINTS) {
            if (event.getItemStack().is(hint.item().get())) {
                event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                        Component.translatable(key(hint)).withStyle(ChatFormatting.GRAY));
                return;
            }
        }
    }

    private static String key(Hint hint) {
        return "tooltip.nookworks.hint." + hint.key();
    }

    /** Every hint's lang key, for the test that checks each is written. */
    public static List<String> keys() {
        return Stream.concat(HINTS.stream().map(HintTooltips::key), Stream.of("tooltip.nookworks.hint.unlit_campfire")).toList();
    }

    private HintTooltips() {}
}
