package dev.nookworks.port.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 26.3 NeoForge's {@code ExtractBlockOutlineRenderStateEvent}: the block under the crosshair is about to be outlined;
 * cancel it for no outline, or add a renderer of one's own. Posted on the game's bus by the port from 1.21.1's
 * {@code RenderHighlightEvent.Block}, whose outline is left out when this is cancelled or a renderer says so.
 */
public final class ExtractBlockOutlineRenderStateEvent extends Event implements ICancellableEvent {
    private final ClientLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final BlockHitResult hitResult;
    private final Camera camera;
    private final LevelRenderState levelRenderState;
    private final List<CustomBlockOutlineRenderer> customRenderers = new ArrayList<>();

    ExtractBlockOutlineRenderStateEvent(ClientLevel level, BlockPos pos, BlockState state, BlockHitResult hitResult, Camera camera,
            LevelRenderState levelRenderState) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.hitResult = hitResult;
        this.camera = camera;
        this.levelRenderState = levelRenderState;
    }

    public void addCustomRenderer(CustomBlockOutlineRenderer renderer) {
        this.customRenderers.add(renderer);
    }

    public ClientLevel getLevel() {
        return this.level;
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    public BlockState getBlockState() {
        return this.state;
    }

    public BlockHitResult getHitResult() {
        return this.hitResult;
    }

    /** 1.21.1 draws the outline once, after the see-through blocks. */
    public boolean isInTranslucentPass() {
        return true;
    }

    /** 1.21.1 has no high-contrast outline. */
    public boolean isHighContrast() {
        return false;
    }

    public Camera getCamera() {
        return this.camera;
    }

    public LevelRenderState getLevelRenderState() {
        return this.levelRenderState;
    }

    List<CustomBlockOutlineRenderer> customRenderers() {
        return this.customRenderers;
    }
}
