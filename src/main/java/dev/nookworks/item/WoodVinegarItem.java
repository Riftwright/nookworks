package dev.nookworks.item;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.WoodVinegar;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A bottle of wood vinegar. Used on a field - the farmland, or a crop standing on it - it is sprinkled over the 5x5
 * square about it ({@link WoodVinegar}); the bottle comes back empty. It still goes into the Vacuum Distiller as before.
 */
public class WoodVinegarItem extends Item {
    public WoodVinegarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clicked = level.getBlockState(pos);
        if (!WoodVinegar.takes(clicked)) {
            // a crop was clicked: the field is under it - under its stalk, for the top of a tall one (corn two blocks
            // high is clicked at eye height, on its top)
            boolean top = clicked.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && clicked.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            pos = pos.below(top ? 2 : 1);
            if (!WoodVinegar.takes(level.getBlockState(pos))) {
                return InteractionResult.PASS;
            }
        }
        Player player = context.getPlayer();
        if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
            return InteractionResult.PASS; // someone else's claim, spawn protection, adventure mode
        }
        if (level instanceof ServerLevel server) {
            int treated = WoodVinegar.sprinkle(server, pos, p -> player == null || level.mayInteract(player, p));
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 0.9F);
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
            if (player != null) {
                ItemStack stack = context.getItemInHand();
                player.setItemInHand(context.getHand(), ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                player.sendOverlayMessage(Component.translatable("message.nookworks.wood_vinegar.sprinkled", treated));
            }
            if (player instanceof ServerPlayer sprinkler && treated > 0) {
                // the advancement is the mod's to award: any click with the bottle on a block (loading a Distiller)
                // counted while vanilla's item_used_on_block gave it
                AdvancementHolder field = server.getServer().getAdvancements().get(Nookworks.id("wood_vinegar"));
                if (field != null) {
                    sprinkler.getAdvancements().award(field, "vinegar");
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
