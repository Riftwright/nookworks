package dev.nookworks.mercury;

import dev.nookworks.fluid.PipePorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The Condenser, the top of the Vacuum Distiller's tower: a block with no work of its own. A pipe of ours joined to its
 * side - nookTechnology draws benzene from one topping its Tar Still - shows a port there, a flange with a neck back to
 * the finned body, which stands two or three pixels in from the block's edge (the user, 2026-11-10, through
 * nookTechnology's session: 「パイプと冷却器の間に隙間ができる。見た目的に漏れそうで心配になる」). Only on its four sides: its
 * hood reaches the top edge and its flange the bottom one, so a pipe there meets it already.
 * <p>
 * It keeps no block entity, so a Condenser saved before it had ports gets them from the pipe beside it as that loads
 * ({@link PipePorts#showBesideOnLoad}).
 */
public class CondenserBlock extends Block {
    public CondenserBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(PipePorts.none(this.stateDefinition.any()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PipePorts.add(builder, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return PipePorts.withPorts(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PipePorts.rotate(state, rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return PipePorts.mirror(state, mirror);
    }

    /** A pipe joined or taken away beside it: the port on that side shown or not. */
    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return PipePorts.updated(super.updateShape(state, side, neighbourState, level, pos, neighbourPos), side, neighbourState);
    }
}
