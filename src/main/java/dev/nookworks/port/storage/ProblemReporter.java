package dev.nookworks.port.storage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Stand-in for 26.3's {@code net.minecraft.util.ProblemReporter} (1.21.1 has a class of that name, but it only takes
 * plain strings and has no ScopedCollector). Saving and loading through {@link TagValueOutput} / {@link TagValueInput}
 * tell what they could not encode or decode to one of these, with the path of fields where it happened; a
 * {@link ScopedCollector} logs them all as a warning when its try block closes, as 26.3 does.
 */
public interface ProblemReporter {
    /** Forgets every problem: for a save or a read whose failures are of no interest. */
    ProblemReporter DISCARDING = new ProblemReporter() {
        @Override
        public ProblemReporter forChild(PathElement path) {
            return this;
        }

        @Override
        public void report(Problem problem) {
        }
    };

    ProblemReporter forChild(PathElement path);

    void report(Problem problem);

    /**
     * What 26.3's {@code BlockEntity.problemPath()} gives (1.21.1's block entities have no such method): the block
     * entity's type and class, and where it stands.
     */
    static PathElement pathOf(BlockEntity entity) {
        return () -> BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()) + " // " + entity.getClass().getCanonicalName()
                + "@" + entity.getBlockPos();
    }

    /** What 26.3's {@code Entity.problemPath()} gives: the entity as it prints itself. */
    static PathElement pathOf(Entity entity) {
        return entity::toString;
    }

    /** One step of the path a problem is reported at. */
    @FunctionalInterface
    interface PathElement {
        String get();
    }

    /** A thing that went wrong. */
    interface Problem {
        String description();
    }

    /** A named field: {@code .name}. */
    record FieldPathElement(String name) implements PathElement {
        @Override
        public String get() {
            return "." + this.name;
        }
    }

    /** One element of a named list: {@code .name[index]}. */
    record IndexedFieldPathElement(String name, int index) implements PathElement {
        @Override
        public String get() {
            return "." + this.name + "[" + this.index + "]";
        }
    }

    /** One element of a list with no name: {@code [index]}. */
    record IndexedPathElement(int index) implements PathElement {
        @Override
        public String get() {
            return "[" + this.index + "]";
        }
    }

    /** Keeps the problems reported to it and to its children, each with its path. */
    class Collector implements ProblemReporter {
        private final @Nullable Collector parent;
        private final PathElement element;
        private final Set<Entry> problems;

        public Collector() {
            this(() -> "");
        }

        public Collector(PathElement root) {
            this.parent = null;
            this.element = root;
            this.problems = new LinkedHashSet<>();
        }

        private Collector(Collector parent, PathElement element) {
            this.parent = parent;
            this.element = element;
            this.problems = parent.problems;
        }

        @Override
        public ProblemReporter forChild(PathElement path) {
            return new Collector(this, path);
        }

        @Override
        public void report(Problem problem) {
            this.problems.add(new Entry(this, problem));
        }

        public boolean isEmpty() {
            return this.problems.isEmpty();
        }

        /** Every problem on a line of its own, after the path it was reported at. */
        public String getTreeReport() {
            List<String> lines = new ArrayList<>();
            for (Entry entry : this.problems) {
                List<String> path = new ArrayList<>();
                for (Collector at = entry.source; at != null; at = at.parent) {
                    path.addFirst(at.element.get());
                }
                lines.add(String.join("", path) + ": " + entry.problem.description());
            }
            return String.join("\n", lines);
        }

        private record Entry(Collector source, Problem problem) {
        }
    }

    /** A collector for a try-with-resources block: what it collected is logged as one warning when it closes. */
    class ScopedCollector extends Collector implements AutoCloseable {
        private final Logger logger;

        public ScopedCollector(Logger logger) {
            this.logger = logger;
        }

        public ScopedCollector(PathElement root, Logger logger) {
            super(root);
            this.logger = logger;
        }

        @Override
        public void close() {
            if (!this.isEmpty()) {
                this.logger.warn("[{}] Serialization errors:\n{}", this.logger.getName(), this.getTreeReport());
            }
        }
    }
}
