package dev.nookworks.client.render;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.block.LaserBlock;
import dev.nookworks.machine.LaserBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the laser's beam as an actual beam.
 * <p>
 * It is the beacon's beam geometry turned on its side: a bright core inside a wider glow, both scrolling and
 * slowly spinning so the beam reads as a beam from any angle instead of a line of dots. The length comes
 * straight off the {@link LaserBlock#BEAM} block state, which the server already keeps up to date, so nothing
 * extra is synced for it.
 */
public class LaserBeamRenderer implements BlockEntityRenderer<LaserBlockEntity, LaserBeamRenderer.State> {
    public static final Identifier BEAM_TEXTURE = Nookworks.id("textures/entity/laser_beam.png");

    /** Thin: a laser is a line, not a pillar of light. */
    private static final float CORE_RADIUS = 0.055F;
    private static final float GLOW_RADIUS = 0.10F;
    /** Hot red; the helper draws the glow from the same colour at a wider radius. */
    private static final int CORE_COLOUR = 0xFFFF4A32;

    public LaserBeamRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static class State extends BlockEntityRenderState {
        public int length;
        public Direction facing = Direction.NORTH;
        public float animationTime;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LaserBlockEntity laser, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(laser, state, partialTicks, cameraPosition, breakProgress);
        var block = laser.getBlockState();
        state.length = block.hasProperty(LaserBlock.BEAM) ? block.getValue(LaserBlock.BEAM) : 0;
        state.facing = block.hasProperty(LaserBlock.FACING) ? block.getValue(LaserBlock.FACING) : Direction.NORTH;
        state.animationTime = AnimationTime.cycle(laser.getLevel(), partialTicks, 40L); // as vanilla's beacon beam
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.length <= 0) {
            return;
        }
        poseStack.pushPose();
        // Stand the beam on end along the barrel: the beacon helper draws up the +Y axis from the block corner,
        // so centre first, turn, then undo the half-block the helper adds back.
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.rotate(alongBarrel(state.facing));
        poseStack.translate(-0.5, 0.0, -0.5);
        BeaconRenderer.submitBeaconBeam(poseStack, collector, BEAM_TEXTURE, 1.0F, state.animationTime,
                0, state.length, CORE_COLOUR, CORE_RADIUS, GLOW_RADIUS);
        poseStack.popPose();
    }

    /** Turns the +Y axis the beacon draws along onto the way the laser points. */
    private static Quaternionf alongBarrel(Direction facing) {
        return switch (facing) {
            case UP -> Axis.YP.rotationDegrees(0.0F);
            case DOWN -> Axis.XP.rotationDegrees(180.0F);
            case NORTH -> Axis.XP.rotationDegrees(-90.0F);
            case SOUTH -> Axis.XP.rotationDegrees(90.0F);
            case EAST -> Axis.ZP.rotationDegrees(-90.0F);
            case WEST -> Axis.ZP.rotationDegrees(90.0F);
        };
    }

    /** The beam is drawn well outside its own block, so it must not be culled with it. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /** Its beam's whole reach: culled by its own block alone, a beam was not drawn while the laser was off screen. */
    @Override
    public AABB getRenderBoundingBox(LaserBlockEntity laser) {
        BlockPos pos = laser.getBlockPos();
        Direction facing = laser.getBlockState().getValue(LaserBlock.FACING);
        return AABB.encapsulatingFullBlocks(pos, pos.relative(facing, LaserBlockEntity.RANGE));
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
