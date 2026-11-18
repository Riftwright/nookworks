package dev.nookworks.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 26.3's {@code BeaconRenderer.submitBeaconBeam}: a beacon's beam, a turning core in a wider glow, up the pose's y axis
 * from {@code beamStart} for {@code height} blocks. 1.21.1's {@code renderBeaconBeam} draws the same beam, taking the
 * time as whole ticks and the part of one, where 26.3 takes them added together.
 */
public final class BeaconRenderer {
    private BeaconRenderer() {}

    public static void submitBeaconBeam(PoseStack poseStack, SubmitNodeCollector collector, ResourceLocation beamLocation, float scale,
            float animationTime, int beamStart, int height, int color, float solidBeamRadius, float beamGlowRadius) {
        int ticks = Mth.floor(animationTime);
        net.minecraft.client.renderer.blockentity.BeaconRenderer.renderBeaconBeam(poseStack, collector.buffers(), beamLocation,
                animationTime - ticks, scale, ticks, beamStart, height, color, solidBeamRadius, beamGlowRadius);
    }
}
