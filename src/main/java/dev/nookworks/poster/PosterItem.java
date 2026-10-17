package dev.nookworks.poster;

import java.util.List;
import java.util.function.Consumer;

import dev.nookworks.photo.PhotoPrints;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Places a {@link PosterEntity} on the wall it is used against. Blank posters (no design) cannot be placed. */
public class PosterItem extends Item {
    public PosterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            return InteractionResult.FAIL; // a poster goes on a wall, not on the floor or ceiling
        }
        ItemStack stack = context.getItemInHand();
        Identifier variant = Posters.idOf(stack);
        Level level = context.getLevel();
        if (variant == null || Posters.get(level, variant) == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(face);
        if (player != null && !player.mayUseItemAt(pos, face, stack)) {
            return InteractionResult.FAIL;
        }
        PosterEntity poster = new PosterEntity(level, pos, face, variant);
        if (!poster.survives()) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            poster.playPlacementSound();
            server.addFreshEntity(poster);
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier variant = Posters.idOf(stack);
        if (variant == null) {
            return super.getName(stack);
        }
        if (PhotoPrints.parse(variant) != null) {
            return Component.translatable("item.nookworks.photo");
        }
        // A datapack poster gets a name key of its own; untranslated ones fall back to the plain item name.
        String key = "poster." + variant.getNamespace() + "." + variant.getPath();
        Component translated = Component.translatable(key);
        return translated.getString().equals(key) ? super.getName(stack) : translated;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
            TooltipFlag flag) {
        Identifier variant = Posters.idOf(stack);
        PhotoPrints.Print print = PhotoPrints.parse(variant);
        if (print != null) {
            tooltip.accept(Component.translatable("gui.nookworks.printing_press.size", print.size().width(), print.size().height())
                    .withStyle(ChatFormatting.GRAY));
            Component about = PhotoPrints.describe.apply(print.photo());
            if (about != null) {
                tooltip.accept(about.copy().withStyle(ChatFormatting.GRAY));
            }
        }
        if (variant != null && flag.isAdvanced()) {
            tooltip.accept(Component.literal(variant.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    /** Every known design, for the creative tab. */
    public static List<ItemStack> allDesigns(HolderLookup.Provider registries) {
        return Posters.ids(registries).stream().map(Posters::item).toList();
    }
}
