package dev.nookworks.block.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.DrawRights;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * An Item Warp Receiver's owner: whoever set it down ({@link DrawRights#ownerOf}). A sender with an owner sends only to
 * receivers of the same owner, or of none ({@link ItemWarpSenderBlockEntity#findReceiver}). A receiver from before owners
 * were kept had no block entity: one is made for it as its chunk loads, with no owner, and every sender sends to it, as
 * before.
 */
public class ItemWarpReceiverBlockEntity extends BlockEntity implements DrawRights.Owned {
    private @Nullable UUID owner;
    private @Nullable DrawRights drawRights;

    public ItemWarpReceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_WARP_RECEIVER.get(), pos, state);
    }

    @Override
    public @Nullable UUID owner() {
        return this.owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    /** Kept for {@link DrawRights.Owned}; a warp asks no claim, it only matches owners. */
    @Override
    public DrawRights drawRights() {
        if (this.drawRights == null) {
            this.drawRights = new DrawRights();
        }
        return this.drawRights;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }
}
