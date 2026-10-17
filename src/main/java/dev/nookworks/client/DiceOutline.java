package dev.nookworks.client;

import org.joml.Quaternionf;

import dev.nookworks.Nookworks;
import dev.nookworks.block.DiceBlock;
import dev.nookworks.block.DiceTumble;
import dev.nookworks.block.entity.DiceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;

/**
 * A die's outline drawn round the die where it lies, turned as it is, as vanilla draws a block's (the same lines, the
 * same high-contrast option): its block's shape, made of boxes on the block's axes, could only stair-step round a die
 * lying at a slant. While it is in the air no outline is drawn where it lay.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class DiceOutline {
    /** The die about its middle. */
    private static final VoxelShape DIE = Shapes.box(-DiceTumble.HALF, -DiceTumble.HALF, -DiceTumble.HALF,
            DiceTumble.HALF, DiceTumble.HALF, DiceTumble.HALF);
    private static final int HIGH_CONTRAST = -11010079;

    @SubscribeEvent
    static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getBlockState().getBlock() instanceof DiceBlock)
                || !(event.getLevel().getBlockEntity(event.getBlockPos()) instanceof DiceBlockEntity die)) {
            return;
        }
        if (event.getBlockState().getValue(DiceBlock.ROLL) > 0) {
            event.setCanceled(true);
            return;
        }
        double[] pose = die.lying(event.getBlockState());
        BlockPos pos = event.getBlockPos();
        Quaternionf turn = new Quaternionf((float) pose[3], (float) pose[4], (float) pose[5], (float) pose[6]);
        float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x + pose[0], pos.getY() - camera.y + pose[1], pos.getZ() - camera.z + pose[2]);
            poseStack.rotate(turn);
            if (state.highContrast()) {
                collector.submitShapeOutline(poseStack, DIE, RenderTypes.secondaryBlockOutline(), 0xFF000000, 7.0F, state.isTranslucent());
            }
            int colour = state.highContrast() ? HIGH_CONTRAST : ARGB.black(102); // vanilla's outline colours
            collector.submitShapeOutline(poseStack, DIE, RenderTypes.lines(), colour, width, state.isTranslucent());
            poseStack.popPose();
            return true;
        });
    }

    private DiceOutline() {}
}
