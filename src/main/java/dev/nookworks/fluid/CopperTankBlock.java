package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.experience.Move;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.mercury.MercuryBucketItem;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import dev.nookworks.port.core.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import dev.nookworks.port.core.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import dev.nookworks.port.core.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.ItemAccess;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.FluidUtil;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.ItemInteractionResult;
import dev.nookworks.port.core.ItemInteractions;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/**
 * Copper Tank (see {@link CopperTankBlockEntity}): a banded copper vessel on a frame plinth, its sight glasses
 * showing what is in it. A bucket or a bottle of water, oil, biodiesel, methanol or wood vinegar pours in; an empty one draws out.
 */
public class CopperTankBlock extends BaseEntityBlock implements RemovalHooks {
    /** The plinth, and the vessel on it (tools/gen_machine_models.py). */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(1, 3, 1, 15, 16, 15));

    public CopperTankBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LineContent.PROPERTY, LineContent.EMPTY).setValue(SightGlass.LEVEL, 0));
        this.registerDefaultState(PipePorts.none(this.defaultBlockState()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LineContent.PROPERTY, SightGlass.LEVEL);
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

    /** A pipe joined or taken away beside it: the port on that side shown or not ({@link PipePorts}). */
    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return PipePorts.updated(super.updateShape(state, side, neighbourState, level, pos, neighbourPos), side, neighbourState);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.COPPER_TANK.get(), CopperTankBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CopperTankBlockEntity tank)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof MercuryBucketItem) {
            // turned down, as at the mercury machines: it has no fluid capability, so it fell through and was poured out
            // as a pool beside the tank (r21, 2026-11-06)
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.nookworks.mercury_bucket.into_tank"), true);
            }
            return ItemInteractions.of(Clicks.REFUSED);
        }
        Fluid poured = bottleOf(stack);
        if (level.isClientSide() && (poured != null || stack.is(Items.GLASS_BOTTLE)
                || ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) != null)) {
            // The client does not know what the tank holds: the click ends here and the server decides. Had it gone
            // on, a bucket the server had just filled would be poured out in front of the tank.
            return ItemInteractionResult.SUCCESS;
        }
        // a bottle: a quarter of a bucket in or out
        if (poured != null) {
            if (!level.isClientSide()) {
                if (Move.insertExactly(tank.fluidHandler(), FluidResource.of(poured), OilFluids.BOTTLE)) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
                } else {
                    player.displayClientMessage(Component.translatable("message.nookworks.copper_tank.will_not_take"), true);
                    return ItemInteractions.of(Clicks.REFUSED);
                }
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            ItemStack filled = filledBottle(tank.resource().getFluid());
            if (tank.amount() < OilFluids.BOTTLE || filled.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.nookworks.copper_tank.too_little"), true);
                return ItemInteractions.of(Clicks.REFUSED);
            }
            if (!level.isClientSide() && Move.extractExactly(tank.fluidHandler(), tank.resource(), OilFluids.BOTTLE)) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }
        // a bucket, or anything else that holds fluid: the fluid item capability, as any tank takes it
        if (FluidUtil.interactWithFluidHandler(player, hand, pos, tank.fluidHandler(), null)) {
            return ItemInteractionResult.SUCCESS;
        }
        var held = ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM);
        if (held != null) {
            boolean empty = true;
            Fluid other = null;
            for (int i = 0; i < held.size(); i++) {
                empty &= held.getAmountAsLong(i) == 0;
                Fluid fluid = held.getResource(i).getFluid();
                if (held.getAmountAsLong(i) > 0 && !CopperTankBlockEntity.holds(fluid)) {
                    other = fluid;
                }
            }
            // an empty container: the tank has too little for it (a bucket takes a whole bucket); a liquid no Copper Tank
            // holds (lava): says so by name - it said "something else is in it, or it is full" of an empty tank (the
            // user, 2026-11-14); else it holds another liquid or is full
            player.displayClientMessage(empty ? Component.translatable("message.nookworks.copper_tank.too_little")
                    : other != null ? Component.translatable("message.nookworks.copper_tank.cannot_hold", other.getFluidType().getDescription())
                    : Component.translatable("message.nookworks.copper_tank.will_not_take"), true);
            return ItemInteractions.of(Clicks.REFUSED); // it moved nothing: the bucket is not poured out in front of the tank either
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // an empty hand reads it; anything else is used on it as on any block
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CopperTankBlockEntity tank) {
            tank.readout(line -> player.displayClientMessage(line, true));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The fluid a filled bottle pours: water, oil, biodiesel, methanol, wood vinegar, liquid experience, an add-on's
     * ({@link Bottles}); null for anything else.
     */
    public static @Nullable Fluid bottleOf(ItemStack stack) {
        return Bottles.fluidOf(stack);
    }

    /** A bottle of what the tank holds, or empty for a fluid there is no bottle of ({@link Bottles}). */
    public static ItemStack filledBottle(Fluid fluid) {
        return Bottles.filled(fluid);
    }

    /** Comparators reading it through a block hear that it is gone, as with vanilla's containers. */
    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // PORT: vanilla's part of affectNeighborsAfterRemoval runs in 1.21.1's super.onRemove
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CopperTankBlockEntity tank ? tank.comparatorSignal() : 0;
    }

    /** Mobs do not try to walk through it (as vanilla's cauldron): it stands a block tall, near a block wide. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // PORT: 1.21.1 calls only onRemove where 26.3 calls the old block entity's preRemoveSideEffects and this
    // block's affectNeighborsAfterRemoval: BlockRemoval runs them in 26.3's order
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockRemoval.onRemove(this, state, level, pos, newState, movedByPiston,
                () -> super.onRemove(state, level, pos, newState, movedByPiston));
    }

    // PORT: 1.21.1 asks every block for a codec (26.3 has none); only the block-list data report reads it
    @Override
    protected MapCodec<? extends CopperTankBlock> codec() {
        return BlockCodecs.of(this);
    }
}
