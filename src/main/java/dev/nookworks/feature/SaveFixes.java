package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;

import dev.nookworks.Nookworks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.fixes.References;

/**
 * Brings the items kept in our data up to date when a world saved by an older game is opened, as the game's data fixer
 * brings its own (r15: a decorated pot's sherds, its look turned from a list into a map in 26.3, were kept in a chest
 * and lost in a Crate; an explorer map renamed in 26.3 would have gone from a barrel). The fixer knows vanilla's block
 * entities, entities and item components, and passes over anything else as it stands: our block entities and entities
 * (a chunk's block entity of an unknown kind is kept whole, untouched), our attachments, and our components inside
 * an item it did fix (a Storage Barrel's contents, a Mob Bottle's mob).
 * <p>
 * Run round whatever the fixer updates ({@code dev.nookworks.mixin.DataFixTypesMixin}): chunks, entity chunks, players,
 * structures. Every item stack in our data - a compound of an {@code id} and a {@code count} or {@code components}, as
 * the item codecs write one - is put through the fixer's item stack rules over the same versions, a Mob Bottle's mob
 * through its entity rules, and every item anywhere is searched for our components holding more. Nothing is changed
 * when the data is the game's own version already, so a world of this game version is never walked. The save's shape
 * is untouched: the versions come from the file the data is in, as vanilla's do.
 * <p>
 * Entities of a kind the fixer does not know (asked of its schema for the data's version) are taken out of a chunk's
 * list before it runs and put back after, each updated by itself: in a list holding one, the fixer passed over every
 * entity, vanilla's too (r15 fix check: an item frame's explorer map was lost beside a poster). Another mod's are put
 * back as they were.
 */
public final class SaveFixes {
    private SaveFixes() {}

    /** The keys an item stack (or an item template, its count left out at one) is written with, and nothing else. */
    private static final Set<String> ITEM_KEYS = Set.of("id", "count", "components", "Slot", "slot");
    /** Keys whose value is a block entity, never an item, however few its keys (an empty one of ours: an id, components). */
    private static final Set<String> BLOCK_ENTITY_KEYS = Set.of("nbt", "block_entities", "TileEntities", "minecraft:block_entity_data");
    /** The entity kinds each version's schema knows, by version. */
    private static final Map<Integer, Set<String>> KNOWN = new ConcurrentHashMap<>();

    /**
     * {@code data} updated from {@code from} to {@code to}: by {@code vanilla} (the fixer's own update of {@code type}),
     * entities it cannot read taken out first and updated one by one, and then our items.
     */
    @SuppressWarnings("unchecked")
    public static <T> Dynamic<T> update(DataFixTypes type, DataFixer fixer, Dynamic<T> data, int from, int to, UnaryOperator<Dynamic<T>> vanilla) {
        if (from >= to || data.getOps() != NbtOps.INSTANCE || !(data.getValue() instanceof CompoundTag tag)) {
            return vanilla.apply(data);
        }
        Walk walk = new Walk(fixer, from, to);
        String list = type == DataFixTypes.ENTITY_CHUNK ? "Entities" : type == DataFixTypes.CHUNK ? "entities" : null;
        List<Tag> aside = new ArrayList<>();
        CompoundTag vehicle = null;
        Dynamic<T> given = data;
        try {
            CompoundTag copy = null; // what the fixer is given: the data itself is left whole, should the fixer fail
            if (list != null && tag.get(list) instanceof ListTag entities && entities.stream().anyMatch(walk::foreign)) {
                ListTag kept = new ListTag();
                for (Tag entity : entities) {
                    (walk.foreign(entity) ? aside : kept).add(entity);
                }
                copy = shallow(tag);
                copy.put(list, kept);
            }
            if (type == DataFixTypes.PLAYER && tag.get("RootVehicle") instanceof CompoundTag root && walk.foreign(root.get("Entity"))) {
                vehicle = root.copy(); // ridden out of the game on a seat or one of our boats
                copy = copy != null ? copy : shallow(tag);
                copy.remove("RootVehicle");
            }
            if (copy != null) {
                given = new Dynamic<>(data.getOps(), (T) copy);
            }
        } catch (RuntimeException e) {
            Nookworks.LOGGER.warn("Could not sort the entities of old data ({} to {})", from, to, e);
            aside.clear();
            vehicle = null;
            given = data;
        }
        Dynamic<T> out = vanilla.apply(given);
        if (!(out.getValue() instanceof CompoundTag result)) {
            if (!aside.isEmpty() || vehicle != null) {
                Nookworks.LOGGER.warn("Old data came out of the fixer as no compound; its other mods' entities are not put back");
            }
            return out;
        }
        try {
            walk.walk(result, false, false);
        } catch (RuntimeException e) {
            // never in the way of a world opening: what this cannot bring up to date stays as vanilla left it
            Nookworks.LOGGER.warn("Could not update the Nookworks items in old data ({} to {})", from, to, e);
        }
        // what was set aside goes back whatever happens: updated if it can be, as it was if not
        if (!aside.isEmpty()) {
            ListTag entities = result.get(list) instanceof ListTag l ? l : new ListTag();
            for (Tag entity : aside) {
                entities.add(walk.safely(entity));
            }
            result.put(list, entities);
        }
        if (vehicle != null) {
            vehicle.put("Entity", walk.safely(vehicle.get("Entity")));
            result.put("RootVehicle", vehicle);
        }
        return out;
    }

