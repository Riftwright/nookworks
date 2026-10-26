package dev.nookworks.client.dev.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.dev.bot.path.Goal;
import dev.nookworks.dev.bot.path.PathWorld;
import dev.nookworks.dev.bot.path.Pathfinder;
import dev.nookworks.dev.bot.path.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** What is round the walker, written out for the agent: blocks counted and found, a path planned without walking it. */
final class Scan {
    private Scan() {}

    /** The nearest block of the kind within the radius (a cube, nearest by straight line), skipping some; null if none. */
    static @Nullable BlockPos nearest(Minecraft mc, Predicate<BlockState> what, int radius, Set<BlockPos> skip) {
        LocalPlayer player = mc.player;
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int minY = Math.max(mc.level.getMinY(), center.getY() - radius);
        int maxY = Math.min(mc.level.getMaxY(), center.getY() + radius);
        for (int r = 0; r <= radius; r++) {
            // a shell at a time: once something is found, a shell or two more can only hold something nearer by a little
            if (best != null && r * r > bestD + 4) {
                break;
            }
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean edgeXZ = Math.abs(x) == r || Math.abs(z) == r;
                    for (int y = Math.max(minY, center.getY() - r); y <= Math.min(maxY, center.getY() + r); y++) {
                        if (!edgeXZ && Math.abs(y - center.getY()) != r) {
                            continue;
                        }
                        p.set(center.getX() + x, y, center.getZ() + z);
                        if (!mc.level.hasChunkAt(p) || skip.contains(p)) {
                            continue;
                        }
                        if (what.test(mc.level.getBlockState(p))) {
                            double d = p.distSqr(center);
                            if (d < bestD) {
                                bestD = d;
                                best = p.immutable();
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Every kind of block within the radius, counted, with the nearest of each: bot_scan.json. */
    static void scan(Minecraft mc, int radius) {
        LocalPlayer player = mc.player;
        BlockPos center = player.blockPosition();
        Map<String, int[]> counts = new TreeMap<>();
        Map<String, BlockPos> nearest = new TreeMap<>();
        Map<String, Double> nearestD = new TreeMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    p.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!mc.level.hasChunkAt(p)) {
                        continue;
                    }
                    BlockState s = mc.level.getBlockState(p);
                    if (s.isAir()) {
                        continue;
                    }
                    String id = Bot.id(s.getBlock());
                    counts.computeIfAbsent(id, k -> new int[1])[0]++;
                    double d = p.distSqr(center);
                    if (d < nearestD.getOrDefault(id, Double.MAX_VALUE)) {
                        nearestD.put(id, d);
                        nearest.put(id, p.immutable());
                    }
                }
            }
        }
        List<String> rows = new ArrayList<>();
        counts.forEach((id, n) -> {
            BlockPos at = nearest.get(id);
            rows.add(new Json().put("id", id).put("count", n[0]).put("x", at.getX()).put("y", at.getY()).put("z", at.getZ())
                    .put("dist", Math.sqrt(nearestD.get(id))).toString());
        });
        write("bot_scan.json", new Json().put("center", center.toShortString()).put("radius", radius).raw("blocks", Json.array(rows)).toString());
        Bot.event("scan", counts.size() + " kinds of block within " + radius);
    }

    /** The ten nearest blocks of a kind: bot_find.json. */
    static void find(Minecraft mc, Predicate<BlockState> what, int radius) {
        List<String> rows = new ArrayList<>();
        java.util.Set<BlockPos> found = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++) {
            BlockPos at = nearest(mc, what, radius, found);
            if (at == null) {
                break;
            }
            found.add(at);
            rows.add(new Json().put("x", at.getX()).put("y", at.getY()).put("z", at.getZ())
                    .put("id", Bot.id(mc.level.getBlockState(at).getBlock())).put("dist", Math.sqrt(at.distSqr(mc.player.blockPosition()))).toString());
        }
        write("bot_find.json", Json.array(rows));
        Bot.event("find", rows.size() + " found");
    }

    /** A path planned from here, not walked: bot_path.json (the steps, and how the search went). */
    static void path(Minecraft mc, Goal goal) {
        Pathfinder search = new Pathfinder(new PathWorld(mc.level, Bot.abilities(mc)), Walker.feetCell(mc.player), goal);
        long t0 = System.nanoTime();
        Pathfinder.Status status = search.run(400_000_000L);
        List<String> steps = new ArrayList<>();
        for (Step s : search.path()) {
            steps.add(Json.str(s.toString()));
        }
        write("bot_path.json", new Json().put("status", status.name()).put("nodes", search.expanded())
                .put("ms", (System.nanoTime() - t0) / 1_000_000.0).raw("steps", Json.array(steps)).toString());
        Bot.event("path", status + ", " + steps.size() + " steps");
    }

    private static void write(String name, String text) {
        try {
            Files.writeString(Bot.dir().resolve(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Nookworks.LOGGER.warn("[bot] cannot write {}: {}", name, e.toString());
        }
    }
}
