package dev.nookworks.cooking;

import java.util.function.Consumer;

import com.mojang.logging.LogUtils;

import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** What is on a Plate: one thing to eat ({@link PlateBlock}), kept as vanilla keeps a container's (its Items list). */
public class PlateBlockEntity extends BlockEntity implements Readout {
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public PlateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLATE.get(), pos, state);
    }

    /** Whether {@code stack} goes on a plate: anything to eat (bread dough rises on a tray, not here). */
    public static boolean fits(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    public ItemStack food() {
        return this.items.getFirst();
    }

    public boolean isEmpty() {
        return this.food().isEmpty();
    }

    /** Puts one of {@code stack} on it, if it is empty. */
    public boolean place(ItemStack stack, LivingEntity by) {
        if (!this.isEmpty()) {
            return false;
        }
        this.items.set(0, stack.consumeAndReturn(1, by));
        this.updated();
        return true;
    }

    public ItemStack take() {
        ItemStack out = this.food();
        this.items.set(0, ItemStack.EMPTY);
        this.updated();
        return out;
    }

    @Override
    public void readout(Consumer<Component> lines) {
        if (!this.isEmpty()) {
            lines.accept(this.food().getHoverName());
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
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, this.food().copy());
            this.items.clear();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client draws what is on it. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, this.items, true);
            return output.buildResult();
        }
    }
}
