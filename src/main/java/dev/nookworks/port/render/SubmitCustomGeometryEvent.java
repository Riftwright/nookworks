package dev.nookworks.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.neoforged.bus.api.Event;

/**
 * 26.3 NeoForge's {@code SubmitCustomGeometryEvent}: one's own geometry added to the world each frame, from the camera
 * (the pose stack has no camera offset: translate by a place minus {@code cameraRenderState.pos}). Posted on the game's
 * bus by the port from 1.21.1's {@code RenderLevelStageEvent} after the particles - the world, its see-through blocks
 * and its entities drawn - into buffers drawn as soon as everyone has added theirs ({@link PortClientEvents}).
 */
public final class SubmitCustomGeometryEvent extends Event {
    private final LevelRenderState levelRenderState;
    private final SubmitNodeCollector submitNodeCollector;
    private final PoseStack poseStack;

    SubmitCustomGeometryEvent(LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector, PoseStack poseStack) {
        this.levelRenderState = levelRenderState;
        this.submitNodeCollector = submitNodeCollector;
        this.poseStack = poseStack;
    }

    public LevelRenderState getLevelRenderState() {
        return this.levelRenderState;
    }

    public SubmitNodeCollector getSubmitNodeCollector() {
        return this.submitNodeCollector;
    }

    public PoseStack getPoseStack() {
        return this.poseStack;
    }
}
