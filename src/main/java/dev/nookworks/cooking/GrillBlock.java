package dev.nookworks.cooking;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.ventilation.Ventilation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Grill: an iron grate on four legs. Over a campfire it stands astride the fire, the legs down its corners to the ground
 * and the grate just above the flames ({@link #OVER_FIRE}); anywhere else it is a little table of a grate at the top of
 * its block, for building with. Lay meat, fish or potatoes on it (anything a campfire cooks, four at a time) and take
 * them off when they are done - over a lit campfire they cook, and stay on the grate, cooked, until you do (an empty hand
 * takes one piece off, done ones first; sneaking, all of them). An Iron Pot set on it over a fire takes its place and
 * cooks too (see {@link IronPotBlock#ON_GRILL}).
 */
public class GrillBlock extends BaseEntityBlock {
    /** Astride a campfire: the grate at the bottom of the block, the legs reaching down past the fire. */
    public static final BooleanProperty OVER_FIRE = BooleanProperty.create("over_fire");
    /** Over a fire just the grate, a pixel thick: a pot or a player stands on it. */
    private static final VoxelShape OVER_FIRE_SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    /**
     * What is aimed at over a fire: the grate and the room above it a pot takes. A pixel was all but missed from beside a
     * campfire, where the grate is at eye height and seen edge on: a pot or food was put down only by aiming at its edge.
     */
    private static final VoxelShape OVER_FIRE_OUTLINE = Block.box(0, 0, 0, 16, 8, 16);
    /** Standing on its own: the grate on top and the four legs. */
    private static final VoxelShape STANDING_SHAPE = Shapes.or(Block.box(0, 15, 0, 16, 16, 16), Block.box(0, 0, 0, 1, 15, 1),
            Block.box(15, 0, 0, 16, 15, 1), Block.box(0, 0, 15, 1, 15, 16), Block.box(15, 0, 15, 16, 15, 16));

    public GrillBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OVER_FIRE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OVER_FIRE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(OVER_FIRE, overFire(context.getLevel(), context.getClickedPos()));
    }

    private static boolean overFire(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.CAMPFIRES);
    }

    /** Whether the grill at {@code pos} stands astride a campfire, grate down low. */
    public static boolean overFire(BlockState state) {
        return state.getBlock() instanceof GrillBlock && state.getValue(OVER_FIRE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OVER_FIRE) ? OVER_FIRE_OUTLINE : STANDING_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OVER_FIRE) ? OVER_FIRE_SHAPE : STANDING_SHAPE;
    }

    /** A campfire put under it or taken away: the grate drops down over the fire or stands back up. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side == Direction.DOWN ? state.setValue(OVER_FIRE, overFire(level, pos)) : state;
    }

    /**
     * Whether the grill at {@code pos} cooks: astride a lit campfire, or standing on a block that conducts heat (a hopper)
     * over a lit fire - the hopper then takes off what is done.
     */
    public static boolean hot(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (overFire(level.getBlockState(pos))) {
            return CampfireBlock.isLitCampfire(below); // a mod's campfire with no "lit" state reads as unlit
        }
        return below.is(IronPotBlock.CONDUCTS_HEAT) && IronPotBlock.heatsThrough(level.getBlockState(pos.below(2)));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof GrillBlockEntity grill)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stack.is(ModCooking.IRON_POT_ITEM.get()) && state.getValue(OVER_FIRE)) {
            return this.setPotDown(stack, level, pos, player, grill);
        }
        // Anything a campfire does not cook is used as it would be on any block (a block placed on the table, a bucket
        // of water on the fire). The set of campfire inputs is sent to clients, so both sides agree on the answer.
        if (!level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            if (!grill.place(server, player, stack)) {
                player.sendOverlayMessage(Component.translatable("nookworks.grill.full").withStyle(ChatFormatting.RED));
                return Clicks.REFUSED;
            }
            level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    /** An Iron Pot put on the grate over a fire: it takes the grill's place, the grill drawn with it. */
    private InteractionResult setPotDown(ItemStack stack, Level level, BlockPos pos, Player player, GrillBlockEntity grill) {
        if (!player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }
        if (grill.items().stream().anyMatch(s -> !s.isEmpty())) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("nookworks.grill.clear_first").withStyle(ChatFormatting.RED));
            }
            return Clicks.REFUSED;
        }
        if (!level.isClientSide()) {
            BlockState pot = ModCooking.IRON_POT.get().onGrill(player.getDirection().getOpposite());
            level.setBlock(pos, pot, Block.UPDATE_ALL);
            // the setter's, as one set down by hand is (no place event comes of this setBlock): an unowned pot drew from
            // anyone's pipe line, a claim's included (r21, 2026-11-06)
            if (level.getBlockEntity(pos) instanceof DrawRights.Owned owned) {
                owned.setOwner(DrawRights.ownerOf(player));
            }
            level.playSound(null, pos, pot.getSoundType(level, pos, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** An empty hand takes a piece off (a cooked one first); sneaking, everything on the grate. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Only an empty hand takes a piece off, and only when there is one: otherwise the click is what is in hand -
        // a lantern set on the grill as on a little table.
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof GrillBlockEntity onIt)
                || onIt.items().stream().allMatch(ItemStack::isEmpty)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GrillBlockEntity grill) {
            boolean any = false;
            for (ItemStack off = grill.take(); !off.isEmpty(); off = player.isSecondaryUseActive() ? grill.take() : ItemStack.EMPTY) {
                any = true;
                if (!player.getInventory().add(off)) {
                    player.drop(off, false, Prediction.PREDICTED);
                }
            }
            if (any) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.5F, 1.2F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Smoke off whatever is still cooking, and a sizzle now and then. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!hot(level, pos) || !(level.getBlockEntity(pos) instanceof GrillBlockEntity grill)) {
            return;
        }
        for (int slot = 0; slot < GrillBlockEntity.SLOTS; slot++) {
            if (!grill.items().get(slot).isEmpty() && !grill.done(slot) && random.nextFloat() < 0.15F) {
                double x = pos.getX() + (slot % 2 == 0 ? 0.3 : 0.7);
                double z = pos.getZ() + (slot < 2 ? 0.3 : 0.7);
                double grate = state.getValue(OVER_FIRE) ? 0.15 : 1.0; // low over a fire, at the top standing
                Ventilation.smoke(level, pos, ParticleTypes.SMOKE, x, pos.getY() + grate, z, 0.0, 0.02, 0.0);
                if (random.nextFloat() < 0.1F) {
                    level.playLocalSound(pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.4F, 1.6F, false);
                }
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GrillBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.GRILL.get(), GrillBlockEntity::serverTick);
    }

    /** Standing, a little table of a grate mobs would walk into: no way through. Over a fire it is a plate, walked over. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(OVER_FIRE) && super.isPathfindable(state, type);
    }
}
