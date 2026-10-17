package dev.nookworks.trading;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.Permission;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModTrading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * See {@link TradingCounterBlockEntity}. A shop counter, its front to whoever set it down and the villager's place behind
 * it. Waist high to look at, but as high as a fence to be climbed: a villager behind it does not hop over. A Mob Bottle
 * with a villager in it, clicked on it, lets the villager out behind it; an empty one takes the villager behind it back
 * into the bottle, by the bottle's own rules.
 */
public class TradingCounterBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape FENCE_HIGH = Shapes.box(0, 0, 0, 1, 1.5, 1);

    public TradingCounterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FENCE_HIGH;
    }

    /** Never a way through or over for a mob finding its path, as a fence is not: the villager does not try to climb it. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Whoever sets it down: their price is the villager's price, and theirs the reputation its trades give. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof TradingCounterBlockEntity counter) {
            counter.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TradingCounterBlockEntity counter) {
            player.openMenu(counter);
        }
        return InteractionResult.SUCCESS;
    }

    /** A Mob Bottle: a villager let out behind it, or taken back in. Anything else: the screen, as an empty hand opens it. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(ModItems.MOB_BOTTLE.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        EntityType<?> held = MobBottleItem.captured(stack);
        if (held != null && held != EntityTypes.VILLAGER) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos cell = TradingCounterBlockEntity.stand(pos, state);
        Villager there = TradingCounterBlockEntity.behind(level, pos, state);
        if (held != null) {
            Vec3 at = there == null ? roomBehind(server, cell) : null;
            if (there != null) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.trading_counter.taken"));
            } else if (at == null) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.trading_counter.no_room"));
            } else if (!Permission.mayPlace(server, cell, Direction.UP, player)) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.trading_counter.not_yours"));
            } else {
                Entity out = MobBottleItem.letOut(server, stack, at, player.hasInfiniteMaterials()); // kept: a new one each time
                if (out != null) {
                    ItemStack empty = MobBottleItem.emptied(stack);
                    if (!player.hasInfiniteMaterials()) {
                        stack.shrink(1);
                        if (!player.getInventory().add(empty)) {
                            player.drop(empty, false, Prediction.PREDICTED);
                        }
                    }
                    level.playSound(null, cell, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (there == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.trading_counter.nobody"));
        } else if (there.isTrading() || there.isSleeping()) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.trading_counter.busy"));
        } else if (!Permission.mayPlace(server, there.blockPosition(), Direction.UP, player)) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.trading_counter.not_yours"));
        } else {
            // as the bottle takes it anywhere, by its rules - at half health or less, not a kind a pack keeps out of
            // bottles - and giving up its bed and workstation; the counter only reaches over to it
            MobBottleItem.tryCapture(player, hand, there);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Where a villager let out behind the counter stands, or null with no room for one: on whatever floor is in the cell -
     * a carpet, a bottom slab, a rail or a pressure plate, as trading halls have, where the counter serves it too - with
     * nothing in the way of its body, and no water about it, nor anything that burns (fire, lava, magma, a lit campfire).
     * (Two blocks of open air, as a Dock-a-Daemon Door's traveller needs, turned all of those halls down.) A whole block
     * in the cell would stand it above the cell, where the counter does not serve it.
     */
    private static @Nullable Vec3 roomBehind(ServerLevel level, BlockPos cell) {
        VoxelShape in = level.getBlockState(cell).getCollisionShape(level, cell);
        double floor = in.isEmpty() ? 0.0 : in.max(Direction.Axis.Y);
        if (floor >= 1.0) {
            return null;
        }
        if (in.isEmpty()) { // nothing in the cell to stand on: the block under it holds
            BlockState under = level.getBlockState(cell.below());
            if (!under.isFaceSturdy(level, cell.below(), Direction.UP) || NodeEvaluator.isBurningBlock(under)) {
                return null;
            }
        }
        Vec3 at = new Vec3(cell.getX() + 0.5, cell.getY() + floor, cell.getZ() + 0.5);
        AABB body = EntityTypes.VILLAGER.getDimensions().makeBoundingBox(at);
        if (!level.noCollision(body) || level.containsAnyLiquid(body) || level.getBlockStates(body).anyMatch(NodeEvaluator::isBurningBlock)) {
            return null;
        }
        return at;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradingCounterBlockEntity(pos, state);
    }

    /** Taken away, a comparator reading it through a block is told as well (the drop of its contents tells none). */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    /** A comparator reads how full its slots are, payment and goods, as it reads a chest or one of our machines. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != ModTrading.TRADING_COUNTER_ENTITY.get() ? null
                : (BlockEntityTicker<T>) (BlockEntityTicker<TradingCounterBlockEntity>) TradingCounterBlockEntity::serverTick;
    }
}
