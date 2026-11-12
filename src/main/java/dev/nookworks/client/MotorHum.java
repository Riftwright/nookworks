package dev.nookworks.client;

import java.util.HashMap;
import java.util.Map;

import dev.nookworks.block.MotorBlock;
import dev.nookworks.feature.Lubrication;
import dev.nookworks.machine.WorkSounds;
import dev.nookworks.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A running Motor's hum: one sound held on while it runs, not a sound now and then (the user, 2026-11-12: 「ずっと動いてる
 * のになったり止まったりしてるのも変」). Started from the Motor's client tick when the player is near enough to hear it,
 * it stops as the Motor stops, is oiled (the Oil Can quiets it, as before), goes, or the player leaves its world. The
 * machine sound settings' volume is its volume (0, none); no more than {@link #MAX} play at once, the nearest first come.
 * The sound is a loop of our own the user picked by ear (block.motor.run, one file, 3 s, made to loop whole): steady hum
 * only, no attack and no tail, so it is faded in and out here over {@link #FADE} ticks (a hum started or cut hard
 * clicks), at one pitch, the loudness theirs in sounds.json times the work-sound setting.
 */
public final class MotorHum extends AbstractTickableSoundInstance {
    /** How many Motors are heard humming at once. */
    static final int MAX = 4;
    /** How near the player a Motor starts its hum, in blocks. */
    private static final double REACH = 20;
    private static final Map<BlockPos, MotorHum> PLAYING = new HashMap<>();
    /** Ticks it takes to fade in, and to fade out before it stops. */
    static final int FADE = 4;

    private final Level level;
    private final BlockPos pos;
    /** How far it has faded in, 0 to {@link #FADE}. */
    private int fade;
    /** Fading out, to stop when it is silent. */
    private boolean leaving;

    private MotorHum(Level level, BlockPos pos) {
        super(ModSounds.MOTOR_RUN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = level;
        this.pos = pos;
        this.looping = true;
        this.delay = 0;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.volume = 0.0F; // faded in from nothing (canStartSilent)
    }

    /** It starts at no volume, to fade in. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** From a Motor's client tick: its hum started, if it runs, is not oiled, and the player is near. */
    public static void keep(Level level, BlockPos pos, BlockState state) {
        if (!state.getValue(MotorBlock.POWERED) || WorkSounds.volume() <= 0 || Lubrication.isLubricated(level, pos)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MotorHum playing = PLAYING.get(pos);
        if (playing != null && !playing.isStopped() && mc.getSoundManager().isActive(playing)) {
            playing.leaving = false; // running again while it faded out: it fades back in
            return;
        }
        if (mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
            return;
        }
        PLAYING.values().removeIf(hum -> hum.isStopped() || !mc.getSoundManager().isActive(hum));
        if (PLAYING.size() >= MAX) {
            return;
        }
        MotorHum hum = new MotorHum(level, pos.immutable());
        PLAYING.put(hum.pos, hum);
        mc.getSoundManager().play(hum);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = this.level.getBlockState(this.pos);
        if (mc.level != this.level) {
            this.stop(); // its world gone: no fade, nothing is left to hear it in
            PLAYING.remove(this.pos, this);
            return;
        }
        if (!(state.getBlock() instanceof MotorBlock) || !state.getValue(MotorBlock.POWERED)
                || Lubrication.isLubricated(this.level, this.pos) || WorkSounds.volume() <= 0
                || mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(this.pos)) > (REACH + 8) * (REACH + 8)) {
            this.leaving = true;
        }
        this.fade = this.leaving ? this.fade - 1 : Math.min(FADE, this.fade + 1);
        if (this.leaving && this.fade <= 0) {
            this.stop();
            PLAYING.remove(this.pos, this);
            return;
        }
        this.volume = WorkSounds.volume() * this.fade / FADE; // the setting changed while it hums, too
    }
}
