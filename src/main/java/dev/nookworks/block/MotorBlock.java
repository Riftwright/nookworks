package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.feature.Lubrication;
import dev.nookworks.machine.Crankable;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import java.util.EnumMap;
import java.util.Map;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * See {@link MotorBlockEntity}. Its shaft points into the block it was placed against, so set it against the machine
 * it should turn. POWERED is only for the look: the coil glows while it runs.
 * <p>
 * Its body is set back two pixels on the four sides round the shaft, where an Energy Cable's arm, ending at the block's
 * edge, left a gap (the user, 2026-11-23: 「モーターのケーブルの間に隙間ができるのが気になる」). A socket is drawn
 * across it on each such side a cable is joined to ({@link #SOCKETS}, by world side, as a pipe's ports are).
 * <p>
 * Set down, it turns its shaft to what it can drive beside it (the user, 2026-11-23: 「いつも変な向きに置かさるから、毎回
 * レンチで向きを変える必要があって」): a machine with a handle that side may turn, a Drive Wheel on the shaft's line, or a
 * block in {@link #DRIVES} (another mod's shafts - Nook Technology's). Never into a cable or what gives or takes power,
 * nor into a solid face, while another way is open; with nothing to drive, away from the one placing it
 * ({@link #shaftFor}).
 */
public class MotorBlock extends MachineBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /**
     * Blocks a Motor set down beside turns its shaft to, for another mod's drive parts (a shaft, a gearbox) that turn by
     * the Motor in ways of their own. One with an axis is turned to only along it; one with Direction properties named
     * "input" or "output" only when the shaft meets that face. Our own machines and Drive Wheels need no tag: they are
     * known by their block entities.
     */
    public static final TagKey<Block> DRIVES = TagKey.create(Registries.BLOCK, Nookworks.id("motor_drives"));

    /** A cable joined on that side, for the model: a socket across the gap to the body. By world side. */
    public static final Map<Direction, BooleanProperty> SOCKETS = new EnumMap<>(Direction.class);

    static {
        for (Direction side : Direction.values()) {
            SOCKETS.put(side, BooleanProperty.create("socket_" + side.getSerializedName()));
        }
    }

    /**
     * No sound now and then: a running Motor hums on, one sound held while it runs (client/MotorHum - the user,
     * 2026-11-12: 「ずっと動いてるのになったり止まったりしてるのも変」); oiled (the Oil Can), it runs without it, as before.
     */
    @Override
    public @Nullable SoundEvent workSound(BlockState state) {
        return null;
    }

    public MotorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false);
        for (BooleanProperty socket : SOCKETS.values()) {
            state = state.setValue(socket, false);
        }
        this.registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
        SOCKETS.values().forEach(builder::add);
    }

    /** Whether the motor in {@code state} shows a socket on {@code side}: a cable there joined to it, off the shaft's line. */
    public static boolean socket(BlockState state, BlockState there, Direction side) {
        return side.getAxis() != state.getValue(FACING).getAxis() && there.getBlock() instanceof EnergyCableBlock
                && there.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite()));
    }

    /** {@code state} with its sockets set from the cables round it. */
    public static BlockState withSockets(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            state = state.setValue(SOCKETS.get(side), socket(state, level.getBlockState(pos.relative(side)), side));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction shaft = shaftFor(context.getLevel(), context.getClickedPos(), context.getClickedFace().getOpposite(),
                context.getHorizontalDirection());
        return withSockets(this.defaultBlockState().setValue(FACING, shaft), context.getLevel(), context.getClickedPos());
    }

    /**
     * The way a Motor set down at {@code pos} points its shaft: into what it can drive if anything beside it is; never
     * into a cable or a block that gives or takes power, and not into a solid face, while any other way is open; else
     * into the block clicked (as before), or away from the player ({@code look}), sideways before up or down.
     */
    public static Direction shaftFor(Level level, BlockPos pos, Direction clicked, Direction look) {
        Direction best = clicked;
        int bestScore = Integer.MIN_VALUE;
        for (Direction side : Direction.values()) {
            int score = shaftScore(level, pos, side);
            if (score > -100 && side == clicked) {
                score += 3;
            }
            if (side == look) {
                score += 2;
            }
            if (side.getAxis().isHorizontal()) {
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = side;
            }
        }
        return best;
    }

    private static int shaftScore(Level level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        BlockState there = level.getBlockState(next);
        if (drives(level, next, there, side)) {
            return 100;
        }
        if (there.getBlock() instanceof EnergyCableBlock || level.getCapability(Capabilities.Energy.BLOCK, next, side.getOpposite()) != null) {
            return -100; // where the power comes in: the shaft is never put there
        }
        return there.isFaceSturdy(level, next, side.getOpposite()) ? -20 : 0;
    }

    /** Whether a Motor's shaft pointing {@code side} would drive {@code there}: what {@link MotorBlockEntity} turns. */
    public static boolean drives(Level level, BlockPos at, BlockState there, Direction side) {
        BlockEntity entity = level.getBlockEntity(at);
        if (entity instanceof Crankable crankable) {
            return crankable.drivenFrom(side.getOpposite());
        }
        if (entity instanceof DriveWheelBlockEntity wheel) {
            return wheel.axis() == side.getAxis();
        }
        if (!there.is(DRIVES)) {
            return false;
        }
        if (there.hasProperty(BlockStateProperties.AXIS)) {
            return there.getValue(BlockStateProperties.AXIS) == side.getAxis();
        }
        // a part that takes a shaft on faces of its own, named by Direction properties "input" and "output" (Nook
        // Technology's Bevel Gearbox and Hobson's Joint): only when the face the shaft meets is one of them
        boolean faced = false;
        for (String name : FACES) {
            if (there.getBlock().getStateDefinition().getProperty(name) instanceof Property<?> property && property.getValueClass() == Direction.class) {
                faced = true;
                if (there.getValue(property) == side.getOpposite()) {
                    return true;
                }
            }
        }
        return !faced;
    }

    /** The Direction properties a tagged part names the faces it takes a shaft on by. */
    private static final String[] FACES = {"input", "output"};

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        BlockState out = super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
        return out.is(this) ? out.setValue(SOCKETS.get(side), socket(out, neighbourState, side)) : out;
    }

    /** Turned with the Wrench, its sockets are worked out again: a side that is now on the shaft's line shows none. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && oldState.is(this)) {
            BlockState fixed = withSockets(state, level, pos);
            if (fixed != state) {
                level.setBlock(pos, fixed, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** A motor from a world saved before it showed its sockets: set right once its chunk is in (MotorBlockEntity.onLoad). */
    public static void socketsOnLoad(BlockEntity entity) {
        if (entity.getLevel() instanceof ServerLevel server) {
            server.getServer().execute(() -> {
                if (entity.isRemoved() || entity.getLevel() != server) {
                    return;
                }
                BlockPos pos = entity.getBlockPos();
                BlockState state = server.getBlockState(pos);
                if (!(state.getBlock() instanceof MotorBlock)) {
                    return;
                }
                BlockState fixed = state;
                for (Direction side : Direction.values()) {
                    BlockPos next = pos.relative(side);
                    if (server.isLoaded(next)) { // a side whose chunk is not in is left: reading it would load it
                        fixed = fixed.setValue(SOCKETS.get(side), socket(fixed, server.getBlockState(next), side));
                    }
                }
                if (fixed != state) {
                    server.setBlock(pos, fixed, Block.UPDATE_CLIENTS);
                }
            });
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            out = out.setValue(SOCKETS.get(rotation.rotate(side)), state.getValue(SOCKETS.get(side)));
        }
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            out = out.setValue(SOCKETS.get(mirror.mirror(side)), state.getValue(SOCKETS.get(side)));
        }
        return out;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MotorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.MOTOR.get(),
                level.isClientSide() ? MotorBlockEntity::clientTick : MotorBlockEntity::serverTick);
    }
}
