package dev.nookworks.quench;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;

/**
 * A cauldron of vegetable oil, a bucket's worth in three levels as water is: filled and emptied by bucket or pipe, and
 * what Velarium is quenched in (see {@link Quenching}). Rain does not fill it, and it puts out no fire - it is oil. A
 * red-hot ingot that falls into it is quenched as one a hopper feeds.
 */
public class OilCauldronBlock extends LayeredCauldronBlock implements RemovalHooks {
    public OilCauldronBlock(BlockBehaviour.Properties properties) {
        super(Biome.Precipitation.NONE, Quenching.OIL, properties);
    }

    /**
     * NeoForge refreshes a cauldron's capabilities only when it stops or starts being a cauldron at all; ours have an item
     * handler a plain cauldron has not, so a cache that asked an empty cauldron (none) or this one (a bath) is told when
     * one turns into the other (r14).
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // PORT: vanilla's part of affectNeighborsAfterRemoval runs in 1.21.1's super.onRemove
        if (!level.getBlockState(pos).is(this)) {
            Quenching.forget(level, pos);
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof ItemEntity item && item.getY() < pos.getY() + this.getContentHeight(state)) {
            Quenching.fallenIn(level, pos, item);
        }
    }

    // PORT: 1.21.1 calls only onRemove where 26.3 calls the old block entity's preRemoveSideEffects and this
    // block's affectNeighborsAfterRemoval: BlockRemoval runs them in 26.3's order
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockRemoval.onRemove(this, state, level, pos, newState, movedByPiston,
                () -> super.onRemove(state, level, pos, newState, movedByPiston));
    }
}
