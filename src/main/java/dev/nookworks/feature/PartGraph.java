package dev.nookworks.feature;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.NookworksStartupConfig;

/**
 * Every way to each item of ours, and what can still be had with parts of the mod switched off (the user, 2026-11-01:
 * every part can be switched off, and the parts list says what goes with it). The table, {@code /nookworks/part_graph.json},
 * is written by tools/part_graph.py from the mod's recipes, machines' data maps, sifting and distilling loot, the
 * blocks' drops, the Wandering Trader and what Java makes; this walks it as that tool does, so the two give the same
 * answer (the GameTests ask both).
 * <p>
 * A source is live when its conditions hold, every machine it needs can be had, and each of its inputs has an
 * alternative that can be had; then each of its outputs not taken by its own part's switch can be had. An item that can
 * be had with every part on but not with those on now is {@link #lost lost}: it goes as a switched-off part's items go.
 * One nothing reaches even with every part on (creative mode's Generator) is never lost: it is had as it always was.
 */
public final class PartGraph {
    /** A source's condition: a part on or off, or a start-up setting as given. */
    record Condition(@Nullable String part, @Nullable String setting, boolean is) {}

    /** One way to the items {@code out}: from {@code in} (each a list of alternatives), worked in every one of {@code at}. */
    record Source(String how, List<String> out, List<List<String>> in, List<String> at, List<Condition> conditions) {}

    private static final JsonObject FILE = file();
    /** Every item of ours, by id. */
    public static final List<String> ITEMS = strings(FILE.getAsJsonArray("items"));
    static final List<Source> SOURCES = sources(FILE.getAsJsonArray("sources"));
    /** What the tool found lost with each part alone switched off, by default settings: for the GameTests. */
    public static final Map<String, List<String>> EXPECTED = expected(FILE.getAsJsonObject("expected"));

    private PartGraph() {}

    private static JsonObject file() {
        try (var in = PartGraph.class.getResourceAsStream("/nookworks/part_graph.json")) {
            if (in == null) {
                throw new IllegalStateException("no /nookworks/part_graph.json in the mod jar");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read /nookworks/part_graph.json", e);
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> list = new ArrayList<>(array.size());
        array.forEach(e -> list.add(e.getAsString()));
        return List.copyOf(list);
    }

    private static List<Source> sources(JsonArray array) {
        List<Source> sources = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject s = element.getAsJsonObject();
            List<List<String>> in = new ArrayList<>();
            s.getAsJsonArray("in").forEach(alternatives -> in.add(strings(alternatives.getAsJsonArray())));
            List<Condition> conditions = new ArrayList<>();
            for (JsonElement c : s.getAsJsonArray("if")) {
                JsonObject o = c.getAsJsonObject();
                conditions.add(new Condition(o.has("part") ? o.get("part").getAsString() : null,
                        o.has("setting") ? o.get("setting").getAsString() : null, o.get("is").getAsBoolean()));
            }
            sources.add(new Source(s.get("how").getAsString(), strings(s.getAsJsonArray("out")), List.copyOf(in),
                    strings(s.getAsJsonArray("at")), List.copyOf(conditions)));
        }
        return List.copyOf(sources);
    }

    private static Map<String, List<String>> expected(JsonObject object) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        object.entrySet().forEach(e -> map.put(e.getKey(), strings(e.getValue().getAsJsonArray())));
        return Collections.unmodifiableMap(map);
    }

    /** Whether start-up setting {@code setting} holds, with the parts {@code partOn} says are on. */
    static boolean setting(String setting, Predicate<String> partOn) {
        if (setting.equals("flour_bakery")) { // the bakery goes with the kitchen whose flour and dough it is made of
            return NookworksStartupConfig.FLOUR_BAKERY.getAsBoolean() && Features.enabled("kitchen", partOn);
        }
        if (setting.startsWith("feature.")) {
            return Features.enabled(setting.substring("feature.".length()), partOn);
        }
        var value = NookworksStartupConfig.byName(setting);
        return value != null && value.getAsBoolean();
    }

    /** The items of ours that can be had with the parts {@code partOn} says are on, {@code kept} had whatever the table says. */
    public static Set<String> reachable(Predicate<String> partOn, Collection<String> kept) {
        Set<String> had = new HashSet<>();
        for (String id : kept) {
            if (Features.enabledByPart(id, partOn)) {
                had.add(id);
            }
        }
        List<Source> live = new ArrayList<>();
        for (Source source : SOURCES) {
            boolean holds = true;
            for (Condition c : source.conditions()) {
                boolean now = c.part() != null ? Features.enabled(c.part(), partOn) : setting(c.setting(), partOn);
                if (now != c.is()) {
                    holds = false;
                    break;
                }
            }
            if (holds) {
                live.add(source);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Source source : live) {
                if (!had.containsAll(source.at()) || !source.in().stream().allMatch(alternatives -> alternatives.stream().anyMatch(had::contains))) {
                    continue;
                }
                for (String out : source.out()) {
                    if (!had.contains(out) && Features.enabledByPart(out, partOn)) {
                        had.add(out);
                        changed = true;
                    }
                }
            }
        }
        return had;
    }

    /**
     * The items, each still on by its own part, that nothing reaches with the parts {@code partOn} says are on but that
     * can be had with every part on: those the parts switched off take with them. Sorted.
     */
    public static Set<String> lost(Predicate<String> partOn, Collection<String> kept) {
        Set<String> everything = reachable(part -> true, kept);
        Set<String> now = reachable(partOn, kept);
        Set<String> lost = new TreeSet<>();
        for (String id : everything) {
            if (!now.contains(id) && Features.enabledByPart(id, partOn)) {
                lost.add(id);
            }
        }
        return Collections.unmodifiableSet(lost);
    }
}
