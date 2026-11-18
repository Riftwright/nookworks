package dev.nookworks.experience;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModItems;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import dev.nookworks.port.core.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import dev.nookworks.port.core.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.nookworks.fluid.PipePorts;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ItemInteractionResult;
import dev.nookworks.port.core.ItemInteractions;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/**
 * Experience Tank block. A bucket or a bottle fills or empties it. It has a tap low on its front: turn it with a click
 * on the tap itself, not anywhere on the tank, whatever is in hand - and the tank pours its experience out as a stream of orbs
 * until it is empty or the tap is turned back, which is how a tank pays out without a machine. Comparators read the
 * fill level.
 */
public class ExperienceTankBlock extends BaseEntityBlock implements RemovalHooks {
    public static final int BUCKET = 1_000;
    /** The side the tap is on: towards whoever placed the tank. */
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** The tap as the tank facing north has it (tools/textures/gen_experience.py: plate, spout and handle). */
    private static final VoxelShape TAP_NORTH = Block.box(5, 2, -3, 11, 10, 0);
    private static final Map<Direction, VoxelShape> TAPS =
            Shapes.rotateHorizontal(TAP_NORTH);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        TAPS.forEach((facing, tap) -> SHAPES.put(facing, Shapes.or(Shapes.block(), tap)));
    }

    public ExperienceTankBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false)
                .setValue(SightGlass.LEVEL, 0));
        this.registerDefaultState(PipePorts.none(this.defaultBlockState()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, SightGlass.LEVEL);
        PipePorts.add(builder, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN);
    }

    /** A pipe joined or taken away beside it: the port on that side shown or not ({@link PipePorts}). */
    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return PipePorts.updated(super.updateShape(state, side, neighbourState, level, pos, neighbourPos), side, neighbourState);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return PipePorts.withPorts(this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context.getLevel(), context.getClickedPos());
    }

    /** The tank and its tap: the tap is outlined as its own part of the block, where a click turns it. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Walked into, it is the plain block; the tap is not something to stand on or bump against. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** Whether {@code hit} (a point in the world) is on the tap of the tank at {@code pos}. */
    public static boolean onTap(BlockState state, BlockPos pos, Vec3 hit) {
        Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        double margin = 0.5 / 16; // a hit lands on a surface of the box, and the handle stands a little proud of it
        return TAPS.get(state.getValue(FACING)).toAabbs().stream().anyMatch(box -> box.inflate(margin).contains(local));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PipePorts.rotate(state, rotation).setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return PipePorts.mirror(state, mirror).setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.EXPERIENCE_TANK.get(), ExperienceTankBlockEntity::serverTick);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExperienceTankBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        boolean bucket = stack.is(Items.BUCKET) || stack.is(ModItems.EXPERIENCE_BUCKET.get());
        boolean bottle = stack.is(Items.GLASS_BOTTLE) || stack.is(ModItems.LIQUID_EXPERIENCE_BOTTLE.get());
        if (!bucket && !bottle || !(level.getBlockEntity(pos) instanceof ExperienceTankBlockEntity tank)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            // The client does not know what the tank holds. Whatever the server finds, the click ends here: had it
            // gone on, the client would use the item itself next - and the bucket the server had just filled would
            // be poured out in front of the tank.
            return ItemInteractionResult.SUCCESS;
        }
        int amount = bucket ? BUCKET : ExperienceBottling.BOTTLE;
        boolean taking = stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
        boolean done = taking
                ? (bucket ? tank.drainBucket() : tank.amount() >= amount && tank.drain(amount) == amount)
                : (bucket ? tank.fillBucket() : tank.space() >= amount && tank.fill(amount) == amount);
        if (!done) {
            player.displayClientMessage(Component.translatable(taking ? "message.nookworks.experience_tank.too_little"
                    : "message.nookworks.experience_tank.no_room", tank.amount(), ExperienceTankBlockEntity.CAPACITY), true);
            return ItemInteractions.of(Clicks.REFUSED); // and the bucket or bottle stays as it was
        }
        ItemStack result = taking
                ? new ItemStack(bucket ? ModItems.EXPERIENCE_BUCKET.get() : ModItems.LIQUID_EXPERIENCE_BOTTLE.get())
                : new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE);
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
        level.playSound(null, pos, taking ? (bucket ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL)
                : (bucket ? SoundEvents.BUCKET_EMPTY : SoundEvents.BOTTLE_EMPTY), SoundSource.BLOCKS, 1.0F, 1.2F);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onTap(state, pos, hit.getLocation())) {
            // The tap is turned by a click on the tap itself, with anything in hand, as a lever is (the user, 2026-11-05):
            // a click anywhere else on the tank leaves it as it is, rather than letting the tank run out by surprise,
            // and sets a pipe in hand down against it.
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExperienceTankBlockEntity tank) {
            boolean open = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.COPPER_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, open ? 1.4F : 1.1F);
            player.displayClientMessage(Component.translatable(open ? "nookworks.experience.tap_open" : "nookworks.experience.tap_shut",
                    tank.amount(), ExperienceTankBlockEntity.CAPACITY), true);
        }
        return InteractionResult.SUCCESS;
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
        return level.getBlockEntity(pos) instanceof ExperienceTankBlockEntity tank ? tank.comparatorSignal() : 0;
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
    protected MapCodec<? extends ExperienceTankBlock> codec() {
        return BlockCodecs.of(this);
    }
}
