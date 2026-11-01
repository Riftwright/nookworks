package dev.nookworks.client.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;


/**
 * The blocks Standing Mirrors show: meshes of their own of the sections before each mirror's glass, made by the game's
 * own section compiler from a copy of the section and its neighbours (as the world's meshes are), so a mirror shows the
 * blocks whatever draws the world itself (Sodium draws it its own way, and has no meshes of the game's to borrow). A
 * section is compiled off the render thread when a mirror first wants it and again when its blocks or light change
 * (the client level says so, ClientLevelMirrorMixin); one no mirror has wanted for a while is let go, looked over every
 * tick whether a mirror is drawn or not ({@link #tick()}). Translucent faces are put in order for the eye that sees them,
 * again when it has moved; a compile begun before the world or the blocks' looks changed is thrown away when it is done.
 */
public final class MirrorScenes {
    /** Sections compiled at once, each with its own buffers. */
    private static final int WORKERS = 2;
    /** A section no mirror has wanted for this long is let go. */
    private static final long KEEP_MS = 15_000;
    /** A section's translucent faces are put in order again when the eye is this far (squared) from where they were. */
    private static final double RESORT_DISTANCE_SQR = 1.0;
    /** At most this many sections' translucent faces put in order again in a frame, each mirror's. */
    private static final int RESORTS = 4;

    private static final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private static final ArrayDeque<SectionBufferBuilderPack> packs = new ArrayDeque<>();
    private static final ConcurrentLinkedQueue<Compiled> done = new ConcurrentLinkedQueue<>();
    private static int working;
    /** Counted up when every section is let go: a compile begun before it is not taken. */
    private static int generation;
    private static @Nullable ByteBufferBuilder sortBytes;
    /** Counted up each frame: a section's faces put in order for one mirror this frame are not for the other. */
    private static long frame;
    private static @Nullable ClientLevel level;
    /** What the meshes were made with: the block models and the options that change them. */
    private static @Nullable Object looks;

    private MirrorScenes() {}

    /** A section's meshes as uploaded, by layer; and whether it wants compiling again. */
    private static final class Section {
        final long node;
        final Map<ChunkSectionLayer, Mesh> meshes = new EnumMap<>(ChunkSectionLayer.class);
        boolean compiled;
        boolean dirty = true;
        boolean pending;
        long wanted;
        /** The translucent faces' centres, to put them in order again; and the eye they are in order for. */
        MeshData.@Nullable SortState sortState;
        double sortedX;
        double sortedY;
        double sortedZ;
        long sortedFrame = -1;

        Section(long node) {
            this.node = node;
        }

        void close() {
            this.meshes.values().forEach(Mesh::close);
            this.meshes.clear();
        }
    }

    private record Mesh(GpuBuffer vertices, @Nullable GpuBuffer indices, @Nullable IndexType indexType, int indexCount) {
        void close() {
            this.vertices.close();
            if (this.indices != null) {
                this.indices.close();
            }
        }
    }

    private record Compiled(Section section, int generation, Vector3dc eye, SectionCompiler.@Nullable Results results,
            SectionBufferBuilderPack pack) {}

