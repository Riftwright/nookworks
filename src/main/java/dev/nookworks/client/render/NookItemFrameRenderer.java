package dev.nookworks.client.render;

import org.joml.Matrix4f;

import dev.nookworks.Nookworks;
import dev.nookworks.poster.NookItemFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Draws a {@link NookItemFrame} as vanilla draws its item frame - the item or map in it, turned, lit, named - with its own
 * frame (tools/textures/gen_frames.py: vanilla's frame models in dark oak, the brass moulding, vanilla's map frame in
 * brass, each with vanilla's glowing back for a glowing one), put in where vanilla puts its fake block state's model. A
 * glowing one is drawn as vanilla's glowing frame: its item or map at full light, the frame itself never darker than
 * vanilla's glowing frame.
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

    static void registerModels(ModelEvent.RegisterStandalone event) {
        for (int i = 0; i < NAMES.length; i++) {
            register(event, KEYS[i][0], NAMES[i]);
            register(event, KEYS[i][1], NAMES[i] + "_map");
        }
    }

    private static void register(ModelEvent.RegisterStandalone event, StandaloneModelKey<BlockStateModelPart> key, String name) {
        event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/" + name)));
    }

    @Override
    protected int getBlockLightLevel(NookItemFrame entity, BlockPos pos) {
        int light = super.getBlockLightLevel(entity, pos);
        return entity.glow() ? Math.max(GLOW_FRAME_BRIGHTNESS, light) : light;
    }

    @Override
    public void extractRenderState(NookItemFrame entity, ItemFrameRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isGlowFrame = entity.glow();
        if (state.isInvisible) {
            return;
        }
        var key = KEYS[(entity.brass() ? 2 : 0) + (entity.glow() ? 1 : 0)][state.mapId != null ? 1 : 0];
        BlockStateModelPart part = Minecraft.getInstance().getModelManager().getStandaloneModel(key);
        state.frameModel.clear();
        if (part != null) {
            state.frameModel.setupModel(new Matrix4f(), false).add(part);
        }
    }
}
