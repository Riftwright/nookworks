package dev.nookworks.furniture;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wooden bench: a long seat with no back, as high as a stool, that joins the benches beside it into one (offered
 * beside the stool on 2026-10-11, made at the user's word on 2026-10-22: 「作って」; "a long seat that joins its
 * neighbours, as the desks do"). Each block seats one rider, as a stool does, facing the way the bench faces.
 * <p>
 * It faces whoever sets it down, as a chair does, and runs across that way: {@link #LEFT} and {@link #RIGHT} say it is
 * joined to the bench on that side, to someone in front of it - any wood, either way round, so long as it runs the same
 * way. Where it is joined, that end's legs go and the rail under the seat runs on, so a row of benches is one long seat
 * on legs at its two ends (tools/textures/gen_bench.py; the blockstate leaves the legs out).
 * <p>
 * Set down beside a bench, it takes that bench's facing, so a row put down from any side runs on (the user, 2026-10-23:
 * one set down at a slant had turned across its neighbour and stood apart); sneaking, it faces whoever sets it down.
 * Two benches side by side are kept apart by a Wrench sneak-used on the end between them ({@link #LEFT_APART},
 * {@link #RIGHT_APART}; WrenchItem), and joined again the same way.
 */
public class BenchBlock extends StoolBlock {
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    /** Kept apart from the bench on that side (a Wrench, sneaking): set on both, cleared when that bench goes. */
    public static final BooleanProperty LEFT_APART = BooleanProperty.create("left_apart");
    public static final BooleanProperty RIGHT_APART = BooleanProperty.create("right_apart");

    /** Drawn for a bench facing north, whose left (to someone in front of it, looking south) is east. */
    private static final VoxelShape SEAT = Block.box(0.0, SEAT_TOP - 2, 3.0, 16.0, SEAT_TOP, 13.0);
    private static final VoxelShape LEFT_LEGS = Shapes.or(Block.box(13.0, 0.0, 4.0, 15.0, SEAT_TOP - 2, 6.0),
            Block.box(13.0, 0.0, 10.0, 15.0, SEAT_TOP - 2, 12.0));
    private static final VoxelShape RIGHT_LEGS = Shapes.or(Block.box(1.0, 0.0, 4.0, 3.0, SEAT_TOP - 2, 6.0),
            Block.box(1.0, 0.0, 10.0, 3.0, SEAT_TOP - 2, 12.0));
    /** What stops a player: the seat's whole box, as a stool's is, so a bench is hopped over and not walked under. */
    private static final Map<Direction, VoxelShape> COLLISION = Shapes.rotateHorizontal(Block.box(0.0, 0.0, 3.0, 16.0, SEAT_TOP, 13.0));
    /** The outline, by facing and by which sides are joined (left 1, right 2): the seat, and the legs at each free end. */
    private static final Map<Direction, VoxelShape[]> OUTLINES = new EnumMap<>(Direction.class);

    static {
        VoxelShape[] north = new VoxelShape[4];
        for (int joined = 0; joined < 4; joined++) {
            VoxelShape shape = SEAT;
            if ((joined & 1) == 0) {
                shape = Shapes.or(shape, LEFT_LEGS);
            }
            if ((joined & 2) == 0) {
                shape = Shapes.or(shape, RIGHT_LEGS);
            }
            north[joined] = shape;
        }
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] turned = new VoxelShape[4];
            for (int joined = 0; joined < 4; joined++) {
                turned[joined] = Shapes.rotateHorizontal(north[joined]).get(facing);
            }
            OUTLINES.put(facing, turned);
        }
    }

    public BenchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LEFT, false).setValue(RIGHT, false)
                .setValue(LEFT_APART, false).setValue(RIGHT_APART, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEFT, RIGHT, LEFT_APART, RIGHT_APART);
    }

    /** The joined property for {@code side} of a bench facing {@code facing} (a side along its run). */
    static BooleanProperty joinedOn(Direction facing, Direction side) {
        return side == leftOf(facing) ? LEFT : RIGHT;
    }

    /** The kept-apart property for {@code side} of a bench facing {@code facing} (a side along its run). */
    static BooleanProperty apartOn(Direction facing, Direction side) {
        return side == leftOf(facing) ? LEFT_APART : RIGHT_APART;
    }

    /** The side a bench facing {@code facing} has on its left, to someone in front of it. */
    public static Direction leftOf(Direction facing) {
        return facing.getClockWise();
    }

    /** Whether {@code next}, on a side of a bench facing {@code facing}, is a bench it joins: one running the same way. */
    private static boolean joins(BlockState next, Direction facing) {
        return next.getBlock() instanceof BenchBlock && next.getValue(FACING).getAxis() == facing.getAxis();
    }

    /** Its sides joined to the benches beside it, but where it is kept apart; kept apart only from a bench still there. */
    static BlockState joined(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        for (Direction side : new Direction[] {leftOf(facing), leftOf(facing).getOpposite()}) {
            boolean bench = joins(level.getBlockState(pos.relative(side)), facing);
            boolean apart = bench && state.getValue(apartOn(facing, side));
            state = state.setValue(apartOn(facing, side), apart).setValue(joinedOn(facing, side), bench && !apart);
        }
        return state;
    }

    /**
     * Facing as a bench beside it does, so it runs on from it - the one it was set against first, else one facing as
     * the player would have it; sneaking, as the player faces (a corner seat, a bench set across another's end).
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null || context.isSecondaryUseActive()) {
            return state == null ? null : joined(state, context.getLevel(), context.getClickedPos());
        }
        Direction own = state.getValue(FACING);
        Direction taken = null;
        Direction against = context.getClickedFace().getOpposite();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState next = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            if (next.getBlock() instanceof BenchBlock && next.getValue(FACING).getAxis() != side.getAxis()) {
                Direction theirs = next.getValue(FACING);
                if (side == against || taken == null || theirs == own && taken != own) {
                    taken = theirs;
                }
                if (side == against) {
                    break;
                }
            }
        }
        return joined(taken == null ? state : state.setValue(FACING, taken), context.getLevel(), context.getClickedPos());
    }

    /** Joined to a bench set down beside it, or let go of one taken away; standing water flows on, as from a stool. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        state = super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        Direction facing = state.getValue(FACING);
        if (directionToNeighbour.getAxis().isHorizontal() && directionToNeighbour.getAxis() != facing.getAxis()) {
            boolean bench = joins(neighbourState, facing);
            boolean apart = bench && state.getValue(apartOn(facing, directionToNeighbour));
            state = state.setValue(apartOn(facing, directionToNeighbour), apart).setValue(joinedOn(facing, directionToNeighbour), bench && !apart);
        }
        return state;
    }

    /** The end of the bench nearer {@code hit}, if a bench it joins (or is kept apart from) stands there; else null. */
    public static @Nullable Direction apartSide(LevelReader level, BlockPos pos, BlockState state, Vec3 hit) {
        Direction facing = state.getValue(FACING);
        Direction left = leftOf(facing);
        double along = (hit.x - pos.getX() - 0.5) * left.getStepX() + (hit.z - pos.getZ() - 0.5) * left.getStepZ();
        Direction side = along >= 0 ? left : left.getOpposite();
        return joins(level.getBlockState(pos.relative(side)), facing) ? side : null;
    }

    /** The bench on {@code side} kept apart from this one, or joined to it again - on both; whether they are apart now. */
    public static boolean toggleApart(Level level, BlockPos pos, BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        BlockPos nextPos = pos.relative(side);
        BlockState next = level.getBlockState(nextPos);
        boolean apart = !state.getValue(apartOn(facing, side));
        level.setBlock(pos, joined(state.setValue(apartOn(facing, side), apart), level, pos), Block.UPDATE_ALL);
        level.setBlock(nextPos, joined(next.setValue(apartOn(next.getValue(FACING), side.getOpposite()), apart), level, nextPos), Block.UPDATE_ALL);
        return apart;
    }

    /** Mirrored with a structure, its left is its right: the bench that was on one side is on the other. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? turned : turned.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT))
                .setValue(LEFT_APART, state.getValue(RIGHT_APART)).setValue(RIGHT_APART, state.getValue(LEFT_APART));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.get(state.getValue(FACING))[(state.getValue(LEFT) ? 1 : 0) | (state.getValue(RIGHT) ? 2 : 0)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION.get(state.getValue(FACING));
    }
}
