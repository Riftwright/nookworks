package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.compat.FeatureGate;
import dev.nookworks.item.Hammers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Hammers mine the 3x3 square around the broken block, in the plane facing the player.
 * Only triggers when the hammer suits the block that was hit. Neighbours are only taken if the hammer
 * mines them properly (with Earthmover: also dirt and wood), they hold no block entity (no spilling
 * furnaces or chests), they are not much harder than the block that was hit (no free obsidian), and they are not in
 * {@link #SPARES} - what can never be had back once broken, as Budding Amethyst, broken in passing in a geode.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class HammerMining {
    /** A neighbour may be at most this many times as hard as the centre block (plus a small allowance). */
    private static final float HARDNESS_RATIO = 2.0F;
    private static final float HARDNESS_ALLOWANCE = 0.5F;
    /** Never taken in passing, though the hammer could: a pack can add to it. */
    public static final TagKey<Block> SPARES =
            TagKey.create(Registries.BLOCK, Nookworks.id("hammer_spares"));

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BreakBlockEvent event) {
        if (ExtraBreaks.inProgress() || Permission.asking() || event.isCanceled()
                || !(event.getPlayer() instanceof ServerPlayer player)
                || event.getPlayer() instanceof FakePlayer
                || !(event.getLevel() instanceof ServerLevel level)
                || player.preventsBlockDrops()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(Hammers.TAG) || !FeatureGate.hammer()
                || !NookworksConfig.HAMMER_ACTIVATION.get().allows(player.isShiftKeyDown())) {
            return;
        }
        BlockPos origin = event.getPos();
        if (!ChainBreaking.outermost(player, origin)) {
            return; // a block another mod breaks for the player inside their own break: no 3x3 of its own
        }
        if (!Earthmover.hammerHandles(tool, event.getState())) {
            return; // hitting dirt with a plain hammer must not sweep the stone around it
        }
        Direction face = hitFace(player, origin);
        ExtraBreaks.breakAll(player, tool, targets(level, player, tool, origin, event.getState(), face));
    }

    /** The face the player is looking at; falls back to the nearest looking direction. */
    static Direction hitFace(ServerPlayer player, BlockPos origin) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(origin)) {
            return blockHit.getDirection();
        }
        return Direction.orderedByNearest(player)[0].getOpposite();
    }

    static List<BlockPos> targets(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos origin,
            BlockState originState, Direction face) {
        float maxHardness = originState.getDestroySpeed(level, origin) * HARDNESS_RATIO + HARDNESS_ALLOWANCE;
        Direction.Axis axis = face.getAxis();
        List<BlockPos> result = new ArrayList<>(8);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                BlockPos pos = switch (axis) {
                    case X -> origin.offset(0, a, b);
                    case Y -> origin.offset(a, 0, b);
                    case Z -> origin.offset(a, b, 0);
                };
                BlockState state = level.getBlockState(pos);
                float hardness = state.getDestroySpeed(level, pos);
                if (!state.isAir() && hardness >= 0 && hardness <= maxHardness
                        && !state.hasBlockEntity() && !state.is(SPARES)
                        && Earthmover.hammerHandles(tool, state)
                        && state.canHarvestBlock(level, pos, player)) {
                    result.add(pos);
                }
            }
        }
        return result;
    }

    private HammerMining() {}
}
