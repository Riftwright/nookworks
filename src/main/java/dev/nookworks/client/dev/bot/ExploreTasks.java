package dev.nookworks.client.dev.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.BotRules;
import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Going out to see what is about - the user's last wish for NookAI's days alone (2026-10-22): find a village.
 * {@code explore [DIST] [STEP] [stay]} walks a widening square spiral round where it starts (BotRules.spiralCorner),
 * on the surface, looking about at each corner: a village (bells, beds, workstations, villagers), lava, animals worth
 * having (event "poi", once a place). Each leg and find goes in {@code explore_notes.txt}; at the end it walks back to
 * where it started unless {@code stay}. {@code findmob ID [R]} lists the mobs of a kind about it (event "findmob").
 */
final class ExploreTasks {
    private ExploreTasks() {}

    /** The animals and people worth a note. */
    private static final List<EntityType<?>> NOTED = List.of(net.minecraft.world.entity.EntityTypes.VILLAGER, net.minecraft.world.entity.EntityTypes.WANDERING_TRADER, net.minecraft.world.entity.EntityTypes.IRON_GOLEM,
            net.minecraft.world.entity.EntityTypes.COW, net.minecraft.world.entity.EntityTypes.SHEEP, net.minecraft.world.entity.EntityTypes.PIG, net.minecraft.world.entity.EntityTypes.CHICKEN, net.minecraft.world.entity.EntityTypes.HORSE);

