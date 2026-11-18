package dev.nookworks.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.jspecify.annotations.Nullable;

/**
 * What Standing Mirrors need of Iris, looked up by name so a game without it never loads its classes (as CompatHooks'
 * mixins do). Under a shader pack Iris draws the world with programs of its own, into its own buffers, in vertex
 * formats of its own and with depth the usual way round (the game's 26.3 depth is reversed) - while its
 * {@code ImmediateState.isRenderingLevel} says the world is being drawn. A mirror's picture is drawn with that put
 * down, as Iris has it for its menus (with {@code bypass} too, which keeps the game's own programs, and
 * {@code skipExtension} for the threads that build the meshes): the game's own programs, formats and depth. Its glass
 * is drawn so too, over the pack's finished picture (MirrorReflections).
 *
 * <p>In 1.21.1 (Iris 1.8) the names are the same. A picture's sky is drawn by the game's own {@code renderSky}, into
 * which Iris hooks the stages of its pipeline (sky, sun, moon, stars...): the stage the pipeline was at is put back
 * after a picture, or the world's own drawing would go on at the picture's last.
 */
final class IrisHooks {
    private static boolean looked;
    private static @Nullable MethodHandle inUse;
    private static @Nullable MethodHandle shadowPass;
    private static @Nullable MethodHandle getBypass;
    private static @Nullable MethodHandle setBypass;
    private static @Nullable MethodHandle getLevel;
    private static @Nullable MethodHandle setLevel;
    private static @Nullable MethodHandle getExtended;
    private static @Nullable MethodHandle setExtended;
    private static @Nullable ThreadLocal<Boolean> skipExtension;
    private static @Nullable MethodHandle pipeline;
    private static @Nullable MethodHandle getPhase;
    private static @Nullable MethodHandle setPhase;

    private IrisHooks() {}

    @SuppressWarnings("unchecked")
    private static void look() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            inUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
            shadowPass = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(instance);
            Class<?> state = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
            getBypass = lookup.findStaticGetter(state, "bypass", boolean.class);
            setBypass = lookup.findStaticSetter(state, "bypass", boolean.class);
            getLevel = lookup.findStaticGetter(state, "isRenderingLevel", boolean.class);
            setLevel = lookup.findStaticSetter(state, "isRenderingLevel", boolean.class);
            skipExtension = (ThreadLocal<Boolean>) state.getField("skipExtension").get(null);
            try {
                getExtended = lookup.findStaticGetter(state, "renderWithExtendedVertexFormat", boolean.class);
                setExtended = lookup.findStaticSetter(state, "renderWithExtendedVertexFormat", boolean.class);
            } catch (ReflectiveOperationException e) {
                getExtended = null;
                setExtended = null;
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            inUse = null;
            return;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Class<?> manager = Class.forName("net.irisshaders.iris.pipeline.PipelineManager");
            Class<?> world = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline");
            Class<?> phase = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
            MethodHandle managerOf = lookup.findStatic(iris, "getPipelineManager", MethodType.methodType(manager));
            pipeline = MethodHandles.filterReturnValue(managerOf, lookup.findVirtual(manager, "getPipelineNullable", MethodType.methodType(world)));
            getPhase = lookup.findVirtual(world, "getPhase", MethodType.methodType(phase));
            setPhase = lookup.findVirtual(world, "setPhase", MethodType.methodType(void.class, phase));
        } catch (ReflectiveOperationException | LinkageError e) {
            pipeline = null; // the pictures are drawn all the same; the pipeline's stage is not put back after them
        }
    }

    /** The pack's pipeline, and the stage it is at, or nulls. */
    private static Object @Nullable [] phase() {
        if (pipeline == null || getPhase == null) {
            return null;
        }
        try {
            Object world = pipeline.invoke();
            return world == null ? null : new Object[] {world, getPhase.invoke(world)};
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Whether a mirror can be drawn now: no shader pack, or Iris's switches found (an Iris without them would draw the
     * picture its own way, broken).
     */
    static boolean mirrorsDrawable() {
        return !shaderPackInUse() || setBypass != null && setLevel != null && skipExtension != null;
    }

    /** Whether an Iris shader pack draws the world. */
    static boolean shaderPackInUse() {
        look();
        try {
            return inUse != null && (boolean) inUse.invoke();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Whether Iris is drawing its shadows (the world again, from the sun), not the view. */
    static boolean shadowPass() {
        look();
        try {
            return shadowPass != null && (boolean) shadowPass.invoke();
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Runs {@code drawing} with the game's own programs, vertex formats and depth, as Iris draws its menus; Iris's
     * switches set back after. Without Iris, just runs it.
     */
    static void vanilla(Runnable drawing) {
        look();
        if (setBypass == null || setLevel == null || skipExtension == null) {
            drawing.run();
            return;
        }
        boolean bypassed;
        boolean level;
        try {
            bypassed = (boolean) getBypass.invoke();
            level = (boolean) getLevel.invoke();
            setBypass.invoke(true);
            setLevel.invoke(false);
        } catch (Throwable e) {
            drawing.run();
            return;
        }
        boolean skipped = skipExtension.get();
        skipExtension.set(true);
        boolean extended = extended(false);
        Object[] phase = phase();
        try {
            drawing.run();
        } finally {
            extended(extended);
            skipExtension.set(skipped);
            try {
                setLevel.invoke(level);
                setBypass.invoke(bypassed);
                if (phase != null && setPhase != null) {
                    setPhase.invoke(phase[0], phase[1]);
                }
            } catch (Throwable ignored) {
                // set as they were a moment ago: cannot fail
            }
        }
    }

    /**
     * Iris 1.8 (1.21.1) lays the game's block, glyph and entity formats out as its own wider ones whenever a pack is in use
     * ({@code renderWithExtendedVertexFormat}, never turned off): a vertex buffer of the game's block format set up
     * then reads its vertices with Iris's strides, and the mirrors' terrain came out as nothing. Sets it, answering what it
     * was; false without Iris.
     */
    private static boolean extended(boolean on) {
        if (getExtended == null || setExtended == null) {
            return false;
        }
        try {
            boolean was = (boolean) getExtended.invoke();
            setExtended.invoke(on);
            return was;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Runs {@code uploading} - meshes of the game's own formats going onto the GPU - with those formats laid out as the
     * game lays them (see {@link #extended}). Without Iris, just runs it.
     */
    static void vanillaUpload(Runnable uploading) {
        look();
        if (getExtended == null) {
            uploading.run();
            return;
        }
        boolean extended = extended(false);
        try {
            uploading.run();
        } finally {
            extended(extended);
        }
    }

    /** The game's own vertex formats for what this thread builds meanwhile (a worker compiling a section's meshes). */
    static <T> T vanillaFormats(java.util.function.Supplier<T> building) {
        look();
        if (skipExtension == null) {
            return building.get();
        }
        boolean skipped = skipExtension.get();
        skipExtension.set(true);
        try {
            return building.get();
        } finally {
            skipExtension.set(skipped);
        }
    }
}
