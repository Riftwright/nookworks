package dev.nookworks.wood;

import java.util.function.Supplier;

import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A wooden slab with its boards turned to run across ({@link TurnedPlanks}): the slab in every way but the look. It
 * drops and is picked as the slab; and the slab's item set into a turned half makes a turned double, as it would make a
 * double of a plain one - the slab's own placing knows only its own block, so this does it.
 */
public class TurnedSlabBlock extends SlabBlock {
    private final Supplier<? extends Block> plain;

    public TurnedSlabBlock(Supplier<? extends Block> plain, Properties properties) {
        super(properties);
        this.plain = plain;
    }

    /** The slab this is, turned. */
    public Block plain() {
        return plain.get();
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(plain.get());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        SlabType type = state.getValue(TYPE);
        if (type == SlabType.DOUBLE || stack.isEmpty() || !stack.is(plain.get().asItem())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        // the faces a slab of its own kind is set into its other half from (SlabBlock#canBeReplaced)
        boolean above = hit.getLocation().y - pos.getY() > 0.5;
        Direction face = hit.getDirection();
        boolean into = type == SlabType.BOTTOM ? face == Direction.UP || above && face.getAxis().isHorizontal()
                : face == Direction.DOWN || !above && face.getAxis().isHorizontal();
        if (!into || !player.mayBuild() || !player.mayUseItemAt(pos, face, stack)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        BlockState doubled = state.setValue(TYPE, SlabType.DOUBLE).setValue(WATERLOGGED, false);
        if (level instanceof ServerLevel server) {
            if (!Permission.mayPlace(server, pos, face, player, doubled)) {
                return InteractionResult.FAIL;
            }
            level.setBlock(pos, doubled, Block.UPDATE_ALL);
            SoundType sound = doubled.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, doubled));
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
