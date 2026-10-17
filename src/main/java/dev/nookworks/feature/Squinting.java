package dev.nookworks.feature;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Squinting into the distance (the user, 2026-10-10: OptiFine's zoom on C, a hand raised to the brow with what it holds
 * still in it, and gentle enough to leave the spyglass its worth). The view and the hand in first person are the
 * client's ({@code client.Squint}); here is what others see - a player squinting is marked by an attachment synced to
 * them and to whoever can see them, so their arm is raised in third person - and the arithmetic the client uses, kept
 * where a test can reach it.
 */
public final class Squinting {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);
    /** Whether a player squints now: not saved, gone with death, synced to them and to whoever tracks them. */
    public static final Supplier<AttachmentType<Boolean>> SQUINTING = ATTACHMENT_TYPES.register("squinting",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    private Squinting() {}

    public static boolean of(Player player) {
        return player.getData(SQUINTING);
    }

    /** A player's word that they squint, or stop (sent as their key goes down and up). */
    public static void set(ServerPlayer player, boolean squinting) {
        if (player.getData(SQUINTING) != squinting) {
            player.setData(SQUINTING, squinting);
        }
    }

    /** Smoothly in and out: {@code t} from 0 to 1, slow at both ends. */
    public static double ease(double t) {
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** What the field of view is divided by at {@code zoom} times, {@code eased} of the way in. */
    public static double fovDivisor(double zoom, double eased) {
        return 1.0 + (zoom - 1.0) * eased;
    }

    /**
     * The mouse-sensitivity setting that turns the view {@code scale} times as fast as {@code sensitivity} does: the game
     * turns by the cube of (setting x 0.6 + 0.2), so the setting goes through the cube root. Below the slider's own range
     * at times (a slow mouse, well zoomed), which the game takes as it is.
     */
    public static double sensitivityFor(double sensitivity, double scale) {
        return (Math.cbrt(scale) * (sensitivity * 0.6 + 0.2) - 0.2) / 0.6;
    }
}
