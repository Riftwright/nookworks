package dev.nookworks.anchor;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Warp Anchor (ワープアンカー; the user, 2026-10-31): Dark Matter, obsidian and amethyst. While on, nothing warps
 * into or out of the 9x5x9 round it - endermen and shulkers, ender pearls, Lode Pearls, chorus fruit ({@link WarpAnchors});
 * it is for keeping endermen out of a spawner room, where they teleport in, set gravel down and break the build, and a
 * shulker spawner becomes a trap. Elevators, the Dock-a-Daemon Door and Item Warps go on as before. A click turns it on
 * and off. It looks it: a floating cube in ender pearl colours with a ring round it like Saturn's, turning while it is
 * on and still and dull while it is off ({@code WarpAnchorRenderer}); the block's own model is only its particle.
 */
public class WarpAnchorBlock extends BaseEntityBlock {
    public static final BooleanProperty ON = BooleanProperty.create("on");
    /** What a player bumps into and clicks: the ring's square, from just over the floor to over the cube. */
    private static final VoxelShape SHAPE = Block.box(1, 3, 1, 15, 13, 15);
    /** Its glow while on: faint, as a lantern's is not. */
    public static final int LIGHT = 7;

    public WarpAnchorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ON, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ON);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockState next = state.cycle(ON);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, next.getValue(ON) ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.5F, 1.6F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarpAnchorBlockEntity(pos, state);
    }
}
