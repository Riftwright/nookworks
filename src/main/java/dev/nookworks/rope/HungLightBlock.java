package dev.nookworks.rope;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The light a Filament Bulb or glow berries hung on a Thin Rope give the air they hang in: unseen, nothing to bump into
 * or break, and anything set there takes its place. It is ours rather than vanilla's light block so that it never
 * outlives what gives it (the user, 2026-10-01): it goes with the mod, and now and then it looks whether a lit thing
 * still hangs in its block, and goes if none does - one lost while its light stayed, sent to another dimension, its save
 * damaged. Vanilla's stayed for good in all those cases.
 */
public class HungLightBlock extends Block {
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;
    /** How often it looks whether anything still lights it: every five seconds. */
    private static final int LOOK_EVERY = 100;

    public HungLightBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 15));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    /** The light at {@code level}. */
    public BlockState at(int level) {
        return this.defaultBlockState().setValue(LEVEL, level);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        if (!level.isClientSide() && !old.is(this)) {
            level.scheduleTick(pos, this, LOOK_EVERY);
        }
    }

    /** Nothing lit hangs here any more: it goes. The hung things there not yet loaded (they load after blocks), it waits. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.areEntitiesLoaded(ChunkPos.pack(pos)) || HungItemEntity.lightsAt(level, pos)) {
            level.scheduleTick(pos, this, LOOK_EVERY);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
