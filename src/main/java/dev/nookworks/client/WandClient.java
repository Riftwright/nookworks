package dev.nookworks.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.client.render.WorkOutline;
import dev.nookworks.compat.FeatureGate;
import dev.nookworks.item.BuildingWandItem;
import dev.nookworks.item.WandBox;
import dev.nookworks.item.WandMode;
import dev.nookworks.item.WandWork;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Building Wands in the hand, on the client (the user, 2026-11-07): what a click would do is shown where it would
 * happen - the squares it would fill, and with the Reinforced Building Wand the surface it would replace (brick red where
 * Replace would refuse) or the box being marked out; and with that wand scrolling while sneaking steps its mode instead
 * of the hotbar.
 * <p>
 * Drawn by {@link WorkOutline}, as nookTechnology draws a Conveyor Belt about to be laid (its BeltPreview), so the two
 * read as one family: a faint dark-steel skin over the squares' outer faces, brass corner brackets at a box's corners,
 * and a brass rim round a spread's outline - copper for a surface to be replaced, a muted brick red for a box too big to
 * fill or a surface Replace refuses.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class WandClient {
    private WandClient() {}

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.screen() != null || !player.isShiftKeyDown() || event.getScrollDeltaY() == 0
                || !BuildingWandItem.hasModes(player.getMainHandItem()) || !FeatureGate.wand()) {
            return; // the plain wand has no modes: the hotbar scrolls as normal
        }
        event.setCanceled(true);
        ClientPacketDistributor.sendToServer(new NookworksNetwork.StepWandMode(event.getScrollDeltaY() > 0 ? -1 : 1));
    }

    /** What the preview was last worked out from. */
    private record Key(BlockPos pos, Direction face, BlockState state, WandMode mode, @Nullable WandBox box,
            net.minecraft.world.item.Item other, long tick) {}

    private static @Nullable Key lastKey;
    private static @Nullable Preview last;

    /**
     * What to show: the squares, and how. A box (no cells listed) is squared up by corner brackets; a spread or a surface,
     * whose squares lie as the walk from the click reached them - a diamond, a ragged patch - has a rim drawn round its
     * outline on the face clicked, as the belt's top is rimmed: brackets on its bounding box stood in the air beside it
     * (the user, 2026-11-07: 「黄色い枠とグレーの枠がずれる」).
     */
    private record Preview(Set<BlockPos> cells, BlockPos min, BlockPos max, WorkOutline.Style style, Direction face) {}

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(player.getMainHandItem().getItem() instanceof BuildingWandItem) || player.isSpectator() || !FeatureGate.wand()) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        ItemStack wand = player.getMainHandItem();
        // worked out again only when what it depends on changes, or every few ticks for the blocks round it (r1 review: it
        // walked up to 256 blocks every frame)
        Key key = new Key(pos, hit.getDirection(), mc.level.getBlockState(pos), BuildingWandItem.mode(wand),
                wand.get(ModDataComponents.WAND_BOX.get()), player.getOffhandItem().getItem(), mc.level.getGameTime() / 4);
        if (!key.equals(lastKey)) {
            lastKey = key;
            last = preview(mc.level, player, wand, pos, hit.getDirection(), key.state());
        }
        Preview preview = last;
        if (preview == null) {
            return;
        }
        if (preview.cells().isEmpty()) {
            WorkOutline.box(event, preview.min(), preview.max(), preview.style());
        } else {
            WorkOutline.cells(event, preview.cells(), preview.face(), preview.style());
        }
    }

    private static @Nullable Preview preview(Level level, LocalPlayer player, ItemStack wand, BlockPos pos, Direction face, BlockState state) {
        WandMode mode = BuildingWandItem.mode(wand);
        if (mode.box()) {
            WandBox box = wand.get(ModDataComponents.WAND_BOX.get());
            BlockPos aim = pos.relative(face);
            if (box == null || box.first().dimension() != level.dimension()) {
                return box(aim, aim, true);
            }
            BlockPos first = box.first().pos();
            BlockPos second = box.second().orElse(aim);
            return box(first, second, WandBox.fits(first, second));
        }
        if (mode == WandMode.REPLACE) {
            // as the server will answer (WandWork.replaceRefusal): nothing shown with no block to put in, or the same one;
            // brick red where it would refuse - not a full block, an ore, too hard, no tool for it
            String refusal = WandWork.replaceRefusal(level, player, pos, state);
            if ("need_block".equals(refusal) || "same_block".equals(refusal) || state.isAir()) {
                return null;
            }
            List<BlockPos> surface = WandWork.surface(level, pos, face, state);
            return cells(surface, refusal == null ? WorkOutline.Style.REPLACE : WorkOutline.Style.REFUSED, face);
        }
        if (!BuildingWandItem.canCopy(state)) {
            return null;
        }
        return cells(BuildingWandItem.plan(level, pos, face, state), WorkOutline.Style.WORK, face);
    }

    /** A box: its outer faces only, so the cells need not be listed (one of 4,096 would be). */
    private static Preview box(BlockPos a, BlockPos b, boolean fits) {
        return new Preview(Set.of(), BlockPos.min(a, b), BlockPos.max(a, b), fits ? WorkOutline.Style.WORK : WorkOutline.Style.REFUSED, Direction.UP);
    }

    private static @Nullable Preview cells(List<BlockPos> cells, WorkOutline.Style style, Direction face) {
        if (cells.isEmpty()) {
            return null;
        }
        BlockPos min = cells.getFirst();
        BlockPos max = min;
        for (BlockPos cell : cells) {
            min = BlockPos.min(min, cell);
            max = BlockPos.max(max, cell);
        }
        return new Preview(new HashSet<>(cells), min, max, style, face);
    }
}
