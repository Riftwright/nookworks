package dev.nookworks.ventilation;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import dev.nookworks.registry.ModSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Ventilation Fan: set into a wall, its guard to the room. Switched on with a click, or by a redstone signal, its
 * blades turn and it draws the smoke and steam in front of it off (see {@link Ventilation}) and sends it out behind -
 * into the open, or into the Ducts there. Placed, it faces the one placing it, so it is set from inside the room.
 * <p>
 * Set in the wall of a Barter Terminal's room it works the other way: the terminal drives it ({@link #DRIVEN}), and it
 * blows the Nether's air, made in the terminal and brought along the ducts, into the room.
 */
public class VentFanBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");
    /** Switched on by hand; redstone turns it on whatever this says. */
    public static final BooleanProperty SWITCHED = BooleanProperty.create("switched");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Turned by a Barter Terminal blowing the Nether's air through it, whatever the switch and redstone say. */
    public static final BooleanProperty DRIVEN = BooleanProperty.create("driven");

    public VentFanBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false)
                .setValue(SWITCHED, false).setValue(POWERED, false).setValue(DRIVEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, SWITCHED, POWERED, DRIVEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, powered).setValue(ON, powered);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockState next = state.cycle(SWITCHED);
            next = next.setValue(ON, next.getValue(SWITCHED) || next.getValue(POWERED) || next.getValue(DRIVEN));
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, next.getValue(SWITCHED) ? 0.7F : 0.6F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered).setValue(ON, powered || state.getValue(SWITCHED) || state.getValue(DRIVEN)),
                    Block.UPDATE_ALL);
        }
    }

    /** Sets the fan at {@code pos} turning for a Barter Terminal, or lets it go; nothing if it already is as asked. */
    public static void drive(Level level, BlockPos pos, boolean driven) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof VentFanBlock && state.getValue(DRIVEN) != driven) {
            level.setBlock(pos, state.setValue(DRIVEN, driven)
                    .setValue(ON, driven || state.getValue(SWITCHED) || state.getValue(POWERED)), Block.UPDATE_ALL);
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

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VentFanBlockEntity(pos, state);
    }

    /** A soft hum while it runs; driven by a Barter Terminal, the Nether's crimson motes blown out of its guard. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ON) && random.nextInt(24) == 0) {
            level.playLocalSound(pos, ModSounds.VENTILATION_FAN_RUN.get(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        if (state.getValue(DRIVEN) && random.nextInt(2) == 0) {
            Direction out = state.getValue(FACING);
            double across = random.nextDouble() * 0.6 - 0.3;
            double x = pos.getX() + 0.5 + out.getStepX() * 0.55 + (out.getAxis() == Direction.Axis.Z ? across : 0);
            double z = pos.getZ() + 0.5 + out.getStepZ() * 0.55 + (out.getAxis() == Direction.Axis.X ? across : 0);
            double y = pos.getY() + 0.2 + random.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.CRIMSON_SPORE, x, y, z, out.getStepX() * 0.04, 0.0, out.getStepZ() * 0.04);
        }
    }
}
