package dev.nookworks.port.render;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * 26.3's wall banner flag ({@code BannerFlagModel}): the cloth, 20 by 40 pixels, hanging from the bar's top, swaying by
 * its phase (0 to 1, a sway) as 26.3's: a hundredth of a half turn each way about a slight lean back.
 */
public class BannerFlagModel extends Model<Float> {
    /** 26.3's {@code ModelLayers.WALL_BANNER_FLAG}. */
    public static final ModelLayerLocation WALL_LAYER = new ModelLayerLocation(PortIds.id("wall_banner"), "flag");
    private final ModelPart flag;

    public BannerFlagModel(ModelPart root) {
        super(root, RenderType::entitySolid);
        this.flag = root.getChild("flag");
    }

    public static LayerDefinition createWallLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("flag", CubeListBuilder.create().texOffs(0, 0).addBox(-10.0F, 0.0F, -2.0F, 20.0F, 40.0F, 1.0F),
                PartPose.offset(0.0F, -20.5F, 10.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** The flag part itself, as 1.21.1's pattern drawing takes it. */
    public ModelPart flag() {
        return this.flag;
    }

    @Override
    public void setupAnim(Float phase) {
        super.setupAnim(phase);
        this.flag.xRot = (-0.0125F + 0.01F * Mth.cos(Mth.TWO_PI * phase)) * Mth.PI;
    }
}
