package dev.nookworks.client.render;

import java.util.EnumSet;
import java.util.Set;

import dev.nookworks.Nookworks;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.core.Direction;

/**
 * The Repair Box's model: shaped as the vanilla chest is - the same lid on the same hinge, the same latch, so it opens
 * as a chest does - but hollow. Vanilla's chest is a solid block whose top is painted as an opening; this one is a
 * floor and four walls round a well 12 by 12 by 9 pixels, so the gear left in it lies in it, under the liquid
 * experience, whose surface rises as it fills (RepairBoxRenderer draws both). A carrying handle stands proud of the lid,
 * a leather grip a pixel above it on a brass mount at each end, as the latch stands proud of the front; it hangs off
 * the lid, so it swings up with it. A pair's halves leave out the wall where they meet, and share one handle.
 * <p>
 * The sheet is 128 by 64: the lid and latch where vanilla's are, the floor below them, the walls and the handle on the
 * right-hand half (tools/textures/gen_repair_box.py draws it to the same layout).
 */
public final class RepairBoxModel {
    private RepairBoxModel() {}

    public static final MultiblockChestResources<ModelLayerLocation> LAYERS = new MultiblockChestResources<>(
            new ModelLayerLocation(Nookworks.id("repair_box"), "single"),
            new ModelLayerLocation(Nookworks.id("repair_box"), "left"),
            new ModelLayerLocation(Nookworks.id("repair_box"), "right"));

    public static final int SHEET_WIDTH = 128;
    public static final int SHEET_HEIGHT = 64;
    /** The well's floor and the top of its walls, in pixels from the ground. */
    public static final float FLOOR = 1.0F;
    public static final float RIM = 10.0F;

    public static LayerDefinition single() {
        return box(1.0F, 14.0F, 2.0F, 4.0F, 8.0F, new float[] {4.0F, 11.0F}, null);
    }

    /** The left half of a pair: its seam on its west side, at x 0. */
    public static LayerDefinition left() {
        return box(0.0F, 15.0F, 1.0F, 0.0F, 5.0F, new float[] {4.0F}, Direction.WEST);
    }

    /** The right half of a pair: its seam on its east side, at x 16. */
    public static LayerDefinition right() {
        return box(1.0F, 15.0F, 1.0F, 11.0F, 5.0F, new float[] {11.0F}, Direction.EAST);
    }

    /**
     * A box from x {@code x0}, {@code width} wide: a floor, a wall front and back, a wall at each end but the
     * {@code seam}'s, the lid, the latch {@code lock} wide at the middle of the front (or at the seam), and the handle.
     */
    private static LayerDefinition box(float x0, float width, float lock, float grip, float gripLength, float[] mounts,
            Direction seam) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Set<Direction> visible = seam == null ? EnumSet.allOf(Direction.class) : EnumSet.complementOf(EnumSet.of(seam));
        float x1 = x0 + width;
        float h = RIM - FLOOR;
        root.addOrReplaceChild("bottom", CubeListBuilder.create()
                .texOffs(0, 19).addBox(x0, 0.0F, 1.0F, width, FLOOR, 14.0F, visible)
                .texOffs(64, 26).addBox(x0, FLOOR, 14.0F, width, h, 1.0F, visible) // the front wall
                .texOffs(96, 26).addBox(x0, FLOOR, 1.0F, width, h, 1.0F, visible), // the back wall
                PartPose.ZERO);
        if (seam != Direction.WEST) {
            root.addOrReplaceChild("west_wall", CubeListBuilder.create().texOffs(64, 4).addBox(x0, FLOOR, 2.0F, 1.0F, h, 12.0F),
                    PartPose.ZERO);
        }
        if (seam != Direction.EAST) {
            root.addOrReplaceChild("east_wall", CubeListBuilder.create().texOffs(90, 4).addBox(x1 - 1.0F, FLOOR, 2.0F, 1.0F, h, 12.0F),
                    PartPose.ZERO);
        }
        PartDefinition lid = root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 0).addBox(x0, 0.0F, 0.0F, width, 5.0F, 14.0F, visible),
                PartPose.offset(0.0F, 9.0F, 1.0F));
        float lockX = seam == null ? 7.0F : seam == Direction.EAST ? 15.0F : 0.0F;
        root.addOrReplaceChild("lock", CubeListBuilder.create().texOffs(0, 0).addBox(lockX, -2.0F, 14.0F, lock, 4.0F, 1.0F, visible),
                PartPose.offset(0.0F, 9.0F, 1.0F));
        lid.addOrReplaceChild("grip", CubeListBuilder.create().texOffs(64, 0).addBox(grip, 6.0F, 6.0F, gripLength, 1.0F, 2.0F, visible),
                PartPose.ZERO);
        CubeListBuilder posts = CubeListBuilder.create();
        for (float at : mounts) {
            posts.texOffs(84, 0).addBox(at, 5.0F, 6.0F, 1.0F, 1.0F, 2.0F);
        }
        lid.addOrReplaceChild("mounts", posts, PartPose.ZERO);
        return LayerDefinition.create(mesh, SHEET_WIDTH, SHEET_HEIGHT);
    }
}
