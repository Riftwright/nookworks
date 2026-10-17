package dev.nookworks.ventilation;

import java.util.Map;

import dev.nookworks.block.DieselGeneratorBlock;
import dev.nookworks.block.GeneratorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Chimney: a stack of brick, a stone cap and a round clay chimney pot on the top one ({@link #TOP}: nothing of the
 * chimney over it). Smoke that reaches it - up a Range Hood, along Ducts, which join its sides with a collar - comes
 * out of the pot (see {@link Ventilation}); and a chimney built over a fire - a lit campfire, furnace, smoker or blast
 * furnace, or a running Generator - smokes of itself, as a chimney should.
 */
public class ChimneyBlock extends Block {
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final Map<Direction, BooleanProperty> SIDES = Map.of(Direction.NORTH, PipeBlock.NORTH, Direction.EAST, PipeBlock.EAST,
            Direction.SOUTH, PipeBlock.SOUTH, Direction.WEST, PipeBlock.WEST);
    private static final VoxelShape STACK = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape TOP_SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 12, 13), Block.box(2, 12, 2, 14, 13, 14),
            Block.box(5, 13, 5, 11, 16, 11));

    public ChimneyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(TOP, true);
        for (BooleanProperty side : SIDES.values()) {
            state = state.setValue(side, false);
        }
        this.registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP);
        SIDES.values().forEach(builder::add);
    }

    private BlockState shaped(LevelReader level, BlockPos pos, BlockState state) {
        state = state.setValue(TOP, !(level.getBlockState(pos.above()).getBlock() instanceof ChimneyBlock));
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            state = state.setValue(side.getValue(), level.getBlockState(pos.relative(side.getKey())).getBlock() instanceof DuctBlock);
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.shaped(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return this.shaped(level, pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TOP) ? TOP_SHAPE : STACK;
    }

    /** Whether {@code state} is a fire a chimney built over it draws smoke from. */
    public static boolean isFire(BlockState state) {
        if (state.is(BlockTags.CAMPFIRES)) {
            return CampfireBlock.isLitCampfire(state);
        }
        // a Diesel Generator's exhaust is no fire, but it goes up a chimney stood on its stack all the same
        return (state.getBlock() instanceof AbstractFurnaceBlock || state.getBlock() instanceof GeneratorBlock
                || state.getBlock() instanceof DieselGeneratorBlock) && state.getValue(BlockStateProperties.LIT);
    }

    /** The top of a chimney over a fire smokes, for as long as the fire burns (see VentilationClient). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(TOP)) {
            return;
        }
        BlockPos bottom = pos;
        for (int i = 0; i < 64 && level.getBlockState(bottom.below()).getBlock() instanceof ChimneyBlock; i++) {
            bottom = bottom.below();
        }
        if (isFire(level.getBlockState(bottom.below())) && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            Ventilation.markSmoking(level, new Vec3(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5), true, 300);
        }
    }
}