    /** A new compound holding {@code tag}'s own entries (not copies of them). */
    private static CompoundTag shallow(CompoundTag tag) {
        CompoundTag copy = new CompoundTag();
        for (String key : tag.keySet()) {
            copy.put(key, tag.get(key));
        }
        return copy;
    }

    /**
     * Whether {@code compound} is an item stack or template as the item codecs write one: an id, and nothing beyond a
     * count, components and the slot it is in.
     */
    static boolean isItem(CompoundTag compound) {
        return compound.get("id") instanceof StringTag && ITEM_KEYS.containsAll(compound.keySet())
                && (compound.get("count") == null || compound.get("count") instanceof NumericTag);
    }

    private static boolean ours(String id) {
        return id.startsWith(Nookworks.MODID + ":");
    }

    private record Walk(DataFixer fixer, int from, int to) {
        /**
         * The entity kinds the fixer's schema for {@code from} knows; null if it cannot be asked, when a kind is taken
         * as known by its namespace.
         */
        private @Nullable Set<String> known() {
            Set<String> kinds = KNOWN.get(this.from);
            if (kinds == null) {
                try {
                    kinds = new HashSet<>();
                    for (Object key : this.fixer.getSchema(DataFixUtils.makeKey(this.from)).findChoiceType(References.ENTITY).types().keySet()) {
                        kinds.add(String.valueOf(key));
                    }
                    KNOWN.put(this.from, kinds);
                } catch (RuntimeException e) {
                    return null;
                }
            }
            return kinds;
        }

        /** Whether the fixer can read an entity of kind {@code id} in data of this version. */
        boolean knows(String id) {
            Set<String> kinds = this.known();
            if (kinds == null || kinds.isEmpty()) {
                return id.startsWith("minecraft:") || !id.contains(":");
            }
            return kinds.contains(id.contains(":") ? id : "minecraft:" + id);
        }

        /** Whether {@code entity}, or one riding it, is of a kind the fixer cannot read. */
        boolean foreign(@Nullable Tag entity) {
            if (!(entity instanceof CompoundTag compound)) {
                return entity != null; // not even a compound: the fixer cannot read it
            }
            if (!(compound.get("id") instanceof StringTag id) || !this.knows(id.value())) {
                return true;
            }
            Tag riders = compound.get("Passengers");
            return riders != null && (!(riders instanceof ListTag list) || list.stream().anyMatch(this::foreign));
        }

        /**
         * Walks {@code tag}, changing it in place, and returns what stands for it. {@code mine}: inside our data, where
         * the fixer has not been - every item stack there is updated; elsewhere only searched. {@code blockEntity}: it is
         * a block entity (or a list of them), whatever its keys look like.
         */
        Tag walk(Tag tag, boolean mine, boolean blockEntity) {
            if (tag instanceof ListTag list) {
                for (int i = 0; i < list.size(); i++) {
                    Tag child = list.get(i);
                    Tag done = this.walk(child, mine, blockEntity);
                    if (done != child) {
                        list.set(i, done);
                    }
                }
                return list;
            }
            if (!(tag instanceof CompoundTag compound)) {
                return tag;
            }
            if (!blockEntity && isItem(compound)) {
                CompoundTag item = mine ? this.update(compound) : compound;
                this.inside(item);
                return item;
            }
            // one of our block entities or entities: the fixer passed over all of it, its components too
            boolean ourThing = mine || compound.get("id") instanceof StringTag id && ours(id.value());
            for (String key : List.copyOf(compound.keySet())) {
                Tag child = compound.get(key);
                Tag done = this.walk(child, ourThing || ours(key), BLOCK_ENTITY_KEYS.contains(key)); // ours(key): an attachment of ours
                if (done != child) {
                    compound.put(key, done);
                }
            }
            return compound;
        }

