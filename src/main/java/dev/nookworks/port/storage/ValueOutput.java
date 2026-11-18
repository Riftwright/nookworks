package dev.nookworks.port.storage;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

/**
 * Stand-in for 26.3's {@code net.minecraft.world.level.storage.ValueOutput}: an object being saved, field by field.
 * 1.21.1 writes a {@code CompoundTag} directly; {@link TagValueOutput} is this over one, writing the same keys, nesting
 * and encodings as 26.3's.
 */
public interface ValueOutput {
    /** The value encoded with the codec (registry-aware) under the name; a failure is reported, its partial result kept. */
    <T> void store(String name, Codec<T> codec, T value);

    /** As {@link #store}, but nothing is written for null. */
    <T> void storeNullable(String name, Codec<T> codec, @Nullable T value);

    void putBoolean(String name, boolean value);

    void putByte(String name, byte value);

    void putShort(String name, short value);

    void putInt(String name, int value);

    void putLong(String name, long value);

    void putFloat(String name, float value);

    void putDouble(String name, double value);

    void putString(String name, String value);

    void putIntArray(String name, int[] value);

    /** A new object of its own under the name, to write into. */
    ValueOutput child(String name);

    /** A new list under the name, its elements encoded with the codec. */
    <T> TypedOutputList<T> list(String name, Codec<T> codec);

    /** Takes the field out again. */
    void discard(String name);

    boolean isEmpty();

    /** A list being written. */
    interface TypedOutputList<T> {
        void add(T value);

        boolean isEmpty();
    }
}
