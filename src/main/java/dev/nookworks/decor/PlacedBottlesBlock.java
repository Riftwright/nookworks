package dev.nookworks.decor;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.TrayBlock;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Bottles set down on a block (the user, 2026-10-12: "置けるタイプの瓶", small - "黄昏の森とかbeautiflyで使える瓶のサイズ
 * 感"). Sneak and right-click the top of a block with a bottle - empty, a potion, honey, oil, anything in
 * {@link BottleLook}'s data map - and it stands there; up to four to a block, each where the last left room. An empty
 * hand takes the last one back up (into the inventory, as a tray's food). What is in each shows in its colour
 * (models: block/placed_bottle_*, tint i for place i). Broken, the bottles drop.
 */
public class PlacedBottlesBlock extends BaseEntityBlock {
    public static final int MAX = 4;
    public static final IntegerProperty BOTTLES = IntegerProperty.create("bottles", 1, MAX);
    public static final List<BooleanProperty> FILLED = List.of(BooleanProperty.create("filled_0"), BooleanProperty.create("filled_1"),
            BooleanProperty.create("filled_2"), BooleanProperty.create("filled_3"));
    /** Where each stands, its centre in pixels: two to the back, two to the front, a little out of line. */
    public static final double[][] AT = {{5.0, 5.0}, {11.0, 4.5}, {4.5, 11.0}, {11.0, 11.5}};
    private static final VoxelShape[] SHAPES = new VoxelShape[MAX];

    static {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < MAX; i++) {
            shape = Shapes.or(shape, Block.box(AT[i][0] - 1.5, 0, AT[i][1] - 1.5, AT[i][0] + 1.5, 6, AT[i][1] + 1.5));
            SHAPES[i] = shape;
        }
    }

    public PlacedBottlesBlock(Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(BOTTLES, 1);
        for (BooleanProperty filled : FILLED) {
            state = state.setValue(filled, false);
        }
        this.registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOTTLES);
        FILLED.forEach(builder::add);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(BOTTLES) - 1];
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    /** An empty hand takes the last bottle back up, into the inventory; with none left the block goes. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof PlacedBottlesBlockEntity bottles)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack taken = bottles.take();
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false, Prediction.PREDICTED);
            }
            TrayBlock.keepHandEmpty(player.getInventory());
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.6F, 1.4F);
            refresh(level, pos, bottles);
        }
        return InteractionResult.SUCCESS;
    }

    /** The block's state to match its bottles: how many, and which have something in them; none, and it goes. */
    static void refresh(Level level, BlockPos pos, PlacedBottlesBlockEntity bottles) {
        int count = bottles.count();
        if (count == 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        BlockState state = level.getBlockState(pos).setValue(BOTTLES, count);
        for (int i = 0; i < MAX; i++) {
            state = state.setValue(FILLED.get(i), i < count && BottleLook.liquid(bottles.bottle(i)) != -1);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    /**
     * Where {@code held}'s bottle would stand, clicking {@code clicked}'s {@code face}: beside the others there, or on top
     * of {@code clicked}; null when it cannot be set down (not a bottle, no room, water there - so a glass bottle aimed at
     * a river bed still fills).
     */
    public static @Nullable BlockPos placeFor(Level level, BlockPos clicked, Direction face, ItemStack held) {
        if (BottleLook.of(held) == null) {
            return null;
        }
        Block block = ModBlocks.PLACED_BOTTLES.get();
        BlockPos at = level.getBlockState(clicked).is(block) ? clicked : face == Direction.UP ? clicked.above() : null;
        if (at == null) {
            return null;
        }
        BlockState there = level.getBlockState(at);
        if (there.is(block)) {
            return there.getValue(BOTTLES) < MAX ? at : null;
        }
        return there.canBeReplaced() && there.getFluidState().isEmpty() && block.defaultBlockState().canSurvive(level, at) ? at : null;
    }

    /**
     * Sets {@code held}'s bottle down on top of {@code clicked}, or beside the others there; whether it did. A new stand
     * of bottles goes up on the block above the one clicked, so claim mods and spawn protection are asked about it, as
     * the player putting a block there (r15: a bottle went down on anyone's claim). Public for tests.
     */
    public static boolean setDown(Level level, BlockPos clicked, Direction face, Player player, ItemStack held) {
        BlockPos at = placeFor(level, clicked, face, held);
        if (at == null) {
            return false;
        }
        Block block = ModBlocks.PLACED_BOTTLES.get();
        if (!level.getBlockState(at).is(block)) {
            if (level instanceof ServerLevel server && !Permission.mayPlace(server, at, face, player, block.defaultBlockState())) {
                return false;
            }
            level.setBlock(at, block.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!(level.getBlockEntity(at) instanceof PlacedBottlesBlockEntity bottles) || !bottles.add(held)) {
            return false;
        }
        held.consume(1, player);
        refresh(level, at, bottles);
        level.playSound(null, at, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.6F, 1.5F);
        return true;
    }

    /** The pick-block key gives the last bottle set down, as it is (a potion, that potion). */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return level.getBlockEntity(pos) instanceof PlacedBottlesBlockEntity bottles && bottles.count() > 0
                ? bottles.bottle(bottles.count() - 1).copy() : ItemStack.EMPTY;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlacedBottlesBlockEntity(pos, state);
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Placing {
        /**
         * Sneak and right-click with a bottle: set it down (a potion is drunk only without sneaking, as ever). Where it
         * cannot stand the click goes on as usual, unless it was on bottles already there (four, full: nothing happens).
         * Setting one down is putting a block down: not for a spectator, nor in adventure mode (unless the bottle may be
         * placed on what was clicked), and heard at low priority, after a claim mod (r15: a spectator set bottles down).
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (!player.isSecondaryUseActive() || event.getFace() == null || player.isSpectator() || event.getUseItem() == TriState.FALSE
                    || !player.mayUseItemAt(event.getPos().relative(event.getFace()), event.getFace(), event.getItemStack())) {
                return;
            }
            boolean ours = event.getLevel().getBlockState(event.getPos()).is(ModBlocks.PLACED_BOTTLES.get());
            if (placeFor(event.getLevel(), event.getPos(), event.getFace(), event.getItemStack()) == null
                    && !(ours && BottleLook.of(event.getItemStack()) != null)) {
                return;
            }
            if (!event.getLevel().isClientSide()) {
                setDown(event.getLevel(), event.getPos(), event.getFace(), player, event.getItemStack());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }

        private Placing() {}
    }
}
