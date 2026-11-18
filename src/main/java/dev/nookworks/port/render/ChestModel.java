package dev.nookworks.port.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

/**
 * 26.3's {@code ChestModel} (1.21.1 keeps a chest's parts in its renderer): a model with a {@code lid} and a
 * {@code lock}, both swung up about the hinge by how far open it is, 0 to 1, a quarter turn at most. Drawn on one side
 * only, cut out, as 26.3's.
 */
public class ChestModel extends Model<Float> {
    private final ModelPart lid;
    private final ModelPart lock;

    public ChestModel(ModelPart root) {
        super(root, RenderType::entityCutout);
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    @Override
    public void setupAnim(Float openness) {
        super.setupAnim(openness);
        this.lid.xRot = -(openness * (float) (Math.PI / 2));
        this.lock.xRot = this.lid.xRot;
    }
}
