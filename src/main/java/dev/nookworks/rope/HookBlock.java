package dev.nookworks.rope;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hook: a small iron hook screwed into a wall or under a ceiling, with nothing to walk into. Thin Rope ties to it as to
 * a fence post ({@link RopeSpans}: a line across a room, wall to wall), and berries hang on it ({@link #SPRIG}):
 * <ul>
 * <li>sweet berries - a sprig of the bush drooping down the wall or from the ceiling, that pricks whatever moves
 * through it as the bush does: a spider climbing the wall past it is hurt, and slowed;
 * <li>glow berries - strands of the cave vine with their berries alight, a lamp for a wall or a beam, as the vine is
 * one in a cave ({@link #GLOW_LIGHT}).
 * </ul>
 * An empty hand takes the sprig back off.
 * <p>
 * {@link #FACING} is the way a wall hook points, out from its wall; a ceiling hook's bend is turned that way too.
 */
public class HookBlock extends FaceAttachedHorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Sprig> SPRIG = EnumProperty.create("sprig", Sprig.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** A sprig of glow berries lights as the cave vine bearing them does. */
    public static final int GLOW_LIGHT = 14;

    /** The hook itself, to aim at: under a ceiling, and on a wall (drawn pointing north, its wall to the south). */
    private static final VoxelShape CEILING = Block.box(6, 9, 6, 10, 16, 10);
    private static final Map<Direction, VoxelShape> WALL = Shapes.rotateHorizontal(Block.box(6, 7, 11, 10, 12, 16));
    /** And with a sprig on it, down to where the sprig reaches: what the sweet berries prick. */
    private static final VoxelShape CEILING_SPRIG = Block.box(3, 0, 3, 13, 16, 13);
    /** Stood on a floor - only by a command: a placed hook goes on a wall or a ceiling - it is the ceiling's upside down. */
    private static final VoxelShape FLOOR = Block.box(6, 0, 6, 10, 7, 10);
    private static final VoxelShape FLOOR_SPRIG = Block.box(3, 0, 3, 13, 16, 13);
    private static final Map<Direction, VoxelShape> WALL_SPRIG = Shapes.rotateHorizontal(Block.box(2, 0, 10, 14, 13, 16));

    /** What hangs on a hook, besides a rope tied to it. */
    public enum Sprig implements StringRepresentable {
        NONE("none", Items.AIR, SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES),
        SWEET_BERRIES("sweet_berries", Items.SWEET_BERRIES, SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES),
        GLOW_BERRIES("glow_berries", Items.GLOW_BERRIES, SoundEvents.CAVE_VINES_PLACE, SoundEvents.CAVE_VINES_PICK_BERRIES);

        private final String name;
        private final Item item;
        private final SoundEvent hung;
        private final SoundEvent taken;

        Sprig(String name, Item item, SoundEvent hung, SoundEvent taken) {
            this.name = name;
            this.item = item;
            this.hung = hung;
            this.taken = taken;
        }

        /** The sprig {@code stack} hangs on a hook as, or NONE when it is not one. */
        public static Sprig of(ItemStack stack) {
            return stack.is(Items.SWEET_BERRIES) ? SWEET_BERRIES : stack.is(Items.GLOW_BERRIES) ? GLOW_BERRIES : NONE;
        }

        /** The berries it is, given back when it is taken off. */
        public Item item() {
            return this.item;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public HookBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH)
                .setValue(SPRIG, Sprig.NONE).setValue(WATERLOGGED, false));
    }

    /** The light of a hook as it is: a sprig of glow berries is a lamp. */
    public static int light(BlockState state) {
        return state.getValue(SPRIG) == Sprig.GLOW_BERRIES ? GLOW_LIGHT : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, SPRIG, WATERLOGGED);
    }

    /** Where a rope is tied on the hook at {@code pos}: the middle of its knot, round the foot of the upright. */
    public static Vec3 eye(BlockState state, BlockPos pos) {
        // The knot, KNOT_HEIGHT high, wound round the foot of the hook's upright and lying on the iron across under it
        // (the user, 2026-10-02: tied round the end of the shank, it looked threaded through the hook). Under a
        // ceiling round the shank, on the bend (its top at 11 pixels); on a wall round the point, on the shank (its
        // top at 9.5), the point 4.5 to 5.5 pixels out from the middle towards the wall. A tenth of a pixel sunk in,
        // so no face of the knot lies on one of the iron's.
        if (state.getValue(FACE) == AttachFace.CEILING) {
            return new Vec3(pos.getX() + 0.5, pos.getY() + (10.9 + KNOT_HEIGHT / 2) / 16, pos.getZ() + 0.5);
        }
        if (state.getValue(FACE) == AttachFace.FLOOR) { // only by a command: the ceiling's hook, stood on end
            return new Vec3(pos.getX() + 0.5, pos.getY() + (5.1 - KNOT_HEIGHT / 2) / 16, pos.getZ() + 0.5);
        }
        Direction wall = state.getValue(FACING).getOpposite();
        return new Vec3(pos.getX() + 0.5 + wall.getStepX() * 5.0 / 16, pos.getY() + (9.4 + KNOT_HEIGHT / 2) / 16,
                pos.getZ() + 0.5 + wall.getStepZ() * 5.0 / 16);
    }

    /**
     * The knot of a rope on a hook, in pixels: as high as the cord is thick, and {@code 2 * KNOT_HALF} across - a little
     * wider than the iron it is wound round, and clear of the point beside the shank under a ceiling.
     */
    public static final double KNOT_HALF = 0.9;
    public static final double KNOT_HEIGHT = 1.0;

    /** On a wall or under a ceiling: a hook stood on the floor would only trip someone. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction == Direction.DOWN) {
                continue;
            }
            BlockState state = direction == Direction.UP
                    ? this.defaultBlockState().setValue(FACE, AttachFace.CEILING).setValue(FACING, context.getHorizontalDirection().getOpposite())
                    : this.defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, direction.getOpposite());
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state.setValue(WATERLOGGED, water);
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean sprig = state.getValue(SPRIG) != Sprig.NONE;
        if (state.getValue(FACE) == AttachFace.CEILING) {
            return sprig ? CEILING_SPRIG : CEILING;
        }
        if (state.getValue(FACE) == AttachFace.FLOOR) {
            return sprig ? FLOOR_SPRIG : FLOOR;
        }
        return (sprig ? WALL_SPRIG : WALL).get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Sweet berries prick only what is in the sprig, not the whole block round it; glow berries prick nothing. */
    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return state.getValue(SPRIG) == Sprig.SWEET_BERRIES ? this.getShape(state, level, pos, CollisionContext.empty()) : Shapes.empty();
    }

    /**
     * A sprig of sweet berries does what the bush does: it slows what pushes through it, and pricks it if it moves -
     * up and down as well as across, so a spider climbing the wall is hurt as well as a mob walking into it. Foxes and
     * bees go through berry bushes unharmed, and through this.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        if (state.getValue(SPRIG) != Sprig.SWEET_BERRIES || !(entity instanceof LivingEntity) || entity.is(EntityTypes.FOX)
                || entity.is(EntityTypes.BEE)) {
            return;
        }
        entity.makeStuckInBlock(state, new Vec3(0.8F, 0.75, 0.8F));
        if (level instanceof ServerLevel server) {
            Vec3 moved = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());
            if (Math.abs(moved.x) >= 0.003 || Math.abs(moved.y) >= 0.003 || Math.abs(moved.z) >= 0.003) {
                entity.hurtServer(server, level.damageSources().sweetBerryBush(), 1.0F);
            }
        }
    }

    /** Berries hang on an empty hook; Thin Rope is tied by the rope itself (ThinRopeItem). */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        Sprig sprig = Sprig.of(stack);
        if (sprig == Sprig.NONE || state.getValue(SPRIG) != Sprig.NONE) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(SPRIG, sprig), Block.UPDATE_ALL);
            stack.consume(1, player);
            level.playSound(null, pos, sprig.hung, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** An empty hand takes the sprig back off, and the hook is bare again. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Sprig sprig = state.getValue(SPRIG);
        if (sprig == Sprig.NONE || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // something in hand is used as it would be anywhere
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(SPRIG, Sprig.NONE), Block.UPDATE_ALL);
            Block.popResource(level, pos, new ItemStack(sprig.item()));
            level.playSound(null, pos, sprig.taken, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
