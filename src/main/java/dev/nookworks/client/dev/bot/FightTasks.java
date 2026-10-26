package dev.nookworks.client.dev.bot;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Fighting, and sleeping the night through. */
final class FightTasks {
    private FightTasks() {}

    /** {@code attack hostile} (the nearest hostile mob), {@code attack ID} (the nearest of a kind), or a player's name. */
    static Task attack(Minecraft mc, String spec) {
        LocalPlayer me = mc.player;
        Entity target = null;
        if (spec.equals("hostile")) {
            target = Bot.nearestHostile(me, 24.0);
        } else if (spec.contains(":")) {
            // any kind, those in water too (fish: cod, salmon, squid), within 48; the nearest seen at all said if further
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(spec))
                    .orElseThrow(() -> new IllegalArgumentException("no entity type " + spec));
            double best = Double.MAX_VALUE;
            Entity nearest = null;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.getType() == type && e.isAlive() && e.distanceToSqr(me) < best) {
                    best = e.distanceToSqr(me);
                    nearest = e;
                }
            }
            if (nearest == null) {
                throw new IllegalArgumentException("nothing to attack: no " + spec + " in sight (none loaded round the player)");
            }
            if (best > 48 * 48) {
                throw new IllegalArgumentException("nothing to attack: the nearest " + spec + " is " + (int) Math.sqrt(best)
                        + " blocks off, at " + nearest.blockPosition().toShortString() + " (go nearer first)");
            }
            target = nearest;
        } else {
            target = MoveTasks.player(mc, spec);
            if (target == null) {
                throw new IllegalArgumentException("nothing to attack: no player " + spec
                        + " (a mob kind needs its id with the namespace, minecraft:cod)");
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("nothing to attack: no hostile mob within 24");
        }
        return new Attack(target.getUUID(), 56.0);
    }

    /**
     * Fights one mob until it dies: walks within reach, turns to it, strikes with the best weapon each time the swing has
     * come back to full strength - from a jump when it can, for the critical hit. Gives up if it runs off too far.
     */
    static final class Attack implements Task {
        private final UUID target;
        private final double giveUp;
        private @Nullable BlockPos heading;
        private int ticks;
        private int hits;

        Attack(UUID target, double giveUp) {
            this.target = target;
            this.giveUp = giveUp;
        }

        @Override
        public String describe() {
            return "fight (" + this.hits + " hits)";
        }

        private @Nullable Entity find(Minecraft mc) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e.getUUID().equals(this.target)) {
                    return e;
                }
            }
            return null;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer me = mc.player;
            Entity e = this.find(mc);
            if (e == null || !e.isAlive() || e instanceof LivingEntity l && l.isDeadOrDying()) {
                Bot.walker.stop(mc);
                return Result.DONE;
            }
            double d = me.distanceTo(e);
            if (d > this.giveUp || ++this.ticks > 2400) {
                Bot.walker.stop(mc);
                Bot.fail("it got away");
                return Result.FAILED;
            }
            double reach = me.entityInteractionRange();
            Vec3 aim = e.getBoundingBox().getCenter().add(0, e.getBbHeight() * 0.2, 0);
            if (d > reach - 0.4 && HomeTasks.leashed(mc) && !HomeTasks.inside(mc, e.blockPosition())) {
                Bot.walker.stop(mc);
                return Result.DONE; // in for the night, the mob outside and out of reach: left (going after it was going out)
            }
            if (d > reach - 0.4) {
                BlockPos at = e.blockPosition();
                if (this.heading == null || this.heading.distManhattan(at) > 1 || Bot.walker.state() != Walker.State.MOVING
                        && Bot.walker.state() != Walker.State.PLANNING) {
                    this.heading = at;
                    Bot.walker.go(Goal.near(at, 1.5));
                }
                Bot.walker.tick(mc, c);
                if (d < 6) {
                    c.lookAt(me, aim);
                }
                c.sprint = true;
                return Result.RUNNING;
            }
            if (this.heading != null) {
                Bot.walker.stop(mc);
                this.heading = null;
            }
            int weapon = Inv.bestWeapon(me);
            if (weapon >= 0) {
                Inv.hold(mc, weapon);
            }
            c.lookAt(me, aim);
            // step in a little if it backs off; keep facing it
            c.forward = d > reach - 0.9;
            float strength = me.getAttackStrengthScale(0.5F);
            boolean critReady = !me.onGround() && me.getDeltaMovement().y < -0.08 && !me.isInWater() && !me.onClimbable();
            if (me.onGround() && strength > 0.7F && !(e instanceof Player) && !me.isInWater()) {
                c.jump = true; // for the falling strike
            }
            if (Controls.looking(me, aim, 18.0F) && strength >= 0.95F && (critReady || me.onGround() && strength >= 1.0F && this.ticks % 4 == 0
                    || me.isInWater())) {
                Acts.attack(mc, e);
                this.hits++;
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }

    /** Sleeps in the nearest bed (walking to it), until morning. */
    static final class Sleep implements Task, Bot.ScreenTask {
        private @Nullable BlockPos bed;
        private @Nullable Goal goal;
        private int tries;
        private int asleep;

        @Override
        public String describe() {
            return this.asleep > 0 ? "asleep" : "go to bed";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer me = mc.player;
            if (me.isSleeping()) {
                this.asleep++;
                return Result.RUNNING;
            }
            if (this.asleep > 0) {
                return Result.DONE; // woken: morning
            }
            if (this.bed == null) {
                this.bed = Scan.nearest(mc, s -> s.is(BlockTags.BEDS), 32, java.util.Set.of());
                if (this.bed == null) {
                    Bot.fail("no bed within 32");
                    return Result.FAILED;
                }
                this.goal = new WorkTasks.ReachGoal(this.bed);
                Bot.walker.go(this.goal);
            }
            if (Bot.walker.state() != Walker.State.DONE) {
                if (!Bot.walker.isFor(this.goal)) {
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    Bot.fail("cannot get to the bed");
                    return Result.FAILED;
                }
                return Result.RUNNING;
            }
            if (!ItemTasks.useBlock(mc, c, this.bed, ++this.tries)) {
                Bot.fail("could not sleep (not night, or monsters near)");
                return Result.FAILED;
            }
            if (this.tries > 60) {
                Bot.fail("could not sleep (not night, or monsters near)");
                return Result.FAILED;
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }

    static boolean hostile(Entity e) {
        return e instanceof Enemy;
    }
}
