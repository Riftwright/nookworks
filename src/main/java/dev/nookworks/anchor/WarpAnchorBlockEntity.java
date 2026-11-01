package dev.nookworks.anchor;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps a Warp Anchor in {@link WarpAnchors}' list while it is loaded, on the server; the client draws it from it. */
public class WarpAnchorBlockEntity extends BlockEntity {
    public WarpAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WARP_ANCHOR.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide()) {
            WarpAnchors.add(this.level, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide()) {
            WarpAnchors.remove(this.level, this.worldPosition);
        }
    }
}
