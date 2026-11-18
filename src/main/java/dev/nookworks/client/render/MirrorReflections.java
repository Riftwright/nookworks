package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SequencedMap;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import dev.nookworks.Nookworks;
import dev.nookworks.decor.ChevalMirrorBlock;
import dev.nookworks.decor.MirrorSight;
import dev.nookworks.decor.StandingMirrorBlockEntity;
import dev.nookworks.port.render.FogCamera;
import dev.nookworks.port.render.GlPass;
import dev.nookworks.port.render.MirrorShaders;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
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
 * the game's own programs all the same ({@link IrisHooks}), and the glass over the pack's finished picture; what lights
 * the picture then is the pack's own light, measured from the pack on the glass itself ({@link MirrorLight}).
 *
 * <p>In 1.21.1 the pictures are drawn once the world's sky is ({@code RenderLevelStageEvent} AFTER_SKY), in a pass of
 * their own that puts the game's drawing state back as it was ({@link GlPass}); the mirrors drawn are those whose block
 * entities stand in the world's view then (26.3 has the mirrors' renderers offer themselves, and draws before the world
 * in its frame graph; 1.21.1 runs a block entity's renderer only once the world is half drawn).
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
    /** A picture no mirror has been drawn into for this long is let go (ms): made again when one is. */
    private static final long KEEP_MS = 15_000;

    /**
     * The world's camera this frame, as 1.21.1 hands it to the stages of the level's drawing: where it is, its turn (the
     * model-view matrix), and its projection, which in 1.21.1 carries the view's shake (bobbing, the hurt tilt, nausea)
     * - 26.3 keeps that apart, in its camera render state.
     */
    record View(Vec3 pos, Matrix4f viewRotation, Matrix4f projection, Camera camera) {}

    /** A glass to draw over the pack's picture this frame: where, and its slot's picture. */
    private record Over(BlockPos lower, Direction facing, int slot) {}

    private static final List<Over> overShaders = new ArrayList<>();

    /** A mirror in view this frame, as its renderer saw it. */
    record Glass(BlockPos lower, Direction facing, double distance) {}

    private static final List<Glass> offered = new ArrayList<>();
    /**
     * The mirrors whose renderers the world ran this frame and the last (it runs only those it does not find hidden),
     * and those in the view's frustum this frame and the last, by their lower halves' places.
     */
    private static LongOpenHashSet rendered = new LongOpenHashSet();
    private static LongOpenHashSet renderedBefore = new LongOpenHashSet();
    private static LongOpenHashSet inView = new LongOpenHashSet();
    private static LongOpenHashSet inViewBefore = new LongOpenHashSet();
    private static final Slot[] slots = new Slot[SLOTS];
    private static boolean chosen;
    /** While a picture is drawn: name tags are left out of it (they face the camera, not the glass, and read backwards). */
    private static boolean drawing;
    /** The programs' load the pictures were made under: they are let go when the resources load again. */
    private static int programs = -1;

    private static MultiBufferSource.@Nullable BufferSource buffers;
    private static final RandomSource MODEL_RANDOM = RandomSource.create();

    private MirrorReflections() {}

    /** Where the glass's face is: the plane it reflects in, through its middle. */
    static Vec3 glassCentre(BlockPos lower, Direction facing) {
        return MirrorSight.glassCentre(lower, facing);
    }

    /** The glass's face, half a pixel in front of the block's middle (as the model has it). */
    static final float GLASS_OUT = MirrorSight.GLASS_OUT;

    /** A mirror in view: it may be drawn. */
    static void offer(BlockPos lower, Direction facing, Vec3 camera) {
        Vec3 centre = glassCentre(lower, facing);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        double distance = camera.distanceTo(centre);
        if (camera.subtract(centre).dot(normal) > 0.01 && distance < REACH && offered.stream().noneMatch(glass -> glass.lower.equals(lower))
                && !veiledUnderAPack(lower, facing, camera)) {
            offered.add(new Glass(lower.immutable(), facing, distance));
        }
    }

    /** A mirror's renderer ran in the world's view this frame (not in a pack's shadow pass). */
    static void rendered(BlockPos lower) {
        rendered.add(lower.asLong());
    }

    /**
     * The mirrors near enough whose two blocks stand in the world's view this frame, each offered. 26.3's mirror renderers
     * offer themselves as they are extracted, before anything is drawn; 1.21.1 calls them in the middle of the world's
     * drawing, too late for their pictures, so the mirrors are looked for in the chunks round the camera instead. One
     * that was in view last frame too but whose renderer the world did not run (hidden behind walls, as the world's
     * renderer found) is passed over, as 26.3 never offers it; one just come into view is offered at once.
     */
    private static void offerInView(ClientLevel level, Vec3 camera, Frustum frustum) {
        int reach = (int) Math.ceil(REACH) + 1;
        for (int cx = SectionPos.blockToSectionCoord(camera.x - reach); cx <= SectionPos.blockToSectionCoord(camera.x + reach); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(camera.z - reach); cz <= SectionPos.blockToSectionCoord(camera.z + reach); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof StandingMirrorBlockEntity mirror) || mirror.isRemoved()) {
                        continue;
                    }
                    BlockPos lower = mirror.getBlockPos();
                    if (!frustum.isVisible(new AABB(lower).expandTowards(0, 1, 0))) {
                        continue;
                    }
                    long key = lower.asLong();
                    inView.add(key);
                    if (inViewBefore.contains(key) && !renderedBefore.contains(key)) {
                        continue; // hidden
                    }
                    BlockState block = mirror.getBlockState();
                    offer(lower, block.hasProperty(ChevalMirrorBlock.FACING) ? block.getValue(ChevalMirrorBlock.FACING) : Direction.NORTH, camera);
                }
            }
        }
    }

    /**
     * Under a shader pack: whether water, glass or the like lies between the eye and this glass ({@link MirrorSight}).
     * The glass could not be painted behind them there, and the board the pack's light is measured on showed through
     * them; such a mirror is not drawn, and its silver shows. Without a pack the glass is drawn with the world, and the
     * water over it.
     */
    private static boolean veiledUnderAPack(BlockPos lower, Direction facing, Vec3 camera) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && IrisHooks.shaderPackInUse()
                && MirrorSight.veiled(level, lower, facing, camera, MirrorReflections::drawnSeeThrough);
    }

    /**
     * A block a pack may draw in its see-through pass: one whose model is drawn translucent (stained glass, ice, slime),
     * and plain glass and panes, which the game cuts out but packs draw as glass (Complementary does).
     */
    private static boolean drawnSeeThrough(BlockState state) {
        if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) {
            return true;
        }
        // PORT: 26.3 asks the model whether any of its faces is translucent; 1.21.1 names a model's render layers
        MODEL_RANDOM.setSeed(42L);
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getRenderTypes(state, MODEL_RANDOM, ModelData.EMPTY)
                .contains(RenderType.translucent());
    }

    /** A mirror's picture this frame, for its glass: the render type that reads it where it is on the screen, and its slot. */
    record Reflection(RenderType type, int slot) {}

    /**
     * The picture a mirror's glass shows this frame, or null if it is not drawn (its silver shows): the nearest mirrors
     * offered were given their pictures before the world was drawn ({@link #beforeWorld}).
     */
    static @Nullable Reflection glass(BlockPos lower) {
        if (!chosen) {
            return null; // none drawn this frame (the world's sky stage did not come)
        }
        for (Slot slot : slots) {
            if (slot != null && slot.glass != null && slot.target != null && slot.glass.lower.equals(lower)) {
                return new Reflection(slot.type, slot.index);
            }
        }
        return null;
    }

    /** The nearest mirrors offered given their slots, each made ready for the screen's size. */
    private static void choose(int width, int height) {
        chosen = true;
        offered.sort(Comparator.comparingDouble(Glass::distance));
        for (int i = 0; i < SLOTS; i++) {
            if (i < offered.size()) {
                if (slots[i] == null) {
                    slots[i] = new Slot(i);
                }
                slots[i].glass = offered.get(i);
                slots[i].fit(width, height);
            } else if (slots[i] != null) {
                slots[i].glass = null;
            }
        }
    }

    /** The world's drawing, by its stages: the pictures once the sky is drawn; the glasses over a pack's picture at the end. */
    @SubscribeEvent
    static void onLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            beforeWorld(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            onAfterLevel(event);
        }
    }

    /** Before the world's blocks are drawn: the mirrors in view chosen, and each chosen mirror's picture drawn. */
    private static void beforeWorld(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (chosen || level == null || mc.player == null || IrisHooks.shadowPass()) {
            return; // once a frame; and not in a pack's shadow pass, which draws the world again from the sun
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0 || !IrisHooks.mirrorsDrawable() || !MirrorShaders.ready()) {
            return;
        }
        Camera camera = event.getCamera();
        View view = new View(camera.getPosition(), new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()), camera);
        offerInView(level, view.pos, event.getFrustum());
        if (offered.isEmpty()) {
            chosen = true;
            return;
        }
        DeltaTracker delta = event.getPartialTick();
        IrisHooks.vanilla(() -> {
            try (GlPass pass = GlPass.begin()) {
                choose(main.width, main.height);
                for (Slot slot : slots) {
                    if (slot != null && slot.glass != null) {
                        draw(slot, view, delta);
                    }
                }
                // the clear colour the world's frame had (the fog's), after the pictures' own
                float[] fog = pass.fogColour();
                RenderSystem.clearColor(fog[0], fog[1], fog[2], 0.0F);
            }
        });
    }

    /** Under a shader pack: this glass is drawn over the pack's picture, once the world is ({@link #onAfterLevel}). */
    static void overShaders(BlockPos lower, Direction facing, int slot) {
        overShaders.add(new Over(lower.immutable(), facing, slot));
    }

    /**
     * The world drawn, and the pack's picture of it finished: the glasses left for it, over it, each reading its own
     * picture where it is on the screen. The world's depth hides them where something stands before the glass.
     */
    private static void onAfterLevel(RenderLevelStageEvent event) {
        if (overShaders.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        View view = new View(camera.getPosition(), new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()), camera);
        IrisHooks.vanilla(() -> {
            try (GlPass pass = GlPass.begin()) {
                drawOverShaders(view);
            }
        });
        overShaders.clear();
    }

    private static void drawOverShaders(View camera) {
        ShaderInstance shader = MirrorShaders.glassOverShaders();
        if (shader == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        ClientLevel level = mc.level;
        if (MirrorLight.mode() == MirrorLight.Mode.ON && level != null) {
            // the pack's picture is finished and the glasses are not yet painted over it: each one's board is read, for
            // the pictures to come
            for (Over over : overShaders) {
                Slot slot = slots[over.slot];
                if (slot.light == null) {
                    slot.light = new MirrorLight.Table(slot.index);
                }
                MirrorLight.read(slot.light, main.getColorTextureId(), main.getDepthTextureId(), camera, over.lower, over.facing,
                        MirrorLight.shade(level));
            }
        }
        // over the finished picture, tested against the world's depth but writing none
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, main.viewWidth, main.viewHeight);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.colorMask(true, true, true, true);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.set(camera.viewRotation());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(camera.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(() -> shader);
        PoseStack pose = new PoseStack();
        for (Over over : overShaders) {
            Slot slot = slots[over.slot];
            if (slot == null || slot.target == null) {
                continue;
            }
            RenderSystem.setShaderTexture(0, slot.target.getColorTextureId());
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            pose.pushPose();
            pose.translate(over.lower.getX() - camera.pos.x, over.lower.getY() - camera.pos.y, over.lower.getZ() - camera.pos.z);
            StandingMirrorRenderer.faceOverShaders(pose.last(), builder, over.facing);
            pose.popPose();
            BufferUploader.drawWithShader(builder.buildOrThrow());
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

    /** A picture being drawn: no name tags in it. */
    @SubscribeEvent
    static void onNameTag(RenderNameTagEvent event) {
        if (drawing) {
            event.setCanRender(TriState.FALSE);
        }
    }

    /**
     * The frame done: the next one's mirrors are offered afresh. A picture not drawn into for a while, or made before the
     * resources loaded again, is let go.
     */
    @SubscribeEvent
    static void onFrameEnd(RenderFrameEvent.Post event) {
        MirrorScenes.frameDone();
        offered.clear();
        LongOpenHashSet swap = renderedBefore;
        renderedBefore = rendered;
        rendered = swap;
        rendered.clear();
        swap = inViewBefore;
        inViewBefore = inView;
        inView = swap;
        inView.clear();
        overShaders.clear();
        chosen = false;
        long now = net.minecraft.Util.getMillis();
        boolean reloaded = programs != MirrorShaders.loads();
        programs = MirrorShaders.loads();
        for (Slot slot : slots) {
            if (slot == null) {
                continue;
            }
            if (slot.glass != null) {
                slot.used = now;
            } else if (slot.target != null && (reloaded || now - slot.used > KEEP_MS)) {
                try (GlPass pass = GlPass.begin()) {
                    slot.release(); // which binds the screen's framebuffer: put back
                }
            }
            slot.glass = null;
        }
    }

    private static void draw(Slot slot, View camera, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        TextureTarget target = slot.target;
        if (level == null || mc.player == null || target == null) {
            return;
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
        // seen from there, and turned the right way round: flipped left to right on the screen (the glass reads it back
        // so). PORT: 1.21.1's projection carries the view's shake (GameRenderer#renderLevel), so the flip is laid on the
        // screen after it - the same picture as 26.3's flip put before the shake, as a projection only scales its x
        Matrix4f view = new Matrix4f(camera.viewRotation).mul(reflect);
        Matrix4f screen = new Matrix4f().scale(-1, 1, 1).mul(camera.projection);
        // the glass as the near plane: what is behind it (n . p < d) is not drawn
        Vector4f plane = new Vector4f(n.x, n.y, n.z, -d + 0.002F);
        new Matrix4f(view).invert().transpose().transform(plane);
        Matrix4f projection = oblique(new Matrix4f(screen), plane);
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
        MirrorScenes.Drawn chunks = MirrorScenes.prepare(level, wanted, new Vector3d(eye.x, eye.y, eye.z));

        // the fog of where the glass looks out, not of where the eye is (from under water, a mirror in the air)
        float partial = delta.getGameTimeDeltaPartialTick(false);
        FogType seen = fogTypeAt(level, glass.lower.relative(glass.facing));
        Camera real = camera.camera;
        Camera fog = seen == real.getFluidInCamera() ? real : new FogCamera(real, seen);
        int chunkDistance = mc.options.getEffectiveRenderDistance();
        float renderDistance = mc.gameRenderer.getRenderDistance();
        float darken = mc.gameRenderer.getDarkenWorldAmount(partial);
        boolean foggy = level.effects().isFoggyAt(Mth.floor(cam.x), Mth.floor(cam.y)) || mc.gui.getBossOverlay().shouldCreateWorldFog();
        if (fog != real) {
            FogRenderer.setupColor(fog, partial, level, chunkDistance, darken);
        }
        FogRenderer.levelFogColor();
        float[] fogColour = RenderSystem.getShaderFogColor();

        // and all of it into the slot's picture: the fog's colour, the sky as the reflected camera sees it, the rest
        target.bindWrite(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(fogColour[0], fogColour[1], fogColour[2], 1.0F);
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        boolean panoramic = mc.gameRenderer.isPanoramicMode();
        try {
            // PORT: the game's "Fabulous" graphics draw see-through things into targets of their own, bound by their
            // render types; while the picture is drawn the game is told it is drawing a panorama, for which it does not,
            // so all of it goes into the picture (26.3's pass is drawn into its own target whatever the setting)
            mc.gameRenderer.setPanoramicMode(true);
            // the sky only turns: the reflection's turn without its shift, and the camera's own frustum (the sky is not
            // drawn in powder snow or lava, nor when blind - renderSky asks the fog's camera)
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(screen, VertexSorting.DISTANCE_TO_ORIGIN);
            FogRenderer.setupFog(fog, FogRenderer.FogMode.FOG_SKY, renderDistance, foggy, partial);
            RenderSystem.setShader(GameRenderer::getPositionShader);
            mc.levelRenderer.renderSky(new Matrix4f(view).setTranslation(0, 0, 0), screen, partial, fog, foggy,
                    () -> FogRenderer.setupFog(fog, FogRenderer.FogMode.FOG_SKY, renderDistance, foggy, partial));
            // the rest, through the glass, in the world's fog for the blocks
            FogRenderer.levelFogColor();
            FogRenderer.setupFog(fog, FogRenderer.FogMode.FOG_TERRAIN, Math.max(renderDistance, 32.0F), foggy, partial);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            // under a shader pack, lit as the pack lights: by the table measured from it, once there is one (MirrorLight)
            boolean lit = IrisHooks.shaderPackInUse() && MirrorLight.mode() == MirrorLight.Mode.ON && slot.light != null && slot.light.measured;
            MirrorLight.use(lit ? slot.light : null);
            drawing = true;
            drawWorld(chunks, level, camera, view, projection, centre, normal, partial);
        } finally {
            drawing = false;
            MirrorLight.use(null);
            mc.gameRenderer.setPanoramicMode(panoramic);
            if (fog != real) {
                FogRenderer.setupColor(real, partial, level, chunkDistance, darken); // the world's fog colour again
            }
        }
    }

    /** The blocks, entities and block entities before the glass, into the picture bound, as the world draws its own. */
    private static void drawWorld(MirrorScenes.Drawn chunks, ClientLevel level, View camera, Matrix4f view, Matrix4f projection,
            Vec3 centre, Vec3 normal, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.pos;
        chunks.render(RenderType.solid(), cam, view, projection);
        // as the world's renderer draws the mipped cut-out layer (LevelRenderer#renderLevel, NeoForge's leaves fix)
        AbstractTexture atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        atlas.setBlurMipmap(false, mc.options.mipmapLevels().get() > 0);
        chunks.render(RenderType.cutoutMipped(), cam, view, projection);
        atlas.restoreLastBlurMipmap();
        chunks.render(RenderType.cutout(), cam, view, projection);
        if (level.effects().constantAmbientLight()) {
            Lighting.setupNetherLevel();
        } else {
            Lighting.setupLevel();
        }

        // the entities and block entities before the glass, placed from the camera, seen through the reflection
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.set(view);
        RenderSystem.applyModelViewMatrix();
        MultiBufferSource.BufferSource source = buffers();
        PoseStack pose = new PoseStack();
        AABB near = new AABB(centre, centre).inflate(ENTITY_REACH);
        EntityRenderDispatcher entities = mc.getEntityRenderDispatcher();
        for (Entity entity : level.getEntities((Entity) null, near, e -> true)) {
            if (normal.dot(entity.position().subtract(centre)) < -0.5) {
                continue; // behind the mirror
            }
            double x = Mth.lerp(partial, entity.xOld, entity.getX());
            double y = Mth.lerp(partial, entity.yOld, entity.getY());
            double z = Mth.lerp(partial, entity.zOld, entity.getZ());
            float yaw = Mth.lerp(partial, entity.yRotO, entity.getYRot());
            entities.render(entity, x - cam.x, y - cam.y, z - cam.z, yaw, partial, pose, source, entities.getPackedLightCoords(entity, partial));
        }
        source.endLastBatch();
        source.endBatch(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        source.endBatch(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        source.endBatch(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        source.endBatch(RenderType.entitySmoothCutout(TextureAtlas.LOCATION_BLOCKS));
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
                    pose.pushPose();
                    pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                    blockEntities.render(blockEntity, partial, pose, source);
                    pose.popPose();
                }
            }
        }
        // what is solid before the see-through blocks, as the world's renderer has it; the rest after them
        source.endLastBatch();
        source.endBatch(RenderType.solid());
        source.endBatch(RenderType.endPortal());
        source.endBatch(RenderType.endGateway());
        source.endBatch(Sheets.solidBlockSheet());
        source.endBatch(Sheets.cutoutBlockSheet());
        source.endBatch(Sheets.bedSheet());
        source.endBatch(Sheets.shulkerBoxSheet());
        source.endBatch(Sheets.signSheet());
        source.endBatch(Sheets.hangingSignSheet());
        source.endBatch(Sheets.chestSheet());
        chunks.render(RenderType.translucent(), cam, view, projection);
        chunks.render(RenderType.tripwire(), cam, view, projection);
        source.endBatch();
    }

    /**
     * The buffers the pictures' entities and block entities are drawn into: those of the kinds the world keeps apart
     * (RenderBuffers' own list), and one for the rest. PORT: 26.3 makes a whole {@code RenderBuffers} for this; 1.21.1's
     * would also take a section builder pack and ask every mod again for buffers of its own, which a picture needs not.
     */
    private static MultiBufferSource.BufferSource buffers() {
        MultiBufferSource.BufferSource source = buffers;
        if (source == null) {
            SequencedMap<RenderType, ByteBufferBuilder> fixed = new Object2ObjectLinkedOpenHashMap<>();
            for (RenderType type : List.of(Sheets.solidBlockSheet(), Sheets.cutoutBlockSheet(), Sheets.bannerSheet(),
                    Sheets.translucentCullBlockSheet(), Sheets.shieldSheet(), Sheets.bedSheet(), Sheets.shulkerBoxSheet(), Sheets.signSheet(),
                    Sheets.hangingSignSheet(), Sheets.chestSheet(), RenderType.armorEntityGlint(), RenderType.glint(),
                    RenderType.glintTranslucent(), RenderType.entityGlint(), RenderType.entityGlintDirect(), RenderType.waterMask())) {
                fixed.putIfAbsent(type, new ByteBufferBuilder(type.bufferSize()));
            }
            source = MultiBufferSource.immediateWithBuffers(fixed, new ByteBufferBuilder(786432));
            buffers = source;
        }
        return source;
    }

    /** The fog of a place: the water's in water, lava's in lava, the air's anywhere else. */
    static FogType fogTypeAt(ClientLevel level, BlockPos pos) {
        var fluid = level.getFluidState(pos);
        return fluid.is(net.minecraft.tags.FluidTags.WATER) ? FogType.WATER
                : fluid.is(net.minecraft.tags.FluidTags.LAVA) ? FogType.LAVA : FogType.NONE;
    }

    /** Whether any of the box is on the glass's front side. */
    private static boolean inFront(AABB box, Vec3 centre, Vec3 normal) {
        double reach = Math.abs(normal.x) * box.getXsize() / 2 + Math.abs(normal.z) * box.getZsize() / 2;
        return normal.dot(box.getCenter().subtract(centre)) + reach > 0;
    }

    /**
     * {@code projection} with its near plane moved onto {@code plane} (in view space, the side to be drawn positive), its
     * far plane tilted to keep the frustum's far corner (E. Lengyel's oblique near-plane clipping); null if the plane is
     * not in view. PORT: the projection is 1.21.1's, OpenGL's own way round (the near plane at -1, the far at 1); 26.3's
     * depth is reversed.
     */
    static @Nullable Matrix4f oblique(Matrix4f projection, Vector4f plane) {
        // the frustum's corner farthest out along the plane, in view space: on the far plane
        Vector4f clipPlane = new Vector4f(plane);
        new Matrix4f(projection).invert().transpose().transform(clipPlane);
        Vector4f corner = new Vector4f(Math.signum(clipPlane.x), Math.signum(clipPlane.y), 1, 1);
        new Matrix4f(projection).invert().transform(corner);
        Vector4f w = new Vector4f(projection.m03(), projection.m13(), projection.m23(), projection.m33());
        float along = plane.dot(corner);
        if (along <= 1e-6F) {
            return null;
        }
        float k = 2 * w.dot(corner) / along;
        // the depth row becomes k C - w: near (z + w >= 0) is then the plane, far (z <= w) keeps the corner
        projection.m02(k * plane.x - w.x);
        projection.m12(k * plane.y - w.y);
        projection.m22(k * plane.z - w.z);
        projection.m32(k * plane.w - w.w);
        return projection;
    }

    /** A picture a mirror is drawn into, and the render type its glass reads it with. */
    private static final class Slot {
        final int index;
        final RenderType type;
        @Nullable Glass glass;
        @Nullable TextureTarget target;
        /** When a mirror was last drawn into it. */
        long used = net.minecraft.Util.getMillis();
        /** Under a shader pack: the pack's light as measured at this slot's glass. */
        MirrorLight.@Nullable Table light;

        Slot(int index) {
            this.index = index;
            // the glass: its corners, drawn with the world's block entities, reading this slot's picture where each pixel is
            // on the screen; fogged as the world is, solid, tested and written into the world's depth
            this.type = RenderType.create("nookworks_mirror_glass_" + index, DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS,
                    RenderType.TRANSIENT_BUFFER_SIZE, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(MirrorShaders::glass))
                            .setTextureState(new RenderStateShard.EmptyTextureStateShard(
                                    () -> RenderSystem.setShaderTexture(0, this.target == null ? 0 : this.target.getColorTextureId()), () -> {}))
                            .createCompositeState(false));
        }

        /** As big as the screen: made again if it is not. Only inside a pass that puts the bound framebuffer back. */
        void fit(int width, int height) {
            if (this.target != null && this.target.width == width && this.target.height == height) {
                return;
            }
            if (this.target == null) {
                this.target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            } else {
                this.target.resize(width, height, Minecraft.ON_OSX);
            }
        }

        /** Its picture let go; made again the next time a mirror is drawn into it. */
        void release() {
            if (this.target != null) {
                this.target.destroyBuffers();
                this.target = null;
            }
            if (this.light != null) {
                this.light.close();
                this.light = null;
            }
        }
    }
}
