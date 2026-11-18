package dev.nookworks.port.render;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.GlStateBackup;

/**
 * A drawing of the mod's own in the middle of the game's frame (a Standing Mirror's picture, its glass over a shader
 * pack's picture, the pack's light read off the screen): what the game's drawing has set up is taken down when the pass
 * starts and put back exactly when it ends, so the world's own drawing goes on as if nothing had happened between.
 * 26.3 draws such passes in render passes of their own, which leave nothing behind; 1.21.1 draws through one OpenGL
 * state, so it is kept here by hand: the framebuffers bound for drawing and reading, the viewport, the projection and
 * its vertex sorting, the model-view stack, depth, blending, culling and colour mask (NeoForge's GlStateBackup), the
 * active shader and texture unit, the first three shader textures (the picture, the overlay, the lightmap), the shader
 * colour and the fog. The vertex array bound is let go (as the game's own drawings leave it).
 */
public final class GlPass implements AutoCloseable {
    private final GlStateBackup gl = new GlStateBackup();
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int[] viewport = new int[4];
    private final Matrix4f projection;
    private final VertexSorting sorting;
    private final @Nullable ShaderInstance shader;
    private final int activeTexture;
    private final int[] textures = new int[3];
    private final float[] shaderColour;
    private final float fogStart;
    private final float fogEnd;
    private final float[] fogColour;
    private final FogShape fogShape;

    private GlPass() {
        RenderSystem.assertOnRenderThread();
        RenderSystem.backupGlState(this.gl);
        this.drawFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        this.readFramebuffer = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.viewport);
        this.projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        this.sorting = RenderSystem.getVertexSorting();
        this.shader = RenderSystem.getShader();
        this.activeTexture = GlStateManager._getActiveTexture();
        for (int i = 0; i < this.textures.length; i++) {
            this.textures[i] = RenderSystem.getShaderTexture(i);
        }
        this.shaderColour = RenderSystem.getShaderColor().clone();
        this.fogStart = RenderSystem.getShaderFogStart();
        this.fogEnd = RenderSystem.getShaderFogEnd();
        this.fogColour = RenderSystem.getShaderFogColor().clone();
        this.fogShape = RenderSystem.getShaderFogShape();
        RenderSystem.getModelViewStack().pushMatrix();
    }

    /** The state as it stands now, to be put back by {@link #close()}. */
    public static GlPass begin() {
        return new GlPass();
    }

    /** The fog's colour when the pass began (the world's, as its fog is drawn this frame). */
    public float[] fogColour() {
        return this.fogColour.clone();
    }

    @Override
    public void close() {
        VertexBuffer.unbind();
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(this.projection, this.sorting);
        RenderSystem.restoreGlState(this.gl);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.readFramebuffer);
        RenderSystem.viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
        for (int i = 0; i < this.textures.length; i++) {
            RenderSystem.setShaderTexture(i, this.textures[i]);
        }
        GlStateManager._activeTexture(this.activeTexture);
        RenderSystem.setShaderColor(this.shaderColour[0], this.shaderColour[1], this.shaderColour[2], this.shaderColour[3]);
        RenderSystem.setShaderFogStart(this.fogStart);
        RenderSystem.setShaderFogEnd(this.fogEnd);
        RenderSystem.setShaderFogColor(this.fogColour[0], this.fogColour[1], this.fogColour[2], this.fogColour[3]);
        RenderSystem.setShaderFogShape(this.fogShape);
        ShaderInstance shader = this.shader;
        if (shader != null) {
            RenderSystem.setShader(() -> shader);
        }
    }
}
