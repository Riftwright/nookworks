package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 26.3's {@code MovingBlockRenderState}: a block drawn away from its cell as the world would draw it in a cell -
 * {@link #blockPos}'s tint and light, shaded by its neighbours there. 1.21.1 draws it as its falling block renderer does,
 * from the client's world; the biome and light engine 26.3 is handed are the world's own here.
 */
public class MovingBlockRenderState {
    public BlockPos randomSeedPos = BlockPos.ZERO;
    public BlockPos blockPos = BlockPos.ZERO;
    public BlockState blockState = Blocks.AIR.defaultBlockState();
    public @Nullable Holder<Biome> biome;

    void submit(PoseStack poseStack, MultiBufferSource buffers) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(this.blockState);
        long seed = this.blockState.getSeed(this.randomSeedPos);
        for (RenderType type : model.getRenderTypes(this.blockState, RandomSource.create(seed), ModelData.EMPTY)) {
            dispatcher.getModelRenderer().tesselateBlock(level, model, this.blockState, this.blockPos, poseStack,
                    buffers.getBuffer(RenderTypeHelper.getMovingBlockRenderType(type)), false, RandomSource.create(), seed,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
        }
    }
}
