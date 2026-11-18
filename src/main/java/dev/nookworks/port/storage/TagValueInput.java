package dev.nookworks.port.storage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;

/**
 * Stand-in for 26.3's {@code net.minecraft.world.level.storage.TagValueInput}: a {@link ValueInput} over a
 * {@code CompoundTag}, read as 26.3 reads one. A number field is read from any kind of number tag; a field of the wrong
 * kind is reported and taken as missing; codecs decode against the registries; a list element that does not decode is
 * reported and left out.
 */
public class TagValueInput implements ValueInput {
    private final ProblemReporter problemReporter;
    private final HolderLookup.Provider lookup;
    private final DynamicOps<Tag> ops;
    private final CompoundTag input;

    private TagValueInput(ProblemReporter problemReporter, HolderLookup.Provider lookup, DynamicOps<Tag> ops, CompoundTag input) {
        this.problemReporter = problemReporter;
        this.lookup = lookup;
        this.ops = ops;
        this.input = input;
    }

    /** The tag read against the registries. */
    public static ValueInput create(ProblemReporter problemReporter, HolderLookup.Provider lookup, CompoundTag tag) {
        return new TagValueInput(problemReporter, lookup, lookup.createSerializationContext(NbtOps.INSTANCE), tag);
    }

    /** The tag this reads (for handing on to 1.21.1 methods that take one). */
    public CompoundTag tag() {
        return this.input;
    }

    @Override
    public HolderLookup.Provider lookup() {
        return this.lookup;
    }

    @Override
    public <T> Optional<T> read(String name, Codec<T> codec) {
        Tag tag = this.input.get(name);
        if (tag == null) {
            return Optional.empty();
        }
        DataResult<T> result = codec.parse(this.ops, tag);
        if (result.result().isPresent()) {
            return result.result();
        }
        DataResult.Error<T> error = result.error().orElseThrow();
        this.problemReporter.report(() -> "Failed to decode value '" + tag + "' from field '" + name + "': " + error.message());
        return error.partialValue();
    }

    private <T extends Tag> @Nullable T typed(String name, TagType<T> expected) {
        Tag tag = this.input.get(name);
        if (tag == null) {
            return null;
        }
        if (tag.getType() != expected) {
            this.problemReporter.report(() -> "Expected field '" + name + "' to contain value of type " + expected.getName() + ", but got "
                    + tag.getType().getName());
            return null;
        }
        @SuppressWarnings("unchecked")
        T typed = (T) tag;
        return typed;
    }

    private @Nullable NumericTag number(String name) {
        Tag tag = this.input.get(name);
        if (tag == null) {
            return null;
        }
        if (tag instanceof NumericTag number) {
            return number;
        }
        this.problemReporter.report(() -> "Expected field '" + name + "' to contain number, but got " + tag.getType().getName());
        return null;
    }

    private ValueInput wrap(String name, CompoundTag tag) {
        return new TagValueInput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(name)), this.lookup, this.ops, tag);
    }

    @Override
    public Optional<ValueInput> child(String name) {
        CompoundTag tag = this.typed(name, CompoundTag.TYPE);
        return tag == null ? Optional.empty() : Optional.of(this.wrap(name, tag));
    }

    @Override
    public ValueInput childOrEmpty(String name) {
        CompoundTag tag = this.typed(name, CompoundTag.TYPE);
        return this.wrap(name, tag == null ? new CompoundTag() : tag);
    }

    @Override
    public <T> Optional<TypedInputList<T>> list(String name, Codec<T> codec) {
        ListTag tag = this.typed(name, ListTag.TYPE);
        return tag == null ? Optional.empty() : Optional.of(this.decoded(name, tag, codec));
    }

    @Override
    public <T> TypedInputList<T> listOrEmpty(String name, Codec<T> codec) {
        ListTag tag = this.typed(name, ListTag.TYPE);
        return this.decoded(name, tag == null ? new ListTag() : tag, codec);
    }

    /** The list decoded at once (26.3 decodes as it is walked; the values and the problems reported are the same). */
    private <T> TypedInputList<T> decoded(String name, ListTag tag, Codec<T> codec) {
        List<T> values = new ArrayList<>(tag.size());
        for (int i = 0; i < tag.size(); i++) {
            Tag element = tag.get(i);
            DataResult<T> result = codec.parse(this.ops, element);
            if (result.result().isPresent()) {
                values.add(result.result().get());
                continue;
            }
            int index = i;
            DataResult.Error<T> error = result.error().orElseThrow();
            this.problemReporter.report(() -> "Failed to decode value '" + element + "' from field '" + name + "' at index " + index + "': "
                    + error.message());
            error.partialValue().ifPresent(values::add);
        }
        return new TypedInputList<T>() {
            @Override
            public boolean isEmpty() {
                return values.isEmpty();
            }

            @Override
            public Stream<T> stream() {
                return values.stream();
            }

            @Override
            public Iterator<T> iterator() {
                return values.iterator();
            }
        };
    }

    @Override
    public boolean getBooleanOr(String name, boolean defaultValue) {
        NumericTag number = this.number(name);
        return number != null ? number.getAsByte() != 0 : defaultValue;
    }

    @Override
    public byte getByteOr(String name, byte defaultValue) {
        NumericTag number = this.number(name);
        return number != null ? number.getAsByte() : defaultValue;
    }

    @Override
    public int getShortOr(String name, short defaultValue) {
        NumericTag number = this.number(name);
        return number != null ? number.getAsShort() : defaultValue;
    }

    @Override
    public Optional<Integer> getInt(String name) {
        NumericTag number = this.number(name);
        return number != null ? Optional.of(number.getAsInt()) : Optional.empty();
    }

    @Override
    public int getIntOr(String name, int defaultValue) {
        NumericTag number = this.number(name);
        return number != null ? number.getAsInt() : defaultValue;
    }

    @Override
    public long getLongOr(String name, long defaultValue) {
        NumericTag number = this.number(name);
        return number != null ? number.getAsLong() : defaultValue;
    }

    @Override
    public Optional<Long> getLong(String name) {
        NumericTag number = this.number(name);
        return number != null ? Optional.of(number.getAsLong()) : Optional.empty();
    }

    @Override
    public float getFloatOr(String name, float defaultValue) {
        NumericTag number = this.number(name);
        return number != null ? number.getAsFloat() : defaultValue;
    }

    @Override
    public double getDoubleOr(String name, double defaultValue) {
        NumericTag number = this.number(name);
        return number != null ? number.getAsDouble() : defaultValue;
    }

    @Override
    public Optional<String> getString(String name) {
        StringTag tag = this.typed(name, StringTag.TYPE);
        return tag != null ? Optional.of(tag.getAsString()) : Optional.empty();
    }

    @Override
    public String getStringOr(String name, String defaultValue) {
        StringTag tag = this.typed(name, StringTag.TYPE);
        return tag != null ? tag.getAsString() : defaultValue;
    }

    @Override
    public Optional<int[]> getIntArray(String name) {
        IntArrayTag tag = this.typed(name, IntArrayTag.TYPE);
        return tag != null ? Optional.of(tag.getAsIntArray()) : Optional.empty();
    }
}
