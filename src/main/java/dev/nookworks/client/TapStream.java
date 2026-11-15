package dev.nookworks.client;

import java.util.HashMap;
import java.util.Map;

import dev.nookworks.cooking.KitchenTapBlock;
import dev.nookworks.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * A Kitchen Tap's stream, heard while it pours: one sound held on, as the Motor's hum is ({@link MotorHum}), not a cue
 * now and then - a pour that lasts two seconds a bucket would be cut into pieces by one. Started from the tap's client
 * tick while its stream is drawn ({@code POURING}, open, something poured) and the player is near, it goes as the
 * stream goes. The sound is a loop of our own (block.kitchen_tap.pour, one file made to loop), with no start and no end
 * of its own, so it is faded in and out here; a thick liquid (oil) runs a little lower than water. No more than
 * {@link #MAX} are heard at once, the first come.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = dev.nookworks.Nookworks.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class TapStream extends AbstractTickableSoundInstance {
    /** How many taps are heard running at once. */
    static final int MAX = 4;
    /** How near the player a tap's stream starts to sound, in blocks: as far as a sound of its volume carries. */
    private static final double REACH = 16;
    /** Ticks it takes to come on, and to die away as the stream stops. */
    static final int FADE_IN = 3, FADE_OUT = 6;
    /** Ticks before a tap whose sound did not start (sounds off, no channel free) is tried again. */
    private static final int RETRY = 20;
    /** A liquid thicker than this (water is 1000) is heard a little lower. */
    private static final int THICK = 1500;
    private static final Map<BlockPos, TapStream> PLAYING = new HashMap<>();
    private static final Map<BlockPos, Long> TRIED = new HashMap<>();

    private final Level level;
    private final BlockPos pos;
    /** How far it has come on, 0 to 1. */
    private float on;
    /** Dying away, to stop when it is silent. */
    private boolean leaving;

    private TapStream(Level level, BlockPos pos, Fluid fluid) {
        super(ModSounds.KITCHEN_TAP_POUR.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.level = level;
        this.pos = pos;
        this.looping = true;
        this.delay = 0;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.1; // where the stream lands, more than where it leaves
        this.z = pos.getZ() + 0.5;
        this.volume = 0.0F; // faded in from nothing (canStartSilent)
        this.pitch = pitchOf(fluid);
    }

    /** Water and what runs like it at the pitch the loop is made at; oil and thicker a little lower. */
    static float pitchOf(Fluid fluid) {
        return fluid.getFluidType().getViscosity() > THICK ? 0.85F : 1.0F;
    }

    /** The stream is drawn: the tap is open and something has just run through it. */
    private static boolean pours(BlockState state) {
        return state.getBlock() instanceof KitchenTapBlock && state.getValue(KitchenTapBlock.POURING) && state.getValue(KitchenTapBlock.OPEN);
    }

    /** A world the client leaves takes its streams with it (as {@link MotorHum#onUnload}). */
    @net.neoforged.bus.api.SubscribeEvent
    static void onUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PLAYING.values().removeIf(stream -> stream.level == event.getLevel());
            TRIED.clear();
        }
    }

    /** It starts at no volume, to fade in. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** From a tap's client tick: its stream's sound started, if it pours and the player is near. */
    public static void keep(Level level, BlockPos pos, BlockState state, Fluid fluid) {
        if (!pours(state) || fluid == Fluids.EMPTY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        TapStream playing = PLAYING.get(pos);
        if (playing != null && !playing.isStopped() && mc.getSoundManager().isActive(playing)) {
            playing.leaving = false; // pouring again while it died away: it comes back
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
        PLAYING.values().removeIf(stream -> stream.isStopped() || !mc.getSoundManager().isActive(stream));
        if (PLAYING.size() >= MAX) {
            return;
        }
        TRIED.keySet().removeIf(other -> !PLAYING.containsKey(other) && !other.equals(pos));
        TRIED.put(pos.immutable(), now);
        TapStream stream = new TapStream(level, pos.immutable(), fluid);
        PLAYING.put(stream.pos, stream);
        mc.getSoundManager().play(stream);
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
        if (!pours(this.level.getBlockState(this.pos))
                || mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(this.pos)) > (REACH + 8) * (REACH + 8)) {
            this.leaving = true;
        }
        this.on = this.leaving ? this.on - 1.0F / FADE_OUT : Math.min(1.0F, this.on + 1.0F / FADE_IN);
        if (this.leaving && this.on <= 0) {
            this.stop();
            PLAYING.remove(this.pos, this);
            return;
        }
        this.volume = this.on;
    }
}
