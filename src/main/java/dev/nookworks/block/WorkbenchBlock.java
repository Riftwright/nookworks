package dev.nookworks.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Workbench: a crafting table built as a machinist's bench, in black steel or in brass and walnut, for a workshop the
 * wooden crafting table looks out of place in. It is a crafting table in every way that matters - the same 3x3 grid,
 * the same recipe book, the same statistic - drawn as a table: legs, a drawer, a shelf with a toolbox, and a vise, a
 * blueprint and a hammer on its top (tools/textures/gen_workbench.py). It faces whoever sets it down.
 */
public class WorkbenchBlock extends HorizontalDirectionalBlock {
    /** The top, the four legs, the shelf and the vise at the front right, as the model draws them facing north. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 12, 0, 16, 14, 16),
            Block.box(1, 0, 1, 3, 12, 3), Block.box(13, 0, 1, 15, 12, 3), Block.box(1, 0, 13, 3, 12, 15), Block.box(13, 0, 13, 15, 12, 15),
            Block.box(2, 3, 2, 14, 4, 14),
            Block.box(11, 14, 1, 15, 16, 4)));

    public WorkbenchBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(state.getMenuProvider(level, pos));
            player.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) -> new Menu(id, inventory, ContainerLevelAccess.create(level, pos), this),
                Component.translatable(this.getDescriptionId()));
    }

    /**
     * The crafting table's own menu (so the client shows the crafting screen, and JEI and the recipe book work as on a
     * crafting table), kept open only as long as the player stays at this bench - vanilla's asks for a crafting table.
     */
    public static final class Menu extends CraftingMenu {
        private final ContainerLevelAccess access;
        private final Block bench;

        public Menu(int id, Inventory inventory, ContainerLevelAccess access, Block bench) {
            super(id, inventory, access);
            this.access = access;
            this.bench = bench;
        }

        @Override
        public boolean stillValid(Player player) {
            return stillValid(this.access, player, this.bench);
        }
    }
}
