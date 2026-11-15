package dev.nookworks.travel;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A Dock-a-Daemon Door slid open has its leaf over the wall beside the doorway, and that is where a hand reaches to
 * shut it (the user, 2026-11-13: 「閉じたいときは、開いたときの右側の部分をクリックしたくなる」): a click on the wall's face
 * there, under the leaf, shuts the door as a click on the door does - on both sides at once, so the client slides it as
 * the server does. As at a door, sneaking with something in hand uses that instead. The rail along the doorway's top
 * still takes a click too (the open doorway takes none). The outline drawn there is the leaf's
 * ({@code client.DaemonLeafOutline}), and a hit there meets the leaf, not the wall behind it ({@link #onHit}).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class DaemonLeaf {
    private DaemonLeaf() {}

    /**
     * The lower half of an open door whose slid leaf lies over face {@code face} of the block at {@code pos} - the wall
     * beside its doorway, either half's height - or null.
     */
    public static @Nullable BlockPos over(BlockGetter level, BlockPos pos, @Nullable Direction face) {
        if (face == null || !face.getAxis().isHorizontal()) {
            return null;
        }
        for (Direction toDoor : new Direction[] {face.getClockWise(), face.getCounterClockWise()}) {
            BlockPos at = pos.relative(toDoor);
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof DaemonDoorBlock && state.getValue(DoorBlock.OPEN) && DaemonDoorBlock.front(state) == face
                    && DaemonDoorBlock.slideSide(state) == toDoor.getOpposite()) {
                return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? at : at.below();
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.isSpectator() || player.isSecondaryUseActive()
                && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())) {
            return;
        }
        Level level = event.getLevel();
        BlockHitResult hit = event.getHitVec();
        BlockPos door = over(level, event.getPos(), hit.getDirection());
        if (door == null) {
            return;
        }
        BlockState state = level.getBlockState(door);
        InteractionResult result = state.useWithoutItem(level, player, new BlockHitResult(hit.getLocation(), hit.getDirection(), door, false));
        if (result.consumesAction()) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    /**
     * A hit on the leaf slid over the wall does not dig the wall behind it: the outline there is the leaf's, and the wall
     * is the door's frame - dug out from under the leaf, it took the door's gate away (1.7 review). Shut the door first,
     * or dig the wall from another face. On both sides, so the client starts no digging the server would refuse.
     */
    @SubscribeEvent
    static void onHit(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && !event.getEntity().isSpectator()
                && over(event.getLevel(), event.getPos(), event.getFace()) != null) {
            event.setCanceled(true);
        }
    }
}
