package dev.nookworks.feature;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Whether something of ours may move what one block holds into another: liquid along pipes and out of machines,
 * experience, mercury over mercury pipes, power over cables, and a Motor's turning of the machines on its shaft or
 * reached by its Drive Wheels (the user, 2026-10-13, on r15: "おすすめで" - a Repair Box set down outside another's
 * claim drew the experience out of their tank inside it; r15 fix check 4: a stranger's Oil Press against a claimed
 * motor's shaft ran on the claim's owner's power). Claim mods (and spawn protection) are asked for the side that takes
 * it in, as its owner breaking the block it comes out of; for a block that keeps no owner of ours (vanilla's, another
 * mod's), as the giver's owner breaking the block it goes into - so nothing is drawn out of a claim by a block a stranger
 * set down beside it, and nothing of ours pours into someone else's claim. A pipe of the claim's owner joined to a
 * stranger's beside it hands it nothing.
 * <p>
 * Asked at any step, sideways, over or under: Flan's claims need not keep to chunks, and have a floor (and, made 3D, a
 * ceiling) - a stranger's Repair Box set straight over a claimed tank drew it dry while over and under went unasked
 * (r15's other-mods check, 2026-10-14). Not asked: between two blocks the same player set down; for a block set down before owners were kept, or by a machine
 * of another mod whose fake player is no player the server knows ({@link #ownerOf}), as before. The answer is kept five
 * minutes, or a little less by place so that a row of them does not ask all in one tick, as the Spawner Controller keeps
 * its own; and asked only when something would move, since each asking is a break event every listener hears.
 */
public final class DrawRights {
    /** How long an answer is kept, in ticks, at most. */
    public static final long ASK_EVERY = 6000;
    /** By the position asked about: who it was asked for, until when, and whether they were let. */
    private final Map<BlockPos, Answer> answers = new HashMap<>();

    private record Answer(UUID owner, long until, boolean let) {}

    /**
     * What each owner's blocks were told about taking from a giver that keeps no owner of ours - a cable another mod puts
     * power into, a cauldron, another mod's tank - by level, then owner. Whether that owner could break the block there
     * is one question for all their blocks: asked by each receiver for itself, power another mod put into a cable reaching
     * fifty machines of one player posted fifty break events at each place it came in (r15 fix check 4, N2). Server
     * thread only; let go of as the answers run out, and when the server stops.
     */
    private static final Map<ResourceKey<Level>, Shared> SHARED = new HashMap<>();

    private static final class Shared {
        final Map<UUID, DrawRights> byOwner = new HashMap<>();
        long sweptAt;
    }

    /**
     * Whether what the block at {@code from} holds may go into the block at {@code into} - {@code source} and
     * {@code target} being the block entities there (or null). See the class's note.
     */
    public static boolean mayMove(ServerLevel level, BlockPos from, @Nullable BlockEntity source, BlockPos into, @Nullable BlockEntity target) {
        UUID giver = source instanceof Owned owned ? owned.owner() : null;
        if (target instanceof Owned taker) {
            UUID owner = taker.owner();
            if (owner == null || owner.equals(giver)) {
                return true;
            }
            DrawRights rights = giver == null ? shared(level, owner) : taker.drawRights();
            return rights.couldBreak(level, into, from, owner);
        }
        return giver == null || ((Owned) source).drawRights().couldBreak(level, from, into, giver);
    }

    /**
     * Whether {@code owner}, for a block of theirs at {@code by}, could break the block at {@code at}: always for nobody;
     * else asked as them, the answer kept.
     */
    public boolean couldBreak(ServerLevel level, BlockPos by, BlockPos at, @Nullable UUID owner) {
        if (owner == null) {
            return true;
        }
        long now = level.getGameTime();
        Answer answer = this.answers.get(at);
        if (answer != null && answer.until() > now && answer.owner().equals(owner)) {
            return answer.let();
        }
        boolean let = Permission.mayTake(level, at, Permission.actor(level, owner));
        this.answers.put(at.immutable(), new Answer(owner, now + ASK_EVERY - Math.floorMod(at.hashCode(), ASK_EVERY / 5), let));
        return let;
    }

    /** {@code owner}'s answers about givers of no owner of ours, in {@code level}; those run out are let go of now and then. */
    private static DrawRights shared(ServerLevel level, UUID owner) {
        Shared shared = SHARED.computeIfAbsent(level.dimension(), k -> new Shared());
        long now = level.getGameTime();
        if (now - shared.sweptAt >= ASK_EVERY || now < shared.sweptAt) {
            shared.sweptAt = now;
            shared.byOwner.values().forEach(rights -> rights.answers.values().removeIf(answer -> answer.until() <= now));
            shared.byOwner.values().removeIf(rights -> rights.answers.isEmpty());
        }
        return shared.byOwner.computeIfAbsent(owner, k -> new DrawRights());
    }

    /**
     * Who owns a block {@code placer} sets down: a player, they do. Another mod's machine placing as a fake player (a
     * Create Deployer) places for the player whose id it carries, when that is a player this server knows - one who has
     * played on it, in its user cache: so a Deployer line in its owner's own claim takes across a step sideways there,
     * and one set beside someone else's claim takes nothing out of it (a pipe it set down as nobody's drew a claimed tank
     * dry, r15 fix check 4). A fake player of no known player - a machine's own id - owns nothing, as before: there is
     * nobody to ask as but the anonymous one, and asked so, a claim would keep its owner's own machines from taking
     * anything across a step sideways inside it. Nobody for anything else (a mob).
     */
    public static @Nullable UUID ownerOf(@Nullable Entity placer) {
        if (!(placer instanceof Player player)) {
            return null;
        }
        if (!(player instanceof FakePlayer)) {
            return player.getUUID();
        }
        return player.level() instanceof ServerLevel level && level.getServer().services().nameToIdCache().get(player.getUUID()).isPresent()
                ? player.getUUID() : null;
    }

    /** A block entity of ours that keeps who set it down, to move things in and out with their say-so. */
    public interface Owned {
        @Nullable UUID owner();

        void setOwner(@Nullable UUID owner);

        /** Its answers so far, as its owner. */
        DrawRights drawRights();
    }

    /**
     * Whoever sets one down owns it, as {@link #ownerOf} says (a player's placing, a wand's, a known player's Deployer) -
     * not this mod's own asking.
     */
    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Placing {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        static void onPlace(BlockEvent.EntityPlaceEvent event) {
            if (Permission.asking()) {
                return;
            }
            UUID owner = ownerOf(event.getEntity());
            LevelAccessor level = event.getLevel();
            if (owner != null && !level.isClientSide() && level.getBlockEntity(event.getPos()) instanceof Owned owned && owned.owner() == null) {
                owned.setOwner(owner);
            }
        }

        /** The answers kept for givers of no owner go with the server: game time starts again in the next world. */
        @SubscribeEvent
        static void onServerStopped(ServerStoppedEvent event) {
            SHARED.clear();
        }

        private Placing() {}
    }
}
