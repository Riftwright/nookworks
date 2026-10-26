package dev.nookworks.client.dev.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.dev.bot.BotRules;
import dev.nookworks.dev.bot.path.Abilities;
import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.Tags;

/**
 * DEVELOPMENT ONLY: the agent client's player as a player of its own - the agent's Mineflayer, inside the real game, so
 * it plays in a Nookworks world beside a person, and is filmed as it does. Active only in the agentClient run (the same
 * switch as {@code AgentInput}, which hands it its commands); inert in normal play and in the released mod.
 * <p>
 * Commands are queued and done one after another ({@code bot now ...} drops the queue first; a job that fails drops
 * those queued after it). Each sends an event when it
 * ends ({@code bot_events.txt}, one JSON object a line: task done or failed and why, chat, hurt, death, replans), and
 * {@code bot_state.json} says where it is and what it holds and sees, twice a second.
 * <pre>
 *   bot goto X Y Z [R]           walk there (within R)          bot goto X Z | X ~ Z [R] (any height there)
 *   bot goto player NAME [R]     to a player                    bot goto block ID [RADIUS] (the nearest of a kind)
 *   bot companion NAME|off       play beside a player: near them between jobs, defending them (armed)
 *   bot home X Y Z|off [FILE OX OY OZ]..  where it heads back to at dusk, and spends the night (in at night, the door
 *                                shut, in bed); with plans (build files, offset), the house kept to them by the chores:
 *                                mended, a bed set in it, the yard lit
 *   bot pen X Y Z|off            a 3x3 pen by home (its middle): built, animals fetched to it, bred, some eaten
 *   bot store X Y Z|off          the chest its chores empty a full bag into (bot set chores on: a full bag stored,
 *                                a missing pickaxe/sword/axe made, raw food cooked or a field picked when hungry)
 *   bot follow NAME [DIST]       keep near a player until stopped   bot come NAME: to them, once
 *   bot flee [DIST]              get away from the nearest hostile mob
 *   bot mine ID[,ID..] [N] [R]   mine N blocks of these kinds within R (block ids or #tags), picking up the drops
 *   bot collect [R] [ID]         pick up the items lying near
 *   bot place ID[STATE] X Y Z    set a block down (its state - facing, half - as asked, the click worked out to get it)
 *   bot build FILE [OX OY OZ]    set down every block of a file ("x y z block[state]" a line), offset by O
 *   bot cleanup                  break the blocks it set down to get about (bridges, pillars)
 *   bot stairdown Y [DIR]        dig a stair 3 wide, 3 high down to Y with its best hammer (one made if none), a step at a
 *                                time, lava and water looked for first (turns aside, or stops); a torch every 8 steps
 *   bot tunnel DIR [LEN]         a level tunnel 3x3, LEN (32) long, the same way; ores in the walls mined, vein and all
 *   bot ways [clear [NAME]]      the stairs and tunnels it dug (kept; trips up and down go by them)
 *   bot till X Y Z [R] [dry]     hoe the grass and dirt within R (4) - only where water is within 4, unless dry
 *   bot plant ID X Y Z [R]       seeds on the bare farmland within R | bot harvest [R]: ripe crops picked, set again
 *   bot useon ID X Y Z           use an item on a block once (flint and steel, bone meal, a bucket)
 *   bot explore [DIST] [STEP] [stay]  a widening spiral out to DIST (96), corners every STEP (24), on the surface,
 *                                noting villages, lava, animals (event poi, explore_notes.txt); back to the start after
 *   bot findmob ID [R]           the mobs of a kind within R (48), nearest first (event findmob)
 *   bot fish [N|TICKS]           fish from a bank within 16: N (up to 100) catches, or for TICKS (a rod made if none)
 *   bot craft ID [N]             craft N (the recipe book's recipes; a crafting table found or set down when needed)
 *   bot smelt ID [N] [FUEL]      smelt N of the input in a furnace found or set down nearby
 *   bot eat | bot equip          eat the best food | put on the best armour
 *   bot attack [hostile|ID|NAME] fight the nearest such mob until it dies
 *   bot sleep                    sleep in the nearest bed
 *   bot deposit X Y Z [ID|all]   put items in the container there | bot withdraw X Y Z ID [N]
 *   bot drop ID [N|all]          throw items | bot hold ID        take an item into the hand
 *   bot give NAME [ID|all] [N]   walk up to a player and throw them items (all: but tools, weapons, armour)
 *   bot look X Y Z | bot lookat NAME | bot say TEXT | bot wait TICKS
 *   bot stop | bot pause | bot resume | bot respawn | bot state | bot scan [R] | bot find ID [R] | bot path X Y Z
 *   bot set KEY VALUE            break none|natural|any, place, parkour, sprint, reflexes, defend, eat, cleanup on|off,
 *                                onfail drop|go, dusk on|off, chores on|off, nightout on|off, food N, turn N
 *   bot guard | bot farm | bot stock   the home's upkeep, the field and pen, the larder: now, as a job
 * </pre>
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class Bot {
    static final boolean ENABLED = Boolean.getBoolean("nookworks.agentInput");

    /** How the walker may play. */
    static final class Settings {
        String breaking = "natural";
        boolean place = true;
        boolean parkour = true;
        boolean sprint = true;
        boolean reflexes = true;
        boolean defend = true;
        boolean eat = true;
        boolean cleanup = false;
        /** A job that fails drops the jobs queued after it (they were planned on it going well: a "say" that it went
         *  well, beside the user on 2026-10-21, went out after the jobs it spoke of had failed). Off: they go on. */
        boolean dropOnFail = true;
        /** The player it plays beside (bot companion NAME): kept near between jobs, and defended. */
        @Nullable String companion;
        /** Home (bot home X Y Z): where it heads at dusk from a job far off; the companion when none is set. */
        @Nullable BlockPos home;
        /** At dusk, a job far from home is broken off for the way back (event "dusk"). */
        boolean dusk = true;
        /** Between jobs, the daily round (bot set chores on): a full bag into the store, tools made, food found. */
        boolean chores = false;
        /** The chest it empties its bag into (bot store X Y Z). */
        @Nullable BlockPos store;
        /** At night with a home it stays in (jobs wait for morning, fights are fought from inside); on: it may go out. */
        boolean nightOut = false;
        /** Meals (cooked food) the chores keep in the bag. */
        int foodStock = BotRules.FOOD_STOCK;
        /** The middle of the pen by home (bot pen X Y Z). */
        @Nullable BlockPos pen;
    }

    /** Between jobs: near the companion (a few blocks off, looking at them when close). Not a job of its own: not
     *  counted as busy, and set aside the moment a job comes. */
    static final class Companion implements Task {
        private final MoveTasks.Follow follow;

        Companion(String name) {
            this.follow = new MoveTasks.Follow(name, 4.0);
        }

        @Override
        public String describe() {
            return "keeping " + settings.companion + " company";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            var friend = MoveTasks.player(mc, settings.companion);
            if (HomeTasks.leashed(mc) && (friend == null || !HomeTasks.inside(mc, friend.blockPosition()))) {
                // in for the night: not out of the door after them (they are told instead, watchFriendAtNight)
                walker.stop(mc);
                if (friend != null && friend.distanceTo(mc.player) < 16) {
                    c.lookAt(mc.player, friend.getEyePosition());
                }
                return Result.RUNNING;
            }
            this.follow.tick(mc, c);
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            this.follow.cancel(mc);
        }
    }

    static final Settings settings = new Settings();
    static final Controls controls = new Controls();
    static final Walker walker = new Walker();
    private static final Deque<Task> queue = new ArrayDeque<>();
    private static @Nullable Task current;
    private static boolean paused;
    private static @Nullable String lastFailure;
    /** Tasks the reflexes put in (a fight, eating, running): their failing drops nothing queued. */
    private static final java.util.Set<Task> reflexTasks = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** When backing off last failed (cornered): it may fight then, hurt or not. */
    private static long fleeFailedAt = -1000;
    /** The day (game time / 24000) whose dusk has been seen to. */
    private static long duskDay = -1;
    /** When eating last failed: not tried again at once. */
    private static long eatFailedAt = -1000;
    private static long tick;

    static long tick() {
        return tick;
    }
    private static float lastHealth = -1;
    private static boolean toldDeath;
    /** The water bucket landing under way: 0 none, 1 falling with the bucket aimed down, 2 landed, picking the water up. */
    private static int mlg;
    private static @Nullable BlockPos mlgWater;

    private Bot() {}

    // ---- the tick

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        tick++;
        watchHealth(player);
        watchCompanion(mc);
        if (tick % 20 == 0) {
            watchFriendAtNight(mc);
        }
        if (!paused) {
            watchDusk(mc, player);
            if (tick % 20 == 0) {
                watchNight(mc, player);
            }
            if (tick % 100 == 0) {
                watchChores(mc, player);
            }
        }
        boolean active = current != null || !queue.isEmpty() || mlg != 0;
        boolean screenOpen = mc.gui.screen() != null;
        if (!paused) {
            if (!screenOpen) {
                reflexes(mc);
            }
            if (!screenOpen || inScreen(current)) {
                runTasks(mc);
                strayScreen = 0;
            } else {
                controls.stopMoving();
                // a container opened by a stray click (the use key on a chest in view) holds up every job that does
                // not work in screens: shut after a second
                if (current != null && !(current instanceof Companion)
                        && mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && ++strayScreen > 20) {
                    event("reflex", "shut a stray " + mc.gui.screen().getClass().getSimpleName());
                    mc.player.closeContainer();
                    strayScreen = 0;
                }
            }
        }
        active = active || current != null;
        if (mlg != 0) {
            mlgTick(mc);
        }
        controls.apply(mc, active && !paused);
        if (tick % 10 == 0) {
            writeState(mc);
        }
    }

    private static void runTasks(Minecraft mc) {
        // keeping a companion company is what it does between jobs: any job asked for comes first
        if (current instanceof Companion && !queue.isEmpty()) {
            current.cancel(mc);
            current = null;
        }
        if (current == null) {
            current = holdForMorning(mc) ? null : queue.pollFirst();
            if (current == null && settings.companion != null) {
                current = new Companion(settings.companion);
            }
            if (current == null) {
                controls.stopMoving();
                controls.stopTurning();
                return;
            }
            if (!(current instanceof Companion)) {
                event("start", current.describe());
            }
        }
        controls.stopMoving();
        Task task = current;
        Task.Result result;
        try {
            result = task.tick(mc, controls);
            if (task instanceof Later && current != task && current != null) {
                task = current; // made: the job itself from here on (its result is its own)
            }
        } catch (RuntimeException e) {
            Nookworks.LOGGER.warn("[bot] {} failed", task.describe(), e);
            fail(e.toString());
            result = Task.Result.FAILED;
        }
        if (result != Task.Result.RUNNING && current == task) {
            current = null;
            task.cancel(mc);
            controls.stopMoving();
            boolean reflex = reflexTasks.remove(task);
            if (task == choreTask && result != Task.Result.DONE) {
                lastChoreFailed = true;
            }
            if (result == Task.Result.DONE) {
                event("done", task.describe());
            } else {
                event("failed", task.describe() + ": " + lastFailure);
                if (task instanceof MoveTasks.Flee || task instanceof MoveTasks.Shelter) {
                    fleeFailedAt = tick;
                } else if (task instanceof ItemTasks.Eat) {
                    eatFailedAt = tick;
                }
                if (!reflex && settings.dropOnFail && !queue.isEmpty()) {
                    List<String> dropped = new ArrayList<>();
                    for (Task t : queue) {
                        dropped.add(t.describe());
                    }
                    queue.clear();
                    reflexTasks.clear();
                    event("dropped", dropped.size() + " queued after the failure: " + String.join(" | ", dropped));
                }
            }
        }
    }

    /** Puts a task in front of the one under way, which goes on after it. */
    static void interrupt(Minecraft mc, Task task) {
        if (current instanceof Companion) {
            current.cancel(mc);
            current = null;
        }
        if (current != null && reflexTasks.remove(current)) {
            current.cancel(mc); // a reflex put aside for another: not taken up again later (a stale run or meal)
        } else if (current != null) {
            queue.addFirst(current);
            if (!(current instanceof Resumable)) {
                current.cancel(mc);
            }
        }
        current = task;
        reflexTasks.add(task);
        event("start", task.describe() + " (interrupting)");
    }

    /** A task that may be put aside and taken up again as it was (a walk plans again from where it is anyway). */
    interface Resumable {}

    /** A task that works in a screen (a crafting table, a chest): ticked while the screen is open. */
    interface ScreenTask {}

    /** A task that runs others in turn (a chain of steps): it works in a screen when the step under way does. */
    interface Wrapper {
        @Nullable Task inner();
    }

    static boolean inScreen(@Nullable Task t) {
        return t instanceof ScreenTask || t instanceof Wrapper w && inScreen(w.inner());
    }

    static void fail(String why) {
        lastFailure = why;
    }

    public static boolean busy() {
        return current != null && !(current instanceof Companion) || !queue.isEmpty();
    }

    // ---- staying alive

    private static void watchHealth(LocalPlayer player) {
        float health = player.getHealth();
        if (lastHealth >= 0 && health < lastHealth - 0.01F) {
            event("hurt", String.format("%.1f health left", health));
        }
        lastHealth = health;
        if (player.isDeadOrDying()) {
            if (!toldDeath) {
                toldDeath = true;
                event("death", "died at " + player.blockPosition().toShortString());
                queue.clear();
                current = null;
                walker.stop(Minecraft.getInstance());
            }
        } else {
            toldDeath = false;
        }
    }

    private static boolean companionDown;

    /**
     * When the companion dies, what they carried lies where they fell, for five minutes: the walker goes there and picks
     * it up (the user's wish on the first evening, 2026-10-21: 「俺が死んだら、アイテム拾ってくれたら助かるな」), and
     * says so; it gives it back when asked.
     */
    private static void watchCompanion(Minecraft mc) {
        if (settings.companion == null) {
            return;
        }
        var friend = MoveTasks.player(mc, settings.companion);
        if (friend == null || !friend.isDeadOrDying()) {
            companionDown = false;
            return;
        }
        if (companionDown) {
            return;
        }
        companionDown = true;
        BlockPos where = friend.blockPosition();
        event("companion_died", settings.companion + " at " + where.toShortString());
        queue.addFirst(new MoveTasks.Collect(10.0, null));
        queue.addFirst(new MoveTasks.Goto(Goal.near(where, 2.0), "to where " + settings.companion + " fell"));
        queue.addFirst(new MoveTasks.Say("アイテム拾いに行ってくるね！"));
        if (current instanceof Companion) {
            current.cancel(mc);
            current = null;
        }
    }

    private static void reflexes(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!settings.reflexes || player.getAbilities().invulnerable) {
            return;
        }
        // a long fall with a water bucket in the bag: the bucket landing
        if (mlg == 0 && !player.onGround() && !player.isInWater() && player.fallDistance > 4.5
                && player.getDeltaMovement().y < -0.5 && Inv.find(player, s -> s.is(Items.WATER_BUCKET)) >= 0) {
            mlg = 1;
            event("reflex", "water bucket landing");
        }
        // the head inside a block (sand or gravel fell on it): dug out at once - it lay at one heart for half a minute
        // and suffocated (2026-10-22)
        if (player.isInWall() && !player.isUnderWater()) {
            if (!(current instanceof MoveTasks.DigOut)) {
                interrupt(mc, new MoveTasks.DigOut());
            }
            return;
        }
        if (BotRules.needsAir(player.getAirSupply(), player.isUnderWater())) {
            if (!(current instanceof MoveTasks.Surface)) {
                interrupt(mc, new MoveTasks.Surface());
            }
            return;
        }
        if (lowHealth(mc, player)) {
            return;
        }
        Entity threat = nearestHostile(player, 7.0);
        // in the house at night: it stays in, and strikes only what is in the room or within reach (it went out of the
        // door after mobs and was killed there, night after night, 2026-10-22)
        boolean leashed = HomeTasks.leashed(mc);
        double reach = player.entityInteractionRange();
        int weapon = Inv.bestWeapon(player);
        boolean armed = BotRules.mayFight(weapon >= 0 ? Inv.damage(player.getInventory().getItem(weapon)) : 1.0, player.getHealth(),
                player.getArmorValue());
        boolean cornered = tick - fleeFailedAt < 100;
        if (threat == null && settings.companion != null && settings.defend && armed && player.getHealth() > 8.0F) {
            var friend = MoveTasks.player(mc, settings.companion);
            if (friend != null && friend.distanceTo(player) < 16.0) {
                Entity near = nearestHostile(friend, 6.0);
                if (near != null && !(near instanceof Creeper) && !(current instanceof FightTasks.Attack)
                        && (!leashed || HomeTasks.inside(mc, friend.blockPosition()) && BotRules.strikeFromInside(HomeTasks.inside(mc, near.blockPosition()),
                                player.distanceTo(near), reach))) {
                    interrupt(mc, new FightTasks.Attack(near.getUUID(), 20.0));
                    return;
                }
            }
        }
        // Running from what chases it gets it caught from behind (a zombie, a skeleton's arrows: it died both ways on the
        // first evening with the user, 2026-10-21): it stands and fights instead, and runs only from a creeper, or from a
        // crowd. A skeleton shooting from further off is gone after rather than ignored.
        boolean fighting = current instanceof FightTasks.Attack || running();
        if (!fighting) {
            int crowd = hostilesWithin(player, 8.0);
            Entity creeper = nearestOf(player, Creeper.class, 5.0);
            if (creeper != null && leashed) {
                // a creeper at the wall: to the room's far side, not out of the door
                if (tick - farSideAt > 60 && !HomeTasks.inside(mc, creeper.blockPosition())) {
                    farSideAt = tick;
                    Task away = HomeTasks.awayInside(mc, creeper.position());
                    if (away != null) {
                        interrupt(mc, away);
                    }
                }
                if (!HomeTasks.inside(mc, creeper.blockPosition())) {
                    return;
                }
            }
            if (creeper != null) {
                interrupt(mc, new MoveTasks.Flee(creeper.position(), 10.0));
                return;
            }
            if (crowd >= 3 && player.getHealth() <= 10.0F && threat != null) {
                Task away = retreat(mc, player, threat, 20.0);
                if (away != null) {
                    interrupt(mc, away);
                    return;
                }
            }
            if (threat != null && leashed && !BotRules.strikeFromInside(HomeTasks.inside(mc, threat.blockPosition()), player.distanceTo(threat), reach)) {
                threat = null; // outside and out of reach: left
            }
            if (threat != null && settings.defend && player.distanceTo(threat) < 4.5 && !(threat instanceof Creeper)) {
                // bare handed it goes home (to the companion, or away over dry land), and fights only what reaches it
                // there, or when it cannot get away
                Task away = armed || cornered ? null : retreat(mc, player, threat, 12.0);
                if (away != null) {
                    interrupt(mc, away);
                } else if (armed || cornered || player.distanceTo(threat) < 3.0) {
                    interrupt(mc, new FightTasks.Attack(threat.getUUID(), 12.0));
                }
                return;
            }
            Entity archer = nearestOf(player, net.minecraft.world.entity.monster.skeleton.AbstractSkeleton.class, 16.0);
            if (archer != null && settings.defend && armed && player.getHealth() > 6.0F && player.hasLineOfSight(archer)
                    && (!leashed || BotRules.strikeFromInside(HomeTasks.inside(mc, archer.blockPosition()), player.distanceTo(archer), reach))) {
                interrupt(mc, new FightTasks.Attack(archer.getUUID(), 24.0));
                return;
            }
        }
        if (threat == null && (current != null || walker.state() == Walker.State.MOVING)) {
            watchDark(mc, player);
        }
        if (settings.eat && !(current instanceof ItemTasks.Eat) && threat == null && !player.isUnderWater() && player.getFoodData().needsFood()
                && (player.getFoodData().getFoodLevel() <= 6 || player.getFoodData().getFoodLevel() <= 14 && player.getHealth() <= 14.0F)
                && Inv.bestFood(player, player.getFoodData().getFoodLevel() <= 4) >= 0 && walker.state() != Walker.State.PLANNING) {
            interrupt(mc, new ItemTasks.Eat(false));
        }
    }

    /** Ends the job under way and those queued (not to be taken up again after a retreat), saying so. */
    private static void endJobs(Minecraft mc, String why) {
        List<String> ended = new ArrayList<>();
        if (current != null && !(current instanceof Companion) && !reflexTasks.contains(current)) {
            ended.add(current.describe());
            current.cancel(mc);
            current = null;
        }
        for (Task t : queue) {
            if (!reflexTasks.contains(t)) {
                ended.add(t.describe());
            }
        }
        queue.removeIf(t -> !reflexTasks.contains(t));
        if (!ended.isEmpty()) {
            lastFailure = why;
            event("dropped", why + ": " + ended.size() + " jobs ended: " + String.join(" | ", ended));
        }
    }

    /** Whether it is running from something (away, or to shelter). */
    private static boolean running() {
        return current instanceof MoveTasks.Flee || current instanceof MoveTasks.Shelter;
    }

    /** Where to go, bare handed or hurt, from a mob: home, the companion, away over dry land - or null, to stay put
     *  (home, or beside the companion, already). */
    private static @Nullable Task retreat(Minecraft mc, LocalPlayer player, Entity from, double distance) {
        if (HomeTasks.leashed(mc)) {
            return null; // in the house at night: there already
        }
        double home = settings.home == null ? Double.POSITIVE_INFINITY : player.position().distanceTo(Vec3.atBottomCenterOf(settings.home));
        var friend = settings.companion != null ? MoveTasks.player(mc, settings.companion) : null;
        double near = friend == null || friend.isDeadOrDying() ? Double.POSITIVE_INFINITY : player.distanceTo(friend);
        BotRules.Retreat where = BotRules.retreat(home, near, underground(mc, player));
        if (BotRules.retreatEndsJobs(where, mc.level.getDefaultClockTime(), player.getHealth())) {
            endJobs(mc, BotRules.night(mc.level.getDefaultClockTime()) ? "night: went home" : "badly hurt: went home");
        }
        return switch (where) {
            case HOME -> new MoveTasks.Shelter(MoveTasks.travel(mc, Goal.near(settings.home, 1.0), settings.home,
                    "into home at " + settings.home.toShortString()), true);
            case COMPANION -> new MoveTasks.Shelter(Goal.near(friend.blockPosition(), 2.0), "to " + settings.companion + "'s side", false);
            case FLEE -> new MoveTasks.Flee(from.position(), distance);
            case SURFACE -> {
                var level = mc.level;
                BlockPos at = player.blockPosition();
                var up = WayBook.ways(mc).wayUp(Walker.feetCell(player));
                if (up != null) {
                    // up the stair it dug: the way it knows
                    event("path", "up its own way: " + up.size() + " waypoints to " + up.get(up.size() - 1).toShortString());
                    yield new MoveTasks.Shelter(new MoveTasks.Via(up, Goal.near(up.get(up.size() - 1), 1.5), "up its own way"), false);
                }
                int sea = level.getSeaLevel();
                yield new MoveTasks.Shelter(Goal.columnNear(at.getX(), at.getZ(), 6.0, (x, y, z) -> y >= sea - 2 && BotRules.dry(level, x, y, z)
                        && BotRules.onSurface(level, x, y, z, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z),
                                Bot::natural)), "up to the surface", false);
            }
            case HOLD -> null;
        };
    }

    static boolean underground(Minecraft mc, LocalPlayer player) {
        BlockPos at = player.blockPosition();
        return BotRules.underground(at.getY(), mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                at.getX(), at.getZ()), mc.level.getSeaLevel());
    }

    /** Where it set its last torch. */
    private static @Nullable BlockPos lastTorch;

    /** A torch set by a job of its own (a dig's, to one side): the next at its feet no nearer than 8 to it. */
    static void torchSetAt(BlockPos at) {
        lastTorch = at;
    }

    /** In the dark underground: a torch at its feet now and then (it died twice in a dark cave, 2026-10-22) - not along
     *  its own ways, lit as they were dug (the first trip home up one set 12 more down its middle). */
    private static void watchDark(Minecraft mc, LocalPlayer player) {
        if (!player.onGround() || player.isInWater() || current instanceof MoveTasks.SetTorch || current instanceof FightTasks.Attack
                || current instanceof DigTasks.Dig || current instanceof MoveTasks.Via || running()
                || walker.state() == Walker.State.PLANNING) {
            return;
        }
        BlockPos feet = Walker.feetCell(player);
        int light = mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet);
        double from = lastTorch == null || !lastTorch.closerThan(feet, 64) ? Double.MAX_VALUE : Math.sqrt(lastTorch.distSqr(feet));
        boolean has = Inv.find(player, s -> s.is(Items.TORCH)) >= 0;
        if (!BotRules.wantsTorch(underground(mc, player), light, from, has)) {
            return;
        }
        BlockState below = mc.level.getBlockState(feet.below());
        if (!mc.level.getBlockState(feet).isAir() || !below.isFaceSturdy(mc.level, feet.below(), net.minecraft.core.Direction.UP)) {
            return;
        }
        lastTorch = feet;
        interrupt(mc, new MoveTasks.SetTorch(feet));
    }

    /** Chores tried and failed lately, with when: left alone for five minutes. */
    private static final java.util.Map<BotRules.Chore, Long> choreTried = new java.util.EnumMap<>(BotRules.Chore.class);
    private static @Nullable Task choreTask;
    private static BotRules.Chore choreKind = BotRules.Chore.NONE;

    /**
     * Between jobs, with chores on: the daily round, one chore at a time (event "chore"), so a day alone does not end
     * with a full bag, a broken pickaxe or an empty stomach. Jobs asked for come first: none starts while it is busy.
     */
    private static void watchChores(Minecraft mc, LocalPlayer player) {
        if (choreTask != null && (current == choreTask || queue.contains(choreTask))) {
            return; // one under way
        }
        if (choreTask != null && lastChoreFailed && choreKind != BotRules.Chore.GUARD && choreKind != BotRules.Chore.FARM
                && choreKind != BotRules.Chore.STOCK) {
            choreTried.put(choreKind, tick); // (the home's, the field's and the larder's parts keep their own count)
        }
        choreTask = null;
        if (!settings.chores || busy() || player.getAbilities().instabuild || player.isDeadOrDying()) {
            return;
        }
        if (settings.home != null && BotRules.night(mc.level.getDefaultClockTime())) {
            return; // with a home, nights are spent in it: a chore would walk it out among the mobs
        }
        choreTried.values().removeIf(at -> tick - at > 6000);
        int free = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                free++;
            }
        }
        // a worn-out tool is as good as none: kept, never used (Inv.wornOut)
        boolean pickaxe = Inv.find(player, s -> s.is(net.minecraft.tags.ItemTags.PICKAXES) && !Inv.wornOut(s)) >= 0;
        boolean sword = Inv.find(player, s -> s.is(net.minecraft.tags.ItemTags.SWORDS) && !Inv.wornOut(s)) >= 0;
        boolean axe = Inv.find(player, s -> s.is(net.minecraft.tags.ItemTags.AXES) && !Inv.wornOut(s)) >= 0;
        int torches = count(player, s -> s.is(Items.TORCH));
        boolean canTorch = count(player, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL)) > 0
                && count(player, s -> s.is(Items.STICK) || s.is(net.minecraft.tags.ItemTags.PLANKS) || s.is(net.minecraft.tags.ItemTags.LOGS)) > 0;
        boolean hasFood = Inv.bestFood(player, false) >= 0;
        Task guard = null;
        Task farm = null;
        Task stock = null;
        if (BotRules.chore(free, settings.store != null, pickaxe, sword, axe, torches, canTorch, player.getFoodData().getFoodLevel(), hasFood,
                choreTried.keySet()) == BotRules.Chore.NONE) {
            guard = HomeTasks.guard(mc);
            farm = guard == null ? HomeTasks.farm(mc) : null;
            stock = guard == null && farm == null ? HomeTasks.stock(mc) : null;
        }
        BotRules.Chore chore = BotRules.chore(free, settings.store != null, pickaxe, sword, axe, torches, canTorch,
                player.getFoodData().getFoodLevel(), hasFood, guard != null, farm != null, stock != null, choreTried.keySet());
        Task task = switch (chore) {
            case DEPOSIT -> new ItemTasks.Deposit(settings.store, "loot");
            case PICKAXE -> tool(player, "pickaxe", 3);
            case SWORD -> tool(player, "sword", 2);
            case AXE -> tool(player, "axe", 3);
            case TORCHES -> new Crafting.Craft(Items.TORCH, Math.min(16, 4 * count(player, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL))));
            case FOOD -> food(mc, player);
            case GUARD -> guard;
            case FARM -> farm;
            case STOCK -> stock;
            case NONE -> null;
        };
        if (chore != BotRules.Chore.NONE && task == null) {
            choreTried.put(chore, tick); // nothing to do it with: not asked again for a while
            event("chore", chore.name().toLowerCase() + ": nothing to do it with");
            return;
        }
        if (task != null) {
            choreTask = task;
            choreKind = chore;
            lastChoreFailed = false;
            event("chore", chore.name().toLowerCase() + ": " + task.describe());
            queue.addLast(task);
        }
    }

    private static boolean lastChoreFailed;
    private static int strayScreen;

    /** A tool of the best material in the bag, or null. */
    private static @Nullable Task tool(LocalPlayer player, String kind, int need) {
        int iron = count(player, s -> s.is(Items.IRON_INGOT));
        int stone = count(player, s -> s.is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS));
        int wood = count(player, s -> s.is(net.minecraft.tags.ItemTags.PLANKS)) + 4 * count(player, s -> s.is(net.minecraft.tags.ItemTags.LOGS));
        String tier = BotRules.toolTier(iron, stone, wood, need);
        return tier == null ? null : new Crafting.Craft(item("minecraft:" + tier + "_" + kind), 1);
    }

    /** Hungry with no food in the bag: raw food cooked, bread baked, a ripe field picked - or null. */
    private static @Nullable Task food(Minecraft mc, LocalPlayer player) {
        for (Item raw : new Item[] {Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.COD, Items.SALMON, Items.POTATO, Items.RABBIT}) {
            int n = count(player, s -> s.is(raw));
            if (n > 0) {
                return new ItemTasks.Smelt(raw, Math.min(n, 8), null);
            }
        }
        if (count(player, s -> s.is(Items.WHEAT)) >= 3 && Crafting.canCraft(mc, Items.BREAD)) {
            return new Crafting.Craft(Items.BREAD, count(player, s -> s.is(Items.WHEAT)) / 3);
        }
        if (!BotRules.ripeCrops(mc.level, player.blockPosition(), 16).isEmpty()) {
            return new FarmTasks.Harvest(16);
        }
        return null;
    }

    private static int count(LocalPlayer player, java.util.function.Predicate<ItemStack> kind) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (kind.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /**
     * At dusk, a job far from home (or from the companion, with no home set) is broken off and it heads back - once a
     * day; the event "dusk" says so. Not when just keeping the companion company: it is beside them already.
     */
    private static void watchDusk(Minecraft mc, LocalPlayer player) {
        long time = mc.level.getDefaultClockTime();
        long day = Math.floorDiv(time, 24000L);
        if (!settings.dusk || day == duskDay || player.getAbilities().invulnerable) {
            return;
        }
        Vec3 home = settings.home != null ? Vec3.atBottomCenterOf(settings.home) : null;
        var friend = settings.companion != null ? MoveTasks.player(mc, settings.companion) : null;
        if (home == null && friend != null) {
            home = friend.position();
        }
        if (home == null || !BotRules.duskCall(time, player.position().distanceTo(home))) {
            return;
        }
        duskDay = day;
        if (!busy() && settings.home == null) {
            event("dusk", "night falls " + (int) player.position().distanceTo(home) + " blocks from home");
            return;
        }
        if (!busy()) {
            // idle far off (a hunt that found nothing ended out there): home before dark all the same
            event("dusk", "night falls " + (int) player.position().distanceTo(home) + " blocks from home: heading back");
            if (current instanceof Companion) {
                current.cancel(mc);
                current = null;
            }
            queue.addLast(new MoveTasks.Goto(Goal.near(settings.home, 3.0), "back home to " + settings.home.toShortString()));
            return;
        }
        String broken = current != null ? current.describe() : "the queue";
        stopAll(mc);
        Task back = settings.home != null ? MoveTasks.travel(mc, Goal.near(settings.home, 3.0), settings.home, "back home to " + settings.home.toShortString())
                : new MoveTasks.Goto(Goal.near(friend.blockPosition(), 3.0), "back to " + settings.companion);
        queue.addLast(back);
        event("dusk", "night falls " + (int) player.position().distanceTo(home) + " blocks from home: broke off " + broken
                + ", heading back");
    }

    // ---- nights at home

    private static long goInAt = -1000;
    private static long sleepTriedAt = -1000;
    private static long farSideAt = -1000;
    private static boolean heldSaid;

    /**
     * At night with a home (unless {@code bot set nightout on}): out of doors, it goes in (the job under way waits for
     * morning); in, a job that would take it out is put back to wait, the doors are shut, and it goes to bed if the
     * house has one. On the day alone, 2026-10-22, it died by the hut night after night: out of the door to fight, out
     * to the store chest after a respawn, the door left open.
     */
    private static void watchNight(Minecraft mc, LocalPlayer player) {
        if (!BotRules.keepIn(mc.level.getDefaultClockTime(), settings.home != null, settings.nightOut) || player.isDeadOrDying()
                || player.getAbilities().invulnerable || player.isSleeping() || mlg != 0) {
            heldSaid = false;
            return;
        }
        boolean inside = HomeTasks.inside(mc, Walker.feetCell(player));
        boolean reflex = current != null && reflexTasks.contains(current);
        if (!inside) {
            if (reflex || current instanceof FightTasks.Sleep || current instanceof HomeTasks.ShutDoor || tick - goInAt < 200) {
                return;
            }
            goInAt = tick;
            event("night", "out at night, " + (int) player.position().distanceTo(Vec3.atBottomCenterOf(settings.home)) + " from home: going in");
            interrupt(mc, new MoveTasks.Shelter(Goal.near(settings.home, 1.0), "in for the night to " + settings.home.toShortString(), true));
            return;
        }
        if (current != null && !(current instanceof Companion) && !reflex && !indoor(mc, current)) {
            String what = current.describe();
            current.cancel(mc);
            queue.addFirst(current);
            current = null;
            walker.stop(mc);
            event("night", "in for the night: " + what + " waits for morning");
            return;
        }
        if (current != null && !(current instanceof Companion) || !queue.isEmpty() && !holdForMorning(mc)) {
            return;
        }
        BlockPos open = HomeTasks.openDoor(mc);
        Task next = null;
        if (open != null) {
            next = new HomeTasks.ShutDoor(open);
        } else if (tick - sleepTriedAt > 600 && Math.floorMod(mc.level.getDefaultClockTime(), 24000L) < 23000 && HomeTasks.bed(mc) != null
                && nearestHostile(player, 8.0) == null) {
            sleepTriedAt = tick;
            next = new FightTasks.Sleep();
        }
        if (next != null) {
            if (current instanceof Companion) {
                current.cancel(mc);
                current = null;
            }
            queue.addFirst(next);
            reflexTasks.add(next); // its failing drops nothing waiting for morning
        }
    }

    /** At night with a home: whether the next job waits for morning (one that would take it out of the house). */
    private static boolean holdForMorning(Minecraft mc) {
        Task next = queue.peekFirst();
        if (next == null || !BotRules.keepIn(mc.level.getDefaultClockTime(), settings.home != null, settings.nightOut) || indoor(mc, next)) {
            if (next == null || !BotRules.night(mc.level.getDefaultClockTime())) {
                heldSaid = false;
            }
            return false;
        }
        // fights and runs put aside are stale by morning
        queue.removeIf(t -> reflexTasks.contains(t) && (t instanceof FightTasks.Attack || t instanceof MoveTasks.Flee));
        next = queue.peekFirst();
        if (next == null || indoor(mc, next)) {
            return false;
        }
        if (!heldSaid) {
            heldSaid = true;
            event("night", queue.size() + " jobs wait for morning, first: " + next.describe());
        }
        return true;
    }

    /** Jobs done in the house: they may go on at night. */
    static boolean indoor(Minecraft mc, Task t) {
        if (t instanceof ItemTasks.AtContainer at) {
            return HomeTasks.inside(mc, at.pos);
        }
        return t instanceof MoveTasks.Say || t instanceof MoveTasks.Wait || t instanceof MoveTasks.Look || t instanceof ItemTasks.Eat
                || t instanceof ItemTasks.Equip || t instanceof ItemTasks.Hold || t instanceof ItemTasks.Drop || t instanceof FightTasks.Sleep
                || t instanceof Crafting.Craft || t instanceof ItemTasks.Smelt || t instanceof HomeTasks.ShutDoor || t instanceof MoveTasks.Shelter
                || t instanceof MoveTasks.DigOut || t instanceof MoveTasks.Surface || t instanceof FightTasks.Attack;
    }

    private static @Nullable Vec3 friendSeen;
    private static int friendStill;
    private static long warnedAt = -100000;

    /**
     * The companion out of doors at night, standing still a minute (away from the keyboard, likely): told so by chat,
     * again every few minutes, and at once when a mob comes close to them. The user's own character, left standing
     * outside while they were away, was killed by zombies (2026-10-22).
     */
    private static void watchFriendAtNight(Minecraft mc) {
        if (settings.companion == null) {
            return;
        }
        var friend = MoveTasks.player(mc, settings.companion);
        if (friend == null || friend.isDeadOrDying()) {
            friendSeen = null;
            friendStill = 0;
            return;
        }
        Vec3 at = friend.position();
        if (friendSeen != null && friendSeen.distanceTo(at) < 0.5) {
            friendStill += 20;
        } else {
            friendSeen = at;
            friendStill = 0;
        }
        BlockPos head = friend.blockPosition().above();
        boolean outdoors = !HomeTasks.inside(mc, friend.blockPosition())
                && mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, head) >= 10;
        boolean mob = nearestHostile(friend, 8.0) != null;
        String text = switch (BotRules.warnCompanion(mc.level.getDefaultClockTime(), outdoors, friendStill, mob, tick - warnedAt)) {
            case OUT_AT_NIGHT -> settings.companion + "、夜なのに外で止まってるよ！モンスターが出るから、家に入ってね";
            case MOB_CLOSE -> settings.companion + "、すぐそばにモンスターがいるよ！気をつけて！";
            case NONE -> null;
        };
        if (text != null) {
            warnedAt = tick;
            mc.player.connection.sendChat(text);
            event("warned", settings.companion + " out at night at " + friend.blockPosition().toShortString() + (mob ? ", a mob close" : ""));
        }
    }

    /**
     * Hurt (three hearts or less): no fighting on. A mob near: get away from it; none: eat, if it can (a golden apple
     * even when not hungry). Cornered - backing off just failed - it fights after all. True when it acted.
     */
    private static boolean lowHealth(Minecraft mc, LocalPlayer player) {
        Entity near = nearestHostile(player, BotRules.BACK_OFF_FROM);
        boolean canEat = Inv.healingFood(player) >= 0 && tick - eatFailedAt > 100;
        boolean cornered = tick - fleeFailedAt < 100;
        BotRules.Survive doing = running() ? BotRules.Survive.BACK_OFF
                : current instanceof ItemTasks.Eat ? BotRules.Survive.EAT : BotRules.Survive.CARRY_ON;
        switch (BotRules.survive(player.getHealth(), near == null ? Double.POSITIVE_INFINITY : player.distanceTo(near), canEat, cornered, doing)) {
            case BACK_OFF -> {
                if (near != null && !running()) {
                    Task away = retreat(mc, player, near, 16.0);
                    dropFights(mc);
                    if (away != null) {
                        interrupt(mc, away);
                    } else if (canEat && !(current instanceof ItemTasks.Eat) && !player.isUnderWater()) {
                        interrupt(mc, new ItemTasks.Eat(true)); // home already: heal where it is
                    }
                }
                return true;
            }
            case EAT -> {
                if (!(current instanceof ItemTasks.Eat) && walker.state() != Walker.State.PLANNING && !player.isUnderWater()) {
                    dropFights(mc);
                    interrupt(mc, new ItemTasks.Eat(true));
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Gives up the fights under way and put aside: too hurt for them. */
    private static void dropFights(Minecraft mc) {
        if (current instanceof FightTasks.Attack) {
            event("failed", current.describe() + ": too hurt to fight on");
            current.cancel(mc);
            reflexTasks.remove(current);
            current = null;
        }
        queue.removeIf(t -> t instanceof FightTasks.Attack);
    }

    /** The water bucket landing: look down, pour a block or two above the ground, pick the water up after. */
    private static void mlgTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (mlg == 1) {
            if (player.onGround() || player.isInWater()) {
                mlg = mlgWater != null ? 2 : 0;
                return;
            }
            controls.face(player.getYRot(), 90.0F);
            BlockPos below = player.blockPosition();
            int ground = -1;
            for (int d = 1; d <= 4; d++) {
                if (!player.level().getBlockState(below.below(d)).getCollisionShape(player.level(), below.below(d)).isEmpty()) {
                    ground = d;
                    break;
                }
            }
            if (ground > 0 && ground <= 3 && player.getXRot() > 75.0F) {
                Inv.hold(mc, Inv.find(player, s -> s.is(Items.WATER_BUCKET)));
                Acts.useItem(mc);
                mlgWater = below.below(ground - 1);
                event("reflex", "water poured at " + mlgWater.toShortString());
            }
        } else if (mlg == 2) {
            BlockPos water = mlgWater;
            if (water == null || !player.level().getBlockState(water).getFluidState().isSource()) {
                mlg = 0;
                mlgWater = null;
                return;
            }
            Vec3 at = Vec3.atCenterOf(water);
            controls.lookAt(player, at);
            if (Controls.looking(player, at, 15.0F)) {
                int bucket = Inv.find(player, s -> s.is(Items.BUCKET));
                if (bucket >= 0) {
                    Inv.hold(mc, bucket);
                    Acts.useItem(mc);
                }
                mlg = 0;
                mlgWater = null;
            }
        }
    }

    static int hostilesWithin(Entity player, double radius) {
        int n = 0;
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (e instanceof Enemy && e.isAlive() && e.distanceToSqr(player) < radius * radius) {
                n++;
            }
        }
        return n;
    }

    static @Nullable Entity nearestOf(Entity player, Class<?> kind, double radius) {
        Entity best = null;
        double bestD = radius * radius;
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (kind.isInstance(e) && e.isAlive()) {
                double d = e.distanceToSqr(player);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    static @Nullable Entity nearestHostile(Entity player, double radius) {
        Entity best = null;
        double bestD = radius * radius;
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (e instanceof Enemy && e instanceof LivingEntity living && living.isAlive() && !e.isInvisible()) {
                double d = e.distanceToSqr(player);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    // ---- what the planner is allowed

    static Abilities abilities(Minecraft mc) {
        LocalPlayer player = mc.player;
        Abilities ab = new Abilities();
        boolean creative = player.getAbilities().instabuild;
        ab.canBreak = !settings.breaking.equals("none");
        ab.mayBreak = (state, pos) -> mayBreak(mc, state, pos);
        ab.breakTicks = (state, pos) -> Inv.breakTicks(player, state, pos);
        ab.placeBlocks = settings.place ? Inv.throwawayCount(player) : 0;
        ab.parkour = settings.parkour;
        if (current instanceof MoveTasks.Surface) {
            ab.maxUnderwater = 32; // swimming for air: the nearest air, however far under (no worse than staying)
        }
        ab.canSprint = settings.sprint && (creative || player.getFoodData().getFoodLevel() > 6);
        if (player.getAbilities().invulnerable) {
            ab.safeFall = 256;
            ab.maxFall = 256;
        } else {
            ab.safeFall = 3.0;
            ab.maxFall = 3.0 + Math.max(0.0, player.getHealth() - 12.0);
            if (Inv.find(player, s -> s.is(Items.WATER_BUCKET)) >= 0 && settings.reflexes) {
                ab.maxFall = Math.max(ab.maxFall, 24.0); // the bucket landing
            }
        }
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (e instanceof Enemy && e.isAlive() && e.distanceToSqr(player) < 32 * 32) {
                ab.avoid.add(new Abilities.Avoid(e.position(), e instanceof Creeper ? 7.0 : 4.5, e instanceof Creeper ? 80.0 : 25.0));
            }
        }
        return ab;
    }

    /** Whether the walker may break this block on its way: natural ground and rock by default, never a build. */
    static boolean mayBreak(Minecraft mc, BlockState state, BlockPos pos) {
        if (state.hasBlockEntity() || state.is(BlockTags.BEDS) || state.getDestroySpeed(mc.level, pos) < 0) {
            return false;
        }
        if (!settings.breaking.equals("none") && walker.scaffold.contains(pos) && state.getBlock().asItem() instanceof net.minecraft.world.item.BlockItem
                && Inv.throwaway(new ItemStack(state.getBlock()))) {
            return true; // a pillar or bridge block of its own still there: no wall to it (boxed in by its own pillars in a
                         // doorway, every goto failed for an hour, 2026-10-22)
        }
        return switch (settings.breaking) {
            case "any" -> true;
            case "natural" -> natural(state) && !BotRules.underBuild(mc.level, pos, Bot::natural);
            default -> false;
        };
    }

    /** Natural ground and rock the walker may dig through on its way. Not ores: those are mined on purpose (a path
     *  through them would break them and leave what they drop behind). */
    static boolean natural(BlockState state) {
        if (state.is(Tags.Blocks.ORES)) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(Tags.Blocks.GRAVELS)
                || state.is(Tags.Blocks.NETHERRACKS) || state.is(Tags.Blocks.END_STONES) || state.is(BlockTags.SNOW)
                || state.is(BlockTags.LEAVES) || state.is(net.minecraft.world.level.block.Blocks.CLAY)
                || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
    }

    // ---- commands

    /** A {@code bot ...} line from AgentInput. Throws on a bad command (written to agent_output as failed). */
    public static void command(Minecraft mc, String[] a) {
        if (!ENABLED || mc.player == null) {
            throw new IllegalStateException("no player");
        }
        if (a.length == 0) {
            throw new IllegalArgumentException("bot what?");
        }
        boolean now = a[0].equals("now");
        if (now) {
            a = Arrays.copyOfRange(a, 1, a.length);
            stopAll(mc);
        }
        LocalPlayer player = mc.player;
        String[] args = a;
        Task task = switch (a[0]) {
            // what depends on the world as it will be when the job starts (a field the till before it makes, where a
            // player has got to, the nearest mob) is looked at then: checked when the line was read, a plant queued
            // after a till was refused at once, as no farmland was there yet (2026-10-22)
            case "goto" -> later(String.join(" ", args), () -> MoveTasks.goTo(mc, args));
            case "come" -> later("come " + a[1], () -> MoveTasks.goTo(mc, new String[] {"goto", "player", args[1], "2"}));
            case "follow" -> new MoveTasks.Follow(a[1], a.length > 2 ? Double.parseDouble(a[2]) : 3.0);
            case "flee" -> {
                Entity threat = nearestHostile(player, 32.0);
                Vec3 from = threat != null ? threat.position() : player.position();
                yield new MoveTasks.Flee(from, a.length > 1 ? Double.parseDouble(a[1]) : 16.0);
            }
            case "collect" -> new MoveTasks.Collect(a.length > 1 ? Double.parseDouble(a[1]) : 8.0, a.length > 2 ? item(a[2]) : null);
            case "mine" -> new WorkTasks.Mine(WorkTasks.blocks(a[1]), a.length > 2 ? Integer.parseInt(a[2]) : 1,
                    a.length > 3 ? Integer.parseInt(a[3]) : 32);
            case "place" -> new WorkTasks.Build(List.of(WorkTasks.target(a[1], pos(a, 2))), "place " + a[1]);
            case "build" -> WorkTasks.buildFile(mc, a);
            case "cleanup" -> new WorkTasks.Cleanup();
            case "stairdown" -> later(String.join(" ", args), () -> DigTasks.stairDown(args));
            case "tunnel" -> later(String.join(" ", args), () -> DigTasks.tunnel(args));
            case "ways" -> {
                var ways = WayBook.ways(mc);
                if (a.length > 1 && a[1].equals("clear")) {
                    if (a.length > 2) {
                        ways.remove(a[2]);
                    } else {
                        ways.clear();
                    }
                    WayBook.save(mc);
                }
                List<String> list = new ArrayList<>();
                for (var e : ways.all().entrySet()) {
                    var cells = e.getValue();
                    list.add(e.getKey() + " " + cells.get(0).toShortString() + " -> " + cells.get(cells.size() - 1).toShortString() + " (" + cells.size() + ")");
                }
                event("ways", list.isEmpty() ? "none" : String.join(" | ", list));
                yield null;
            }
            case "till" -> later(String.join(" ", args), () -> FarmTasks.till(mc, args));
            case "plant" -> later(String.join(" ", args), () -> FarmTasks.plant(mc, args));
            case "harvest" -> new FarmTasks.Harvest(a.length > 1 ? Integer.parseInt(a[1]) : 8);
            case "useon" -> new FarmTasks.UseOn(item(a[1]), pos(a, 2));
            case "explore" -> {
                List<String> rest = new ArrayList<>(Arrays.asList(a).subList(1, a.length));
                boolean stay = rest.remove("stay");
                yield new ExploreTasks.Explore(rest.size() > 0 ? Integer.parseInt(rest.get(0)) : 96,
                        rest.size() > 1 ? Integer.parseInt(rest.get(1)) : 24, stay);
            }
            case "findmob" -> {
                ExploreTasks.findMob(mc, a[1], a.length > 2 ? Integer.parseInt(a[2]) : 48);
                yield null;
            }
            case "fish" -> {
                if (Inv.find(player, st -> st.is(Items.FISHING_ROD)) < 0 && !player.getAbilities().instabuild) {
                    queue.addLast(new Crafting.Craft(Items.FISHING_ROD, 1)); // no rod: one made first
                }
                yield new FishTasks.Fish(a.length > 1 ? Integer.parseInt(a[1]) : 10);
            }
            case "craft" -> new Crafting.Craft(item(a[1]), a.length > 2 ? Integer.parseInt(a[2]) : 1);
            case "smelt" -> new ItemTasks.Smelt(item(a[1]), a.length > 2 ? Integer.parseInt(a[2]) : 1, a.length > 3 ? item(a[3]) : null);
            case "eat" -> new ItemTasks.Eat(false);
            case "equip" -> new ItemTasks.Equip();
            case "hold" -> new ItemTasks.Hold(item(a[1]));
            case "give" -> new ItemTasks.Give(a[1], a.length > 2 && !a[2].equals("all") ? item(a[2]) : null,
                    a.length > 3 ? Integer.parseInt(a[3]) : Integer.MAX_VALUE);
            case "drop" -> new ItemTasks.Drop(item(a[1]), a.length > 2 ? a[2] : "all");
            case "deposit" -> new ItemTasks.Deposit(pos(a, 1), a.length > 4 ? a[4] : "all");
            case "withdraw" -> new ItemTasks.Withdraw(pos(a, 1), item(a[4]), a.length > 5 ? Integer.parseInt(a[5]) : 64);
            case "attack" -> later(String.join(" ", args), () -> FightTasks.attack(mc, args.length > 1 ? args[1] : "hostile"));
            case "sleep" -> new FightTasks.Sleep();
            case "look" -> new MoveTasks.Look(Vec3.atCenterOf(pos(a, 1)), null);
            case "lookat" -> new MoveTasks.Look(null, a[1]);
            case "say" -> new MoveTasks.Say(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            case "wait" -> new MoveTasks.Wait(Integer.parseInt(a[1]));
            case "stop" -> {
                stopAll(mc);
                yield null;
            }
            case "pause" -> {
                paused = true;
                yield null;
            }
            case "resume" -> {
                paused = false;
                yield null;
            }
            case "respawn" -> {
                player.respawn();
                yield null;
            }
            case "store" -> {
                settings.store = a[1].equals("off") ? null : pos(a, 1);
                event("store", settings.store == null ? "off" : settings.store.toShortString());
                yield null;
            }
            case "home" -> {
                settings.home = a[1].equals("off") ? null : pos(a, 1);
                if (settings.home != null) {
                    HomeTasks.setPlans(mc, a, 4);
                } else {
                    HomeTasks.plans.clear();
                    HomeTasks.doors.clear();
                }
                event("home", settings.home == null ? "off" : settings.home.toShortString() + " (" + HomeTasks.plans.size() + " plans, "
                        + HomeTasks.doors.size() + " doors)");
                yield null;
            }
            case "pen" -> {
                settings.pen = a[1].equals("off") ? null : pos(a, 1);
                event("pen", settings.pen == null ? "off" : settings.pen.toShortString());
                yield null;
            }
            case "guard", "farm", "stock" -> {
                if (settings.home == null && !a[0].equals("stock")) {
                    throw new IllegalArgumentException("no home (bot home X Y Z [FILE OX OY OZ])");
                }
                Task t = a[0].equals("guard") ? HomeTasks.guard(mc) : a[0].equals("farm") ? HomeTasks.farm(mc) : HomeTasks.stock(mc);
                if (t == null) {
                    event(a[0], "nothing to do (or nothing to do it with)");
                }
                yield t;
            }
            case "companion" -> {
                settings.companion = a[1].equals("off") ? null : a[1];
                if (current instanceof Companion) {
                    current.cancel(mc);
                    current = null;
                }
                event("companion", settings.companion == null ? "off" : "keeping " + a[1] + " company");
                yield null;
            }
            case "set" -> {
                set(a[1], a[2]);
                yield null;
            }
            case "state" -> {
                writeState(mc);
                yield null;
            }
            case "scan" -> {
                Scan.scan(mc, a.length > 1 ? Integer.parseInt(a[1]) : 24);
                yield null;
            }
            case "find" -> {
                Scan.find(mc, WorkTasks.blocks(a[1]), a.length > 2 ? Integer.parseInt(a[2]) : 48);
                yield null;
            }
            case "path" -> {
                Scan.path(mc, Goal.at(pos(a, 1)));
                yield null;
            }
            default -> throw new IllegalArgumentException("unknown bot command " + a[0]);
        };
        if (task != null) {
            queue.addLast(task);
        }
    }

    /** A job made when its turn comes (a bad one fails then, saying why), from a line read now. */
    private static Task later(String what, java.util.function.Supplier<Task> make) {
        return new Later(what, make);
    }

    /** Stands in the queue for a job made when its turn comes; once made, the job takes its place (so a fight is a
     *  fight to the reflexes, a walk a walk). */
    static final class Later implements Task {
        private final String what;
        private final java.util.function.Supplier<Task> make;

        Later(String what, java.util.function.Supplier<Task> make) {
            this.what = what;
            this.make = make;
        }

        @Override
        public String describe() {
            return this.what;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            Task made;
            try {
                made = this.make.get();
            } catch (IllegalArgumentException e) {
                fail(e.getMessage());
                return Result.FAILED;
            }
            if (current == this) {
                current = made;
            }
            return made.tick(mc, c);
        }

        @Override
        public void cancel(Minecraft mc) {
        }
    }

    static void stopAll(Minecraft mc) {
        if (current != null) {
            current.cancel(mc);
        }
        current = null;
        queue.clear();
        reflexTasks.clear();
        walker.stop(mc);
        mlg = 0;
        controls.stopMoving();
        controls.stopTurning();
    }

    private static void set(String key, String value) {
        boolean on = value.equals("on") || value.equals("true");
        switch (key) {
            case "break" -> {
                if (!List.of("none", "natural", "any").contains(value)) {
                    throw new IllegalArgumentException("break none|natural|any");
                }
                settings.breaking = value;
            }
            case "place" -> settings.place = on;
            case "parkour" -> settings.parkour = on;
            case "sprint" -> settings.sprint = on;
            case "reflexes" -> settings.reflexes = on;
            case "defend" -> settings.defend = on;
            case "eat" -> settings.eat = on;
            case "cleanup" -> settings.cleanup = on;
            case "onfail" -> settings.dropOnFail = value.equals("drop");
            case "dusk" -> settings.dusk = on;
            case "chores" -> settings.chores = on;
            case "nightout" -> settings.nightOut = on;
            case "food" -> settings.foodStock = Integer.parseInt(value);
            case "turn" -> controls.turnSpeed = Float.parseFloat(value);
            default -> throw new IllegalArgumentException("unknown setting " + key);
        }
    }

    static BlockPos pos(String[] a, int at) {
        return new BlockPos((int) Math.floor(Double.parseDouble(a[at])), (int) Math.floor(Double.parseDouble(a[at + 1])),
                (int) Math.floor(Double.parseDouble(a[at + 2])));
    }

    static Item item(String id) {
        Identifier key = Identifier.parse(id);
        Item item = BuiltInRegistries.ITEM.getValue(key);
        if (item == Items.AIR && !key.getPath().equals("air")) {
            throw new IllegalArgumentException("no item " + id);
        }
        return item;
    }

    // ---- files

    static Path dir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    /** One line of bot_events.txt. */
    static void event(String type, String detail) {
        String line = new Json().put("tick", tick).put("type", type).put("detail", detail) + "\n";
        try {
            Files.writeString(dir().resolve("bot_events.txt"), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            Nookworks.LOGGER.warn("[bot] cannot write an event: {}", e.toString());
        }
        Nookworks.LOGGER.info("[bot] {}: {}", type, detail);
    }

    @SubscribeEvent
    static void onChat(ClientChatReceivedEvent event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event instanceof ClientChatReceivedEvent.Player said && mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(event.getSender());
            String from = info != null ? info.getProfile().name() : "?";
            String words = said.getPlayerChatMessage().decoratedContent().getString();
            boolean mine = mc.player != null && from.equals(mc.player.getName().getString());
            // a person's words, one JSON line each: what the agent watches for (its own lines are "said")
            event(mine ? "said" : "talk", from + ": " + words);
            return;
        }
        event("chat", event.getMessage().getString());
    }

    static void writeState(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        Json j = new Json();
        j.put("tick", tick);
        j.put("x", player.getX()).put("y", player.getY()).put("z", player.getZ());
        j.put("yaw", player.getYRot()).put("pitch", player.getXRot());
        j.put("dimension", player.level().dimension().identifier().toString());
        j.put("health", player.getHealth()).put("food", player.getFoodData().getFoodLevel())
                .put("saturation", player.getFoodData().getSaturationLevel()).put("air", player.getAirSupply())
                .put("xpLevel", player.experienceLevel);
        j.put("onGround", player.onGround()).put("inWater", player.isInWater()).put("dead", player.isDeadOrDying());
        j.put("gamemode", mc.gameMode.getPlayerMode().getName());
        j.put("dayTime", mc.level.getDefaultClockTime() % 24000);
        j.put("screen", mc.gui.screen() == null ? "" : mc.gui.screen().getClass().getSimpleName());
        Inventory inv = player.getInventory();
        j.put("selected", inv.getSelectedSlot());
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                items.add(new Json().put("slot", i).put("id", BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                        .put("count", s.getCount()).put("damage", s.isDamageableItem() ? s.getDamageValue() : 0).toString());
            }
        }
        j.raw("inventory", Json.array(items));
        List<String> worn = new ArrayList<>();
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.OFFHAND}) {
            ItemStack s = player.getItemBySlot(slot);
            if (!s.isEmpty()) {
                worn.add(Json.str(slot.getName() + ":" + BuiltInRegistries.ITEM.getKey(s.getItem())));
            }
        }
        j.raw("worn", Json.array(worn));
        j.put("task", current == null ? "" : current.describe()).put("queued", queue.size()).put("paused", paused);
        j.put("walker", walker.state().name()).put("stepsLeft", walker.remaining());
        var step = walker.current();
        j.put("step", step == null ? "" : step.toString());
        j.put("lastFailure", lastFailure == null ? "" : lastFailure);
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
            BlockState s = player.level().getBlockState(b.getBlockPos());
            j.put("lookingAt", BuiltInRegistries.BLOCK.getKey(s.getBlock()) + " " + b.getBlockPos().toShortString());
        } else if (hit instanceof EntityHitResult e) {
            j.put("lookingAt", e.getEntity().getType().toShortString() + " " + e.getEntity().getName().getString());
        } else {
            j.put("lookingAt", "");
        }
        List<String> near = new ArrayList<>();
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
            if (e == player || e.distanceToSqr(player) > 24 * 24 || near.size() >= 24) {
                continue;
            }
            Json ej = new Json().put("id", e.getId()).put("type", e.getType().toShortString()).put("name", e.getName().getString())
                    .put("x", e.getX()).put("y", e.getY()).put("z", e.getZ()).put("dist", e.distanceTo(player))
                    .put("hostile", e instanceof Enemy);
            if (e instanceof LivingEntity living) {
                ej.put("health", living.getHealth());
            }
            if (e instanceof net.minecraft.world.entity.item.ItemEntity item) {
                ej.put("item", BuiltInRegistries.ITEM.getKey(item.getItem().getItem()) + " x" + item.getItem().getCount());
            }
            near.add(ej.toString());
        }
        j.raw("entities", Json.array(near));
        j.put("settings", "break=" + settings.breaking + " place=" + settings.place + " parkour=" + settings.parkour
                + " sprint=" + settings.sprint + " reflexes=" + settings.reflexes + " defend=" + settings.defend
                + " eat=" + settings.eat + " cleanup=" + settings.cleanup + " onfail=" + (settings.dropOnFail ? "drop" : "go") + " dusk=" + settings.dusk + " chores=" + settings.chores
                + " store=" + (settings.store == null ? "" : settings.store.toShortString())
                + " home=" + (settings.home == null ? "" : settings.home.toShortString() + " plans=" + HomeTasks.plans.size())
                + " pen=" + (settings.pen == null ? "" : settings.pen.toShortString()) + " nightout=" + settings.nightOut
                + " food=" + settings.foodStock + " turn=" + controls.turnSpeed);
        try {
            Path tmp = dir().resolve("bot_state.json.tmp");
            Files.writeString(tmp, j.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, dir().resolve("bot_state.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // the file is busy being read: next time
        }
    }

    static String name(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
