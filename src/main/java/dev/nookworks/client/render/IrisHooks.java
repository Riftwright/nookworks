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
 */
final class IrisHooks {
    private static boolean looked;
    private static @Nullable MethodHandle inUse;
    private static @Nullable MethodHandle shadowPass;
    private static @Nullable MethodHandle getBypass;
    private static @Nullable MethodHandle setBypass;
    private static @Nullable MethodHandle getLevel;
    private static @Nullable MethodHandle setLevel;
    private static @Nullable ThreadLocal<Boolean> skipExtension;

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
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            inUse = null;
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
        try {
            drawing.run();
        } finally {
            skipExtension.set(skipped);
            try {
                setLevel.invoke(level);
                setBypass.invoke(bypassed);
            } catch (Throwable ignored) {
                // set as they were a moment ago: cannot fail
            }
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
