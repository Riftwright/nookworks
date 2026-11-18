package dev.nookworks.port.storage;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Stand-in for 26.3's {@code net.minecraft.world.level.storage.TagValueOutput}: a {@link ValueOutput} writing into a
 * {@code CompoundTag}. Values go in under the same keys, in the same nesting and with the same encodings as 26.3's:
 * codecs run on registry-aware NBT ops when there are registries, children are compounds, lists are list tags.
 * <p>
 * Besides 26.3's two factories, {@link #into} writes into a tag that 1.21.1 hands over ({@code saveAdditional}'s, an
 * entity's), so a 26.3 save method's body runs unchanged on it.
 */
public class TagValueOutput implements ValueOutput {
    private final ProblemReporter problemReporter;
    private final DynamicOps<Tag> ops;
    private final HolderLookup.@Nullable Provider lookup;
    private final CompoundTag output;

    private TagValueOutput(ProblemReporter problemReporter, DynamicOps<Tag> ops, HolderLookup.@Nullable Provider lookup,
            CompoundTag output) {
        this.problemReporter = problemReporter;
        this.ops = ops;
        this.lookup = lookup;
        this.output = output;
    }

    /** A new, empty object whose codecs encode against the registries. */
    public static TagValueOutput createWithContext(ProblemReporter problemReporter, HolderLookup.Provider lookup) {
        return into(problemReporter, new CompoundTag(), lookup);
    }

    /** A new, empty object whose codecs encode without registries. */
    public static TagValueOutput createWithoutContext(ProblemReporter problemReporter) {
        return into(problemReporter, new CompoundTag(), null);
    }

    /** Writes into the given tag (1.21.1's own), against the registries if there are any. */
    public static TagValueOutput into(ProblemReporter problemReporter, CompoundTag tag, HolderLookup.@Nullable Provider lookup) {
        DynamicOps<Tag> ops = lookup == null ? NbtOps.INSTANCE : lookup.createSerializationContext(NbtOps.INSTANCE);
        return new TagValueOutput(problemReporter, ops, lookup, tag);
    }

    @Override
    public <T> void store(String name, Codec<T> codec, T value) {
        DataResult<Tag> result = codec.encodeStart(this.ops, value);
        result.result().ifPresentOrElse(tag -> this.output.put(name, tag), () -> result.error().ifPresent(error -> {
            this.problemReporter.report(() -> "Failed to encode value '" + value + "' to field '" + name + "': " + error.message());
            error.partialValue().ifPresent(partial -> this.output.put(name, partial));
        }));
    }

    @Override
    public <T> void storeNullable(String name, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            this.store(name, codec, value);
        }
    }

    @Override
    public void putBoolean(String name, boolean value) {
        this.output.putBoolean(name, value);
    }

    @Override
    public void putByte(String name, byte value) {
        this.output.putByte(name, value);
    }

    @Override
    public void putShort(String name, short value) {
        this.output.putShort(name, value);
    }

    @Override
    public void putInt(String name, int value) {
        this.output.putInt(name, value);
    }

    @Override
    public void putLong(String name, long value) {
        this.output.putLong(name, value);
    }

    @Override
    public void putFloat(String name, float value) {
        this.output.putFloat(name, value);
    }

    @Override
    public void putDouble(String name, double value) {
        this.output.putDouble(name, value);
    }

    @Override
    public void putString(String name, String value) {
        this.output.putString(name, value);
    }

    @Override
    public void putIntArray(String name, int[] value) {
        this.output.putIntArray(name, value);
    }

    @Override
    public ValueOutput child(String name) {
        CompoundTag child = new CompoundTag();
        this.output.put(name, child);
        return new TagValueOutput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(name)), this.ops, this.lookup, child);
    }

    @Override
    public <T> TypedOutputList<T> list(String name, Codec<T> codec) {
        ListTag list = new ListTag();
        this.output.put(name, list);
        return new TypedOutputList<T>() {
            @Override
            public void add(T value) {
                DataResult<Tag> result = codec.encodeStart(TagValueOutput.this.ops, value);
                result.result().ifPresentOrElse(list::add, () -> result.error().ifPresent(error -> {
                    TagValueOutput.this.problemReporter.report(
                            () -> "Failed to append value '" + value + "' to list '" + name + "': " + error.message());
                    error.partialValue().ifPresent(list::add);
                }));
            }

            @Override
            public boolean isEmpty() {
                return list.isEmpty();
            }
        };
    }

    @Override
    public void discard(String name) {
        this.output.remove(name);
    }

    @Override
    public boolean isEmpty() {
        return this.output.isEmpty();
    }

    /** The tag written into (the same one each time, still open to more writes). */
    public CompoundTag buildResult() {
        return this.output;
    }

    /** The registries this encodes against, if any: for handing the tag on to 1.21.1 methods that take them. */
    public HolderLookup.@Nullable Provider lookupOrNull() {
        return this.lookup;
    }
}
