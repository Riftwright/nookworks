package dev.nookworks.mercury;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.nookworks.block.Clicks;
import dev.nookworks.block.MachineBlock;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GaugePouring;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.ItemAccess;
import dev.nookworks.port.transfer.FluidUtil;
import dev.nookworks.fluid.PipePorts;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.ItemInteractionResult;
import dev.nookworks.port.core.ItemInteractions;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

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
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return PipePorts.updated(super.updateShape(state, side, neighbourState, level, pos, neighbourPos), side, neighbourState);
    }

    /**
     * A Mercury Bucket clicked on a machine holding mercury, as at its screen's mercury gauge: poured into one that uses
     * mercury (the Stirring Tank, the Electrolytic Cell), which buckets do by a click (the click rules of 2026-11-05);
     * turned down by one that makes it (the Retort, the Distiller), whose mercury goes out into a Mercury Tank. It was
     * turned down everywhere with "into a Mercury Tank" (round two of r21, and its re-check).
     */
    public static ItemInteractionResult pourMercuryBucket(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof MercuryMachineBlockEntity machine)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // PORT: 26.3's TRY_WITH_EMPTY_HAND
        }
        if (machine.mercuryRole() == MercuryHandler.Role.PRODUCER) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.nookworks.mercury_bucket.into_tank"), true);
            }
            return ItemInteractions.of(Clicks.REFUSED);
        }
        if (!level.isClientSide()) {
            // into its store, as the gauge pours: not through fillMercury, which keeps pipes from routing mercury to a
            // Stirring Tank busy with biodiesel or mud (the click refused what the gauge took)
            MercuryStore store = machine.mercuryStore();
            if (store.fill(GaugePouring.BUCKET, true) < GaugePouring.BUCKET) {
                player.displayClientMessage(Component.translatable("message.nookworks.mercury_bucket.no_room", store.amount(), store.capacity()), true);
                return ItemInteractions.of(Clicks.REFUSED);
            }
            store.fill(GaugePouring.BUCKET, false);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.2F); // as at a Mercury Tank
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
        }
        return ItemInteractionResult.SUCCESS;
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
     * A filled bucket on the Stirring Tank pours water, vegetable oil or methanol into its tanks; an empty one opens its
     * screen, whose Liquid tab draws the biodiesel (since 1.5: the user's rule for both mods, 2026-11-08 - containers are
     * filled from a machine only by its Liquid tab or from a tank, 「バケツ・瓶からくみ出したいときは、機械の液体タブを
     * 使うか、一度タンクに移動してタンクから汲みだす」); anything else opens its screen, as on any machine.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof MercuryBucketItem) {
            return ItemInteractions.of(pourMercuryBucket(stack, level, pos, player, hand));
        }
        if (this.kind == Kind.STIRRING_TANK && level.getBlockEntity(pos) instanceof StirringTankBlockEntity tank
                && !FluidUtil.getFirstStackContained(stack).isEmpty()
                && ItemAccess.forPlayerInteraction(player, hand).getCapability(Capabilities.Fluid.ITEM) != null) {
            // The client does not know what the tanks hold: the click ends here and the server decides, as at a Copper
            // Tank. Had it gone on, a bucket the server had just emptied would be poured out in front of the tank.
            if (level.isClientSide() || FluidUtil.interactWithFluidHandler(player, hand, pos, Tanks.fillOnly(tank.fluidHandler()), null)) {
                return ItemInteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.nookworks.stirring_tank.will_not_take"), true);
            return ItemInteractions.of(Clicks.REFUSED);
        }
        if (this.kind == Kind.RETORT && level.getBlockEntity(pos) instanceof RetortBlockEntity retort
                && FluidUtil.getFirstStackContained(stack).getFluid() == Fluids.WATER) {
            // a water bucket pours into its water tank, as into the Stirring Tank's and the Electrolytic Cell's (r21: the
            // click opened the screen, the bucket left full); an empty one draws nothing out - the experience is the
            // screen's to give
            if (level.isClientSide() || FluidUtil.interactWithFluidHandler(player, hand, pos, Tanks.fillOnly(retort.waterHandler()), null)) {
                return ItemInteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.nookworks.copper_tank.will_not_take"), true);
            return ItemInteractions.of(Clicks.REFUSED);
        }
        if (this.kind == Kind.DISTILLER && level.getBlockEntity(pos) instanceof DistillerBlockEntity distiller
                && FluidUtil.getFirstStackContained(stack).getFluid() == OilFluids.WOOD_VINEGAR.get()) {
            // a bucket of wood vinegar pours into its wood vinegar tank, as water into the Retort's (a bottle goes into
            // the charge slot, and is poured from there)
            if (level.isClientSide() || FluidUtil.interactWithFluidHandler(player, hand, pos, Tanks.fillOnly(distiller.woodVinegarTank()), null)) {
                return ItemInteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.translatable("message.nookworks.copper_tank.will_not_take"), true);
            return ItemInteractions.of(Clicks.REFUSED);
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

    // PORT: 1.21.1 asks every block for a codec (26.3 has none); only the block-list data report reads it
    @Override
    protected MapCodec<? extends MercuryMachineBlock> codec() {
        return BlockCodecs.of(this);
    }
}
