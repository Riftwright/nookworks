package dev.nookworks.cooking;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.experience.Move;
import dev.nookworks.fluid.Bottles;
import dev.nookworks.fluid.FluidPipeBlock;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * Kitchen Tap (蛇口). Asked for 2026-11-15, of a Copper Pipe run straight into the side of an Iron Pot (「鍋にパイプ
 * つないだら、すごく不自然な見た目になるよね」), and made again the same day to what a tap is (「「蛇口」というブロックを
 * 追加するのであれば、きちんとした見た目と、蛇口として必要な挙動や仕様をちゃんと考えてほしい」):
 * <ul>
 * <li>It is set on what holds a liquid, on the side: a Copper Pipe's end, a Copper Tank, a cauldron, another mod's
 * tank - anything that gives a liquid out of that face - and on nothing else (it went on dirt, and not on a tank or a
 * cauldron). Its plate sits in the middle of that face, where a pipe's end is.</li>
 * <li>Open, it draws from what it is set on and pours into what stands under its spout - an Iron Pot, a Frying Pan,
 * a cauldron, a tank, a machine - a bucket in two seconds ({@link KitchenTapBlockEntity}): from a tank into a pot,
 * from one cauldron into another.</li>
 * <li>A click turns it on or off; a redstone signal coming on opens it and going off shuts it, as the Valve. It is set
 * down shut, so a new tap never floods a pot by surprise.</li>
 * <li>An empty bucket or a glass bottle held to it is filled from what it is set on, open or shut.</li>
 * <li>A pipe joins it only at its back, where its plate is - and, since the tap, no pipe joins an Iron Pot or a
 * Frying Pan itself ({@link FluidPipeBlock}).</li>
 * </ul>
 * Pouring, a thin stream falls from its spout ({@code POURING}), drawn and heard (client/TapStream).
 */
public class KitchenTapBlock extends BaseEntityBlock {
    /** The way its spout reaches out: away from what it is set on. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Something has run through it in the last moment: the stream is drawn. */
    public static final BooleanProperty POURING = BooleanProperty.create("pouring");
    /** Where the spout's mouth is, in pixels of its own block (facing north): the stream falls from here. */
    public static final double SPOUT_X = 8, SPOUT_Y = 1, SPOUT_Z = 8;

