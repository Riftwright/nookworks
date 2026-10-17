package dev.nookworks.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block only a player in creative mode sets down (the Creative Generator): anyone else - in survival or adventure, or
 * a machine placing it with no player - is refused, with a word in the action bar. Vanilla's command block is placed
 * the same way ({@code GameMasterBlockItem}), but asks for an operator as well: a command block runs commands, while
 * this only makes power, and an operator check would refuse it in a creative world made without cheats, the item in the
 * creative tab and not to be placed. On a server only an operator gives creative mode, so it stays theirs to hand out.
 */
public class CreativeOnlyBlockItem extends BlockItem {
    public CreativeOnlyBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Whether {@code player} may set one down: in creative mode. */
    public static boolean mayPlace(@Nullable Player player) {
        return player != null && player.hasInfiniteMaterials();
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (!mayPlace(player)) {
            if (player != null && !context.getLevel().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.creative_only"));
            }
            return null;
        }
        return super.getPlacementState(context);
    }
}
