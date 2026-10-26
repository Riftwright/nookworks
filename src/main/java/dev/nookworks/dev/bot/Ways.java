package dev.nookworks.dev.bot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * DEVELOPMENT ONLY: the agent bot's own ways underground - the stairs and tunnels it dug, each the cells its feet went
 * through - and the trips that go by them. Underground and back, its own stair is the way it knows to be safe: a goto
 * from a tunnel's end to home goes back along the tunnel and up the stair it dug, not off through the caves it died in
 * on 2026-10-22. Kept apart from the client so the GameTests check the routes (BotDigTests).
 */
public final class Ways {
    /** How far from a way a trip may start or end and still go by it. */
    public static final double REACH = 24.0;
    /** How much height a trip must change for a way to be worth going by (a walk on the level goes as it will). */
    public static final int DROP = 12;
    /** Waypoints along a way: one every this many cells (the walker plans each short leg). */
    public static final int EVERY = 6;

    private final Map<String, List<BlockPos>> ways = new LinkedHashMap<>();

    public Map<String, List<BlockPos>> all() {
        return this.ways;
    }

    /** A new way's name: "stair1", "tunnel2" ... */
    public String begin(String kind, BlockPos first) {
        int n = 1;
        while (this.ways.containsKey(kind + n)) {
            n++;
        }
        String name = kind + n;
        List<BlockPos> cells = new ArrayList<>();
        cells.add(first);
        this.ways.put(name, cells);
        return name;
    }

    public void append(String name, BlockPos cell) {
        List<BlockPos> cells = this.ways.computeIfAbsent(name, k -> new ArrayList<>());
        if (cells.isEmpty() || !cells.get(cells.size() - 1).equals(cell)) {
            cells.add(cell);
        }
    }

    public void remove(String name) {
        this.ways.remove(name);
    }

    public void clear() {
        this.ways.clear();
    }

    /**
     * Waypoints for a trip from {@code from} to {@code to} by its ways - the way's cell nearest the start, along the
     * ways, to the cell nearest the end - or null when the trip is better made as it would be: not much up or down, the
     * start or end far from any way, or the way a long way round.
     */
    public @Nullable List<BlockPos> route(BlockPos from, BlockPos to) {
        if (this.ways.isEmpty() || Math.abs(from.getY() - to.getY()) < DROP) {
            return null;
        }
        BlockPos entry = nearest(from);
        BlockPos exit = nearest(to);
        if (entry == null || exit == null || dist(from, entry) > REACH || dist(to, exit) > REACH) {
            return null;
        }
        List<BlockPos> cells = path(entry, exit);
        if (cells == null || dist(from, entry) + dist(to, exit) > Math.max(REACH, dist(from, to) * 0.75)) {
            return null;
        }
        return thin(cells);
    }

    /** Waypoints from {@code from} up to the highest cell its nearest way leads to: a way out, underground. Null if no
     *  way is near, or it leads no higher. */
    public @Nullable List<BlockPos> wayUp(BlockPos from) {
        BlockPos entry = nearest(from);
        if (entry == null || dist(from, entry) > REACH) {
            return null;
        }
        Map<BlockPos, BlockPos> came = search(entry, null);
        BlockPos top = entry;
        for (BlockPos p : came.keySet()) {
            if (p.getY() > top.getY()) {
                top = p;
            }
        }
        if (top.getY() < from.getY() + 4) {
            return null;
        }
        return thin(unwind(came, top));
    }

    private static double dist(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }

    private @Nullable BlockPos nearest(BlockPos p) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (List<BlockPos> cells : this.ways.values()) {
            for (BlockPos c : cells) {
                double d = c.distSqr(p);
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
        }
        return best;
    }

    /** The cells from one way cell to another, by cells that touch (faces, edges, corners): a stair's next step is a
     *  corner away, a tunnel starts on the stair's last step. Null if they do not join. */
    private @Nullable List<BlockPos> path(BlockPos from, BlockPos to) {
        Map<BlockPos, BlockPos> came = search(from, to);
        return came.containsKey(to) ? unwind(came, to) : null;
    }

    private Map<BlockPos, BlockPos> search(BlockPos from, @Nullable BlockPos to) {
        Map<BlockPos, Boolean> cells = new HashMap<>();
        for (List<BlockPos> way : this.ways.values()) {
            for (BlockPos c : way) {
                cells.put(c, true);
            }
        }
        Map<BlockPos, BlockPos> came = new LinkedHashMap<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        came.put(from, from);
        open.add(from);
        while (!open.isEmpty()) {
            BlockPos p = open.poll();
            if (p.equals(to)) {
                break;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (cells.containsKey(n) && !came.containsKey(n)) {
                            came.put(n, p);
                            open.add(n);
                        }
                    }
                }
            }
        }
        return came;
    }

    private static List<BlockPos> unwind(Map<BlockPos, BlockPos> came, BlockPos end) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p = end; ; p = came.get(p)) {
            out.add(0, p);
            if (came.get(p).equals(p)) {
                return out;
            }
        }
    }

    /** Every {@link #EVERY}th cell, and the last. */
    private static List<BlockPos> thin(List<BlockPos> cells) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (i % EVERY == 0 || i == cells.size() - 1) {
                out.add(cells.get(i));
            }
        }
        return out;
    }

    // ---- kept in a file: one way a line, "name x y z;x y z;..."

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (var e : this.ways.entrySet()) {
            StringBuilder b = new StringBuilder(e.getKey()).append(' ');
            for (int i = 0; i < e.getValue().size(); i++) {
                BlockPos p = e.getValue().get(i);
                b.append(i == 0 ? "" : ";").append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ());
            }
            out.add(b.toString());
        }
        return out;
    }

    public static Ways read(List<String> lines) {
        Ways w = new Ways();
        for (String line : lines) {
            String t = line.strip();
            int space = t.indexOf(' ');
            if (t.isEmpty() || t.startsWith("#") || space < 0) {
                continue;
            }
            List<BlockPos> cells = new ArrayList<>();
            for (String cell : t.substring(space + 1).split(";")) {
                String[] xyz = cell.strip().split("\\s+");
                if (xyz.length == 3) {
                    cells.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
                }
            }
            if (!cells.isEmpty()) {
                w.ways.put(t.substring(0, space), cells);
            }
        }
        return w;
    }
}
