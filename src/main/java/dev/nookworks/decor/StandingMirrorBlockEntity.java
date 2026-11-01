package dev.nookworks.decor;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Standing Mirror's lower half: nothing kept, there only so the client draws its glass. */
public class StandingMirrorBlockEntity extends BlockEntity {
    public StandingMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STANDING_MIRROR.get(), pos, state);
    }
}
