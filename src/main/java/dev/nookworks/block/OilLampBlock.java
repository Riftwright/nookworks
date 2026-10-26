package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * Oil Lamp (油火皿): an unglazed clay dish of vegetable oil with a wick of rush pith lying across it, lit at the rim.
 * Pour a bottle of oil in (it holds four), light the wick with flint and steel, a fire charge or a burning arrow, put
 * it out with an empty hand. It burns its oil as it goes - a bottle for about five minutes, the four of a full dish
 * about a day - and goes out when the dish is dry. Its light is a soft one, less than a torch's: a lamp to read by,
 * where the sconce's candle is struck with a flint and the Generator's oil is burnt in a firebox.
 * <p>
 * A bottle burns down in {@link #STEPS} steps ({@link #BURNT}), so a lamp blown out and lit again goes on from about
 * where it was: a tick already due when it is lit again counts one step, never a whole bottle. Broken or knocked off
 * its stand, the dish keeps what oil is left in it (its loot table copies {@link #OIL}).
 */
public class OilLampBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Bottles of oil in the dish. */
    public static final int FULL = 4;
    public static final IntegerProperty OIL = IntegerProperty.create("oil", 0, FULL);
    /** Steps a bottle burns down in, and how many of the bottle now burning are gone. */
    public static final int STEPS = 10;
    public static final IntegerProperty BURNT = IntegerProperty.create("burnt", 0, STEPS - 1);
    public static final int LIGHT_LEVEL = 11;
    /** Ticks a bottle of oil burns for. */
    public static final int BURN = 6_000;
    private static final int STEP = BURN / STEPS;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 3, 12);

    public OilLampBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(OIL, 0)
                .setValue(BURNT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, OIL, BURNT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // the wick lies towards whoever set the dish down, its lit end at the near rim
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModOil.VEGETABLE_OIL_BOTTLE.get())) {
            if (state.getValue(OIL) >= FULL) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.nookworks.oil_lamp.full"));
                }
                return Clicks.REFUSED;
            }
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(OIL, state.getValue(OIL) + 1), Block.UPDATE_ALL);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.6F, 0.8F);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND && state.getValue(LIT) && player.getAbilities().mayBuild) {
            snuff(level, pos, state);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public @Nullable BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
        if (ability == ItemAbilities.FIRESTARTER_LIGHT && !state.getValue(LIT)) {
            if (state.getValue(OIL) == 0) {
                if (!simulate && context.getPlayer() != null && !context.getLevel().isClientSide()) {
                    context.getPlayer().sendOverlayMessage(Component.translatable("message.nookworks.oil_lamp.dry"));
                }
                return null; // a dry wick only chars
            }
            return state.setValue(LIT, true);
        }
        return super.getToolModifiedState(state, context, ability, simulate);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide() && projectile.isOnFire() && !state.getValue(LIT) && state.getValue(OIL) > 0) {
            level.setBlock(hit.getBlockPos(), state.setValue(LIT, true), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        boolean wasLit = old.is(this) && old.getValue(LIT);
        if (state.getValue(LIT) && !wasLit) {
            level.scheduleTick(pos, this, STEP); // lit: the bottle in the dish goes on burning down
        }
    }

    /** A step of a bottle has burnt; after the last, the dish is a bottle lower, or dry and out. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return; // blown out since: the step it was burning waits for it to be lit again
        }
        int burnt = state.getValue(BURNT) + 1;
        if (burnt < STEPS) {
            // kept on the server alone: no model shows how far a bottle has burnt, and sent, each step had every
            // client near draw the lamp's section again (r17 N6); its look changes with OIL and LIT, sent as ever
            level.setBlock(pos, state.setValue(BURNT, burnt), Block.UPDATE_INVISIBLE);
            level.scheduleTick(pos, this, STEP);
            return;
        }
        int oil = state.getValue(OIL) - 1;
        if (oil <= 0) {
            snuff(level, pos, state.setValue(OIL, 0).setValue(BURNT, 0));
            return;
        }
        level.setBlock(pos, state.setValue(OIL, oil).setValue(BURNT, 0), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, STEP);
    }

    private static void snuff(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL_IMMEDIATE);
        Vec3 tip = flame(pos, state);
        level.addParticle(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 0.0, 0.1, 0.0);
        level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** The wick's lit end, over the rim on the side the dish faces (models: tools/textures/gen_oil_lamp.py). */
    private static Vec3 flame(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        double out = 4.3 / 16;
        return new Vec3(pos.getX() + 0.5 + facing.getStepX() * out, pos.getY() + 7.0 / 16, pos.getZ() + 0.5 + facing.getStepZ() * out);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            // the flame is the model's own; from its tip, now and then, a thread of smoke
            Vec3 tip = flame(pos, state);
            if (random.nextInt(8) == 0) {
                level.addParticle(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 0.0, 0.01, 0.0);
            }
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
