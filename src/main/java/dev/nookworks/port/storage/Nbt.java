package dev.nookworks.port.storage;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 26.3's {@code CompoundTag} and {@code ListTag} getters, which 1.21.1's tags lack or have with other results:
 * tools/port1211/tx/storage.py turns {@code tag.getIntOr("k", 0)} into {@code Nbt.getIntOr(tag, "k", 0)}. They read as
 * 26.3's do: a number from any kind of number tag, a missing field or one of the wrong kind gives the default (or
 * empty), a child compound or list is the tag's own (writes to it go into the parent).
 * <p>
 * The {@code ValueInput} overloads are there for a call the script could not tell from a tag's: they do what the
 * input itself does.
 */
public final class Nbt {
    private Nbt() {
    }

    // ---- CompoundTag: a default for what is missing ----

    public static boolean getBooleanOr(CompoundTag tag, String key, boolean defaultValue) {
        return tag.get(key) instanceof NumericTag number ? number.getAsByte() != 0 : defaultValue;
    }

    public static byte getByteOr(CompoundTag tag, String key, byte defaultValue) {
        return tag.get(key) instanceof NumericTag number ? number.getAsByte() : defaultValue;
    }

    public static short getShortOr(CompoundTag tag, String key, short defaultValue) {
        return tag.get(key) instanceof NumericTag number ? number.getAsShort() : defaultValue;
    }

    public static int getIntOr(CompoundTag tag, String key, int defaultValue) {
        return tag.get(key) instanceof NumericTag number ? number.getAsInt() : defaultValue;
    }

    public static long getLongOr(CompoundTag tag, String key, long defaultValue) {
        return tag.get(key) instanceof NumericTag number ? number.getAsLong() : defaultValue;
    }

    public static float getFloatOr(CompoundTag tag, String key, float defaultValue) {
        return tag.get(key) instanceof NumericTag number ? number.getAsFloat() : defaultValue;
    }

    public static double getDoubleOr(CompoundTag tag, String key, double defaultValue) {
        return tag.get(key) instanceof NumericTag number ? number.getAsDouble() : defaultValue;
    }

    public static String getStringOr(CompoundTag tag, String key, String defaultValue) {
        return tag.get(key) instanceof StringTag string ? string.getAsString() : defaultValue;
    }

    /** The child compound, or a new empty one (not put into the tag). */
    public static CompoundTag getCompoundOrEmpty(CompoundTag tag, String key) {
        return tag.get(key) instanceof CompoundTag child ? child : new CompoundTag();
    }

    /** The list, of whatever its elements are, or a new empty one (not put into the tag). */
    public static ListTag getListOrEmpty(CompoundTag tag, String key) {
        return tag.get(key) instanceof ListTag list ? list : new ListTag();
    }

    // ---- CompoundTag: empty for what is missing ----

    public static Optional<Boolean> getBoolean(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag number ? Optional.of(number.getAsByte() != 0) : Optional.empty();
    }

    public static Optional<Byte> getByte(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag number ? Optional.of(number.getAsByte()) : Optional.empty();
    }

    public static Optional<Short> getShort(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag number ? Optional.of(number.getAsShort()) : Optional.empty();
    }

    public static Optional<Integer> getInt(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag number ? Optional.of(number.getAsInt()) : Optional.empty();
    }

    public static Optional<Long> getLong(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag number ? Optional.of(number.getAsLong()) : Optional.empty();
    }

    public static Optional<Float> getFloat(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag number ? Optional.of(number.getAsFloat()) : Optional.empty();
    }

    public static Optional<Double> getDouble(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag number ? Optional.of(number.getAsDouble()) : Optional.empty();
    }

    public static Optional<String> getString(CompoundTag tag, String key) {
        return tag.get(key) instanceof StringTag string ? Optional.of(string.getAsString()) : Optional.empty();
    }

    public static Optional<int[]> getIntArray(CompoundTag tag, String key) {
        return tag.get(key) instanceof IntArrayTag array ? Optional.of(array.getAsIntArray()) : Optional.empty();
    }

    public static Optional<CompoundTag> getCompound(CompoundTag tag, String key) {
        return tag.get(key) instanceof CompoundTag child ? Optional.of(child) : Optional.empty();
    }

    public static Optional<ListTag> getList(CompoundTag tag, String key) {
        return tag.get(key) instanceof ListTag list ? Optional.of(list) : Optional.empty();
    }

    /** 26.3's {@code keySet()} (1.21.1's {@code getAllKeys()}). */
    public static Set<String> keySet(CompoundTag tag) {
        return tag.getAllKeys();
    }

    // ---- ListTag ----

    private static Tag at(ListTag list, int index) {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    public static Optional<CompoundTag> getCompound(ListTag list, int index) {
        return at(list, index) instanceof CompoundTag child ? Optional.of(child) : Optional.empty();
    }

    public static CompoundTag getCompoundOrEmpty(ListTag list, int index) {
        return at(list, index) instanceof CompoundTag child ? child : new CompoundTag();
    }

    public static ListTag getListOrEmpty(ListTag list, int index) {
        return at(list, index) instanceof ListTag child ? child : new ListTag();
    }

    public static int getIntOr(ListTag list, int index, int defaultValue) {
        return at(list, index) instanceof NumericTag number ? number.getAsInt() : defaultValue;
    }

    public static long getLongOr(ListTag list, int index, long defaultValue) {
        return at(list, index) instanceof NumericTag number ? number.getAsLong() : defaultValue;
    }

    public static float getFloatOr(ListTag list, int index, float defaultValue) {
        return at(list, index) instanceof NumericTag number ? number.getAsFloat() : defaultValue;
    }

    public static double getDoubleOr(ListTag list, int index, double defaultValue) {
        return at(list, index) instanceof NumericTag number ? number.getAsDouble() : defaultValue;
    }

    public static String getStringOr(ListTag list, int index, String defaultValue) {
        return at(list, index) instanceof StringTag string ? string.getAsString() : defaultValue;
    }

    /** The elements that are compounds. */
    public static Stream<CompoundTag> compoundStream(ListTag list) {
        return list.stream().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast);
    }

    // ---- a ValueInput the script took for a tag: what the input does ----

    public static boolean getBooleanOr(ValueInput input, String key, boolean defaultValue) {
        return input.getBooleanOr(key, defaultValue);
    }

    public static byte getByteOr(ValueInput input, String key, byte defaultValue) {
        return input.getByteOr(key, defaultValue);
    }

    public static int getShortOr(ValueInput input, String key, short defaultValue) {
        return input.getShortOr(key, defaultValue);
    }

    public static int getIntOr(ValueInput input, String key, int defaultValue) {
        return input.getIntOr(key, defaultValue);
    }

    public static long getLongOr(ValueInput input, String key, long defaultValue) {
        return input.getLongOr(key, defaultValue);
    }

    public static float getFloatOr(ValueInput input, String key, float defaultValue) {
        return input.getFloatOr(key, defaultValue);
    }

    public static double getDoubleOr(ValueInput input, String key, double defaultValue) {
        return input.getDoubleOr(key, defaultValue);
    }

    public static String getStringOr(ValueInput input, String key, String defaultValue) {
        return input.getStringOr(key, defaultValue);
    }
}
