package dev.nookworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.port.render.BlockStateModelPart;
import dev.nookworks.port.render.FullBrightBuffers;
import dev.nookworks.port.render.PortModelManager;
import dev.nookworks.port.render.SimpleUnbakedStandaloneModel;
import dev.nookworks.port.render.StandaloneModelKey;
import dev.nookworks.poster.NookItemFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Draws a {@link NookItemFrame} as vanilla draws its item frame - the item or map in it, turned, lit, named - with its own
 * frame (tools/textures/gen_frames.py: vanilla's frame models in dark oak, the brass moulding, vanilla's map frame in
 * brass, each with vanilla's glowing back for a glowing one), put in where vanilla puts its fake block state's model. A
 * glowing one is drawn as vanilla's glowing frame: its item or map at full light, the frame itself never darker than
 * vanilla's glowing frame.
 * <p>
 * PORT: 26.3 swaps the frame's model in the render state; 1.21.1's renderer draws vanilla's frame model at once, into the
 * solid block sheet. So vanilla's renderer is handed buffers that drop that render type (nothing else of an item frame
 * is drawn in it: an item's block model goes to the cut-out sheet), and this draws its own frame model where vanilla's
 * goes. A glowing one's item or map is drawn through buffers that make it full light, as vanilla does for its own
 * glowing frame alone.
 */
public class NookItemFrameRenderer extends ItemFrameRenderer<NookItemFrame> {
    private static final String[] NAMES = {"dark_oak_item_frame", "dark_oak_glow_item_frame", "brass_item_frame", "brass_glow_item_frame"};
    /** By brass * 2 + glow, then plain and map. */
    @SuppressWarnings("unchecked")
    private static final StandaloneModelKey<BlockStateModelPart>[][] KEYS = new StandaloneModelKey[NAMES.length][2];

    static {
        for (int i = 0; i < NAMES.length; i++) {
            KEYS[i][0] = key(NAMES[i]);
            KEYS[i][1] = key(NAMES[i] + "_map");
        }
    }

    public NookItemFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> Nookworks.MODID + ":" + name);
    }

    static void registerModels(dev.nookworks.port.render.ModelEvent.RegisterStandalone event) {
        for (int i = 0; i < NAMES.length; i++) {
            register(event, KEYS[i][0], NAMES[i]);
            register(event, KEYS[i][1], NAMES[i] + "_map");
        }
    }

    private static void register(dev.nookworks.port.render.ModelEvent.RegisterStandalone event, StandaloneModelKey<BlockStateModelPart> key, String name) {
        event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/" + name)));
    }

    @Override
    protected int getBlockLightLevel(NookItemFrame entity, BlockPos pos) {
        int light = super.getBlockLightLevel(entity, pos);
        return entity.glow() ? Math.max(GLOW_FRAME_BRIGHTNESS, light) : light;
    }

    @Override
    public void render(NookItemFrame entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        // vanilla's item, map and name, without vanilla's frame; a glowing one's at full light
        MultiBufferSource withoutFrame = type -> type == dev.nookworks.port.render.Sheets.solidBlockSheet() ? NOTHING : buffers.getBuffer(type);
        super.render(entity, yaw, partialTicks, poseStack, entity.glow() ? FullBrightBuffers.wrap(withoutFrame) : withoutFrame, light);
        if (entity.isInvisible()) {
            return;
        }
        // a map's frame where 26.3 has a map id to show, as its render state's mapId
        boolean map = entity.getFramedMapId(entity.getItem()) != null;
        var key = KEYS[(entity.brass() ? 2 : 0) + (entity.glow() ? 1 : 0)][map ? 1 : 0];
        BlockStateModelPart part = PortModelManager.of(Minecraft.getInstance().getModelManager()).getStandaloneModel(key);
        if (part == null) {
            return;
        }
        // where vanilla puts its frame: on the wall it hangs on, turned as it faces
        poseStack.pushPose();
        Direction direction = entity.getDirection();
        Vec3 offset = this.getRenderOffset(entity, partialTicks);
        poseStack.translate(-offset.x(), -offset.y(), -offset.z());
        poseStack.translate(direction.getStepX() * 0.46875, direction.getStepY() * 0.46875, direction.getStepZ() * 0.46875);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(poseStack.last(),
                buffers.getBuffer(dev.nookworks.port.render.Sheets.cutoutBlockSheet()), null, part.model(), 1.0F, 1.0F, 1.0F, light,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /** Takes vertices and keeps none: vanilla's own frame, left out. */
    private static final VertexConsumer NOTHING = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };
}
