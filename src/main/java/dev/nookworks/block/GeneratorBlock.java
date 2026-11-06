package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.Move;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GeneratorBlockEntity;
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
import dev.nookworks.fluid.PipePorts;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/** See {@link GeneratorBlockEntity}. Glows, smokes and shows a firebox while burning. */
public class GeneratorBlock extends MachineBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Same as a furnace: enough to light a small room, not a beacon. */
    public static final int LIGHT_LEVEL = 13;

    public GeneratorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.SOUTH));
        this.registerDefaultState(PipePorts.none(this.defaultBlockState()));
    }


    /**
     * A bottle or bucket of vegetable oil or biodiesel pours into its fuel tank, as at the Diesel Generator; anything else
     * opens its screen (r21, 2026-11-06: the click opened the screen and poured nothing, as no other tank of ours does).
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        boolean bottle = stack.is(ModOil.VEGETABLE_OIL_BOTTLE.get()) || stack.is(ModOil.BIODIESEL_BOTTLE.get());
        Fluid fluid = stack.is(ModOil.BIODIESEL_BOTTLE.get()) ? OilFluids.BIODIESEL.get()
                : bottle ? OilFluids.SOURCE.get() : FluidUtil.getFirstStackContained(stack).getFluid();
        if (!(level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator)
                || fluid != OilFluids.SOURCE.get() && fluid != OilFluids.BIODIESEL.get()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS; // the client does not know how full the tank is: the server decides
        }
        boolean poured = bottle ? Move.insertExactly(generator.fluidHandler(), FluidResource.of(fluid), OilFluids.BOTTLE)
                : FluidUtil.interactWithFluidHandler(player, hand, pos, Tanks.fillOnly(generator.fluidHandler()), null);
        if (!poured) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.copper_tank.will_not_take"));
            return Clicks.REFUSED;
        }
        if (bottle) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
        PipePorts.add(builder, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN);
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.GENERATOR.get(), GeneratorBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random); // its crackle
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        // Exhaust out of the top vents, and a furnace-style firebox on the face that looks at you.
        if (random.nextInt(3) == 0) {
            Ventilation.smoke(level, pos, ParticleTypes.SMOKE, x, y + 1.05, z, 0.0, 0.02, 0.0);
        }
        Direction facing = state.getValue(FACING);
        Direction.Axis axis = facing.getAxis();
        double ss = random.nextDouble() * 0.6 - 0.3;
        double dy = random.nextDouble() * 6.0 / 16.0;
        double dx = axis == Direction.Axis.X ? facing.getStepX() * 0.52 : ss;
        double dz = axis == Direction.Axis.Z ? facing.getStepZ() * 0.52 : ss;
        level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
        level.addParticle(ParticleTypes.FLAME, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
    }

    @Override
    public @Nullable SoundEvent workSound(BlockState state) {
        return state.getValue(LIT) ? ModSounds.GENERATOR_RUN.get() : null;
    }
}
