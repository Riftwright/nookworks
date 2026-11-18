package dev.nookworks.client;

import java.util.function.Supplier;

import org.joml.Vector3f;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.port.render.FluidModel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * What the camera sees inside one of our liquids. Without this the game knows only water and lava: under oil or
 * mercury it draws the open air, clear to the horizon. Each liquid gets its own fog, in its colour and as thick as it
 * is - oil a golden murk a few blocks deep, biodiesel darker and closer, methanol a clear spirit seen through about as
 * far as water, wood vinegar a clear amber-brown a little closer, mercury opaque at arm's length as lava is, liquid experience a green glow you can see a little way
 * through - and water's faint wobble on the screen.
 * <p>
 * PORT: in 1.21.1 a liquid's still and flowing pictures are its fluid type's client extensions too: each look gives the
 * ones registered for its liquid in {@code NookworksClient.registerFluidModels} ({@link FluidModel#registered}). 1.21.1's
 * fog is set through RenderSystem, and its fog colour is handed back rather than set in place.
 */
public final class LiquidLooks {
    /** Vanilla's underwater wobble, by name: the same faint moving shadow on the screen as in water. */
    private static final ResourceLocation WOBBLE = ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new Look(0xD6A834, -1.0F, 7.0F, OilFluids.SOURCE), OilFluids.TYPE.get());
        event.registerFluidType(new Look(0x6E4414, -0.5F, 4.5F, OilFluids.BIODIESEL), OilFluids.BIODIESEL_TYPE.get());
        event.registerFluidType(new Look(0xC4D2DE, -1.0F, 18.0F, OilFluids.METHANOL), OilFluids.METHANOL_TYPE.get());
        event.registerFluidType(new Look(0x8A5428, -1.0F, 12.0F, OilFluids.WOOD_VINEGAR), OilFluids.WOOD_VINEGAR_TYPE.get());
        event.registerFluidType(new Look(0x8C929E, 0.25F, 1.5F, MercuryFluids.SOURCE), MercuryFluids.TYPE.get());
        event.registerFluidType(new Look(0x5CBF30, -2.0F, 12.0F, ExperienceFluids.SOURCE), ExperienceFluids.TYPE.get());
    }

    /**
     * One liquid's look from inside: its fog colour (lit as the spot the camera is in, so a pool of oil in a dark cave
     * is dark, not a golden lamp) and the distances the fog starts and closes at, in blocks; and its pictures.
     */
    private record Look(int colour, float start, float end, Supplier<? extends Fluid> fluid) implements IClientFluidTypeExtensions {
        @Override
        public ResourceLocation getStillTexture() {
            FluidModel.Unbaked model = FluidModel.registered(this.fluid.get());
            return model == null ? null : model.stillMaterial().texture();
        }

        @Override
        public ResourceLocation getFlowingTexture() {
            FluidModel.Unbaked model = FluidModel.registered(this.fluid.get());
            return model == null ? null : model.flowingMaterial().texture();
        }

        @Override
        public ResourceLocation getRenderOverlayTexture(Minecraft mc) {
            return WOBBLE;
        }

        @Override
        public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount,
                Vector3f fluidFogColor) {
            BlockPos pos = camera.getBlockPosition();
            float light = LightTexture.getBrightness(level.dimensionType(), level.getMaxLocalRawBrightness(pos));
            float lit = 0.2F + 0.8F * light;
            return new Vector3f((this.colour >> 16 & 0xFF) / 255.0F * lit, (this.colour >> 8 & 0xFF) / 255.0F * lit,
                    (this.colour & 0xFF) / 255.0F * lit);
        }

        @Override
        public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance,
                float farDistance, FogShape shape) {
            // A spectator looks through, as through lava; anyone else is in it. The same distances for the sky, as 26.3
            // closes its sky and cloud fog at the end too.
            float end = camera.getEntity().isSpectator() ? Math.max(this.end, renderDistance * 0.5F) : this.end;
            RenderSystem.setShaderFogStart(this.start);
            RenderSystem.setShaderFogEnd(end);
        }
    }

    private LiquidLooks() {}
}
