package dev.nookworks.client;

import net.minecraft.world.item.Items;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModTrading;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.compat.jei.CjkLines;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
 * <p>
 * Only while Shift is held, as nookTechnology's (the user, 2026-11-26: 「クリエイティブモードのアイテム取り出す時の画面も、説明が
 * かなり邪魔。説明はShiftで出す」); otherwise one dim line says so (「Shiftで表示 みたいな一行は流石に要るでしょ」). Each hint is
 * broken into lines no wider than vanilla's own tooltips ({@link #WIDTH}): a long one made the tooltip too wide for the
 * room right of the pointer, and it jumped to the left and back from item to item (「今はポインタの右に出たり左に出たりで、
 * 飛びまくるからすごく読みにくい」).
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
            new Hint(ModItems.MACHINE_BELL, "machine_bell"),
            new Hint(dev.nookworks.registry.ModCooking.KITCHEN_TAP_ITEM, "kitchen_tap"),
            new Hint(dev.nookworks.registry.ModRope.WIND_CHIME, "wind_chime"),
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
            new Hint(ModItems.REINFORCED_BUILDING_WAND, "reinforced_building_wand"),
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

    /** How wide a hint's lines run: vanilla's {@code Tooltip.create}. */
    public static final int WIDTH = 170;
    public static final String SHIFT_KEY = "tooltip.nookworks.hint.shift";

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        String key = hintKey(event.getItemStack());
        if (key == null) {
            return;
        }
        List<Component> lines = event.getToolTip();
        int at = Math.min(1, lines.size());
        if (event.getFlags().shouldDisplayAllInformation() || event.getEntity() == null) {
            // read for a search, not shown: the creative search builds its index with no player and no Shift held (on a
            // thread of its own: nothing measured here), a recipe viewer asks for everything - and a hint's own words
            // found nothing, only "Shift" (r23 self-review)
            lines.add(at, Component.translatable(key).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!Minecraft.getInstance().hasShiftDown()) {
            lines.add(at, Component.translatable(SHIFT_KEY).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Font font = Minecraft.getInstance().font;
        for (String line : wrap(Component.translatable(key).getString(), font::width)) {
            lines.add(at++, Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Whether {@code stack} has a hint of ours (a vanilla lodestone or campfire among them): {@link TooltipRoom}'s too. */
    public static boolean hinted(net.minecraft.world.item.ItemStack stack) {
        return hintKey(stack) != null;
    }

    /** The hint's lang key for {@code stack}, or null for a thing with none. */
    private static @org.jspecify.annotations.Nullable String hintKey(net.minecraft.world.item.ItemStack stack) {
        // a campfire item while campfires are set down unlit (r20: nothing in the game said why a new one is out)
        if (dev.nookworks.feature.UnlitCampfires.unlit(stack)) {
            return "tooltip.nookworks.hint.unlit_campfire";
        }
        for (Hint hint : HINTS) {
            if (stack.is(hint.item().get())) {
                return key(hint);
            }
        }
        return null;
    }

    /**
     * {@code text} in lines no wider than {@link #WIDTH} as {@code measure} measures them: Japanese and Chinese between
     * words ({@link CjkLines}), the rest at spaces (Korean too, which spaces its words); a word longer than a line keeps
     * a line of its own.
     */
    public static List<String> wrap(String text, ToIntFunction<String> measure) {
        if (CjkLines.needed(text)) {
            return List.of(CjkLines.wrap(text, WIDTH, measure).split("\n"));
        }
        List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (!line.isEmpty() && measure.applyAsInt(line + " " + word) > WIDTH) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(line.isEmpty() ? "" : " ").append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static String key(Hint hint) {
        return "tooltip.nookworks.hint." + hint.key();
    }

    /** Every hint's lang key, for the test that checks each is written. */
    public static List<String> keys() {
        return Stream.concat(HINTS.stream().map(HintTooltips::key), Stream.of("tooltip.nookworks.hint.unlit_campfire", SHIFT_KEY)).toList();
    }

    private HintTooltips() {}
}
