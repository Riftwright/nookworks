package dev.nookworks.poster;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Hangs a {@link NookItemFrame} as vanilla's item frame hangs its own (HangingEntityItem#useOn): on any face of a block,
 * the item's stored entity data applied, placed only where it survives. Used on a poster on a wall it frames it instead
 * ({@link PosterEntity#interact}: an entity is clicked before the block behind it).
 */
public class NookItemFrameItem extends Item {
    private final Supplier<? extends EntityType<NookItemFrame>> frame;

    public NookItemFrameItem(Supplier<? extends EntityType<NookItemFrame>> frame, Properties properties) {
        super(properties);
        this.frame = frame;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos at = clicked.relative(face);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        // as ItemFrameItem#mayPlace: any face, inside the build height, where the player may use it
        if (player != null && !(player.level().isInsideBuildHeight(at) && player.mayUseItemAt(at, face, stack))) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        NookItemFrame entity = new NookItemFrame(this.frame.get(), level, at, face);
        EntityType.<NookItemFrame>createDefaultStackConfig(level, stack, player).apply(entity);
        if (!entity.survives()) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            entity.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
            level.addFreshEntity(entity);
        }
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
