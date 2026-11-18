package dev.nookworks.port.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Unit;

/**
 * 26.3's wall banner bar ({@code BannerModel}): the bar alone, 20 by 2 by 2 pixels, its top 20.5 pixels down and its
 * back 9.5 pixels out, as 26.3 lays out a wall banner. 1.21.1 has one banner model with the flag, pole and bar in other
 * places, so the wall banner's own is registered as a layer of ours ({@link #WALL_LAYER}; {@link PortClientEvents}).
 */
public class BannerModel extends Model<Unit> {
    public static final int BANNER_WIDTH = 20;
    public static final int BANNER_HEIGHT = 40;
    /** 26.3's {@code ModelLayers.WALL_BANNER}. */
    public static final ModelLayerLocation WALL_LAYER = new ModelLayerLocation(PortIds.id("wall_banner"), "main");

    public BannerModel(ModelPart root) {
        super(root, RenderType::entitySolid);
    }

    public static LayerDefinition createWallLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("bar", CubeListBuilder.create().texOffs(0, 42).addBox(-10.0F, -20.5F, 9.5F, 20.0F, 2.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
