package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.DieselGeneratorBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModSounds;
import dev.nookworks.ventilation.Ventilation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import dev.nookworks.fluid.PipePorts;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;

/**
 * See {@link DieselGeneratorBlockEntity}. A heavy cabinet with a radiator for its front and an exhaust stack on its
 * lid, which smokes while it runs; its lamp lights then. Biodiesel clicked on it pours into its tank, the empty given
 * back; any other liquid is turned away with a word, so nobody wonders where their oil went.
 */
public class DieselGeneratorBlock extends MachineBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * Where the stack's mouth is, drawn facing south (tools/gen_machine_models.py): over the back right-hand corner as
     * seen from the front, a quarter in from each of those sides, at the block's top.
     */
    private static final double STACK_RIGHT = 0.25;
    private static final double STACK_BACK = 0.25;

    public DieselGeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.SOUTH));
        this.registerDefaultState(PipePorts.none(this.defaultBlockState()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
        PipePorts.add(builder, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN);
    }

    /** A pipe joined or taken away beside it: the port on that side shown or not ({@link PipePorts}). */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return PipePorts.updated(super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random), side, neighbourState);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return PipePorts.withPorts(this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context.getLevel(), context.getClickedPos());
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DieselGeneratorBlockEntity gen)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        Fluid fluid = liquidIn(stack);
        if (fluid == Fluids.EMPTY) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit); // nothing poured: the screen opens
        }
        if (fluid != OilFluids.BIODIESEL.get()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.diesel_generator.biodiesel_only"));
            }
            return Clicks.REFUSED;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS; // the client does not know how full the tank is: the server decides
        }
        boolean bottle = stack.is(ModOil.BIODIESEL_BOTTLE.get());
        if (bottle ? gen.pour(DieselGeneratorBlockEntity.mbIn(stack))
                : FluidUtil.interactWithFluidHandler(player, hand, pos, gen.fluidHandler(), null)) {
            if (bottle) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        player.sendOverlayMessage(Component.translatable("message.nookworks.diesel_generator.full"));
        return Clicks.REFUSED;
    }

    /** The liquid a filled bottle or bucket (or another mod's container) would pour; empty for anything else. */
    private static Fluid liquidIn(ItemStack stack) {
        if (stack.is(ModOil.BIODIESEL_BOTTLE.get())) {
            return OilFluids.BIODIESEL.get();
        }
        if (stack.is(ModOil.VEGETABLE_OIL_BOTTLE.get())) {
            return OilFluids.SOURCE.get();
        }
        return FluidUtil.getFirstStackContained(stack).getFluid();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DieselGeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.DIESEL_GENERATOR.get(), DieselGeneratorBlockEntity::serverTick);
    }

    /** Exhaust out of the stack while it runs: a thin grey stream, now and then a darker puff. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random); // its engine note
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Direction right = facing.getCounterClockWise(); // the front's right, as seen standing before it
        double x = pos.getX() + 0.5 + right.getStepX() * STACK_RIGHT - facing.getStepX() * STACK_BACK;
        double z = pos.getZ() + 0.5 + right.getStepZ() * STACK_RIGHT - facing.getStepZ() * STACK_BACK;
        double y = pos.getY() + 1.02;
        Ventilation.smoke(level, pos, ParticleTypes.SMOKE, x, y, z, 0.0, 0.05, 0.0);
        Ventilation.smoke(level, pos, ParticleTypes.SMOKE, x, y + 0.1, z, 0.0, 0.04, 0.0);
        if (random.nextInt(3) == 0) {
            Ventilation.smoke(level, pos, ParticleTypes.LARGE_SMOKE, x, y, z, 0.0, 0.03, 0.0);
        }
    }

    @Override
    public @Nullable SoundEvent workSound(BlockState state) {
        return state.getValue(LIT) ? ModSounds.DIESEL_GENERATOR_RUN.get() : null;
    }

    /** A comparator reads its fuel tank (see {@link DieselGeneratorBlockEntity#comparatorSignal}). */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof DieselGeneratorBlockEntity gen ? gen.comparatorSignal() : 0;
    }
}
