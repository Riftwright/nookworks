package dev.nookworks.port.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Where the port meets 1.21.1's tag-based saves (the calls tools/port1211/tx/storage.py writes in):
 * <ul>
 * <li>{@link #output} / {@link #input} wrap the tag a 1.21.1 save or load method is handed, so the 26.3 body under it
 * runs unchanged;</li>
 * <li>{@link #tag} / {@link #lookup} take a 26.3 output or input apart again, for a 1.21.1 method that wants the tag and
 * the registries;</li>
 * <li>the rest are 26.3's block entity and entity methods that take an output or input (1.21.1's take tags), called
 * with the object as the first argument.</li>
 * </ul>
 * Problems met while saving or loading are logged as warnings with where they happened, as 26.3's own callers do.
 */
public final class ValueIO {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ValueIO() {
    }

    // ---- wrapping what 1.21.1 hands over ----

    /** The tag a save method is handed, to write into the 26.3 way. */
    public static ValueOutput output(Object owner, CompoundTag tag, HolderLookup.@Nullable Provider lookup) {
        return TagValueOutput.into(reporter(owner), tag, lookup);
    }

    /** As {@link #output(Object, CompoundTag, HolderLookup.Provider)}, for a method handed no registries. */
    public static ValueOutput output(Object owner, CompoundTag tag) {
        return TagValueOutput.into(reporter(owner), tag, null);
    }

    /** The tag a load method is handed, to read the 26.3 way. */
    public static ValueInput input(Object owner, CompoundTag tag, HolderLookup.Provider lookup) {
        return TagValueInput.create(reporter(owner), lookup, tag);
    }

    /** The tag an output writes into. */
    public static CompoundTag tag(ValueOutput output) {
        return tagged(output).buildResult();
    }

    /** The tag an input reads. */
    public static CompoundTag tag(ValueInput input) {
        return tagged(input).tag();
    }

    /** The registries an output encodes against (null for one made without). */
    public static HolderLookup.@Nullable Provider lookup(ValueOutput output) {
        return tagged(output).lookupOrNull();
    }

    /** The registries an input decodes against. */
    public static HolderLookup.Provider lookup(ValueInput input) {
        return input.lookup();
    }

    // ---- 26.3's block entity methods that take an output or input ----

    public static void loadWithComponents(BlockEntity entity, ValueInput input) {
        entity.loadWithComponents(tag(input), input.lookup());
    }

    public static void loadCustomOnly(BlockEntity entity, ValueInput input) {
        entity.loadCustomOnly(tag(input), input.lookup());
    }

    public static void handleUpdateTag(BlockEntity entity, ValueInput input) {
        entity.handleUpdateTag(tag(input), input.lookup());
    }

    public static void saveWithoutMetadata(BlockEntity entity, ValueOutput output) {
        tag(output).merge(entity.saveWithoutMetadata(lookup(entity, output)));
    }

    public static void saveWithFullMetadata(BlockEntity entity, ValueOutput output) {
        tag(output).merge(entity.saveWithFullMetadata(lookup(entity, output)));
    }

    public static void saveWithId(BlockEntity entity, ValueOutput output) {
        tag(output).merge(entity.saveWithId(lookup(entity, output)));
    }

    public static void saveCustomOnly(BlockEntity entity, ValueOutput output) {
        tag(output).merge(entity.saveCustomOnly(lookup(entity, output)));
    }

    public static void removeComponentsFromTag(BlockEntity entity, ValueOutput output) {
        entity.removeComponentsFromTag(tag(output));
    }

    /** 26.3's {@code BlockEntity.parseCustomNameSafe(input, name)}: the name as a component, or null. */
    public static @Nullable Component parseCustomNameSafe(ValueInput input, String name) {
        return input.read(name, ComponentSerialization.CODEC).orElse(null);
    }

    // ---- 26.3's entity methods that take an output or input ----

    public static void load(Entity entity, ValueInput input) {
        entity.load(tag(input));
    }

    public static void saveWithoutId(Entity entity, ValueOutput output) {
        entity.saveWithoutId(tag(output));
    }

    public static boolean save(Entity entity, ValueOutput output) {
        return entity.save(tag(output));
    }

    public static boolean saveAsPassenger(Entity entity, ValueOutput output) {
        return entity.saveAsPassenger(tag(output));
    }

    /**
     * 26.3's {@code EntityType.create(type, input, level, reason)}: a new entity of the type, loaded from the input.
     * (1.21.1 makes one for a level alone; the reason only told 26.3's entity how it came.)
     */
    public static Optional<Entity> create(EntityType<?> type, ValueInput input, Level level, MobSpawnType reason) {
        Optional<Entity> entity = Optional.ofNullable(type.create(level));
        entity.ifPresent(e -> e.load(tag(input)));
        return entity;
    }

    // ---- inside ----

    private static TagValueOutput tagged(ValueOutput output) {
        if (output instanceof TagValueOutput tagged) {
            return tagged;
        }
        throw new IllegalArgumentException("not an output over a tag: " + output);
    }

    private static TagValueInput tagged(ValueInput input) {
        if (input instanceof TagValueInput tagged) {
            return tagged;
        }
        throw new IllegalArgumentException("not an input over a tag: " + input);
    }

    /** An output made without registries is saved with the block entity's level's. */
    private static HolderLookup.Provider lookup(BlockEntity entity, ValueOutput output) {
        HolderLookup.Provider lookup = lookup(output);
        if (lookup == null && entity.getLevel() != null) {
            lookup = entity.getLevel().registryAccess();
        }
        if (lookup == null) {
            throw new IllegalArgumentException("no registries to save " + entity + " with");
        }
        return lookup;
    }

    /** A reporter that logs each problem as it comes, after the owner's path and the fields it happened in. */
    private static ProblemReporter reporter(Object owner) {
        ProblemReporter.PathElement root = owner instanceof BlockEntity entity ? ProblemReporter.pathOf(entity)
                : owner instanceof Entity entity ? ProblemReporter.pathOf(entity) : owner::toString;
        return new Logging(List.of(root));
    }

    private record Logging(List<ProblemReporter.PathElement> path) implements ProblemReporter {
        @Override
        public ProblemReporter forChild(PathElement element) {
            List<PathElement> longer = new ArrayList<>(this.path);
            longer.add(element);
            return new Logging(longer);
        }

        @Override
        public void report(Problem problem) {
            StringBuilder at = new StringBuilder();
            this.path.forEach(element -> at.append(element.get()));
            LOGGER.warn("Serialization error at {}: {}", at, problem.description());
        }
    }
}
