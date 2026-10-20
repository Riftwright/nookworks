package dev.nookworks.wood;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wooden support: a squared timber in one of four shapes.
 * <ul>
 * <li>post - an upright, for holding things up from the floor</li>
 * <li>beam - a timber along the top of the block, for a shelf or a floor to rest on</li>
 * <li>bracket - a wall bracket: an upright against the wall, the beam over it and a brace between them</li>
 * <li>wall post - the bracket's upright alone, standing against the wall (the user's sketch, 2026-10-18)</li>
 * </ul>
 * Placed against the side of a block it is a bracket standing out from that wall; on a floor or a ceiling it is a
 * post. Use an axe on it to go through the shapes: post, beam, bracket, wall post. FACING is the way the beam points out from the wall. Thin Rope ties
 * round a bracket's brace, as round a fence post ({@link #brace}).
 */
public class SupportBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

    public enum Shape implements StringRepresentable {
        POST, BEAM, BRACKET, WALL_POST;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public Shape next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private static final Map<Direction, Map<Shape, VoxelShape>> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<Shape, VoxelShape> byShape = new EnumMap<>(Shape.class);
            byShape.put(Shape.POST, box(facing, 5, 0, 5, 11, 16, 11));
            byShape.put(Shape.BEAM, box(facing, 5, 11, 0, 11, 16, 16));
            byShape.put(Shape.BRACKET, Shapes.or(box(facing, 5, 0, 12, 11, 11, 16), box(facing, 5, 11, 0, 11, 16, 16),
                    box(facing, 6, 4, 6, 10, 11, 12)));
            byShape.put(Shape.WALL_POST, box(facing, 5, 0, 12, 11, 16, 16));
            SHAPES.put(facing, byShape);
        }
    }

    /**
     * The middle of a bracket's brace, in pixels, as if it faced north: the brace is a timber 4 pixels wide and 3 thick,
     * turned 45 degrees about this point (tools/gen_wood_data.py), rising from the upright to the beam.
     */
    private static final double BRACE_X = 8;
    private static final double BRACE_Y = 7;
    private static final double BRACE_Z = 9;
    /** How steep the brace is: 45 degrees, up towards the way the beam points. */
    public static final float BRACE_ANGLE = 45;
    /** The brace's timber, in pixels: across it, and through it the other way. */
    public static final double BRACE_WIDTH = 4;
    public static final double BRACE_THICKNESS = 3;

    /** A bracket has a brace, which Thin Rope ties round (RopeSpans); a post, a beam or a wall post has none. */
    public static boolean braced(BlockState state) {
        return state.getBlock() instanceof SupportBlock && state.getValue(SHAPE) == Shape.BRACKET;
    }

    /** Where on the bracket {@code state} at {@code pos} a rope is tied: round the middle of its brace. */
    public static Vec3 brace(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        double x = switch (facing) {
            case SOUTH -> 16 - BRACE_X;
            case EAST -> 16 - BRACE_Z;
            case WEST -> BRACE_Z;
            default -> BRACE_X;
        };
        double z = switch (facing) {
            case SOUTH -> 16 - BRACE_Z;
            case EAST -> BRACE_X;
            case WEST -> 16 - BRACE_X;
            default -> BRACE_Z;
        };
        return new Vec3(pos.getX() + x / 16, pos.getY() + BRACE_Y / 16, pos.getZ() + z / 16);
    }

    /** A box given as if the support faced north, turned to face {@code facing}. */
    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public SupportBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHAPE, Shape.POST));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            return this.defaultBlockState().setValue(FACING, face).setValue(SHAPE, Shape.BRACKET);
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ItemTags.AXES) || !player.getAbilities().mayBuild) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(SHAPE, state.getValue(SHAPE).next()), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 0.8F, 1.3F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING)).get(state.getValue(SHAPE));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** A post, a beam at head height, a bracket or a wall post: no way through, as a fence post is not. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
