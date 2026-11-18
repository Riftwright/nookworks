package dev.nookworks.cooking;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import dev.nookworks.port.core.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/**
 * Electric Stove (電気コンロ; trial, the user, 2026-11-14: 家に焚火置いたら、なんか火事になりそうで見た目怖いもんね): a
 * kitchen range for a house, with no fire in it. An Iron Pot or a Frying Pan stands on it and cooks while it has power
 * ({@link ElectricStoveBlockEntity}); its ring glows ({@link #LIT}) only while it heats, which is what
 * {@code #nookworks:heats_pots} asks of a block with a "lit" state. It takes power as a machine does, from any side.
 * There is nothing to open: a pot on it is looked at as a pot anywhere.
 */
public class ElectricStoveBlock extends BaseEntityBlock implements RemovalHooks {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The glow of a hot ring: enough to see the pot by, not a lamp. */
    public static final int LIGHT_LEVEL = 5;

    public ElectricStoveBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricStoveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ELECTRIC_STOVE.get(), ElectricStoveBlockEntity::serverTick);
    }

    // PORT: 1.21.1 calls only onRemove where 26.3 calls the old block entity's preRemoveSideEffects and this
    // block's affectNeighborsAfterRemoval: BlockRemoval runs them in 26.3's order
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockRemoval.onRemove(this, state, level, pos, newState, movedByPiston,
                () -> super.onRemove(state, level, pos, newState, movedByPiston));
    }

    // PORT: 1.21.1 asks every block for a codec (26.3 has none); only the block-list data report reads it
    @Override
    protected MapCodec<? extends ElectricStoveBlock> codec() {
        return BlockCodecs.of(this);
    }
}
