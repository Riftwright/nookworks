package dev.nookworks.port.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * 26.3's {@code FluidModel}: a liquid's still and flowing pictures and its tint. In 1.21.1 those belong to the liquid's
 * fluid type's client extensions ({@code IClientFluidTypeExtensions}); the mod's own liquids name theirs through
 * {@link RegisterFluidModelsEvent}, which {@code client/LiquidLooks} hands on to their extensions.
 */
public record FluidModel(Material stillMaterial, Material flowingMaterial, @Nullable Material overlayMaterial,
        @Nullable FluidTintSource fluidTintSource) {
    /** The liquids' models, as registered by any mod. */
    static final Map<Fluid, Unbaked> REGISTERED = new ConcurrentHashMap<>();

    public record Unbaked(Material stillMaterial, Material flowingMaterial, @Nullable Material overlayMaterial,
            @Nullable FluidTintSource fluidTintSource) {}

    /**
     * The model registered for {@code fluid}, or null if none was (the game's liquids, and those of mods that do not use
     * the event). A mod's liquids get their pictures from this in their fluid type's client extensions, as ours do
     * ({@code client/LiquidLooks}); the event is posted before any mod's {@code RegisterClientExtensionsEvent} listener
     * runs but ours.
     */
    public static @Nullable Unbaked registered(Fluid fluid) {
        return REGISTERED.get(fluid);
    }

    /** {@code fluid}'s look: as registered, or read off its client extensions. */
    static FluidModel of(FluidState fluid) {
        Unbaked own = REGISTERED.get(fluid.getType());
        if (own != null) {
            return new FluidModel(own.stillMaterial(), own.flowingMaterial(), own.overlayMaterial(), own.fluidTintSource());
        }
        IClientFluidTypeExtensions look = IClientFluidTypeExtensions.of(fluid);
        // the colour held, with no world: what a liquid's extensions give without a place (water's own blue)
        int tint = look.getTintColor();
        return new FluidModel(new Material(TextureAtlas.LOCATION_BLOCKS, look.getStillTexture(fluid, null, null)),
                new Material(TextureAtlas.LOCATION_BLOCKS, look.getFlowingTexture(fluid, null, null)),
                look.getOverlayTexture() == null ? null : new Material(TextureAtlas.LOCATION_BLOCKS, look.getOverlayTexture()),
                tint == -1 ? null : state -> tint);
    }
}
