package dev.nookworks.cooking;

import java.util.function.Consumer;

import com.mojang.logging.LogUtils;

import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * The dishes in bowls standing on a block ({@link PlacedBowlsBlock}): one or two, each kept as it was set down (a
 * Suspicious Stew stays that stew). The client is told what they are, to draw them.
 */
public class PlacedBowlsBlockEntity extends BlockEntity implements PreRemoveSideEffects, Readout {
    private final NonNullList<ItemStack> bowls = NonNullList.withSize(PlacedBowlsBlock.MAX, ItemStack.EMPTY);

    public PlacedBowlsBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLACED_BOWLS.get(), pos, state);
    }

    public ItemStack bowl(int place) {
        return this.bowls.get(place);
    }

    public int count() {
        int n = 0;
        while (n < PlacedBowlsBlock.MAX && !this.bowls.get(n).isEmpty()) {
            n++;
        }
        return n;
    }

    /** Sets one down in the next place; false when both are taken. */
    public boolean add(ItemStack bowl) {
        int n = this.count();
        if (n >= PlacedBowlsBlock.MAX) {
            return false;
        }
        this.bowls.set(n, bowl.copyWithCount(1));
        this.updated();
        return true;
    }

    /** The last one set down, taken up; empty when there is none. */
    public ItemStack take() {
        int n = this.count();
        if (n == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = this.bowls.get(n - 1);
        this.bowls.set(n - 1, ItemStack.EMPTY);
        this.updated();
        return taken;
    }

    @Override
    public void readout(Consumer<Component> lines) {
        for (int i = 0; i < this.count(); i++) {
            lines.accept(this.bowls.get(i).getHoverName());
        }
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
            for (ItemStack bowl : this.bowls) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, bowl.copy());
            }
            this.bowls.clear(); // dropped, so gone from here
        }
    }

    @Override
    protected void saveAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueOutput output = ValueIO.output(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.saveAdditional(portTag, portLookup);
        ContainerHelper.saveAllItems(output, this.bowls, true);
    }

    @Override
    protected void loadAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueInput input = ValueIO.input(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.loadAdditional(portTag, portLookup);
        this.bowls.clear();
        ContainerHelper.loadAllItems(input, this.bowls);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ProblemReporter.pathOf(this), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, this.bowls, true);
            return output.buildResult();
        }
    }
}