    // facing north, set on the block to the south: the flange in the middle of that block's face and the neck out of it,
    // the body, the bonnet and its cross handle either way it is turned, the spout down to its nozzle
    // (tools/textures/gen_kitchen_tap.py draws them)
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(4, 4, 15, 12, 12, 16),
            Block.box(6.5, 6, 10, 9.5, 10, 15),
            Block.box(3.75, 10, 7.75, 12.25, 13.5, 15.25),
            Block.box(6.5, 1, 6.5, 9.5, 8.5, 10)));

    public KitchenTapBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(POWERED, false).setValue(POURING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED, POURING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // a tap is reached past, not bumped into
    }

    /** Set on the side of what it is clicked against, if that holds a liquid; shut, a signal there or not. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        Direction clicked = context.getClickedFace();
        if (clicked.getAxis().isHorizontal()) {
            BlockState state = this.defaultBlockState().setValue(FACING, clicked).setValue(POWERED, powered);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        for (Direction looking : context.getNearestLookingDirections()) {
            if (looking.getAxis().isHorizontal()) {
                BlockState state = this.defaultBlockState().setValue(FACING, looking.getOpposite()).setValue(POWERED, powered);
                if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return state;
                }
            }
        }
        return null;
    }

    /**
     * On what gives a liquid out of the face it is set on: a pipe, a tank, a cauldron. Not an Iron Pot or a Frying Pan
     * (a vessel, not a source) nor another tap. Where the world cannot be asked (a structure being built), as saved.
     * <p>
     * A pipe only by a side that lets liquid out to it: not an Experience Pipe, not a Valve's or a Sight Glass's side
     * off its axis, not a side the Wrench has switched off - a tap stood there and never ran (1.7 review). A shut Valve
     * opens again: the tap stays. A cauldron of any kind, empty or of powder snow or mercury too - which give no liquid
     * to be asked about: a tap set on an empty cauldron fell off when mercury was poured in (1.7 review).
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behind = pos.relative(facing.getOpposite());
        BlockState there = level.getBlockState(behind);
        if (there.getBlock() instanceof FluidPipeBlock) {
            return !(there.getBlock() instanceof dev.nookworks.experience.ExperiencePipeBlock)
                    && !dev.nookworks.block.PipeSides.isOff(there, facing)
                    && !(there.getBlock() instanceof dev.nookworks.fluid.CopperFittingBlock
                            && facing.getAxis() != there.getValue(dev.nookworks.fluid.CopperFittingBlock.AXIS));
        }
        if (there.getBlock() instanceof net.minecraft.world.level.block.AbstractCauldronBlock) {
            return true;
        }
        if (there.getBlock() instanceof IronPotBlock || there.getBlock() instanceof KitchenTapBlock) {
            return false;
        }
        if (!(level instanceof Level full)) {
            return true;
        }
        return full.getCapability(Capabilities.Fluid.BLOCK, behind, facing) != null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * An empty bucket or a glass bottle is filled from what the tap is set on (its cup first). Anything else in hand
     * turns it, as an empty hand does.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        boolean bottle = stack.is(Items.GLASS_BOTTLE);
        var container = ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM);
        if (!bottle && container == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS; // the client does not know what the tap reaches: the server decides
        }
        if (!(level.getBlockEntity(pos) instanceof KitchenTapBlockEntity tap)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        var outlet = tap.outlet();
        if (bottle) {
            FluidResource kind = outlet.getResource(0);
            ItemStack filled = kind.isEmpty() ? ItemStack.EMPTY : Bottles.filled(kind.getFluid());
            if (filled.isEmpty() || !Move.extractExactly(outlet, kind, Bottles.AMOUNT)) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.kitchen_tap.nothing"));
                return Clicks.REFUSED;
            }
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, pos, outlet, null)) {
            return InteractionResult.SUCCESS;
        }
        if (isEmpty(container)) {
            // an empty bucket it could not fill (less than a bucket there): said so, and the tap left as it was - it was
            // turned, and poured out into the pot what the bucket was meant to take (1.7 review)
            player.sendOverlayMessage(Component.translatable("message.nookworks.kitchen_tap.nothing"));
            return Clicks.REFUSED;
        }
        // a full bucket held to it: nothing goes in by the spout; it is turned, as by any other hand
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Whether a held container has nothing in it. */
    private static boolean isEmpty(net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> container) {
        for (int i = 0; i < container.size(); i++) {
            if (!container.getResource(i).isEmpty() && container.getAmountAsLong(i) > 0) {
                return false;
            }
        }
        return true;
    }

    /** A click turns it on or off - as the Valve is turned. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean open = !state.getValue(OPEN);
            turn(level, pos, state.setValue(OPEN, open));
            player.sendOverlayMessage(Component.translatable(open ? "message.nookworks.kitchen_tap.open" : "message.nookworks.kitchen_tap.shut"));
        }
        return InteractionResult.SUCCESS;
    }

    /** As the Valve: a signal coming on opens it, going off shuts it; a hand may turn it in between. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moving) {
        super.neighborChanged(state, level, pos, block, orientation, moving);
        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            turn(level, pos, state.setValue(POWERED, powered).setValue(OPEN, powered));
        }
    }

    /** Its state set, with its own squeak where it is turned (a little brighter opening than shutting). */
    private static void turn(Level level, BlockPos pos, BlockState state) {
        boolean was = level.getBlockState(pos).getValue(OPEN);
        boolean open = state.getValue(OPEN);
        level.setBlock(pos, state.setValue(POURING, open && state.getValue(POURING)), Block.UPDATE_ALL);
        if (was != open) {
            level.playSound(null, pos, ModSounds.KITCHEN_TAP_TURN.get(), SoundSource.BLOCKS, 0.5F, open ? 1.1F : 0.9F);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KitchenTapBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.KITCHEN_TAP.get(),
                level instanceof ServerLevel ? KitchenTapBlockEntity::serverTick : KitchenTapBlockEntity::clientTick);
    }
}
