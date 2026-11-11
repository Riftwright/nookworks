package dev.nookworks.client.render;

import java.util.Locale;
import java.util.Optional;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import dev.nookworks.Nookworks;
import dev.nookworks.client.NookworksClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.level.CardinalLighting;

/**
 * Under a shader pack, how the pack colours each level of light, measured from the pack itself (the user's ask,
 * 2026-11-11: the pack's look in the mirror, by a way that does not break, is light and is sure; DECISIONS).
 *
 * <p>A mirror's glass is painted over once the pack's picture is finished ({@link MirrorReflections}), so what the pack
 * draws there is never seen. A plain grey board is drawn on it for the pack to light as it lights anything: its vertices
 * name every block light across it and every sky light up it (none at the edges, the most in the middle), and the pack
 * paints on it how it colours each of them - at this hour, in this weather, at this place on the screen, with whatever
 * it does afterwards (its tone mapping, its exposure). Read back into a table the size of the game's own lightmap
 * (block light across, sky light up), that is what the mirror's picture is lit with in place of the game's table
 * ({@code GameRendererMirrorLightMixin}). Nothing of Iris's is touched for it, nor is anything known of the pack.
 *
 * <p>The board is read only where the glass's picture will be seen: where the world's depth says something stands
 * before the glass - a hand, what it holds, a fence, a passer-by - it is that thing's colour on the screen, not the
 * board's (a bare arm before a mirror at the screen's edge turned its picture yellow). Every level is on the board
 * four times, once in each quarter, so a mirror half off the screen or half hidden is still read whole; a level seen
 * nowhere keeps what it had, and a table starts as the game's own.
 *
 * <p>What it does not give: the pack's shadows and sunlight (the board says it faces down, so no sun is on it, and the
 * picture is lit as the pack lights the shade), its sky, its water, the glow round its lights.
 *
 * <p>The table is of floats, where the game's own is of bytes: a pack may light the grey board to more than its own
 * grey, which is more light than a byte can say. A face lit so comes out no brighter than white, as any picture.
 */
public final class MirrorLight {
    /** Whether mirrors measure the pack's light: not at all, they do, or (the dev bridge's) with the board left showing. */
    public enum Mode { OFF, ON, SHOW }

    /** The dev bridge's word; null, as the player's setting has it. */
    private static volatile @Nullable Mode forced;

    /** On unless the player's setting says not (Mods > Nookworks > Config), or the dev bridge has said otherwise. */
    static Mode mode() {
        Mode forced = MirrorLight.forced;
        if (forced != null) {
            return forced;
        }
        return NookworksClientConfig.SPEC.isLoaded() && !NookworksClientConfig.MIRROR_PACK_LIGHT.get() ? Mode.OFF : Mode.ON;
    }

    /** The board: plain white, greyed by its vertices, lit by the light levels they name. */
    static final RenderType BOARD = RenderTypes.entitySolid(PixelFaces.LIGHT);
    /** Before the model's glass, behind the picture drawn over it. */
    static final float OUT = MirrorReflections.GLASS_OUT + 0.006F;
    /**
     * The board is darkest at its edges and brightest in its middle, each way: this much of it at every edge holds no
     * light at all, so the pack's glow round bright things does not spill onto the frame (lit to its edge, the board's
     * brightest side shone on the post beside it at night)...
     */
    static final float MARGIN = 0.10F;
    /** ...and this much each side of the middle holds the most. Between them the levels run evenly. */
    static final float PEAK = 0.06F;
    /**
     * Along the top and the bottom, this much holds no light of either kind: the brightest block light runs up the
     * board's middle, and would end on the frame there.
     */
    static final float EDGE = 0.035F;
    /** The board's grey: about what a block's texture is. */
    static final int GREY = 153;
    /** How much of a frame's reading goes into the table: the rest is what it held. */
    private static final float WEIGHT = 0.25F;
    /** A table not read for this long is taken whole from the next reading: the light may have changed since (ms). */
    private static final long STALE_MS = 500;

