package dev.nookworks.client;

import java.util.HashMap;
import java.util.Map;

import dev.nookworks.drive.DriveWheelBlock;
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
 * A turning Drive Wheel's small clatter: one sound held on while it turns, as the Motor's hum is ({@link MotorHum}), not
 * a phrase now and then (the user, 2026-11-15, of the phrase it had - a knock and the belt's rub, 「サッサッサッサッ」 - and
 * of what it should be: 「小さなカタカタがずっと鳴ってるイメージ」). Started from the wheel's client tick while it turns and
 * the player is near, it goes as the wheel stops or goes, or the player leaves its world. The machine sound
 * settings' volume is its volume (0, none). The sound is a loop of our own (block.drive_wheel.turn, one file made to
 * loop whole), so it is faded in and out here over {@link #FADE} ticks.
 * <p>
 * Only {@link #MAX} are heard at once, the first come: a drive line is several wheels close together, and the same
 * clatter from each, out of step, is no longer a small one.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = dev.nookworks.Nookworks.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class WheelClatter extends AbstractTickableSoundInstance {
    /** How many wheels are heard at once. */
    static final int MAX = 2;
    /** How near the player a wheel starts to sound, in blocks. */
    private static final double REACH = 16;
    /** Ticks it takes to fade in, and to fade out before it stops. */
    static final int FADE = 4;
    /** Ticks before a wheel whose sound did not start (no channel free) is tried again. */
    private static final int RETRY = 20;
    private static final Map<BlockPos, WheelClatter> PLAYING = new HashMap<>();
    private static final Map<BlockPos, Long> TRIED = new HashMap<>();

    private final Level level;
    private final BlockPos pos;
    /** How far it has faded in, 0 to {@link #FADE}. */
    private int fade;
    /** Fading out, to stop when it is silent. */
    private boolean leaving;

    private WheelClatter(Level level, BlockPos pos) {
        super(ModSounds.DRIVE_WHEEL_TURN.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = level;
        this.pos = pos;
        this.looping = true;
        this.delay = 0;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.volume = 0.0F; // faded in from nothing (canStartSilent)
    }

    /** It turns, and the settings do not keep it quiet. (No oil: a wheel is not among what the Oil Can oils.) */
    private static boolean clatters(BlockState state) {
        return state.getBlock() instanceof DriveWheelBlock && state.getValue(DriveWheelBlock.TURNING)
                && WorkSounds.volume() > 0;
    }

    /** A world the client leaves takes its sounds with it (as {@link MotorHum#onUnload}). */
    @net.neoforged.bus.api.SubscribeEvent
    static void onUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PLAYING.values().removeIf(clatter -> clatter.level == event.getLevel());
            TRIED.clear();
        }
    }

    /** It starts at no volume, to fade in. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** From a wheel's client tick: its clatter started, if it turns and the player is near. */
    public static void keep(Level level, BlockPos pos, BlockState state) {
        if (!clatters(state)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        WheelClatter playing = PLAYING.get(pos);
        if (playing != null && !playing.isStopped() && mc.getSoundManager().isActive(playing)) {
            playing.leaving = false; // turning again while it faded out: it fades back in
            return;
        }
        if (mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
            return;
        }
        long now = level.getGameTime();
        Long tried = TRIED.get(pos);
        if (tried != null && now - tried < RETRY && now >= tried) {
            return; // it did not start a moment ago: not a new sound every tick
        }
        PLAYING.values().removeIf(clatter -> clatter.isStopped() || !mc.getSoundManager().isActive(clatter));
        if (PLAYING.size() >= MAX) {
            return;
        }
        TRIED.keySet().removeIf(other -> !PLAYING.containsKey(other) && !other.equals(pos));
        TRIED.put(pos.immutable(), now);
        WheelClatter clatter = new WheelClatter(level, pos.immutable());
        PLAYING.put(clatter.pos, clatter);
        mc.getSoundManager().play(clatter);
    }

    @Override
    public void tick() {
        if (this.isStopped()) {
            return; // the engine may tick it once more before it lets it go
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != this.level) {
            this.stop(); // its world gone: no fade, nothing is left to hear it in
            PLAYING.remove(this.pos, this);
            return;
        }
        if (!clatters(this.level.getBlockState(this.pos))
                || mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(this.pos)) > (REACH + 8) * (REACH + 8)) {
            this.leaving = true;
        }
        this.fade = this.leaving ? this.fade - 1 : Math.min(FADE, this.fade + 1);
        if (this.leaving && this.fade <= 0) {
            this.stop();
            PLAYING.remove(this.pos, this);
            return;
        }
        this.volume = WorkSounds.volume() * this.fade / FADE; // the setting changed while it sounds, too
    }
}
