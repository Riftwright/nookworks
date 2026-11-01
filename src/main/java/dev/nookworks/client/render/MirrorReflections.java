package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import dev.nookworks.Nookworks;
import dev.nookworks.decor.StandingMirrorBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.FrameGraphSetupEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * What Standing Mirrors show. Each frame the nearest few mirrors in view (the glass facing the camera, near enough) are
 * given a picture each, as big as the screen: the world before the glass seen from the camera's place reflected through
 * it - the sky, the blocks round the mirror, the block entities among them, and the entities (the player looking in
 * too) - drawn before the world is, and the glass then shows its own part of that picture, pixel for pixel where it is
 * on the screen.
 *
 * <p>It is drawn from the camera reflected in the glass, turned the right way round so that faces are culled as they are
 * in the world (the picture comes out flipped left to right, and the glass reads it back so), with the near plane laid
 * on the glass, so nothing behind the mirror comes between. Only what is near the mirror is drawn: the blocks of the
 * sections within {@link #SECTION_RADIUS} of its own (from meshes of the mirrors' own, {@link MirrorScenes}), the
 * entities and block entities within {@link #ENTITY_REACH}; beyond that is the fog's colour. A mirror's picture is not
 * drawn in another mirror's (its glass there is the model's silver). Under an Iris shader pack the picture is drawn with
 * the game's own programs all the same ({@link IrisHooks}), and the glass over the pack's finished picture.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class MirrorReflections {
    /** How many mirrors are drawn at once: the nearest. */
    static final int SLOTS = 2;
    /** The camera within this many blocks of a glass, for it to be drawn. */
    static final double REACH = 32;
    /** Sections round the mirror's drawn in it, each way. */
    private static final int SECTION_RADIUS = 3;
    /** And up and down. */
    private static final int SECTION_HEIGHT = 2;
    /** Entities and block entities within this many blocks of the glass are drawn in it. */
    private static final double ENTITY_REACH = 24;

    public static final RenderPipeline GLASS = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Nookworks.id("pipeline/mirror_glass"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader(Nookworks.id("core/mirror_glass"))
            .withFragmentShader(Nookworks.id("core/mirror_glass"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();

    /**
     * The glass under an Iris shader pack, drawn over the pack's finished picture: as {@link #GLASS}, tested against the
     * world's depth (which Iris leaves the game's way round, reversed) but writing none.
     */
    public static final RenderPipeline GLASS_OVER_SHADERS = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Nookworks.id("pipeline/mirror_glass_over_shaders"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader(Nookworks.id("core/mirror_glass"))
            .withFragmentShader(Nookworks.id("core/mirror_glass"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    /** A glass to draw over the pack's picture this frame: where, and its slot's picture. */
    private record Over(BlockPos lower, Direction facing, int slot) {}

    private static final List<Over> overShaders = new ArrayList<>();
    private static @Nullable ProjectionMatrixBuffer overProjection;
    private static @Nullable ByteBufferBuilder overBytes;

    /** A mirror in view this frame, as its renderer saw it. */
    record Glass(BlockPos lower, Direction facing, double distance) {}

    private static final List<Glass> offered = new ArrayList<>();
    private static final Slot[] slots = new Slot[SLOTS];
    private static boolean chosen;

    private static @Nullable RenderBuffers buffers;
    private static @Nullable FeatureRenderDispatcher features;
    private static final SubmitNodeStorage submits = new SubmitNodeStorage();
    private static @Nullable FogRenderer fog;

    private MirrorReflections() {}

    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(GLASS);
        event.registerPipeline(GLASS_OVER_SHADERS);
    }

    /** Where the glass's face is: the plane it reflects in, through its middle. */
    static Vec3 glassCentre(BlockPos lower, Direction facing) {
        return Vec3.atLowerCornerOf(lower).add(0.5 + facing.getStepX() * GLASS_OUT, 1.0, 0.5 + facing.getStepZ() * GLASS_OUT);
    }

    /** The glass's face, half a pixel in front of the block's middle (as the model has it). */
    static final float GLASS_OUT = 0.5F / 16;

    /** A mirror's renderer, extracting it: it is in view, and may be drawn. */
    static void offer(BlockPos lower, Direction facing, Vec3 camera) {
        Vec3 centre = glassCentre(lower, facing);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        double distance = camera.distanceTo(centre);
        if (camera.subtract(centre).dot(normal) > 0.01 && distance < REACH && offered.stream().noneMatch(glass -> glass.lower.equals(lower))) {
            offered.add(new Glass(lower.immutable(), facing, distance));
        }
    }

    /** A mirror's picture this frame, for its glass: the render type that reads it where it is on the screen, and its slot. */
    record Reflection(RenderType type, int slot) {}

    /**
     * The picture a mirror's glass shows this frame, or null if it is not drawn (its silver shows). Asked first, the
     * nearest mirrors offered are given their pictures, made ready for the screen's size.
     */
    static @Nullable Reflection glass(BlockPos lower) {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (target.width <= 0 || target.height <= 0 || !IrisHooks.mirrorsDrawable()) {
            return null;
        }
        if (!chosen) {
            chosen = true;
            offered.sort(Comparator.comparingDouble(Glass::distance));
            for (int i = 0; i < SLOTS; i++) {
                if (i < offered.size()) {
                    if (slots[i] == null) {
                        slots[i] = new Slot(i);
                    }
                    slots[i].glass = offered.get(i);
                    slots[i].fit(target.width, target.height);
                } else if (slots[i] != null) {
                    slots[i].glass = null;
                }
            }
        }
        for (Slot slot : slots) {
            if (slot != null && slot.glass != null && slot.glass.lower.equals(lower)) {
                return new Reflection(slot.type, slot.index);
            }
        }
        return null;
    }

    /** Before the world is drawn: each chosen mirror's picture. */
    @SubscribeEvent
    static void onFrameGraph(FrameGraphSetupEvent event) {
        for (Slot slot : slots) {
            if (slot != null && slot.glass != null) {
                IrisHooks.vanilla(() -> draw(slot, event.getCameraState()));
            }
        }
    }

    /** Under a shader pack: this glass is drawn over the pack's picture, once the world is ({@link #onAfterLevel}). */
    static void overShaders(BlockPos lower, Direction facing, int slot) {
        overShaders.add(new Over(lower.immutable(), facing, slot));
    }

    /**
     * The world drawn, and the pack's picture of it finished: the glasses left for it, over it, each reading its own
     * picture where it is on the screen. The world's depth hides them where something stands before the glass.
     */
    @SubscribeEvent
    static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        if (overShaders.isEmpty()) {
            return;
        }
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        IrisHooks.vanilla(() -> drawOverShaders(camera));
        overShaders.clear();
    }

    private static void drawOverShaders(CameraRenderState camera) {
        if (overBytes == null) {
            overProjection = new ProjectionMatrixBuffer("Nookworks mirror glass over shaders");
            overBytes = new ByteBufferBuilder(SLOTS * 4 * DefaultVertexFormat.POSITION.getVertexSize() * 4);
        }
        BufferBuilder builder = new BufferBuilder(overBytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
        PoseStack pose = new PoseStack();
        for (Over over : overShaders) {
            pose.pushPose();
            pose.translate(over.lower.getX() - camera.pos.x, over.lower.getY() - camera.pos.y, over.lower.getZ() - camera.pos.z);
            StandingMirrorRenderer.faceOverShaders(pose.last(), builder, over.facing);
            pose.popPose();
        }
        GpuBuffer vertices;
        try (MeshData mesh = builder.buildOrThrow()) {
            vertices = RenderSystem.getDevice().createBuffer(() -> "Nookworks mirror glass over shaders", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
        }
        var quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = quads.getBuffer(6 * overShaders.size());
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(overProjection.getBuffer(camera.projectionMatrix), ProjectionType.PERSPECTIVE);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(bob(camera).mul(camera.viewRotationMatrix));
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nookworks mirror glass over shaders",
                main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(RenderSystem.getCompiledPipeline(GLASS_OVER_SHADERS));
            pass.setUniform("DynamicTransforms", transform);
            pass.setVertexBuffer(0, vertices.slice());
            pass.setIndexBuffer(indices, quads.type());
            for (int i = 0; i < overShaders.size(); i++) {
                Slot slot = slots[overShaders.get(i).slot];
                pass.setUniform("Sampler0", slot.target.getColorTextureView(), sampler);
                pass.drawIndexed(6, 1, 6 * i, 0, 0);
            }
        } finally {
            RenderSystem.restoreProjectionMatrix();
            vertices.close();
        }
    }

    /** Leaving the world: the mirrors' meshes of it go with it, and the pictures they were drawn into (r19). */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MirrorScenes.forget();
        for (Slot slot : slots) {
            if (slot != null) {
                slot.release();
            }
        }
    }

    /** Sections no mirror wants are let go though none is drawn (one broken, or walked away from). */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        MirrorScenes.tick();
    }

    /** The frame done: the next one's mirrors are offered afresh. */
    @SubscribeEvent
    static void onFrameEnd(RenderFrameEvent.Post event) {
        MirrorScenes.frameDone();
        offered.clear();
        overShaders.clear();
        chosen = false;
        for (Slot slot : slots) {
            if (slot != null) {
                slot.glass = null;
            }
        }
        if (buffers != null) {
            buffers.endFrame();
        }
        if (fog != null) {
            fog.endFrame();
        }
    }

    private static void draw(Slot slot, CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        if (features == null) {
            buffers = new RenderBuffers(0);
            features = new FeatureRenderDispatcher(buffers, mc.getModelManager(), mc.getAtlasManager(), mc.font, mc.gameRenderer.gameRenderState());
            fog = new FogRenderer();
        }
        Glass glass = slot.glass;
        Vec3 cam = camera.pos;
        Vec3 centre = glassCentre(glass.lower, glass.facing);
        Vector3f n = new Vector3f(glass.facing.getStepX(), 0, glass.facing.getStepZ());
        Vector3f o = new Vector3f((float) (centre.x - cam.x), (float) (centre.y - cam.y), (float) (centre.z - cam.z));
        float d = n.dot(o);
        // the reflection through the glass, on positions relative to the camera: p - 2 ((p - o) . n) n
        Matrix4f reflect = new Matrix4f(
                1 - 2 * n.x * n.x, -2 * n.x * n.y, -2 * n.x * n.z, 0,
                -2 * n.y * n.x, 1 - 2 * n.y * n.y, -2 * n.y * n.z, 0,
                -2 * n.z * n.x, -2 * n.z * n.y, 1 - 2 * n.z * n.z, 0,
                2 * d * n.x, 2 * d * n.y, 2 * d * n.z, 1);
        // seen from there and turned the right way round (flipped left to right; the glass reads it back so), shaken as the
        // view is while walking or hurt, or the picture would slide about in the glass
        Matrix4f view = new Matrix4f().scale(-1, 1, 1).mul(bob(camera)).mul(camera.viewRotationMatrix).mul(reflect);
        // the glass as the near plane: what is behind it (n . p < d) is not drawn
        Vector4f plane = new Vector4f(n.x, n.y, n.z, -d + 0.002F);
        new Matrix4f(view).invert().transpose().transform(plane);
        // the game's depth runs reversed (the near plane at 1); under an Iris shader pack too, as this is drawn (IrisHooks)
        Matrix4f projection = oblique(new Matrix4f(camera.projectionMatrix), plane,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        if (projection == null) {
            return;
        }

        // the blocks: the sections before the glass, in meshes of the mirrors' own
        SectionPos at = SectionPos.of(glass.lower);
        Vec3 normal = new Vec3(n.x, n.y, n.z);
        List<SectionPos> wanted = new ArrayList<>();
        for (int dx = -SECTION_RADIUS; dx <= SECTION_RADIUS; dx++) {
            for (int dy = -SECTION_HEIGHT; dy <= SECTION_HEIGHT; dy++) {
                for (int dz = -SECTION_RADIUS; dz <= SECTION_RADIUS; dz++) {
                    SectionPos section = at.offset(dx, dy, dz);
                    if (inFront(new AABB(section.minBlockX(), section.minBlockY(), section.minBlockZ(), section.maxBlockX() + 1,
                            section.maxBlockY() + 1, section.maxBlockZ() + 1), centre, normal)
                            && level.getChunkSource().getChunk(section.x(), section.z(), false) != null) {
                        wanted.add(section);
                    }
                }
            }
        }
        Vec3 eye = cam.subtract(normal.scale(2 * normal.dot(cam.subtract(centre)))); // the camera reflected
        ChunkSectionsToRender chunks = MirrorScenes.prepare(level, wanted, new Vector3d(eye.x, eye.y, eye.z), view);

        // the entities and block entities before the glass
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        PoseStack pose = new PoseStack();
        AABB near = new AABB(centre, centre).inflate(ENTITY_REACH);
        EntityRenderDispatcher entities = mc.getEntityRenderDispatcher();
        for (Entity entity : level.getEntities((Entity) null, near, e -> true)) {
            if (normal.dot(entity.position().subtract(centre)) < -0.5) {
                continue; // behind the mirror
            }
            EntityRenderState state = entities.extractEntity(entity, partial);
            state.nameTag = null; // it would face the camera, not the glass, and read backwards
            entities.submit(state, camera, state.x - cam.x, state.y - cam.y, state.z - cam.z, pose, submits);
        }
        BlockEntityRenderDispatcher blockEntities = mc.getBlockEntityRenderDispatcher();
        int reach = (int) Math.ceil(ENTITY_REACH);
        for (int cx = SectionPos.blockToSectionCoord(centre.x - reach); cx <= SectionPos.blockToSectionCoord(centre.x + reach); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(centre.z - reach); cz <= SectionPos.blockToSectionCoord(centre.z + reach); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (blockEntity instanceof StandingMirrorBlockEntity || !near.contains(Vec3.atCenterOf(pos))
                            || normal.dot(Vec3.atCenterOf(pos).subtract(centre)) < -0.5) {
                        continue;
                    }
                    var renderer = blockEntities.getRenderer(blockEntity);
                    if (renderer == null) {
                        continue;
                    }
                    BlockEntityRenderState state = blockEntities.tryExtractRenderState(blockEntity, partial, null, renderer.shouldRenderOffScreen(), null);
                    if (state != null) {
                        pose.pushPose();
                        pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                        blockEntities.submit(state, pose, submits, camera);
                        pose.popPose();
                    }
                }
            }
        }

        // and all of it into the slot's picture: the fog's colour, the sky as the reflected camera sees it, the rest
        TextureTarget target = slot.target;
        Vector4f fogColour = camera.fogData.color;
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(),
                new Vector4f(fogColour.x, fogColour.y, fogColour.z, 1.0F), target.getDepthTexture(), 0.0);
        GpuBufferSlice fogBefore = RenderSystem.getShaderFog();
        RenderSystem.backupProjectionMatrix();
        fog.updateBuffer(camera.fogData);
        GpuBufferSlice worldFog = fog.getBuffer(FogRenderer.FogMode.WORLD);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        SkyRenderState sky = mc.gameRenderer.gameRenderState().levelRenderState.skyRenderState;
        if (sky.skybox != DimensionType.Skybox.NONE && camera.fogType != FogType.POWDER_SNOW && camera.fogType != FogType.LAVA
                && !camera.entityRenderState.doesMobEffectBlockSky) {
            // the sky only turns: the reflection's turn without its shift, and the camera's own frustum
            modelView.set(new Matrix4f(view).setTranslation(0, 0, 0));
            RenderSystem.setProjectionMatrix(slot.projection.getBuffer(camera.projectionMatrix), ProjectionType.PERSPECTIVE);
            slot.sky.render(worldFog, sky);
        }
        RenderSystem.setProjectionMatrix(slot.skyless.getBuffer(projection), ProjectionType.PERSPECTIVE);
        RenderSystem.setShaderFog(worldFog);
        modelView.set(view);
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
        GpuTextureView atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(submits);
                RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Nookworks mirror",
                        target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            chunks.renderGroup(ChunkSectionLayerGroup.OPAQUE, pass, sampler, atlas, false);
            frame.executeSolid(pass);
            chunks.renderGroup(ChunkSectionLayerGroup.TRANSLUCENT, pass, sampler, atlas, false);
            frame.executeTranslucent(pass);
            frame.executeTranslucentAfterTerrain(pass);
        } finally {
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            if (fogBefore != null) {
                RenderSystem.setShaderFog(fogBefore);
            }
        }
    }

    /**
     * How the view is shaken this frame, as the game shakes it (GameRenderer's bobHurt and bobView, put before the
     * projection): tilted while hurt or dying, bobbed while walking if the options say so.
     */
    static Matrix4f bob(CameraRenderState camera) {
        PoseStack pose = new PoseStack();
        var entity = camera.entityRenderState;
        var options = Minecraft.getInstance().gameRenderer.gameRenderState().optionsRenderState;
        if (entity.isLiving) {
            if (entity.isDeadOrDying) {
                pose.rotateDegrees(Axis.ZP, 40.0F - 8000.0F / (Math.min(entity.deathTime, 20.0F) + 200.0F));
            }
            if (entity.hurtTime >= 0.0F && !entity.preventFlinch) {
                float hurt = entity.hurtTime / entity.hurtDuration;
                hurt = Mth.sin(hurt * hurt * hurt * hurt * Mth.PI);
                pose.rotateDegrees(Axis.YP, -entity.hurtDir);
                pose.rotateDegrees(Axis.ZP, (float) (-hurt * 14.0 * options.damageTiltStrength));
                pose.rotateDegrees(Axis.YP, entity.hurtDir);
            }
        }
        if (options.bobView && entity.isPlayer) {
            float walked = entity.backwardsInterpolatedWalkDistance * Mth.PI;
            pose.translate(Mth.sin(walked) * entity.bob * 0.5F, -Math.abs(Mth.cos(walked) * entity.bob), 0.0F);
            pose.rotateDegrees(Axis.ZP, Mth.sin(walked) * entity.bob * 3.0F);
            pose.rotateDegrees(Axis.XP, Math.abs(Mth.cos(walked - 0.2F) * entity.bob) * 5.0F);
        }
        return new Matrix4f(pose.last().pose());
    }

    /** Whether any of the box is on the glass's front side. */
    private static boolean inFront(AABB box, Vec3 centre, Vec3 normal) {
        double reach = Math.abs(normal.x) * box.getXsize() / 2 + Math.abs(normal.z) * box.getZsize() / 2;
        return normal.dot(box.getCenter().subtract(centre)) + reach > 0;
    }

    /**
     * {@code projection} with its near plane moved onto {@code plane} (in view space, the side to be drawn positive), its
     * far plane tilted to keep the frustum's far corner (E. Lengyel's oblique near-plane clipping); null if the plane is
     * not in view. The projection is the game's: reversed depth, the near plane at 1.
     */
    static @Nullable Matrix4f oblique(Matrix4f projection, Vector4f plane, boolean zZeroToOne) {
        // the frustum's corner farthest out along the plane, in view space
        Vector4f clipPlane = new Vector4f(plane);
        new Matrix4f(projection).invert().transpose().transform(clipPlane);
        Vector4f corner = new Vector4f(Math.signum(clipPlane.x), Math.signum(clipPlane.y), zZeroToOne ? 0 : -1, 1);
        new Matrix4f(projection).invert().transform(corner);
        Vector4f w = new Vector4f(projection.m03(), projection.m13(), projection.m23(), projection.m33());
        float along = plane.dot(corner);
        if (along <= 1e-6F) {
            return null;
        }
        float k = (zZeroToOne ? 1 : 2) * w.dot(corner) / along;
        // the depth row becomes w - k C: near (w - z >= 0) is then the plane, far keeps the corner
        projection.m02(w.x - k * plane.x);
        projection.m12(w.y - k * plane.y);
        projection.m22(w.z - k * plane.z);
        projection.m32(w.w - k * plane.w);
        return projection;
    }

    /** A picture a mirror is drawn into, and the render type its glass reads it with. */
    private static final class Slot {
        final Identifier id;
        final int index;
        final RenderType type;
        /** The camera's own projection, for the sky; and the one with its near plane on the glass, for the rest. */
        final ProjectionMatrixBuffer projection;
        final ProjectionMatrixBuffer skyless;
        final Picture picture = new Picture();
        @Nullable Glass glass;
        @Nullable TextureTarget target;
        @Nullable SkyRenderer sky;

        Slot(int index) {
            this.index = index;
            this.id = Nookworks.id("mirror/" + index);
            this.projection = new ProjectionMatrixBuffer("Nookworks mirror sky " + index);
            this.skyless = new ProjectionMatrixBuffer("Nookworks mirror " + index);
            this.type = RenderType.create("nookworks_mirror_glass_" + index, RenderSetup.builder(GLASS)
                    .withTexture("Sampler0", this.id, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
                    .createRenderSetup());
            Minecraft.getInstance().getTextureManager().register(this.id, this.picture);
        }

        /** As big as the screen: made again if it is not. */
        void fit(int width, int height) {
            if (this.target != null && this.target.width == width && this.target.height == height) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (this.target == null) {
                GpuFormat depthFormat = mc.gameRenderer.mainRenderTarget().getDepthTexture().getFormat();
                this.target = new TextureTarget("Nookworks mirror", width, height, GpuFormat.RGBA8_UNORM, depthFormat);
                this.sky = new SkyRenderer(mc.getTextureManager(), mc.getAtlasManager(), this.target);
            } else {
                this.target.resize(width, height);
            }
            this.picture.show(this.target.getColorTexture(), this.target.getColorTextureView());
        }

        /** Its picture and sky let go; made again the next time a mirror is drawn into it. */
        void release() {
            if (this.target != null) {
                this.picture.releaseTextures();
                this.target.destroyBuffers();
                this.target = null;
            }
            if (this.sky != null) {
                this.sky.close();
                this.sky = null;
            }
        }
    }

    /** A slot's picture as the texture manager has it: the slot owns it, and nothing else closes it. */
    private static final class Picture extends AbstractTexture {
        void show(GpuTexture texture, GpuTextureView view) {
            this.texture = texture;
            this.textureView = view;
        }

        @Override
        protected void releaseTextures() {
            this.texture = null;
            this.textureView = null;
        }
    }
}
