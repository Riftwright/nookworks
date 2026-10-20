package dev.nookworks.block;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Laser block; see {@link LaserBlockEntity}. Faces away from the player like a dispenser. {@link #BEAM} is the
 * current beam length (0 = off), used by the client to draw the beam.
 */
public class LaserBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final IntegerProperty BEAM = IntegerProperty.create("beam", 0, LaserBlockEntity.RANGE);

    /** Mounting plate, housing and barrel, pointing north; turned the same way the blockstate turns the model. */
    private static final Map<Direction, VoxelShape> SHAPES =
            Shapes.rotateAll(Shapes.or(
                    Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0),
                    Block.box(3.0, 3.0, 5.0, 13.0, 13.0, 13.0),
                    Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0)));

    public LaserBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BEAM, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BEAM);
    }

    /** A whine now and then while the beam is out. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        MachineBlock.soundNowAndThen(level, pos, random, state.getValue(BEAM) > 0 ? ModSounds.LASER_RUN.get() : null);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // as DrawRights owns what is set down: another mod's machine (a fake player) only for a player the server knows
        if (DrawRights.ownerOf(placer) instanceof UUID owner && level.getBlockEntity(pos) instanceof LaserBlockEntity laser) {
            laser.setOwner(owner);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, ModBlockEntities.LASER.get(), LaserBlock::clientTick)
                : createTickerHelper(type, ModBlockEntities.LASER.get(), LaserBlockEntity::serverTick);
    }

    /**
     * Sparks where the beam lands. The beam itself is geometry now (see LaserBeamRenderer), so all that is
     * left for particles is the bit that sells it: the spot it is burning into.
     */
    private static void clientTick(Level level, BlockPos pos, BlockState state, LaserBlockEntity laser) {
        int length = state.getValue(BEAM);
        if (length == 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Vec3 step = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        Vec3 impact = Vec3.atCenterOf(pos).add(step.scale(length - 0.5));
        RandomSource random = level.getRandom();
        level.addParticle(LaserBlockEntity.SPARK_PARTICLE, impact.x, impact.y, impact.z, 0, 0, 0);
        for (int i = 0; i < 2; i++) {
            // Thrown back along the beam, the way sparks come off something being cut.
            Vec3 away = step.scale(-0.06 - random.nextDouble() * 0.06)
                    .add((random.nextDouble() - 0.5) * 0.08, (random.nextDouble() - 0.5) * 0.08, (random.nextDouble() - 0.5) * 0.08);
            level.addParticle(ParticleTypes.SMALL_FLAME,
                    impact.x, impact.y, impact.z, away.x, away.y, away.z);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserBlockEntity laser) {
            MachineBlock.openScreen(player, laser);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Mobs do not try to walk through it: its back plate fills a whole face of its block. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
