package dev.nookworks.ifworld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The If: the overworld again, from another seed - a second roll of the world. Its number is six throws of a Die at the
 * If Mirror (each face 1 to 6, so 46,656 worlds), and the number alone makes the world: the same number is the same
 * world in any save. It is rolled once; an operator can reset it, which takes effect at the next start.
 * <p>
 * This keeps, for the whole server, the faces thrown so far, where the two mirrors stand - the home mirror (anywhere but
 * the If) and the If's own (its way home) - and whether a reset is waiting for the next start.
 */
public final class IfWorld extends SavedData {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, Nookworks.id("if"));
    /** How many throws make the number. */
    public static final int THROWS = 6;

    public static final Codec<IfWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 6).listOf().optionalFieldOf("faces", List.of()).forGetter(w -> w.faces),
            GlobalPos.CODEC.optionalFieldOf("home_mirror").forGetter(w -> Optional.ofNullable(w.home)),
            BlockPos.CODEC.optionalFieldOf("if_mirror").forGetter(w -> Optional.ofNullable(w.there)),
            Codec.BOOL.optionalFieldOf("reset_pending", false).forGetter(w -> w.resetPending),
            Codec.INT.optionalFieldOf("generation", 0).forGetter(w -> w.generation),
            Codec.BOOL.optionalFieldOf("if_mirror_set_down").forGetter(w -> Optional.of(w.ifMirrorSetDown))).apply(i, IfWorld::new));
    public static final SavedDataType<IfWorld> TYPE = new SavedDataType<>(Nookworks.id("if_world"), IfWorld::new, CODEC);

    private final List<Integer> faces;
    private @Nullable GlobalPos home;
    private @Nullable BlockPos there;
    private boolean resetPending;
    /** How many times it has been reset: what a player remembers of an If (where they were in it) is of one of these. */
    private int generation;
    /** Whether the If's own mirror has ever been set down in this If: a trip sets the first down free, any later one from the traveller's pocket. */
    private boolean ifMirrorSetDown;

    public IfWorld() {
        this(List.of(), Optional.empty(), Optional.empty(), false, 0, Optional.of(false));
    }

    private IfWorld(List<Integer> faces, Optional<GlobalPos> home, Optional<BlockPos> there, boolean resetPending, int generation,
            Optional<Boolean> ifMirrorSetDown) {
        this.faces = new ArrayList<>(faces.subList(0, Math.min(THROWS, faces.size())));
        this.home = home.orElse(null);
        this.there = there.orElse(null);
        this.resetPending = resetPending;
        this.generation = generation;
        this.ifMirrorSetDown = ifMirrorSetDown.orElse(this.there != null); // a world from before this was kept
    }

    public static IfWorld get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The faces thrown so far, in order. */
    public List<Integer> faces() {
        return List.copyOf(this.faces);
    }

    /** Whether all six are thrown: the If is there (or will be, at the next start, if one was waiting). */
    public boolean rolled() {
        return this.faces.size() == THROWS;
    }

    /** One more throw. Returns false once all six are thrown. */
    public boolean addThrow(int face) {
        if (this.rolled() || face < 1 || face > 6) {
            return false;
        }
        this.faces.add(face);
        this.setDirty();
        return true;
    }

    /** The If's number, its six faces in a row ("352614"); "" until rolled. */
    public String number() {
        if (!this.rolled()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        this.faces.forEach(out::append);
        return out.toString();
    }

    /**
     * The world seed a number makes: the same on every machine and in every save, and nothing to do with this world's own
     * seed (a number told to a friend is the same world in theirs).
     */
    public static long seedOf(String number) {
        long seed = 0x5DEECE66DL;
        for (char c : ("nookworks:if/" + number).toCharArray()) {
            seed = mix(seed + c);
        }
        return seed;
    }

    /** SplitMix64's finaliser: every bit of the input stirred into every bit of the output. */
    private static long mix(long z) {
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }

    public @Nullable GlobalPos homeMirror() {
        return this.home;
    }

    public @Nullable BlockPos ifMirror() {
        return this.there;
    }

    /** A mirror set down at {@code pos} in {@code dimension}: the If's own in the If, else the home mirror. */
    public void record(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension.equals(KEY)) {
            this.there = pos.immutable();
            this.ifMirrorSetDown = true;
        } else {
            this.home = GlobalPos.of(dimension, pos.immutable());
        }
        this.setDirty();
    }

    /** The mirror at {@code pos} gone. */
    public void forget(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension.equals(KEY) ? pos.equals(this.there) : this.home != null && this.home.equals(GlobalPos.of(dimension, pos))) {
            if (dimension.equals(KEY)) {
                this.there = null;
            } else {
                this.home = null;
            }
            this.setDirty();
        }
    }

    /** The mirror already recorded in {@code dimension} (the If, or else the home one wherever it is), or null. */
    public @Nullable GlobalPos mirrorFor(ResourceKey<Level> dimension) {
        return dimension.equals(KEY) ? this.there == null ? null : GlobalPos.of(KEY, this.there) : this.home;
    }

    /** Which If this is, counting resets: a player's memories of an earlier one are dropped ({@code TravelLog}). */
    public int generation() {
        return this.generation;
    }

    /** Whether the If's own mirror has ever been set down in this If. */
    public boolean ifMirrorSetDown() {
        return this.ifMirrorSetDown;
    }

    /** As though the If's own mirror had never been set down (for tests; a reset does it too). */
    public void forgetIfMirrorSetDown() {
        this.ifMirrorSetDown = false;
        this.setDirty();
    }

    public boolean resetPending() {
        return this.resetPending;
    }

    /** An operator's reset: at the next start the If is taken away, and the mirror can be rolled afresh. */
    public void requestReset() {
        this.resetPending = true;
        this.setDirty();
    }

    /** A reset asked for, taken back (for tests: one asked for and undone within a tick). */
    public void forgetReset() {
        this.resetPending = false;
        this.setDirty();
    }

    /** The reset carried out (at a start, before the If would be made): no faces, no mirror in it. */
    void clearForReset() {
        this.faces.clear();
        this.there = null;
        this.ifMirrorSetDown = false;
        this.resetPending = false;
        this.generation++;
        this.setDirty();
    }
}
