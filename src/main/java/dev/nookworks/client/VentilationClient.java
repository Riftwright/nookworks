package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.ventilation.Ventilation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Draws the smoke coming out where Ventilation sends it: every tick a little, from each chimney pot, duct end and fan
 * back that is smoking now - a slow column of it out of a chimney, a thin stream elsewhere - so it keeps coming while
 * the cooking or the fire goes on, as smoke does.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class VentilationClient {
    private VentilationClient() {}

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null || Minecraft.getInstance().isPaused() || !level.tickRateManager().runsNormally()) { // /tick freeze: queued, never ticked
            return;
        }
        RandomSource random = level.getRandom();
        for (Ventilation.Smoking out : Ventilation.smokingNow(level)) {
            var at = out.at();
            if (out.chimney()) {
                if (random.nextFloat() < 0.11F) {
                    level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, at.x + random.nextGaussian() * 0.06, at.y,
                            at.z + random.nextGaussian() * 0.06, 0.0, 0.06, 0.0);
                }
            } else if (random.nextFloat() < 0.3F) {
                level.addParticle(ParticleTypes.SMOKE, at.x + random.nextGaussian() * 0.08, at.y, at.z + random.nextGaussian() * 0.08, 0.0, 0.04, 0.0);
            }
        }
    }
}
