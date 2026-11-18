package dev.nookworks.decor;

import com.mojang.logging.LogUtils;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import dev.nookworks.port.storage.ProblemReporter;
import dev.nookworks.port.storage.ContainerHelper;
import dev.nookworks.port.core.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.nookworks.port.storage.TagValueOutput;
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import dev.nookworks.port.core.PreRemoveSideEffects;
import dev.nookworks.port.storage.ValueIO;

/**
 * The bottles standing on a block, one to a place, each kept as it was set down (a potion stays that potion). The
 * client is told what they are, for the colour of what is in them.
 */
public class PlacedBottlesBlockEntity extends BlockEntity implements PreRemoveSideEffects {
    private final NonNullList<ItemStack> bottles = NonNullList.withSize(PlacedBottlesBlock.MAX, ItemStack.EMPTY);

    public PlacedBottlesBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLACED_BOTTLES.get(), pos, state);
    }

    public ItemStack bottle(int place) {
        return this.bottles.get(place);
    }

    public int count() {
        int n = 0;
        while (n < PlacedBottlesBlock.MAX && !this.bottles.get(n).isEmpty()) {
            n++;
        }
        return n;
    }

    /** Sets one down in the next place; false when all four are taken. */
    public boolean add(ItemStack bottle) {
        int n = this.count();
        if (n >= PlacedBottlesBlock.MAX) {
            return false;
        }
        this.bottles.set(n, bottle.copyWithCount(1));
        this.updated();
        return true;
    }

    /** The last one set down, taken up; empty when there is none. */
    public ItemStack take() {
        int n = this.count();
        if (n == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = this.bottles.get(n - 1);
        this.bottles.set(n - 1, ItemStack.EMPTY);
        this.updated();
        return taken;
    }

    private void updated() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        PreRemoveSideEffects.super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            for (ItemStack bottle : this.bottles) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, bottle.copy());
            }
            this.bottles.clear(); // dropped, so gone from here (the dupe audit's hardening note)
        }
    }

    @Override
    protected void saveAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueOutput output = ValueIO.output(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.saveAdditional(portTag, portLookup);
        ContainerHelper.saveAllItems(output, this.bottles, true);
    }

    @Override
    protected void loadAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueInput input = ValueIO.input(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.loadAdditional(portTag, portLookup);
        this.bottles.clear();
        ContainerHelper.loadAllItems(input, this.bottles);
        if (this.level != null && this.level.isClientSide()) {
            // the colours are drawn from these: draw the block again, whichever of it and its state came first
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ProblemReporter.pathOf(this), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, this.bottles, true);
            return output.buildResult();
        }
    }
}
