package dev.nookworks.machine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * A machine's stop heard (the user, 2026-11-10, of the sound session's survey: 「できそうなことはぜんぶやっていいよ」 - the
 * lamp showed a machine stopped, and nothing was heard): once a machine that was running has stood {@link #SETTLE} ticks
 * stopped for a reason to see to ({@link MachineStatus#alerts}), it gives {@code block.machine.stop} once, and not again
 * for {@link #COOLDOWN} ticks; when a line stops all at once, one cue in a place says it (none within {@link #NEAR} blocks
 * and two seconds of another). Each player may turn the cue off (a client setting). And with the server's setting on - off
 * by default, as it would call the Warden - a machine at work makes a vibration every {@link #VIBRATION_EVERY} ticks, for
 * sculk sensors.
 * <p>
 * Every machine built on {@link MachineBlockEntity}, ours and an add-on's, is watched from its onLoad, a
 * {@link MachineInfo} or not (an add-on's machine with a screen of its own: nookTechnology's, whose stops went unheard
 * and rang no bell - its review r1); a {@link MachineInfo} on some other base calls {@link #track} from its own. Their
 * status is read as {@link #statusOf} says. They are looked at from the level's tick every {@link #LOOK_EVERY} ticks:
 * nothing is added to a machine's own tick.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class MachineAlerts {
    /** How long a machine stands stopped before the cue: a stall that clears itself at once is none. */
    public static final int SETTLE = 40;
    /** The least time between two cues of one machine. */
    public static final int COOLDOWN = 600;
    /** No cue within this many blocks of one given in the last {@link #SETTLE} ticks. */
    public static final int NEAR = 8;
    public static final int VIBRATION_EVERY = 40;
    public static final int LOOK_EVERY = 10;
    private static final Map<Level, Map<BlockEntity, Watch>> WATCHED = new WeakHashMap<>();
    private static final Map<Level, Deque<Cue>> RECENT = new WeakHashMap<>();

    private record Cue(BlockPos pos, long at) {}

    /** What one machine has been doing, as far as the cue needs it. */
    public static final class Watch {
        boolean ran;
        long stoppedAt = -1;
        long cuedAt = Long.MIN_VALUE / 2;
    }

    private MachineAlerts() {}

    /**
     * Whether {@code entity} is a machine whose stop is heard and rung for: a {@link MachineInfo}, or any block entity
     * built on {@link MachineBlockEntity}. The Machine Bell gives such a block no signal.
     */
    public static boolean isMachine(@Nullable BlockEntity entity) {
        return entity instanceof MachineInfo || entity instanceof MachineBlockEntity;
    }

    /**
     * What {@code entity} says it is doing, as its screen says it: a {@link MachineInfo}'s {@code guiStatus()}, or a
     * {@link MachineBlockEntity}'s (which an add-on's machine with its own screen overrides); null for anything else.
     */
    public static @Nullable MachineStatus statusOf(@Nullable BlockEntity entity) {
        if (entity instanceof MachineInfo info) {
            return info.guiStatus();
        }
        return entity instanceof MachineBlockEntity machine ? machine.guiStatus() : null;
    }

    /**
     * Whether {@code entity} is a machine stopped for a reason to see to: its status alerts
     * ({@link MachineBlockEntity#alerts}, which a machine may narrow; else {@link MachineStatus#alerts}).
     */
    public static boolean stopped(@Nullable BlockEntity entity) {
        MachineStatus status = statusOf(entity);
        return status != null && alerts(entity, status);
    }

    private static boolean alerts(@Nullable BlockEntity entity, MachineStatus status) {
        return entity instanceof MachineBlockEntity machine ? machine.alerts(status) : status.alerts();
    }

    /** Watches {@code machine} ({@link #isMachine}) on the server: from its block entity's onLoad. */
    public static void track(BlockEntity machine) {
        if (isMachine(machine) && machine.getLevel() instanceof ServerLevel level) {
            WATCHED.computeIfAbsent(level, l -> new WeakHashMap<>()).putIfAbsent(machine, new Watch());
        }
    }

    /** Whether {@code machine} is watched. Public for tests. */
    public static boolean watched(BlockEntity machine) {
        Map<BlockEntity, Watch> watched = machine.getLevel() == null ? null : WATCHED.get(machine.getLevel());
        return watched != null && watched.containsKey(machine);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % LOOK_EVERY != 0) {
            return;
        }
        Map<BlockEntity, Watch> watched = WATCHED.get(level);
        if (watched == null || watched.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean vibrations = NookworksConfig.SPEC.isLoaded() && NookworksConfig.MACHINE_VIBRATIONS.get();
        for (Iterator<Map.Entry<BlockEntity, Watch>> it = watched.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockEntity, Watch> entry = it.next();
            BlockEntity machine = entry.getKey();
            if (machine.isRemoved() || machine.getLevel() != level) {
                it.remove();
            } else if (level.isLoaded(machine.getBlockPos()) && statusOf(machine) instanceof MachineStatus status) {
                look(level, machine.getBlockPos(), status, alerts(machine, status), entry.getValue(), now, vibrations);
            }
        }
    }

    /**
     * One look at a machine at {@code pos} whose status is {@code status} at game time {@code now}: whether it gave its
     * cue. Public for tests.
     */
    public static boolean look(ServerLevel level, BlockPos pos, MachineStatus status, Watch watch, long now, boolean vibrations) {
        return look(level, pos, status, status.alerts(), watch, now, vibrations);
    }

    /** As above, {@code alerts} saying whether the status is a stop to see to for this machine. */
    static boolean look(ServerLevel level, BlockPos pos, MachineStatus status, boolean alerts, Watch watch, long now, boolean vibrations) {
        if (status == MachineStatus.RUNNING) {
            watch.ran = true;
            watch.stoppedAt = -1;
            if (vibrations && now % VIBRATION_EVERY == 0) {
                level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(level.getBlockState(pos)));
            }
            return false;
        }
        if (!alerts) { // idle, held by redstone, full: as meant, nothing to see to
            watch.ran = false;
            watch.stoppedAt = -1;
            return false;
        }
        if (!watch.ran) {
            return false; // it was not running: a machine set down short of something says so on its screen
        }
        if (watch.stoppedAt < 0) {
            watch.stoppedAt = now;
            return false;
        }
        if (now - watch.stoppedAt < SETTLE) {
            return false;
        }
        watch.ran = false;
        watch.stoppedAt = -1;
        if (now - watch.cuedAt < COOLDOWN || heardNear(level, pos, now)) {
            return false;
        }
        watch.cuedAt = now;
        level.playSound(null, pos, ModSounds.MACHINE_STOP.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Whether a cue was given near {@code pos} lately; if not, this one is noted. */
    private static boolean heardNear(ServerLevel level, BlockPos pos, long now) {
        Deque<Cue> recent = RECENT.computeIfAbsent(level, l -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst().at() > SETTLE) {
            recent.pollFirst();
        }
        for (Cue cue : recent) {
            if (cue.pos().closerThan(pos, NEAR)) {
                return true;
            }
        }
        recent.addLast(new Cue(pos.immutable(), now));
        return false;
    }

    /** Forgets the cues lately given, for tests. */
    public static void forgetRecent() {
        RECENT.clear();
    }
}