    /** What is about within {@code r}: landmarks by kind (count, first place) and noted mobs by kind. */
    static Map<String, int[]> survey(Minecraft mc, BlockPos at, int r) {
        Map<String, int[]> found = new LinkedHashMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = at.getX() - r; x <= at.getX() + r; x++) {
            for (int z = at.getZ() - r; z <= at.getZ() + r; z++) {
                int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                for (int y = Math.max(at.getY() - 12, top - 14); y <= Math.max(at.getY() + 12, top); y++) {
                    p.set(x, y, z);
                    String kind = BotRules.landmark(mc.level.getBlockState(p));
                    if (kind != null) {
                        int[] first = {0, x, y, z};
                        found.computeIfAbsent(kind, k -> first)[0]++;
                    }
                }
            }
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (NOTED.contains(e.getType()) && e.isAlive() && e.distanceToSqr(at.getX(), at.getY(), at.getZ()) < (r + 8.0) * (r + 8.0)) {
                found.computeIfAbsent(e.getType().toShortString(), k -> new int[] {0, e.getBlockX(), e.getBlockY(), e.getBlockZ()})[0]++;
            }
        }
        return found;
    }

    static void note(String line) {
        try {
            Files.writeString(Bot.dir().resolve("explore_notes.txt"), line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            // a note lost: the events say it too
        }
    }

    static final class Explore implements Task, Bot.Resumable {
        private final int dist;
        private final int step;
        private final boolean stay;
        private @Nullable BlockPos start;
        private int corner;
        private @Nullable Goal goal;
        private boolean homeward;
        private int legs;
        private int failedLegs;
        private final Set<String> told = new HashSet<>();
        private final List<String> finds = new ArrayList<>();

        Explore(int dist, int step, boolean stay) {
            this.dist = dist;
            this.step = step;
            this.stay = stay;
        }

        @Override
        public String describe() {
            return "explore (" + this.legs + " legs, " + this.finds.size() + " finds" + (this.homeward ? ", back" : "") + ")";
        }

        private Goal leg(Minecraft mc, int x, int z) {
            var level = mc.level;
            return Goal.columnNear(x, z, 4.0, (cx, cy, cz) -> BotRules.onSurface(level, cx, cy, cz,
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz), Bot::natural) && BotRules.dry(level, cx, cy, cz));
        }

        private void look(Minecraft mc) {
            BlockPos at = mc.player.blockPosition();
            Map<String, int[]> found = survey(mc, at, 24);
            int bells = count(found, "bell");
            int beds = count(found, "bed");
            int work = count(found, "workstation");
            int villagers = count(found, "villager");
            List<String> said = new ArrayList<>();
            if (bells > 0 || villagers > 0 || beds >= 3 && work > 0) {
                int[] where = found.getOrDefault("bell", found.getOrDefault("villager", found.get("bed")));
                said.add(this.tell("village", where, "a village: " + bells + " bells, " + beds + " beds, " + work + " workstations, "
                        + villagers + " villagers"));
            }
            if (found.containsKey("lava")) {
                said.add(this.tell("lava", found.get("lava"), "lava (" + count(found, "lava") + " source blocks)"));
            }
            for (String animal : new String[] {"cow", "sheep", "pig", "chicken", "horse"}) {
                if (found.containsKey(animal)) {
                    said.add(this.tell(animal, found.get(animal), count(found, animal) + " " + animal));
                }
            }
            said.removeIf(s -> s == null);
            note(String.format("tick %d  at %s  %s", mc.level.getGameTime(), at.toShortString(), said.isEmpty() ? "nothing of note" : String.join("; ", said)));
        }

        private static int count(Map<String, int[]> found, String kind) {
            int[] f = found.get(kind);
            return f == null ? 0 : f[0];
        }

        /** An event for a find - once a place (a 48-block square) and kind. */
        private @Nullable String tell(String kind, int[] where, String what) {
            String key = kind + "@" + Math.floorDiv(where[1], 48) + "," + Math.floorDiv(where[3], 48);
            String line = what + " at " + where[1] + " " + where[2] + " " + where[3];
            if (this.told.add(key)) {
                this.finds.add(line);
                Bot.event("poi", line);
            }
            return line;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (this.start == null) {
                this.start = player.blockPosition();
                note("explore from " + this.start.toShortString() + " out to " + this.dist + ", corners every " + this.step);
                this.look(mc);
            }
            if (this.goal == null) {
                if (this.homeward) {
                    note("back at " + player.blockPosition().toShortString() + ": " + this.finds.size() + " finds");
                    return Result.DONE;
                }
                int[] d = BotRules.spiralCorner(++this.corner, this.step);
                if (Math.max(Math.abs(d[0]), Math.abs(d[1])) > this.dist) {
                    if (this.stay) {
                        return Result.DONE;
                    }
                    this.homeward = true;
                    this.goal = this.leg(mc, this.start.getX(), this.start.getZ());
                } else {
                    this.goal = this.leg(mc, this.start.getX() + d[0], this.start.getZ() + d[1]);
                }
                Bot.walker.go(this.goal);
            }
            if (!Bot.walker.isFor(this.goal)) {
                Bot.walker.go(this.goal);
            }
            Bot.walker.tick(mc, c);
            switch (Bot.walker.state()) {
                case DONE -> {
                    this.legs++;
                    this.look(mc);
                    this.goal = null;
                }
                case FAILED -> {
                    note("leg " + this.corner + " failed at " + player.blockPosition().toShortString() + ": " + Bot.walker.failure());
                    this.look(mc);
                    this.goal = null;
                    if (++this.failedLegs > 4 && !this.homeward) {
                        this.homeward = !this.stay;
                        if (this.stay) {
                            Bot.fail("too many ways out failed");
                            return Result.FAILED;
                        }
                        this.goal = this.leg(mc, this.start.getX(), this.start.getZ());
                        Bot.walker.go(this.goal);
                    } else if (this.homeward) {
                        Bot.fail("cannot get back to " + this.start.toShortString());
                        return Result.FAILED;
                    }
                }
                default -> {
                }
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }

    /** {@code findmob ID [R]}: the mobs of a kind within R (48), nearest first, in one event. */
    static void findMob(Minecraft mc, String id, int r) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id))
                .orElseThrow(() -> new IllegalArgumentException("no entity type " + id));
        LocalPlayer me = mc.player;
        List<Entity> found = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() == type && e.isAlive() && e.distanceTo(me) <= r) {
                found.add(e);
            }
        }
        found.sort(java.util.Comparator.comparingDouble(e -> e.distanceTo(me)));
        StringBuilder s = new StringBuilder(found.size() + " " + id + " within " + r);
        for (int i = 0; i < Math.min(8, found.size()); i++) {
            Entity e = found.get(i);
            s.append(i == 0 ? ": " : "; ").append((int) e.distanceTo(me)).append(" off at ").append(e.blockPosition().toShortString());
        }
        Bot.event("findmob", s.toString());
    }
}
