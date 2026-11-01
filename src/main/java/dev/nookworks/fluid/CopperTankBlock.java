package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.experience.Move;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Copper Tank (see {@link CopperTankBlockEntity}): a banded copper vessel on a frame plinth, its sight glasses
 * showing what is in it. A bucket or a bottle of water, oil or biodiesel pours in; an empty one draws out.
 */
public class CopperTankBlock extends BaseEntityBlock {
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
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return PipePorts.updated(super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random), side, neighbourState);
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CopperTankBlockEntity tank)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        Fluid poured = bottleOf(stack);
        if (level.isClientSide() && (poured != null || stack.is(Items.GLASS_BOTTLE)
                || ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) != null)) {
            // The client does not know what the tank holds: the click ends here and the server decides. Had it gone
            // on, a bucket the server had just filled would be poured out in front of the tank.
            return InteractionResult.SUCCESS;
        }
        // a bottle: a quarter of a bucket in or out
        if (poured != null) {
            if (!level.isClientSide()) {
                if (Move.insertExactly(tank.fluidHandler(), FluidResource.of(poured), OilFluids.BOTTLE)) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
                } else {
                    player.sendOverlayMessage(Component.translatable("message.nookworks.copper_tank.will_not_take"));
                    return Clicks.REFUSED;
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            ItemStack filled = filledBottle(tank.resource().getFluid());
            if (tank.amount() < OilFluids.BOTTLE || filled.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.copper_tank.too_little"));
                return Clicks.REFUSED;
            }
            if (!level.isClientSide() && Move.extractExactly(tank.fluidHandler(), tank.resource(), OilFluids.BOTTLE)) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        // a bucket, or anything else that holds fluid: the fluid item capability, as any tank takes it
        if (FluidUtil.interactWithFluidHandler(player, hand, pos, tank.fluidHandler(), null)) {
            return InteractionResult.SUCCESS;
        }
        var held = ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM);
        if (held != null) {
            boolean empty = true;
            for (int i = 0; i < held.size(); i++) {
                empty &= held.getAmountAsLong(i) == 0;
            }
            // an empty container: the tank has too little for it (a bucket takes a whole bucket); a full one: it will not go in
            player.sendOverlayMessage(Component.translatable(empty ? "message.nookworks.copper_tank.too_little" : "message.nookworks.copper_tank.will_not_take"));
            return Clicks.REFUSED; // it moved nothing: the bucket is not poured out in front of the tank either
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // an empty hand reads it; anything else is used on it as on any block
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CopperTankBlockEntity tank) {
            tank.readout(player::sendOverlayMessage);
        }
        return InteractionResult.SUCCESS;
    }

    /** The fluid a filled bottle pours: water, oil, biodiesel; null for anything else. */
    public static @Nullable Fluid bottleOf(ItemStack stack) {
        if (stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
            return Fluids.WATER;
        }
        if (stack.is(ModOil.VEGETABLE_OIL_BOTTLE.get())) {
            return OilFluids.SOURCE.get();
        }
        if (stack.is(ModOil.BIODIESEL_BOTTLE.get())) {
            return OilFluids.BIODIESEL.get();
        }
        return null;
    }

    /** A bottle of what the tank holds, or empty for a fluid there is no bottle of. */
    public static ItemStack filledBottle(Fluid fluid) {
        if (fluid.isSame(Fluids.WATER)) {
            return PotionContents.createItemStack(Items.POTION, Potions.WATER);
        }
        if (fluid.isSame(OilFluids.SOURCE.get())) {
            return new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get());
        }
        if (fluid.isSame(OilFluids.BIODIESEL.get())) {
            return new ItemStack(ModOil.BIODIESEL_BOTTLE.get());
        }
        return ItemStack.EMPTY;
    }

    /** Comparators reading it through a block hear that it is gone, as with vanilla's containers. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CopperTankBlockEntity tank ? tank.comparatorSignal() : 0;
    }

    /** Mobs do not try to walk through it (as vanilla's cauldron): it stands a block tall, near a block wide. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
