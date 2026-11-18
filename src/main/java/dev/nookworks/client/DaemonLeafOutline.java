package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.travel.DaemonDoorBlock;
import dev.nookworks.travel.DaemonLeaf;
import net.minecraft.client.Minecraft;
import dev.nookworks.port.render.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import dev.nookworks.port.core.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.nookworks.port.render.ExtractBlockOutlineRenderStateEvent;

/**
 * Over the wall where a Dock-a-Daemon Door's leaf lies slid open, the outline is the leaf's, not the wall block's: a
 * click there shuts the door ({@link DaemonLeaf}), so what is outlined is what the click takes - both halves of the
 * leaf, two pixels out from the wall, as a door's own outline is the whole leaf.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class DaemonLeafOutline {
    private DaemonLeafOutline() {}

    @SubscribeEvent
    static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        BlockPos wall = event.getBlockPos();
        Direction face = event.getHitResult().getDirection();
        BlockPos door = DaemonLeaf.over(event.getLevel(), wall, face);
        if (door == null) {
            return;
        }
        // the leaf over the wall's two blocks beside the doorway, in front of their face, from the lower one's corner
        BlockPos lower = door.relative(DaemonDoorBlock.slideSide(event.getLevel().getBlockState(door)));
        VoxelShape leaf = Shapes.rotateHorizontal(Block.box(0, 0, -2, 16, 32, 0)).get(face);
        boolean translucent = event.isInTranslucentPass();
        boolean contrast = event.isHighContrast();
        event.addCustomRenderer((state, collector, pose, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            pose.pushPose();
            pose.translate(lower.getX() - camera.x, lower.getY() - camera.y, lower.getZ() - camera.z);
            float width = dev.nookworks.port.render.RenderTypes.lineWidth();
            if (contrast) {
                collector.submitShapeOutline(pose, leaf, RenderTypes.secondaryBlockOutline(), 0xFF000000, 7.0F, translucent);
            }
            collector.submitShapeOutline(pose, leaf, contrast ? RenderTypes.linesDepthBias() : RenderTypes.linesTranslucent(),
                    contrast ? 0xFF57FFE1 : ARGB.black(102), width, translucent);
            pose.popPose();
            return true; // not the wall's
        });
    }
}
