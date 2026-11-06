package dev.nookworks.decor;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.TrayBlock;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A Bookend (the user, 2026-10-19, with a photograph of one and the book drawn on it: books stand on a wall's side leant
 * back; 「こうしたくないときにブックエンド使う」): a thin L of iron on a block's top, its upright at one side of the block
 * and its foot along the floor. A book used on it stands upright against the upright, cover to it and spine out, on
 * the foot; the next beside it, five to the block. So books stand straight anywhere - on a desk, a shelf, the floor in
 * the middle of a room - where a wall's side would lean them back ({@link PlacedBooksBlock}).
 * <p>
 * The row goes on past the block (the user: 「1ブロック超えて永遠に本並べ続けることができたら面白い」): with the
 * bookend full, the next book stands in the block beyond, a Row of Books ({@link ModBlocks#BOOK_ROW}, this class
 * without the upright), five to it, and so on block after block while there is floor; a book used on any block of the
 * row goes to its end, and an empty hand takes the end's last book back. Each block of a row stands on its floor and
 * leans on the one before it: the bookend gone, or a block of the row, the books past it fall (as items).
 * <p>
 * Set down, its spines face the player and its upright goes on the side of the block they clicked (the left half of it,
 * the left; {@link #HINGE}, as seen from in front). Broken, or its floor gone, it drops itself and its books.
 * BookendRenderer draws the books (the standing books' models).
 */
public class BookendBlock extends BaseEntityBlock {
    /** Five books to a block: three pixels each beside the upright's one, or spaced a fifth of the block in a row. */
    public static final int MAX = 5;
    /** The way the spines face: towards whoever set it down. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Which side the upright is on, seen from in front (looking at the spines): the row goes on to the other side. */
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    /** How many books stand there: the row's length, for its shape. */
    public static final IntegerProperty BOOKS = IntegerProperty.create("books", 0, MAX);
    /** The upright's thickness, in pixels: the first book stands against it. */
    public static final int UPRIGHT = 1;
    /** In a Row of Books, how far apart the books stand, in pixels: five to a block, so a row runs on unbroken. */
    public static final float ROW_STEP = 16.0F / MAX;
    /** How far a row is followed, at most, to find its end: a long shelf's worth, and no endless walk. */
    private static final int LONGEST = 256;
    /** By the upright (0 a row, 1 a bookend), facing (2D data value), hinge (right or not) and count. */
    private static final VoxelShape[][][][] SHAPES = new VoxelShape[2][4][2][MAX + 1];

    static {
        for (int upright = 0; upright < 2; upright++) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (int right = 0; right < 2; right++) {
                    for (int books = 0; books <= MAX; books++) {
                        SHAPES[upright][facing.get2DDataValue()][right][books] = shape(upright == 1, facing, right == 1, books);
                    }
                }
            }
        }
    }

    private static VoxelShape shape(boolean upright, Direction facing, boolean right, int books) {
        double wide = PlacedBooksBlock.WIDE;
        if (!upright) {
            return box(facing, right, 0, 0, 8 - wide / 2, Math.max(1, ROW_STEP * books), PlacedBooksBlock.TALL, 8 + wide / 2);
        }
        VoxelShape shape = Shapes.or(box(facing, right, 0, 0, 4, UPRIGHT, 10, 12), box(facing, right, UPRIGHT, 0, 4, 8, 0.5, 12));
        if (books == 0) {
            return shape;
        }
        return Shapes.or(shape, box(facing, right, UPRIGHT, 0, 8 - wide / 2, UPRIGHT + PlacedBooksBlock.THICK * books,
                PlacedBooksBlock.TALL, 8 + wide / 2));
    }

    /**
     * A box given as for spines to the south and the upright to the west (the model's way), mirrored for an upright to
     * the east, then turned as the blockstate turns the model: south to {@code facing}.
     */
    private static VoxelShape box(Direction facing, boolean right, double x0, double y0, double z0, double x1, double y1, double z1) {
        if (right) {
            double was = x0;
            x0 = 16 - x1;
            x1 = 16 - was;
        }
        return switch (facing) {
            case NORTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            case WEST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    /** A bookend's own block (true), or a Row of Books going on from one (false). */
    private final boolean upright;

    public BookendBlock(Properties properties) {
        this(properties, true);
    }

    public BookendBlock(Properties properties, boolean upright) {
        super(properties);
        this.upright = upright;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(HINGE, DoorHingeSide.LEFT)
                .setValue(BOOKS, 0));
    }

    public boolean upright() {
        return this.upright;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HINGE, BOOKS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[this.upright ? 1 : 0][state.getValue(FACING).get2DDataValue()][state.getValue(HINGE) == DoorHingeSide.RIGHT ? 1 : 0]
                [state.getValue(BOOKS)];
    }

    /** Spines towards the player; the upright on the side of the block they clicked, as they see it. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction looking = context.getHorizontalDirection();
        Direction right = looking.getClockWise();
        Vec3 off = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
        boolean onRight = off.x * right.getStepX() + off.z * right.getStepZ() > 0;
        BlockState state = this.defaultBlockState().setValue(FACING, looking.getOpposite())
                .setValue(HINGE, onRight ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** The way the row runs: from the upright across the block, and on into the next. */
    public static Direction along(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(HINGE) == DoorHingeSide.LEFT ? facing.getCounterClockWise() : facing.getClockWise();
    }

    /** Whether {@code state} is a bookend or a row of books facing as {@code like} does: one row with it. */
    private static boolean sameRow(BlockState state, BlockState like) {
        return state.getBlock() instanceof BookendBlock && state.getValue(FACING) == like.getValue(FACING)
                && state.getValue(HINGE) == like.getValue(HINGE);
    }

    /** On a floor; a row's block also leans on the block before it in the row. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        return this.upright || sameRow(level.getBlockState(pos.relative(along(state).getOpposite())), state);
    }

    /**
     * Its floor or the block before it gone, it falls a tick later rather than at once: a long row comes down block after
     * block however long it is (all at once, the game's chain of shape updates stopped 512 blocks on, and the rest of a
     * row stood on nothing; the r18 review) - and a structure set down chunk by chunk, the block before it not yet there
     * in the next chunk, is looked at again once it is (it would have been taken away as it was set down).
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        boolean support = side == Direction.DOWN || !this.upright && side == along(state).getOpposite();
        if (support && !state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true); // itself (a bookend) and its books, as items
        }
    }

    /** A wall to mobs, as the other things that stand in a block without filling it (DECISIONS: Mobs' way through). */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, the spines face the mirrored way and the upright changes sides. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
                .setValue(HINGE, state.getValue(HINGE) == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }

    /** A book goes to the row's end; anything else does what it would. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PlacedBooksBlock.PLACEABLE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return stand(level, pos, player, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /** The last block of the row {@code pos} is in: followed along the row from there. */
    public static BlockPos endOfRow(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos end = pos;
        for (int i = 0; i < LONGEST; i++) {
            BlockPos next = end.relative(along(state));
            if (!level.hasChunkAt(next)) {
                break; // a row's end is never looked for in a chunk not loaded (asking would load it)
            }
            BlockState there = level.getBlockState(next);
            if (!there.is(ModBlocks.BOOK_ROW.get()) || !sameRow(there, state)) {
                break;
            }
            end = next;
        }
        return end;
    }

    /**
     * Stands {@code held} at the end of the row {@code pos} is in: beside the last book, or in the block past it when
     * that is full (a floor there, nothing in the way); whether it did. Public for tests.
     */
    public static boolean stand(Level level, BlockPos pos, Player player, ItemStack held) {
        if (!(level.getBlockState(pos).getBlock() instanceof BookendBlock) || !player.mayBuild()) {
            return false; // not in adventure mode: a row set out there is decoration (the user, 2026-10-20)
        }
        BlockPos at = endOfRow(level, pos);
        if (goesOnUnloaded(level, at)) {
            return false; // its end is further, in a chunk not loaded: not this block's to add to
        }
        BlockState end = level.getBlockState(at);
        if (!(level.getBlockEntity(at) instanceof BookendBlockEntity books)) {
            return false;
        }
        if (books.count() >= MAX) {
            at = at.relative(along(end));
            BlockState row = ModBlocks.BOOK_ROW.get().defaultBlockState().setValue(FACING, end.getValue(FACING))
                    .setValue(HINGE, end.getValue(HINGE));
            BlockState there = level.getBlockState(at);
            if (!there.canBeReplaced() || !there.getFluidState().isEmpty() || !row.canSurvive(level, at)) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.nookworks.bookend.no_room")); // never silent (2026-11-05)
                }
                return false;
            }
            if (level.isClientSide()) {
                return true;
            }
            if (level instanceof ServerLevel server && !Permission.mayPlace(server, at, Direction.UP, player, row)) {
                return false;
            }
            level.setBlock(at, row, Block.UPDATE_ALL);
        } else if (!level.isClientSide() && !mayChange(level, pos, at, player)) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        if (!(level.getBlockEntity(at) instanceof BookendBlockEntity target) || !target.add(held)) {
            return false;
        }
        held.consume(1, player);
        refresh(level, at, target);
        level.playSound(null, at, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** An empty hand takes back the last book of the row, into the inventory; a row's block left empty goes. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos end = endOfRow(level, pos);
        if (goesOnUnloaded(level, end)) {
            return InteractionResult.PASS; // its end is further, in a chunk not loaded: a book from the middle would leave a gap
        }
        if (!player.getMainHandItem().isEmpty() || !player.mayBuild() || !(level.getBlockEntity(end) instanceof BookendBlockEntity books)
                || books.count() == 0
                || !level.isClientSide() && !mayChange(level, pos, end, player)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack taken = books.take();
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false, Prediction.PREDICTED);
            }
            TrayBlock.keepHandEmpty(player.getInventory());
            level.playSound(null, end, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.0F);
            refresh(level, end, books);
        }
        return InteractionResult.SUCCESS;
    }

    /** Whether the row may go on past {@code end} into a chunk not loaded, where its end is not looked for. */
    private static boolean goesOnUnloaded(LevelReader level, BlockPos end) {
        return !level.hasChunkAt(end.relative(along(level.getBlockState(end))));
    }

    /**
     * Whether {@code player}, who used the row at {@code clicked}, may change its end at {@code end}: asked of the end
     * itself when that is another block, as breaking it would ask - the claim a player clicks in is not always the one the
     * row runs on into (a row from open land into a claim gave its books to anyone at its bookend; the r18 review).
     */
    private static boolean mayChange(Level level, BlockPos clicked, BlockPos end, Player player) {
        return end.equals(clicked) || !(level instanceof ServerLevel server) || Permission.mayBreak(server, end, player);
    }

    /** The block's shape to match its books; a row's block with none left goes. */
    private static void refresh(Level level, BlockPos pos, BookendBlockEntity books) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BookendBlock block)) {
            return;
        }
        if (!block.upright && books.count() == 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        level.setBlock(pos, state.setValue(BOOKS, books.count()), Block.UPDATE_ALL);
    }

    /** The pick-block key: the bookend for a bookend; a row's last book, as it is, for a row. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        if (this.upright) {
            return super.getCloneItemStack(level, pos, state, includeData);
        }
        return level.getBlockEntity(pos) instanceof BookendBlockEntity books && books.count() > 0
                ? books.book(books.count() - 1).copy() : new ItemStack(Items.BOOK);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BookendBlockEntity(pos, state);
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Sneaking {
        /**
         * Sneaking with an item in hand, the game uses the item and not the block: a book used on a bookend or a row so
         * still stands in it (a written book would otherwise open). Heard at low priority, after a claim mod.
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (!player.isSecondaryUseActive() || player.isSpectator() || event.getUseItem() == TriState.FALSE
                    || !event.getItemStack().is(PlacedBooksBlock.PLACEABLE)
                    || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BookendBlock)
                    || !player.mayUseItemAt(event.getPos(), event.getFace() == null ? Direction.UP : event.getFace(), event.getItemStack())) {
                return;
            }
            stand(event.getLevel(), event.getPos(), player, event.getItemStack());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }

        private Sneaking() {}
    }
}
