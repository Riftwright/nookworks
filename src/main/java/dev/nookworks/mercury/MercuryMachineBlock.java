package dev.nookworks.mercury;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.nookworks.block.Clicks;
import dev.nookworks.block.MachineBlock;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import dev.nookworks.fluid.PipePorts;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Block of the three mercury machines (Sealed Retort, Stirring Tank, Vacuum Distiller). Like the other FE
 * machines: right-click with an input to load it, empty hand for status, hoppers in from above/sides, out below. A
 * bucket fills or empties the Stirring Tank's tanks.
 */
public class MercuryMachineBlock extends MachineBlock {
    public enum Kind implements StringRepresentable {
        RETORT, STIRRING_TANK, DISTILLER;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private final Kind kind;

    public MercuryMachineBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        if (this.defaultBlockState().hasProperty(MachineBlockEntity.RUNNING)) {
            this.registerDefaultState(this.defaultBlockState().setValue(MachineBlockEntity.RUNNING, false));
        }
        this.registerDefaultState(PipePorts.none(this.defaultBlockState()));
    }

    /**
     * The Stirring Tank's and the Distiller's status lamp. The Sealed Retort, drawn by hand, shows its firebox instead
     * ({@link RetortBlock} has states of its own).
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MachineBlockEntity.RUNNING);
        PipePorts.add(builder, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN);
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
    public @Nullable SoundEvent workSound(BlockState state) {
        if (!state.hasProperty(MachineBlockEntity.RUNNING) || !state.getValue(MachineBlockEntity.RUNNING)) {
            return null;
        }
        return switch (this.kind) {
            case STIRRING_TANK -> ModSounds.STIRRING_TANK_RUN.get();
            case DISTILLER -> ModSounds.DISTILLER_RUN.get();
            case RETORT -> null;
        };
    }

    @Override
    protected boolean holdsMercury() {
        return true;
    }

    /**
     * A bucket on the Stirring Tank pours water or vegetable oil into its tanks, and an empty one takes its biodiesel
     * out, as on a Copper Tank; anything else goes into its charge slot or opens its screen, as on any machine.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof MercuryBucketItem) {
            // said where it goes: the click opened the screen and said nothing, as if missed (play-test, 2026-10-19)
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.mercury_bucket.into_tank"));
            }
            return Clicks.REFUSED;
        }
        if (this.kind == Kind.STIRRING_TANK && level.getBlockEntity(pos) instanceof StirringTankBlockEntity tank
                && ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) != null) {
            // The client does not know what the tanks hold: the click ends here and the server decides, as at a Copper
            // Tank. Had it gone on, a bucket the server had just emptied would be poured out in front of the tank.
            if (level.isClientSide() || FluidUtil.interactWithFluidHandler(player, hand, pos, tank.fluidHandler(), null)) {
                return InteractionResult.SUCCESS;
            }
            player.sendOverlayMessage(Component.translatable("message.nookworks.stirring_tank.will_not_take"));
            return Clicks.REFUSED;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (this.kind) {
            case RETORT -> new RetortBlockEntity(pos, state);
            case STIRRING_TANK -> new StirringTankBlockEntity(pos, state);
            case DISTILLER -> new DistillerBlockEntity(pos, state);
        };
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return switch (this.kind) {
            case RETORT -> createTickerHelper(type, ModBlockEntities.RETORT.get(), RetortBlockEntity::serverTick);
            case STIRRING_TANK -> createTickerHelper(type, ModBlockEntities.STIRRING_TANK.get(), withLamp(StirringTankBlockEntity::serverTick));
            case DISTILLER -> createTickerHelper(type, ModBlockEntities.DISTILLER.get(), withLamp(DistillerBlockEntity::serverTick));
        };
    }
}
