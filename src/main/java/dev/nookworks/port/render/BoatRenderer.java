package dev.nookworks.port.render;

import com.mojang.datafixers.util.Pair;

import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.ChestBoatModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * 26.3's {@code BoatRenderer(context, layer)}: vanilla's boat drawn from a model layer of one's own, its texture named
 * after the layer ({@code ns:boat/x} draws {@code ns:textures/entity/boat/x.png}; {@code ns:chest_boat/x}, a chest boat,
 * {@code textures/entity/chest_boat/x.png}). 1.21.1's renderer picks its model and texture by the boat's vanilla type;
 * this one always draws its own.
 */
public class BoatRenderer extends net.minecraft.client.renderer.entity.BoatRenderer {
    private final Pair<ResourceLocation, ListModel<Boat>> look;

    public BoatRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer) {
        this(context, layer, layer.getModel().getPath().startsWith("chest_boat/"));
    }

    private BoatRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, boolean chest) {
        super(context, chest);
        ResourceLocation model = layer.getModel();
        ListModel<Boat> boat = chest ? new ChestBoatModel(context.bakeLayer(layer)) : new BoatModel(context.bakeLayer(layer));
        this.look = Pair.of(model.withPrefix("textures/entity/").withSuffix(".png"), boat);
    }

    @Override
    public Pair<ResourceLocation, ListModel<Boat>> getModelWithLocation(Boat boat) {
        return this.look;
    }
}
