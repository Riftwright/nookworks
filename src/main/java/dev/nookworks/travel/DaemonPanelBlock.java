package dev.nookworks.travel;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Destination Panel, set on the wall beside the home Dock-a-Daemon Door: its screen lists where the player has been,
 * and the one they pick is where the door opens onto for them - for them only, so on a server no one sets another's
 * way. Beside a return door it says where that door leads (home); with no door beside it, that it needs one.
 */
public class DaemonPanelBlock extends HorizontalDirectionalBlock {
    /** Its screen's glow: a little, well short of a torch. */
    public static final int LIGHT_LEVEL = 4;
    /** How far from a door's lower half a panel may be and still be its: two across, a block under to two over. */
    static final int REACH = 2;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(3, 2, 14, 13, 14, 16));

    public DaemonPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Set beside a door on the side it slides to, the door is turned to slide the other way, clear of it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel server) {
            BlockPos door = door(server, pos);
            if (door != null) {
                DaemonDoorBlock.slideAwayFromPanels(level, door);
            }
        }
    }

    /** On a wall, facing out from it; on a floor or ceiling it does not go. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                BlockState state = this.defaultBlockState().setValue(FACING, direction.getOpposite());
                if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return state;
                }
            }
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            BlockPos door = door(server, pos);
            DaemonDoors doors = DaemonDoors.get(server.getServer());
            if (door == null) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.destination_panel.no_door"));
            } else if (!doors.isHome(server.dimension(), door)) {
                var home = doors.home();
                player.sendOverlayMessage(home == null ? Component.translatable("message.nookworks.destination_panel.no_home")
                        : Component.translatable("message.nookworks.destination_panel.leads_home", DaemonDestinations.name(home.dimension())));
            } else {
                List<ResourceKey<Level>> choices = DaemonDestinations.choices(serverPlayer, server.dimension());
                serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new DaemonPanelMenu(id, choices, pos),
                        this.getName()), buf -> DaemonPanelMenu.write(buf, choices));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The Dock-a-Daemon Door this panel is beside (its lower half), the nearest if more stand near; or null. */
    public static @Nullable BlockPos door(ServerLevel level, BlockPos panel) {
        BlockPos best = null;
        for (BlockPos at : BlockPos.betweenClosed(panel.offset(-REACH, -REACH, -REACH), panel.offset(REACH, 1, REACH))) {
            if (DaemonDoors.standsAt(level, at) && (best == null || at.distSqr(panel) < best.distSqr(panel))) {
                best = at.immutable();
            }
        }
        return best;
    }
}
