package dev.nookworks.port.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's {@code BlockModelResolver}: a block state's model for drawing away from the world (a lantern hung on a rope).
 * One for everyone ({@link #get}); a scripted edit points the mod's {@code context.blockModelResolver()} at it.
 */
public final class BlockModelResolver {
    private static final BlockModelResolver INSTANCE = new BlockModelResolver();

    private BlockModelResolver() {}

    public static BlockModelResolver get() {
        return INSTANCE;
    }

    /**
     * The state's model, its tints as the block colours give them with no world, see-through if its block is drawn so,
     * and its own light: full for a block drawn emissive, else what it gives off.
     */
    public void update(BlockModelRenderState renderState, BlockState blockState, BlockDisplayContext displayContext) {
        renderState.clear();
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(blockState);
        BlockStateModelPart part = new BlockStateModelPart(model, blockState);
        renderState.part = part;
        int most = -1;
        for (BakedQuad quad : part.allQuads()) {
            most = Math.max(most, quad.getTintIndex());
        }
        if (most >= 0) {
            int[] tints = new int[most + 1];
            for (int i = 0; i <= most; i++) {
                tints[i] = 0xFF000000 | minecraft.getBlockColors().getColor(blockState, null, null, i);
            }
            renderState.tints = tints;
        }
        renderState.translucent = ItemBlockRenderTypes.getChunkRenderType(blockState) == RenderType.translucent()
                || part.translucent();
        renderState.blockLightCoords = blockState.emissiveRendering(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                net.minecraft.core.BlockPos.ZERO) ? dev.nookworks.port.util.LightCoordsUtil.FULL_BRIGHT
                : dev.nookworks.port.util.LightCoordsUtil.pack(blockState.getLightEmission(), 0);
    }
}