    /** A block changed: its section wants compiling again, and those it touches (their faces and shade next to it). */
    public static void blockChanged(BlockPos pos) {
        if (sections.isEmpty()) {
            return;
        }
        for (int x = pos.getX() - 1; x <= pos.getX() + 1; x++) {
            for (int y = pos.getY() - 1; y <= pos.getY() + 1; y++) {
                for (int z = pos.getZ() - 1; z <= pos.getZ() + 1; z++) {
                    dirty(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z));
                }
            }
        }
    }

    /** These sections want compiling again (their light came, or the world says so). */
    public static void sectionsDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (sections.isEmpty()) {
            return;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    dirty(x, y, z);
                }
            }
        }
    }

    private static void dirty(int x, int y, int z) {
        Section section = sections.get(SectionPos.asLong(x, y, z));
        if (section != null) {
            section.dirty = true;
        }
    }

    /**
     * The sections {@code wanted}, as many as are compiled, ready to draw from a camera at {@code camera} (relative to which
     * the terrain's shader places them): those not compiled, or changed, are sent to be, nearest {@code eye} first, and
     * shown as they were meanwhile.
     */
    static ChunkSectionsToRender prepare(ClientLevel world, Collection<SectionPos> wanted, Vector3dc eye, Matrix4fc modelView) {
        Minecraft mc = Minecraft.getInstance();
        Object looks = List.of(mc.getModelManager().getBlockStateModelSet(), mc.options.ambientOcclusion().get(), mc.options.cutoutLeaves().get());
        if (world != level || !looks.equals(MirrorScenes.looks)) {
            clear();
            level = world;
            MirrorScenes.looks = looks;
        }
        upload();
        long now = Util.getMillis();
        List<Section> drawn = new ArrayList<>();
        List<SectionPos> toCompile = new ArrayList<>();
        for (SectionPos pos : wanted) {
            Section section = sections.computeIfAbsent(pos.asLong(), Section::new);
            section.wanted = now;
            if (section.dirty && !section.pending) {
                toCompile.add(pos);
            }
            if (section.compiled) {
                drawn.add(section);
            }
        }
        toCompile.sort(Comparator.comparingDouble(pos -> pos.center().distToCenterSqr(eye.x(), eye.y(), eye.z())));
        for (SectionPos pos : toCompile) {
            if (working >= WORKERS) {
                break;
            }
            compile(world, sections.get(pos.asLong()), eye);
        }
        // translucent faces far to near, as the eye sees them: the sections in that order, and within each its faces
        drawn.sort(Comparator.comparingDouble(section -> -SectionPos.of(section.node).center().distToCenterSqr(eye.x(), eye.y(), eye.z())));
        resort(drawn, eye);
        return draws(drawn, modelView);
    }

    /** The frame drawn. */
    static void frameDone() {
        frame++;
    }

    /** Every tick, mirrors drawn or not: what the workers finished taken in, and sections no mirror wants let go. */
    static void tick() {
        if (sections.isEmpty() && done.isEmpty()) {
            return;
        }
        upload();
        long now = Util.getMillis();
        sections.values().removeIf(section -> {
            boolean old = !section.pending && now - section.wanted > KEEP_MS;
            if (old) {
                section.close();
            }
            return old;
        });
    }

    /** How the faces of the section at {@code node} are put in order for an eye at {@code eye}: by where they are in it. */
    private static VertexSorting sorting(long node, Vector3dc eye) {
        BlockPos origin = SectionPos.of(node).origin();
        // the compiler's vertices are relative to the section's corner, as the game's own sorting has it
        return VertexSorting.byDistance((float) (eye.x() - origin.getX()), (float) (eye.y() - origin.getY()), (float) (eye.z() - origin.getZ()));
    }

    /** The translucent faces of the nearest sections whose eye has moved since put in order again, a few a frame. */
    private static void resort(List<Section> drawn, Vector3dc eye) {
        int left = RESORTS;
        for (int i = drawn.size() - 1; i >= 0 && left > 0; i--) { // nearest first: the list runs far to near
            Section section = drawn.get(i);
            Mesh mesh = section.meshes.get(ChunkSectionLayer.TRANSLUCENT);
            // put in order this frame for the other mirror: left so, or two mirrors seeing it would undo each other every frame
            if (section.sortState == null || mesh == null || mesh.indices == null || section.pending || section.sortedFrame == frame) {
                continue;
            }
            double dx = eye.x() - section.sortedX;
            double dy = eye.y() - section.sortedY;
            double dz = eye.z() - section.sortedZ;
            if (dx * dx + dy * dy + dz * dz < RESORT_DISTANCE_SQR) {
                continue;
            }
            if (sortBytes == null) {
                sortBytes = new ByteBufferBuilder(1536);
            }
            try (ByteBufferBuilder.Result sorted = section.sortState.buildSortedIndexBuffer(sortBytes, sorting(section.node, eye))) {
                if (sorted == null) {
                    continue;
                }
                GpuBuffer indices = RenderSystem.getDevice().createBuffer(() -> "Nookworks mirror translucent index",
                        GpuBuffer.USAGE_INDEX, sorted.byteBuffer());
                mesh.indices.close();
                section.meshes.put(ChunkSectionLayer.TRANSLUCENT, new Mesh(mesh.vertices, indices, mesh.indexType, mesh.indexCount));
            }
            section.sortedX = eye.x();
            section.sortedY = eye.y();
            section.sortedZ = eye.z();
            section.sortedFrame = frame;
            left--;
        }
    }

    private static void compile(ClientLevel world, Section section, Vector3dc eye) {
        Minecraft mc = Minecraft.getInstance();
        SectionBufferBuilderPack pack = packs.isEmpty() ? new SectionBufferBuilderPack() : packs.pop();
        RenderSectionRegion region = new RenderRegionCache().createRegion(world, section.node);
        SectionCompiler compiler = new SectionCompiler(mc.options.ambientOcclusion().get(), mc.options.cutoutLeaves().get(),
                mc.getModelManager().getBlockStateModelSet(), mc.getModelManager().getFluidStateModelSet(), mc.getBlockColors());
        VertexSorting sorting = sorting(section.node, eye);
        SectionPos pos = SectionPos.of(section.node);
        Vector3dc from = new Vector3d(eye);
        int made = generation;
        section.dirty = false;
        section.pending = true;
        working++;
        // in the game's own vertex formats, which the mirrors draw with (Iris widens them while it draws the world)
        CompletableFuture.supplyAsync(() -> IrisHooks.vanillaFormats(() -> compiler.compile(pos, region, sorting, pack, List.of())),
                        Util.backgroundExecutor())
                .whenComplete((results, failure) -> done.add(new Compiled(section, made, from, failure == null ? results : null, pack)));
    }

    /** What the workers finished, onto the GPU; their buffers back for the next. */
    private static void upload() {
        Compiled compiled;
        while ((compiled = done.poll()) != null) {
            working--;
            // the section it was made for, if it is still there: not one of another world, or of the looks before
            Section section = compiled.generation == generation && sections.get(compiled.section.node) == compiled.section
                    ? compiled.section : null;
            SectionCompiler.Results results = compiled.results;
            if (results != null && section != null && level != null) {
                section.close();
                section.sortState = results.transparencyState;
                section.sortedX = compiled.eye.x();
                section.sortedY = compiled.eye.y();
                section.sortedZ = compiled.eye.z();
                for (var entry : results.renderedLayers.entrySet()) {
                    MeshData mesh = entry.getValue();
                    MeshData.DrawState state = mesh.drawState();
                    if (!state.format().equals(DefaultVertexFormat.BLOCK)) {
                        continue; // built in another mod's format: not drawable with the game's terrain
                    }
                    String label = "Nookworks mirror " + entry.getKey().label();
                    GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                    GpuBuffer indices = mesh.indexBuffer() == null ? null
                            : RenderSystem.getDevice().createBuffer(() -> label + " index", GpuBuffer.USAGE_INDEX, mesh.indexBuffer());
                    section.meshes.put(entry.getKey(), new Mesh(vertices, indices, indices == null ? null : state.indexType(), state.indexCount()));
                }
                section.compiled = true;
            } else if (section != null) {
                section.dirty = true; // try again
            }
            if (section != null) {
                section.pending = false;
            }
            if (results != null) {
                results.release();
            }
            compiled.pack.clearAll();
            packs.push(compiled.pack);
        }
    }

    /** The sections' meshes as the terrain's draws: each layer's, each with its section's place. */
    private static ChunkSectionsToRender draws(List<Section> drawn, Matrix4fc modelView) {
        Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> layers = new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            layers.put(layer, new ArrayList<>());
        }
        List<DynamicGpuData.ChunkSectionInfo> infos = new ArrayList<>();
        int largest = 0;
        for (Section section : drawn) {
            if (section.meshes.isEmpty()) {
                continue;
            }
            int index = infos.size();
            BlockPos origin = SectionPos.of(section.node).origin();
            infos.add(new DynamicGpuData.ChunkSectionInfo(origin.getX(), origin.getY(), origin.getZ(), 1.0F));
            for (var entry : section.meshes.entrySet()) {
                Mesh mesh = entry.getValue();
                if (mesh.indices == null) {
                    largest = Math.max(largest, mesh.indexCount);
                }
                layers.get(entry.getKey()).add(new RenderPass.Draw<>(0, mesh.vertices, mesh.indices, mesh.indexType, 0, mesh.indexCount, 0,
                        (sectionInfos, uploader) -> uploader.setUniform("ChunkSection", sectionInfos[index])));
            }
        }
        GpuTextureView atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTerrainTransform(modelView, atlas.getWidth(0), atlas.getHeight(0));
        GpuBufferSlice[] sectionInfos = RenderSystem.getDynamicUniforms().writeChunkSections(infos.toArray(new DynamicGpuData.ChunkSectionInfo[0]));
        if (largest != 0) {
            RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(largest);
        }
        return new ChunkSectionsToRender.DrawSeparate(transform, layers, largest, sectionInfos);
    }

    /** Every section let go: another level, or the blocks' looks reloaded. Compiles still going are not taken when done. */
    static void clear() {
        sections.values().forEach(Section::close);
        sections.clear();
        generation++;
    }

    /** The world left: its sections and the level itself let go now, not when a mirror is next drawn (r19 self-review). */
    static void forget() {
        clear();
        upload(); // what is done is let go now; what is still going, when it is done, by the next tick
        level = null;
        looks = null;
    }
}
