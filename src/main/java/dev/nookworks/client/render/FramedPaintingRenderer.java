package dev.nookworks.client.render;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.port.render.FullBrightBuffers;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.util.LightCoordsUtil;
import dev.nookworks.poster.PaintingFrames;
import dev.nookworks.poster.PictureFrames;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's painting renderer, with a frame round a framed painting ({@link PaintingFrames}): the painting drawn as
 * vanilla draws it, then the rim on its face ({@link FrameRims}, standing on the board's face as round a poster it stands
 * on the wall). In a glowing frame the painting and its frame are drawn at full light, as a poster and its frame are:
 * vanilla lights the board's edges as its face, and a frame left dark showed a bright line of board round it at night.
 * <p>
 * PORT: 26.3's renderer fills a render state and lets a subclass change the light per block; 1.21.1's draws at once,
 * lighting each block itself. Here vanilla's draws the painting (through buffers that make it full light, in a glowing
 * frame), and the rim's light is worked out as 26.3 works out the painting's.
 */
public class FramedPaintingRenderer extends PaintingRenderer {
    /** A painting's board: its face a thirty-second out from the middle, as vanilla draws it. */
    private static final float FACE = -0.03125F;
    private static final int FULL_BRIGHT = 0xF000F0;

    public FramedPaintingRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** What the frame is drawn from. */
    public static class State {
        /** The block texture the frame's rim is cut from; null, no frame. */
        public @Nullable ResourceLocation rim;
        public boolean moulded;
        public boolean glow;
        /** The light the rim takes, block by block: the painting's own. */
        public int[] rimLight = new int[0];
    }

    private static State extract(Painting entity, PaintingVariant variant) {
        State framed = new State();
        ItemStack frame = PaintingFrames.frame(entity);
        PictureFrames.Look look = frame.isEmpty() ? null : PictureFrames.look(frame);
        framed.rim = look == null ? null : look.rim();
        framed.moulded = look != null && look.moulded();
        framed.glow = look != null && look.glow();
        if (look != null) {
            framed.rimLight = lightPerBlock(entity, variant);
            if (look.glow()) {
                Arrays.fill(framed.rimLight, FULL_BRIGHT);
            }
        }
        return framed;
    }

    /** The light at each block of the painting, from its bottom left, column + row x width, as 26.3 samples it. */
    private static int[] lightPerBlock(Painting entity, PaintingVariant variant) {
        int width = variant.width();
        int height = variant.height();
        int[] light = new int[width * height];
        Direction direction = entity.getDirection();
        float offsetX = -width / 2.0F;
        float offsetY = -height / 2.0F;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                float across = column + offsetX + 0.5F;
                int x = entity.getBlockX();
                int y = Mth.floor(entity.getY() + row + offsetY + 0.5F);
                int z = entity.getBlockZ();
                switch (direction) {
                    case NORTH -> x = Mth.floor(entity.getX() + across);
                    case WEST -> z = Mth.floor(entity.getZ() - across);
                    case SOUTH -> x = Mth.floor(entity.getX() - across);
                    case EAST -> z = Mth.floor(entity.getZ() + across);
                    default -> { }
                }
                light[column + row * width] = LightCoordsUtil.getLightCoords(entity.level(), new BlockPos(x, y, z));
            }
        }
        return light;
    }

    @Override
    public void render(Painting entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        PaintingVariant variant = entity.getVariant().value();
        State framed = extract(entity, variant);
        super.render(entity, yaw, partialTicks, poseStack, framed.glow ? FullBrightBuffers.wrap(buffers) : buffers, light);
        if (framed.rim == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - entity.getDirection().get2DDataValue() * 90));
        FrameRims.submit(poseStack, new SubmitNodeCollector(buffers), framed.rim, framed.moulded, variant.width(), variant.height(),
                framed.rimLight, FACE);
        poseStack.popPose();
    }
}
