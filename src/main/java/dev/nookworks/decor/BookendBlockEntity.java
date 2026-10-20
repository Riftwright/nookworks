package dev.nookworks.decor;

import com.mojang.logging.LogUtils;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The books a Bookend holds up, the one against its upright first, each kept as it was put there (an enchanted book
 * keeps its enchantments, a written book its pages). The client is sent them, to draw them.
 */
public class BookendBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> books = NonNullList.withSize(BookendBlock.MAX, ItemStack.EMPTY);

    public BookendBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOOKEND.get(), pos, state);
    }

    public ItemStack book(int place) {
        return this.books.get(place);
    }

    public int count() {
        int n = 0;
        while (n < BookendBlock.MAX && !this.books.get(n).isEmpty()) {
            n++;
        }
        return n;
    }

    /** Stands one more beside the others; false when there is no room. */
    public boolean add(ItemStack book) {
        int n = this.count();
        if (n >= BookendBlock.MAX) {
            return false;
        }
        this.books.set(n, book.copyWithCount(1));
        this.updated();
        return true;
    }

    /** The last one put there, taken up; empty when there is none. */
    public ItemStack take() {
        int n = this.count();
        if (n == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = this.books.get(n - 1);
        this.books.set(n - 1, ItemStack.EMPTY);
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
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            for (ItemStack book : this.books) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, book.copy());
            }
            this.books.clear(); // dropped, so gone from here
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.books, true);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.books.clear();
        ContainerHelper.loadAllItems(input, this.books);
        for (int i = 0; i < BookendBlock.MAX; i++) {
            if (this.books.get(i).getCount() > 1) {
                this.books.set(i, this.books.get(i).copyWithCount(1)); // one book to a place, whatever a command wrote
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client is sent what each book is, the bare item, not the book itself (as {@link PlacedBooksBlockEntity}). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, PlacedBooksBlockEntity.shown(this.books), true);
            return output.buildResult();
        }
    }
}