    public static final RenderPipeline READ = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Nookworks.id("pipeline/mirror_light"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Nookworks.id("core/mirror_light"))
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_COLOR))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    /** A new table begun as the game's own lightmap, copied (by the game's own copying shader): what a level not yet read is lit by. */
    public static final RenderPipeline START = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Nookworks.id("pipeline/mirror_light_start"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private static @Nullable GpuTextureView inUse;

    private MirrorLight() {}

    /** The dev bridge's switch: mirrorlight off|on|show, or auto for the player's setting again. */
    public static void set(String word) {
        forced = word.equals("auto") ? null : Mode.valueOf(word.toUpperCase(Locale.ROOT));
    }

    /** The table the game's lightmap is to be read as just now, or null: its own. */
    public static @Nullable GpuTextureView inUse() {
        return inUse;
    }

    static void use(@Nullable GpuTextureView table) {
        inUse = table;
    }

    /** A measured table: the pack's light for each block light (across) and sky light (up), as much as it is. */
    static final class Table {
        final GpuTexture texture;
        final GpuTextureView view;
        boolean measured;
        /** When it was last read. */
        long read;

        Table(int index) {
            var device = RenderSystem.getDevice();
            this.texture = device.createTexture("Nookworks mirror light " + index,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA16_FLOAT, 16, 16, 1, 1);
            this.view = device.createTextureView(this.texture);
            try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Nookworks mirror light start", this.view, Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(START));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", Minecraft.getInstance().gameRenderer.levelLightmap(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
        }

        void close() {
            this.view.close();
            this.texture.close();
        }
    }

    /**
     * Where the glass's point {@code s} across (from the left, as one looks at it) and {@code t} up is in the mirror's
     * block, {@code out} before the block's middle.
     */
    private static float[] point(Direction glass, float out, float s, float t) {
        Direction right = glass.getCounterClockWise();
        float a = -0.5F + (StandingMirrorRenderer.LEFT + s * (StandingMirrorRenderer.RIGHT - StandingMirrorRenderer.LEFT)) / 16.0F;
        return new float[] {0.5F + glass.getStepX() * out + right.getStepX() * a,
                2 - (StandingMirrorRenderer.BOTTOM - t * (StandingMirrorRenderer.BOTTOM - StandingMirrorRenderer.TOP)) / 16.0F,
                0.5F + glass.getStepZ() * out + right.getStepZ() * a};
    }

    /** The glass's (s, t), {@code out} before the block's middle, to where that is from the camera. */
    private static Matrix4f plane(Direction glass, float out, BlockPos lower, CameraRenderState camera) {
        float[] o = point(glass, out, 0, 0);
        float[] u = point(glass, out, 1, 0);
        float[] v = point(glass, out, 0, 1);
        return new Matrix4f(
                u[0] - o[0], u[1] - o[1], u[2] - o[2], 0,
                v[0] - o[0], v[1] - o[1], v[2] - o[2], 0,
                0, 0, 1, 0,
                (float) (lower.getX() + o[0] - camera.pos.x), (float) (lower.getY() + o[1] - camera.pos.y), (float) (lower.getZ() + o[2] - camera.pos.z), 1);
    }

    /** The light level (0 to 240, as vertices carry it) at {@code at} of the way over the board: most in the middle. */
    private static int level(float at) {
        float away = Math.abs(at - 0.5F);
        return Math.round(240 * Math.clamp((0.5F - MARGIN - away) / (0.5F - MARGIN - PEAK), 0.0F, 1.0F));
    }

    /** The board on {@code buffer}, for a mirror at {@code pose}'s block whose glass looks {@code glass}-wards. */
    static void board(PoseStack.Pose pose, VertexConsumer buffer, Direction glass) {
        float[] across = {0, MARGIN, 0.5F - PEAK, 0.5F + PEAK, 1 - MARGIN, 1};
        float[] up = {0, EDGE, MARGIN, 0.5F - PEAK, 0.5F + PEAK, 1 - MARGIN, 1 - EDGE, 1};
        for (int i = 0; i < across.length - 1; i++) {
            for (int j = 0; j < up.length - 1; j++) {
                boolean dark = j == 0 || j == up.length - 2; // the strips along the top and the bottom
                // anticlockwise as seen from the front: bottom left, bottom right, top right, top left
                vertex(pose, buffer, glass, across[i], up[j], dark);
                vertex(pose, buffer, glass, across[i + 1], up[j], dark);
                vertex(pose, buffer, glass, across[i + 1], up[j + 1], dark);
                vertex(pose, buffer, glass, across[i], up[j + 1], dark);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Direction glass, float s, float t, boolean dark) {
        float[] at = point(glass, OUT, s, t);
        buffer.addVertex(pose, at[0], at[1], at[2]).setColor(GREY, GREY, GREY, 255).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(dark ? 0 : level(s) | level(t) << 16)
                .setNormal(pose, 0, -1, 0); // looking down, it says: no sun or moon lights it, wherever the mirror stands
    }

    /**
     * The game's own shade the table is measured against: a wall's (north and south faces), as the level has it. Under
     * a pack that lights the terrain itself (most: {@code oldLighting=false}) Iris has the level say every face is as
     * bright as the next, so the world's meshes carry no shade, nor do the mirror's own (made from the same level), and
     * this is 1: the table is the pack's light as it is. Under a pack that keeps the game's shade the mirror's meshes
     * carry it; measured against a wall's, walls come out as the pack has them, floors brighter and ceilings darker.
     */
    static float shade(CardinalLighting cardinal) {
        return cardinal.byFace(Direction.NORTH);
    }

    /**
     * The pack's finished {@code picture} read where the board of the mirror at {@code lower} is on the screen, into
     * {@code table}: a part of it each frame, so a flicker in the picture is not one in the mirror; and only where
     * {@code depth}, the world's, has nothing before the glass's picture, as that picture itself is drawn.
     */
    static void read(Table table, GpuTextureView picture, GpuTextureView depth, CameraRenderState camera, BlockPos lower, Direction glass,
            float shade) {
        // from the camera on to the screen, as the world was drawn: of the board, and of the glass's picture before it
        Matrix4f screen = new Matrix4f(camera.projectionMatrix).mul(MirrorReflections.bob(camera)).mul(camera.viewRotationMatrix);
        Matrix4f board = new Matrix4f(screen).mul(plane(glass, OUT, lower, camera));
        Matrix4f over = new Matrix4f(screen).mul(plane(glass, StandingMirrorRenderer.OUT_OVER_SHADERS, lower, camera));
        float gain = 1 / (GREY / 255.0F * shade);
        boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        // a mirror looked at again after a while is not faded in from the light it was last seen in
        long now = Util.getMillis();
        float weight = table.measured && now - table.read < STALE_MS ? WEIGHT : 1;
        table.read = now;
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(board,
                new Vector4f(gain, MARGIN, PEAK, weight), new Vector3f(EDGE, zeroToOne ? 1 : 0, 0), over);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nookworks mirror light", table.view, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(READ));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            pass.setUniform("Sampler0", picture, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("Sampler1", depth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
        table.measured = true;
    }
}
