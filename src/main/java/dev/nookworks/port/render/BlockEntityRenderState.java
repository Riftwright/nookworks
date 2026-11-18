package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** 26.3's {@code BlockEntityRenderState}: what every block entity renderer is handed of its block entity. */
public class BlockEntityRenderState {
    public BlockPos blockPos = BlockPos.ZERO;
    private BlockState blockState = Blocks.AIR.defaultBlockState();
    public @Nullable BlockEntityType<?> blockEntityType;
    public int lightCoords;
    public ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress;

    /** As 26.3 fills it; the light is the block's own (the 1.21.1 renderer is handed the same and sets it again). */
    public static void extractBase(BlockEntity blockEntity, BlockEntityRenderState state,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        state.blockPos = blockEntity.getBlockPos();
        state.blockState = blockEntity.getBlockState();
        state.blockEntityType = blockEntity.getType();
        state.lightCoords = blockEntity.getLevel() != null
                ? dev.nookworks.port.util.LightCoordsUtil.getLightCoords(blockEntity.getLevel(), blockEntity.getBlockPos())
                : dev.nookworks.port.util.LightCoordsUtil.FULL_BRIGHT;
        state.breakProgress = breakProgress;
    }

    public BlockState blockState() {
        return this.blockState;
    }
}