        /** {@link #tree} of a copy of {@code entity}, or {@code entity} as it was if that fails. */
        Tag safely(Tag entity) {
            if (entity == null) {
                return null;
            }
            try {
                return this.tree(entity.copy());
            } catch (RuntimeException e) {
                Nookworks.LOGGER.warn("Could not update an entity in old data ({} to {}); kept as it was", this.from, this.to, e);
                return entity;
            }
        }

        /**
         * An entity the fixer could not read in its list, with those riding it: each of a kind the fixer knows updated
         * by the fixer by itself, each of ours walked as our data; another mod's left as it was. Each rider on its own:
         * one failing leaves the rest updated.
         */
        Tag tree(Tag entity) {
            if (!(entity instanceof CompoundTag compound)) {
                return entity;
            }
            Tag riders = compound.get("Passengers");
            CompoundTag self = compound.copy();
            self.remove("Passengers");
            Tag done;
            if (self.get("id") instanceof StringTag id && this.knows(id.value())) {
                done = this.walk(this.fixer.update(References.ENTITY, new Dynamic<Tag>(NbtOps.INSTANCE, self), this.from, this.to).getValue(), false, false);
            } else {
                done = this.walk(self, false, false); // ours: its id makes it our data to the walk
            }
            if (riders != null && done instanceof CompoundTag result) {
                if (riders instanceof ListTag list) {
                    ListTag updated = new ListTag();
                    for (Tag rider : list) {
                        updated.add(this.safely(rider));
                    }
                    result.put("Passengers", updated);
                } else {
                    result.put("Passengers", riders); // not a list: another mod's own way, kept
                }
            }
            return done;
        }

        /** {@code item} after the fixer's item stack rules. */
        private CompoundTag update(CompoundTag item) {
            Tag fixed = this.fixer.update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item), this.from, this.to).getValue();
            return fixed instanceof CompoundTag compound ? compound : item;
        }

        /**
         * Our components in {@code item}, whose contents the fixer has not seen (the item itself it has); vanilla ones
         * are searched for items carrying ours (a barrel item in a shulker box).
         */
        private void inside(CompoundTag item) {
            if (!(item.get("components") instanceof CompoundTag components)) {
                return;
            }
            // the bottled mob's kind as the data names it, before that name is itself brought up to date (r15 fix check:
            // a renamed kind, read in the old version's rules, would leave the mob as it was)
            String kind = components.get(Nookworks.MODID + ":captured_entity") instanceof StringTag t ? t.value() : null;
            for (String key : List.copyOf(components.keySet())) {
                Tag child = components.get(key);
                Tag done;
                if (key.equals(Nookworks.MODID + ":label") && child instanceof StringTag name) {
                    done = this.fixer.update(References.ITEM_NAME, new Dynamic<Tag>(NbtOps.INSTANCE, name), this.from, this.to).getValue();
                } else if ((key.equals(Nookworks.MODID + ":captured_entity") || key.equals(Nookworks.MODID + ":spawner_entity"))
                        && child instanceof StringTag name) {
                    done = this.fixer.update(References.ENTITY_NAME, new Dynamic<Tag>(NbtOps.INSTANCE, name), this.from, this.to).getValue();
                } else if (key.equals(Nookworks.MODID + ":captured_data") && child instanceof CompoundTag mob && kind != null && this.knows(kind)) {
                    done = this.mob(mob, kind);
                } else {
                    done = this.walk(child, ours(key), false);
                }
                if (done != child) {
                    components.put(key, done);
                }
            }
        }

        /**
         * A Mob Bottle's mob of a kind the fixer knows, put through its entity rules as a mob in the world is (r15 fix
         * check: an enderman's block, its state written in 26.2's way, was lost on its way out of the bottle), then
         * searched for our components. Should the fixer not read it, its items are updated as our data's are.
         */
        private Tag mob(CompoundTag mob, String kind) {
            CompoundTag withId = mob.copy();
            withId.putString("id", kind);
            Tag fixed = this.fixer.update(References.ENTITY, new Dynamic<Tag>(NbtOps.INSTANCE, withId), this.from, this.to).getValue();
            if (fixed == withId || !(fixed instanceof CompoundTag result)) {
                return this.walk(mob, true, false); // handed back as it was: not read
            }
            result.remove("id");
            return this.walk(result, false, false);
        }
    }
}
