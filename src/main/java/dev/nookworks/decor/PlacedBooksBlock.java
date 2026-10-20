package dev.nookworks.decor;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.TrayBlock;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Books laid down on a block (the user, 2026-10-19: 「本やエンチャント本、書き込まれた本などをブロックとしてワールドに
 * 置けるように」; 「部屋の隅に乱雑に積んである、みたいな表現を、マイクラらしさを崩さずに」). Sneak and right-click the top of
 * a block with a book - a book, an enchanted book, a written book, a book and quill, or anything in {@link #PLACEABLE}
 * - and it lies there; up to three in a pile, each turned its own way but their middles over one another
 * ({@link PlacedBooksBlockEntity#add}) - a pile put down in passing that still stands. Clicked on a wall's side instead
 * (with a floor under the space in front of it), the books stand there leant against the wall, cover out, their tops on
 * the wall, a second and third leant in front of the first (2026-10-19: 「壁に立てかける感じで」); the wall gone, they fall (to stand them straight, a {@link BookendBlock}). A lying book is nine by thirteen, a
 * standing one ten by fourteen, both three thick (the user, after four sizes side by side: seven by eleven read as a
 * notebook). Each keeps
 * itself as it was (its enchantments, its pages); an empty hand takes the top one back as the item it was, and broken
 * the pile drops them all. What a book is shows on its cover ({@link Kind}); an enchanted one now and then gives off
 * the enchanting table's glyphs. The block itself draws nothing: PlacedBooksRenderer lays each book where it lies,
 * from block/placed_book_<kind> (tools/textures/gen_books.py).
 */
public class PlacedBooksBlock extends BaseEntityBlock {
    /** Up to three in a pile (the user: 3冊くらいまでがいいかも). */
    public static final int MAX = 3;
    /** A book's thickness, in pixels: the pile is this high a book. */
    public static final int THICK = 3;
    /** What may be laid down: the four books of the game; a pack or another mod adds its own. */
    public static final TagKey<Item> PLACEABLE = TagKey.create(Registries.ITEM, Nookworks.id("placeable_books"));
    /** How many lie there: the pile's height, for its shape. */
    public static final IntegerProperty BOOKS = IntegerProperty.create("books", 1, MAX);
    /** Leant against a wall (the user, 2026-10-19: 「縦にも置けないかな。壁に立てかける感じで」) rather than lying. */
    public static final BooleanProperty STANDING = BooleanProperty.create("standing");
    /** For books leant against a wall: away from the wall, into the room (the face of the wall that was clicked). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * Mirrored by a structure set down mirrored: each book drawn as in a mirror, its turn and its place off the middle
     * the other way about ({@link #lieInWorld}). With {@link #FACING}, a pile in a structure turns with it (the r18
     * review: a room turned, its piles of books stayed as they lay).
     */
    public static final BooleanProperty MIRRORED = BooleanProperty.create("mirrored");
    /** A standing book's height, in pixels: the length of the book (block/placed_book_<kind>_tall). */
    public static final int TALL = 14;
    /** A standing book's width, in pixels: how far it reaches out from what it stands against. */
    public static final int WIDE = 10;
    private static final VoxelShape[] SHAPES = new VoxelShape[MAX];
    /** Standing, by facing (its 2D data value) and count: as deep from the wall as the books lean out. */
    private static final VoxelShape[][] STANDING_SHAPES = new VoxelShape[4][MAX];

    static {
        for (int i = 0; i < MAX; i++) {
            SHAPES[i] = Block.box(1, 0, 1, 15, THICK * (i + 1), 15); // as wide as a turned book reaches
            double deep = Math.min(16, 4 + THICK * (i + 1) + 1);
            STANDING_SHAPES[Direction.SOUTH.get2DDataValue()][i] = Block.box(3, 0, 0, 13, TALL, deep);
            STANDING_SHAPES[Direction.NORTH.get2DDataValue()][i] = Block.box(3, 0, 16 - deep, 13, TALL, 16);
            STANDING_SHAPES[Direction.EAST.get2DDataValue()][i] = Block.box(0, 0, 3, deep, TALL, 13);
            STANDING_SHAPES[Direction.WEST.get2DDataValue()][i] = Block.box(16 - deep, 0, 3, 16, TALL, 13);
        }
    }

    /** What a book's cover shows. */
    public enum Kind {
        PLAIN, ENCHANTED, WRITTEN, WRITABLE;

        public static Kind of(ItemStack stack) {
            return stack.is(Items.ENCHANTED_BOOK) ? ENCHANTED : stack.is(Items.WRITTEN_BOOK) ? WRITTEN
                    : stack.is(Items.WRITABLE_BOOK) ? WRITABLE : PLAIN;
        }
    }

    public PlacedBooksBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(BOOKS, 1).setValue(STANDING, false).setValue(FACING, Direction.NORTH)
                .setValue(MIRRORED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOOKS, STANDING, FACING, MIRRORED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STANDING) ? STANDING_SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(BOOKS) - 1]
                : SHAPES[state.getValue(BOOKS) - 1];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        if (!state.getValue(STANDING)) {
            return true;
        }
        // leant against a wall: the wall there to lean on
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    /**
     * Its floor (or wall) gone, it falls a tick later: a structure set down chunk by chunk, the wall in the next chunk not
     * yet there, looks again once it is (taken away at once, the books went as the structure was set down; the r18
     * review), as a Bookend's row does.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        boolean support = side == Direction.DOWN || state.getValue(STANDING) && side == state.getValue(FACING).getOpposite();
        if (support && !state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true); // its books, as items
        }
    }

    /**
     * Books leant against a wall are a wall to mobs, as a Bookend's row (DECISIONS: Mobs' way through); a pile, at most
     * nine pixels, is stepped over.
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return !state.getValue(STANDING) && super.isPathfindable(state, type);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state
                : state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(MIRRORED, !state.getValue(MIRRORED));
    }

    /**
     * Where a lying book lies, in the world: its turn (degrees about up) and its place off the block's middle (pixels, x
     * and z), from the {@code yaw}, {@code dx} and {@code dz} its block entity keeps - which are the pile's own, as it
     * was laid down facing north - mirrored if the pile was, then turned with the pile from north to its {@link #FACING}.
     * A pile laid down in the world faces north, unmirrored: it lies just as it was kept.
     */
    public static float[] lieInWorld(BlockState state, float yaw, float dx, float dz) {
        boolean mirrored = state.hasProperty(MIRRORED) && state.getValue(MIRRORED);
        float turn = 180.0F - (state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH).toYRot(); // north: none
        // a book in a mirror: turned the other way and about, so its spine is on the mirrored side (the fix check)
        float y = mirrored ? 180.0F - yaw : yaw;
        float x = mirrored ? -dx : dx;
        double a = Math.toRadians(turn);
        float cos = (float) Math.cos(a);
        float sin = (float) Math.sin(a);
        return new float[] {Mth.wrapDegrees(y + turn), x * cos + dz * sin, -x * sin + dz * cos};
    }

    /** The turn a pile keeps for a book that is to lie turned {@code worldYaw} in the world: {@link #lieInWorld}'s, undone. */
    public static float yawKept(BlockState state, float worldYaw) {
        boolean mirrored = state.hasProperty(MIRRORED) && state.getValue(MIRRORED);
        float turn = 180.0F - (state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH).toYRot();
        float y = worldYaw - turn;
        return Mth.wrapDegrees(mirrored ? 180.0F - y : y);
    }

    /** An empty hand takes the top book back up, into the inventory; with none left the block goes. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.mayBuild() || !(level.getBlockEntity(pos) instanceof PlacedBooksBlockEntity books)) {
            return InteractionResult.PASS; // not in adventure mode: books set out are decoration there (the user, 2026-10-20)
        }
        if (!level.isClientSide()) {
            ItemStack taken = books.take();
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false, Prediction.PREDICTED);
            }
            TrayBlock.keepHandEmpty(player.getInventory());
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.0F);
            refresh(level, pos, books);
        }
        return InteractionResult.SUCCESS;
    }

    /** The block's height to match its books; none, and it goes. */
    static void refresh(Level level, BlockPos pos, PlacedBooksBlockEntity books) {
        int count = books.count();
        if (count == 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        level.setBlock(pos, level.getBlockState(pos).setValue(BOOKS, count), Block.UPDATE_ALL);
    }

    /**
     * Where {@code held} would lie, clicking {@code clicked}'s {@code face}: on the pile there, or on top of
     * {@code clicked}; null when it cannot be laid down (not a book, a full pile, water there).
     */
    public static @Nullable BlockPos placeFor(Level level, BlockPos clicked, Direction face, ItemStack held) {
        if (held.isEmpty() || !held.is(PLACEABLE)) {
            return null;
        }
        Block block = ModBlocks.PLACED_BOOKS.get();
        BlockPos at = level.getBlockState(clicked).is(block) ? clicked : face == Direction.DOWN ? null : clicked.relative(face);
        if (at == null) {
            return null;
        }
        BlockState there = level.getBlockState(at);
        if (there.is(block)) {
            return there.getValue(BOOKS) < MAX ? at : null;
        }
        return there.canBeReplaced() && there.getFluidState().isEmpty() && fresh(face).canSurvive(level, at) ? at : null;
    }

    /** A new pile by a click on {@code face}: lying on a top, leant against a wall's side. */
    private static BlockState fresh(Direction face) {
        BlockState state = ModBlocks.PLACED_BOOKS.get().defaultBlockState();
        return face.getAxis().isHorizontal() ? state.setValue(STANDING, true).setValue(FACING, face) : state;
    }

    /**
     * Lays {@code held} down on top of {@code clicked}, or on the pile there, as a player facing {@code yaw} (degrees)
     * would let it fall; whether it did. A new pile goes up on the block above, so claim mods and spawn protection are
     * asked about it as of a block put there. Public for tests.
     */
    public static boolean setDown(Level level, BlockPos clicked, Direction face, float yaw, Player player, ItemStack held) {
        BlockPos at = placeFor(level, clicked, face, held);
        if (at == null) {
            return false;
        }
        Block block = ModBlocks.PLACED_BOOKS.get();
        if (!level.getBlockState(at).is(block)) {
            BlockState fresh = fresh(face);
            if (level instanceof ServerLevel server && !Permission.mayPlace(server, at, face, player, fresh)) {
                return false;
            }
            level.setBlock(at, fresh, Block.UPDATE_ALL);
        } else if (!at.equals(clicked) && level instanceof ServerLevel server && !Permission.mayBreak(server, at, player)) {
            return false; // a pile on the block clicked, which may be another's than the block under it (the r18 review)
        }
        if (!(level.getBlockEntity(at) instanceof PlacedBooksBlockEntity books)
                || !books.add(held, yaw, level.getBlockState(at).getValue(STANDING), level.getRandom())) {
            return false;
        }
        held.consume(1, player);
        refresh(level, at, books);
        level.playSound(null, at, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** The pick-block key gives the top book, as it is (an enchanted book, with its enchantments). */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return level.getBlockEntity(pos) instanceof PlacedBooksBlockEntity books && books.count() > 0
                ? books.book(books.count() - 1).copy() : new ItemStack(Items.BOOK);
    }

    /** An enchanted book in the pile now and then gives off the glyphs an enchanting table draws from a bookshelf. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof PlacedBooksBlockEntity books)) {
            return;
        }
        for (int i = 0; i < books.count(); i++) {
            if (Kind.of(books.book(i)) == Kind.ENCHANTED && random.nextInt(6) == 0) {
                double y = pos.getY() + (THICK * (i + 1) + 1) / 16.0;
                level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.3 + random.nextDouble() * 0.4, y,
                        pos.getZ() + 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.4, 0.3 + random.nextDouble() * 0.3,
                        (random.nextDouble() - 0.5) * 0.4);
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlacedBooksBlockEntity(pos, state);
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Placing {
        /**
         * Sneak and right-click the top of a block with a book: lay it down (a book and quill or a written book is opened
         * only without sneaking, as ever). As the bottles: not for a spectator nor in adventure mode unless allowed, and
         * heard at low priority, after a claim mod.
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (!player.isSecondaryUseActive() || event.getFace() == null || player.isSpectator() || event.getUseItem() == TriState.FALSE
                    || !player.mayUseItemAt(event.getPos().relative(event.getFace()), event.getFace(), event.getItemStack())) {
                return;
            }
            boolean ours = event.getLevel().getBlockState(event.getPos()).is(ModBlocks.PLACED_BOOKS.get());
            if (placeFor(event.getLevel(), event.getPos(), event.getFace(), event.getItemStack()) == null
                    && !(ours && event.getItemStack().is(PLACEABLE))) {
                return;
            }
            if (!event.getLevel().isClientSide()) {
                setDown(event.getLevel(), event.getPos(), event.getFace(), player.getYRot(), player, event.getItemStack());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }

        private Placing() {}
    }
}
