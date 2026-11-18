package dev.nookworks.port.storage;

import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;

/**
 * Stand-in for 26.3's {@code net.minecraft.world.level.storage.ValueInput}: a saved object read field by field, each
 * getter with what to take when the field is missing or of the wrong kind. 1.21.1 reads a {@code CompoundTag}
 * directly; {@link TagValueInput} is this over one, read the way 26.3's is.
 */
public interface ValueInput {
    /** The field decoded with the codec (registry-aware), or empty if it is missing or does not decode. */
    <T> Optional<T> read(String name, Codec<T> codec);

    /** The field as an object of its own, if it is one. */
    Optional<ValueInput> child(String name);

    /** The field as an object of its own, or an empty one. */
    ValueInput childOrEmpty(String name);

    /** The field as a list, each element decoded with the codec (those that do not decode left out), if it is a list. */
    <T> Optional<TypedInputList<T>> list(String name, Codec<T> codec);

    /** As {@link #list}, or an empty list. */
    <T> TypedInputList<T> listOrEmpty(String name, Codec<T> codec);

    boolean getBooleanOr(String name, boolean defaultValue);

    byte getByteOr(String name, byte defaultValue);

    int getShortOr(String name, short defaultValue);

    Optional<Integer> getInt(String name);

    int getIntOr(String name, int defaultValue);

    long getLongOr(String name, long defaultValue);

    Optional<Long> getLong(String name);

    float getFloatOr(String name, float defaultValue);

    double getDoubleOr(String name, double defaultValue);

    Optional<String> getString(String name);

    String getStringOr(String name, String defaultValue);

    Optional<int[]> getIntArray(String name);

    /** The registries the codecs read against. */
    HolderLookup.Provider lookup();

    /** A list of decoded values. */
    interface TypedInputList<T> extends Iterable<T> {
        boolean isEmpty();

        Stream<T> stream();
    }
}
