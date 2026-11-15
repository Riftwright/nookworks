package dev.nookworks.client;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.block.PlanterBlock;
import dev.nookworks.client.render.WorkOutline;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Where a Planter plants, shown on the ground (the user, 2026-11-14: 植え付けできる範囲のプレビューを出せる機能があると
 * いいなぁ。プレビューはNWらしい一貫性のある見た目で): the soil under its fifteen spots skinned and rimmed in brass, as a
 * Building Wand's click is ({@link WorkOutline}, the one frame of ours).
 * <p>
 * It shows when it is wanted, with nothing to switch on:
 * <ul>
 * <li>looking at a Planter: its own field;</li>
 * <li>a hoe or a Planter in either hand: the field of every Planter within {@link #NEAR} blocks - the ground to till,
 * and where the next Planter's field would meet them;</li>
 * <li>a Planter in hand, aimed where it would be set down: the field it would have there.</li>
 * </ul>
 * All of it is one shape, drawn once: two Planters' fields that overlap would be two skins in one plane, and flicker.
 * Worked out from {@link PlanterBlockEntity#spots}, the list the planting itself goes by - widened as an add-on has a
 * Planter's field ({@link PlanterBlockEntity#reach}, synced); a Planter yet to be set down shows the field it starts
 * with. Client only: nothing is sent or kept.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class PlanterPreview {
    /** How far from the player a Planter's field is still shown, with a hoe or a Planter in hand. */
    private static final int NEAR = 16;

    /** What the fields were last worked out from: every few ticks, or when any of this changes. */
    private record Key(BlockPos player, long tick, boolean tool, @Nullable BlockPos looked, @Nullable BlockPos placing, Direction facing) {}

    private static @Nullable Key lastKey;
    private static Set<BlockPos> last = Set.of();

    private PlanterPreview() {}

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || player.isSpectator()) {
            return;
        }
        ItemStack held = player.getMainHandItem().is(ModBlocks.PLANTER.asItem()) ? player.getMainHandItem() : player.getOffhandItem();
        boolean placer = held.is(ModBlocks.PLANTER.asItem());
        boolean tool = placer || player.getMainHandItem().is(ItemTags.HOES) || player.getOffhandItem().is(ItemTags.HOES);
        BlockPos looked = null;
        BlockPos placing = null;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof PlanterBlock) {
                looked = hit.getBlockPos();
            } else if (placer) {
                // where the block in hand would go: into what is clicked if that gives way (grass), else against the face
                BlockPos at = state.canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
                if (level.getBlockState(at).canBeReplaced()) {
                    placing = at;
                }
            }
        }
        if (!tool && looked == null) {
            lastKey = null;
            return;
        }
        Key key = new Key(player.blockPosition(), level.getGameTime() / 8, tool, looked, placing, player.getDirection());
        if (!key.equals(lastKey)) {
            lastKey = key;
            last = fields(level, player, tool, looked, placing);
        }
        if (!last.isEmpty()) {
            WorkOutline.cells(event, last, Direction.UP, WorkOutline.Style.WORK);
        }
    }

    /** The soil under every spot to show, as one set. */
    private static Set<BlockPos> fields(Level level, LocalPlayer player, boolean tool, @Nullable BlockPos looked, @Nullable BlockPos placing) {
        Set<BlockPos> soil = new HashSet<>();
        if (looked != null) {
            add(soil, level.getBlockEntity(looked));
        }
        if (tool) {
            BlockPos here = player.blockPosition();
            for (int cx = (here.getX() - NEAR) >> 4; cx <= (here.getX() + NEAR) >> 4; cx++) {
                for (int cz = (here.getZ() - NEAR) >> 4; cz <= (here.getZ() + NEAR) >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        if (entity instanceof PlanterBlockEntity && entity.getBlockPos().closerThan(here, NEAR)) {
                            add(soil, entity);
                        }
                    }
                }
            }
        }
        if (placing != null) {
            // set down, a Planter faces the way its placer looks: the field lies beyond it
            for (BlockPos spot : PlanterBlockEntity.spots(placing, player.getDirection())) {
                soil.add(spot.below());
            }
        }
        return soil;
    }

    private static void add(Set<BlockPos> soil, @Nullable BlockEntity entity) {
        if (entity instanceof PlanterBlockEntity planter && planter.getBlockState().getBlock() instanceof PlanterBlock) {
            for (BlockPos spot : planter.spots()) {
                soil.add(spot.below());
            }
        }
    }
}
