package dev.nookworks.photo;

import java.util.List;
import java.util.function.Consumer;

import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResultHolder;
import dev.nookworks.port.core.ItemUse;

/**
 * The Camera: held up to the eye as a spyglass is (use held down), it shows a viewfinder; a click takes what is in it,
 * which the player's own game crops and shrinks and sends ({@link Photos#take}). It keeps its last {@link Photos#KEPT}
 * photos, and with it in the player's inventory the Printing Press offers them to print as posters. No film, no
 * developing: a camera of brass and glass as a map is of paper, and how it works is no one's business.
 */
public class CameraItem extends Item {
    public CameraItem(Properties properties) {
        super(properties);
    }

    /** The numbers of the photos {@code camera} keeps, oldest first. */
    public static List<Integer> photos(ItemStack camera) {
        return camera.getOrDefault(ModDataComponents.CAMERA_PHOTOS.get(), List.of());
    }

    /** Raised to the eye, heard as a spyglass is (by the player here at once, and by others from the server). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.playSound(ModSounds.CAMERA_RAISE.get(), 1.0F, 1.0F);
        return ItemUse.holder(ItemUtils.startUsingInstantly(level, player, hand), player, hand);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        entity.playSound(ModSounds.CAMERA_LOWER.get(), 1.0F, 1.0F); // (26.3 then answers true: 1.21.1's returns nothing)
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000; // up for as long as the button is held
    }

    /** Up to the eye, as a spyglass: others see it held there. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipLines, TooltipFlag flag) {
        Consumer<Component> tooltip = tooltipLines::add;
        tooltip.accept(Component.translatable("item.nookworks.camera.photos", photos(stack).size(), Photos.KEPT).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipLines, flag);
    }
}
