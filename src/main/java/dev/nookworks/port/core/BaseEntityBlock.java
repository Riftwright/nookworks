package dev.nookworks.port.core;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's {@code net.minecraft.world.level.block.BaseEntityBlock}: a block with a block entity, drawn from its model.
 * 1.21.1's draws nothing by default ({@code getRenderShape} answers {@code INVISIBLE}, leaving it all to a block entity
 * renderer); 26.3's draws the model unless a block says otherwise, and the mod's blocks are written for that - without
 * this every machine, crate and storage barrel stood in the world as a hole in the ground showing the sky.
 */
public abstract class BaseEntityBlock extends net.minecraft.world.level.block.BaseEntityBlock {
    protected BaseEntityBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
