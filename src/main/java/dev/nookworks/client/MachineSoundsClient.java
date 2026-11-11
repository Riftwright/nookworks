package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * A machine's stop cue, which the server sends to everyone near (dev.nookworks.machine.MachineAlerts), left unheard by a
 * player who has turned it off in their own settings ({@link NookworksClientConfig#MACHINE_STOP_CUE}).
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class MachineSoundsClient {
    private MachineSoundsClient() {}

    @SubscribeEvent
    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound != null && !wanted(sound)) {
            event.setSound(null);
        }
    }

    /** Whether {@code sound} is to be heard by this player's settings. */
    static boolean wanted(SoundInstance sound) {
        return !sound.getIdentifier().equals(ModSounds.MACHINE_STOP.get().location()) || !NookworksClientConfig.SPEC.isLoaded()
                || NookworksClientConfig.MACHINE_STOP_CUE.get();
    }
}
