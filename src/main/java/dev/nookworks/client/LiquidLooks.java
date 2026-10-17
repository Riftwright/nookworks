package dev.nookworks.client;

import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.oil.OilFluids;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * What the camera sees inside one of our liquids. Without this the game knows only water and lava: under oil or
 * mercury it draws the open air, clear to the horizon. Each liquid gets its own fog, in its colour and as thick as it
 * is - oil a golden murk a few blocks deep, biodiesel darker and closer, mercury opaque at arm's length as lava is,
 * liquid experience a green glow you can see a little way through - and water's faint wobble on the screen.
 */
public final class LiquidLooks {
    /** Vanilla's underwater wobble, by name: the same faint moving shadow on the screen as in water. */
    private static final Identifier WOBBLE = Identifier.withDefaultNamespace("textures/misc/underwater.png");

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new Look(0xD6A834, -1.0F, 7.0F), OilFluids.TYPE.get());
        event.registerFluidType(new Look(0x6E4414, -0.5F, 4.5F), OilFluids.BIODIESEL_TYPE.get());
        event.registerFluidType(new Look(0x8C929E, 0.25F, 1.5F), MercuryFluids.TYPE.get());
        event.registerFluidType(new Look(0x5CBF30, -2.0F, 12.0F), ExperienceFluids.TYPE.get());
    }

    /**
     * One liquid's look from inside: its fog colour (lit as the spot the camera is in, so a pool of oil in a dark cave
     * is dark, not a golden lamp) and the distances the fog starts and closes at, in blocks.
     */
    private record Look(int colour, float start, float end) implements IClientFluidTypeExtensions {
        @Override
        public Identifier getRenderOverlayTexture(Minecraft mc) {
            return WOBBLE;
        }

        @Override
        public void modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount,
                Vector4f fluidFogColor) {
            BlockPos pos = camera.blockPosition();
            float light = Lightmap.getBrightness(level.dimensionType(), level.getMaxLocalRawBrightness(pos));
            float lit = 0.2F + 0.8F * light;
            fluidFogColor.set((this.colour >> 16 & 0xFF) / 255.0F * lit, (this.colour >> 8 & 0xFF) / 255.0F * lit,
                    (this.colour & 0xFF) / 255.0F * lit, 1.0F);
        }

        @Override
        public void modifyFogRender(Camera camera, @Nullable FogEnvironment environment, float renderDistance, float partialTick,
                FogData fogData) {
            // A spectator looks through, as through lava; anyone else is in it.
            float end = camera.entity().isSpectator() ? Math.max(this.end, renderDistance * 0.5F) : this.end;
            fogData.environmentalStart = this.start;
            fogData.environmentalEnd = end;
            fogData.skyEnd = end;
            fogData.cloudEnd = end;
        }
    }

    private LiquidLooks() {}
}
