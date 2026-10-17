package dev.nookworks.spawner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.machine.MachineTuning;
import net.minecraft.util.Mth;

/**
 * What a Spawner Controller imposes on its linked spawners.
 *
 * @param enabled     OFF stops the spawner completely
 * @param interval    ticks between spawn attempts (fixed, instead of vanilla's random 10-40 s)
 * @param count       spawn attempts per cycle
 * @param maxNearby   stop while this many of the mob are already around the spawner
 * @param anyLight    skip the mob's own spawn rules (light level, ground), only room is needed
 * @param needsPlayer vanilla behaviour: only run with a player within 16 blocks
 */
public record SpawnerSettings(boolean enabled, int interval, int count, int maxNearby, boolean anyLight, boolean needsPlayer) {
    public static final int MIN_INTERVAL = 20;
    public static final int MAX_INTERVAL = 20 * 120;
    public static final int MAX_COUNT = 16;
    public static final int MAX_NEARBY = 64;

    public static final SpawnerSettings DEFAULT = new SpawnerSettings(true, 20 * 10, 4, 6, false, true);
    /** FE a tick a spawner costs at the default settings, and the least it costs running at all. */
    public static final int BASE_FE_PER_TICK = 20;

    public static final Codec<SpawnerSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("enabled").forGetter(SpawnerSettings::enabled),
            Codec.INT.fieldOf("interval").forGetter(SpawnerSettings::interval),
            Codec.INT.fieldOf("count").forGetter(SpawnerSettings::count),
            Codec.INT.fieldOf("max_nearby").forGetter(SpawnerSettings::maxNearby),
            Codec.BOOL.fieldOf("any_light").forGetter(SpawnerSettings::anyLight),
            Codec.BOOL.fieldOf("needs_player").forGetter(SpawnerSettings::needsPlayer)).apply(i, SpawnerSettings::new));

    public SpawnerSettings {
        interval = Mth.clamp(interval, MIN_INTERVAL, MAX_INTERVAL);
        count = Mth.clamp(count, 1, MAX_COUNT);
        maxNearby = Mth.clamp(maxNearby, 1, MAX_NEARBY);
    }

    /**
     * FE a tick one spawner costs under these settings. The defaults (four every ten seconds, the mob's own rules, a
     * player near) cost {@link #BASE_FE_PER_TICK}, about 1,000 FE a mob; more at a time or more often costs as much
     * more, so a mob costs the same whichever way it is asked for. Ignoring the mob's rules doubles it, and so does
     * running with nobody near - a spawner working anywhere, any time, pays for it. It never costs less than the base
     * running; switched off it costs nothing.
     */
    public int energyPerTick() {
        if (!this.enabled) {
            return 0;
        }
        double perMob = BASE_FE_PER_TICK * DEFAULT.interval() / (double) DEFAULT.count();
        double cost = perMob * this.count / this.interval * (this.anyLight ? 2 : 1) * (this.needsPlayer ? 1 : 2);
        return MachineTuning.energy(Math.max(BASE_FE_PER_TICK, (int) Math.round(cost)));
    }

    /** Interval steps: one second below 10 s, five seconds above. */
    public SpawnerSettings stepInterval(int dir) {
        int seconds = this.interval / 20;
        int step = dir > 0 ? (seconds >= 10 ? 5 : 1) : (seconds > 10 ? 5 : 1);
        return this.withInterval((seconds + dir * step) * 20);
    }

    public SpawnerSettings withEnabled(boolean v) {
        return new SpawnerSettings(v, this.interval, this.count, this.maxNearby, this.anyLight, this.needsPlayer);
    }

    public SpawnerSettings withInterval(int v) {
        return new SpawnerSettings(this.enabled, v, this.count, this.maxNearby, this.anyLight, this.needsPlayer);
    }

    public SpawnerSettings withCount(int v) {
        return new SpawnerSettings(this.enabled, this.interval, v, this.maxNearby, this.anyLight, this.needsPlayer);
    }

    public SpawnerSettings withMaxNearby(int v) {
        return new SpawnerSettings(this.enabled, this.interval, this.count, v, this.anyLight, this.needsPlayer);
    }

    public SpawnerSettings withAnyLight(boolean v) {
        return new SpawnerSettings(this.enabled, this.interval, this.count, this.maxNearby, v, this.needsPlayer);
    }

    public SpawnerSettings withNeedsPlayer(boolean v) {
        return new SpawnerSettings(this.enabled, this.interval, this.count, this.maxNearby, this.anyLight, v);
    }
}
