package dev.nookworks.client.dev.bot;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.BotRules;
import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * {@code fish [N|TICKS]}: fishing, the user's second wish for NookAI's days alone (2026-10-22). It finds open water
 * with a bank to stand on (BotRules.fishingSpot), walks there, casts, watches the bobber and reels in on the bite - the
 * moment the server says a fish took it, the dip a person waits for - and says what came up. N up to 100 is a number of
 * catches; more is a number of ticks to fish for.
 */
final class FishTasks {
    private FishTasks() {}

    private static @Nullable Field biting;

    /** Whether a fish has the hook: the hook's own flag, set on the client from the server's word (the bobber's dip). */
    static boolean biting(FishingHook hook) {
        try {
            if (biting == null) {
                biting = FishingHook.class.getDeclaredField("biting");
                biting.setAccessible(true);
            }
            return biting.getBoolean(hook);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FishingHook.biting", e);
        }
    }

    static final class Fish implements Task, Bot.Resumable {
        private final int catches;
        private final int ticksLimit;
        private BotRules.@Nullable FishingSpot spot;
        private @Nullable Goal goal;
        private int ticks;
        private int castTicks;
        private int wait;
        private int caught;
        private int reels;
        private @Nullable Map<Item, Integer> before;
        private int reeledAt = -1;

        Fish(int n) {
            this.catches = n <= 100 ? n : Integer.MAX_VALUE;
            this.ticksLimit = n <= 100 ? Integer.MAX_VALUE : n;
        }

        @Override
        public String describe() {
            return "fish (" + this.caught + " caught" + (this.catches < Integer.MAX_VALUE ? " of " + this.catches : "") + ")";
        }

        private static Map<Item, Integer> bag(LocalPlayer player) {
            Map<Item, Integer> out = new HashMap<>();
            for (int i = 0; i < 36; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    out.merge(s.getItem(), s.getCount(), Integer::sum);
                }
            }
            return out;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            this.ticks++;
            // what the last reel brought: the catch flies to the player for a moment
            if (this.reeledAt >= 0 && this.ticks - this.reeledAt > 30) {
                Map<Item, Integer> now = bag(player);
                StringBuilder got = new StringBuilder();
                for (var e : now.entrySet()) {
                    int more = e.getValue() - this.before.getOrDefault(e.getKey(), 0);
                    if (more > 0 && e.getKey() != Items.FISHING_ROD) {
                        got.append(got.isEmpty() ? "" : ", ").append(more).append(' ').append(Bot.name(e.getKey()));
                    }
                }
                if (!got.isEmpty()) {
                    this.caught++;
                    Bot.event("caught", got.toString());
                }
                this.reeledAt = -1;
                if (this.caught >= this.catches) {
                    return Result.DONE;
                }
            }
            if (this.ticks > this.ticksLimit && player.fishing == null && this.reeledAt < 0) {
                return Result.DONE;
            }
            if (this.spot == null) {
                this.spot = BotRules.fishingSpot(mc.level, player.blockPosition(), 16);
                if (this.spot == null) {
                    Bot.fail("no open water with a bank to stand on within 16");
                    return Result.FAILED;
                }
                this.goal = Goal.at(this.spot.stand());
                Bot.walker.go(this.goal);
            }
            if (!Bot.walker.isFor(this.goal)) {
                Bot.walker.go(this.goal);
            }
            if (Bot.walker.state() != Walker.State.DONE) {
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    Bot.fail("cannot get to the bank at " + this.spot.stand().toShortString() + ": " + Bot.walker.failure());
                    return Result.FAILED;
                }
                return Result.RUNNING;
            }
            if (!FarmTasks.holdOf(mc, s -> s.is(Items.FISHING_ROD), new ItemStack(Items.FISHING_ROD),
                    "no fishing rod (bot craft minecraft:fishing_rod: three sticks, two string)")) {
                return this.caught > 0 ? Result.DONE : Result.FAILED;
            }
            if (!player.getMainHandItem().is(Items.FISHING_ROD)) {
                return Result.RUNNING;
            }
            Vec3 water = Vec3.atCenterOf(this.spot.water()).add(0, 0.5, 0);
            FishingHook hook = player.fishing;
            if (this.wait > 0) {
                this.wait--;
                return Result.RUNNING;
            }
            if (hook == null) {
                // cast: looking a little above the water, so the line flies out to it
                Vec3 aim = water.add(0, 1.0 + player.getEyePosition().distanceTo(water) * 0.15, 0);
                c.lookAt(player, aim);
                if (Controls.looking(player, aim, 3.0F) && this.reeledAt < 0) {
                    this.before = null;
                    Acts.useItem(mc);
                    Acts.swing(mc, false);
                    this.castTicks = 0;
                    this.wait = 10;
                }
                return Result.RUNNING;
            }
            this.castTicks++;
            c.lookAt(player, hook.position());
            boolean wet = hook.isInWater();
            if (biting(hook) || this.castTicks > 60 && !wet && hook.onGround() || this.castTicks > 1500) {
                // a bite: in it comes (or a cast that missed the water, or one too long without a bite: again)
                this.before = bag(player);
                Acts.useItem(mc);
                Acts.swing(mc, false);
                this.reels++;
                this.reeledAt = this.ticks;
                this.wait = 15;
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            LocalPlayer player = mc.player;
            if (player != null && player.fishing != null && player.getMainHandItem().is(Items.FISHING_ROD)) {
                Acts.useItem(mc); // the line in
            }
        }
    }
}
