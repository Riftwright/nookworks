package dev.nookworks.block.entity;

import java.util.List;

import dev.nookworks.menu.CrateMenu;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import dev.nookworks.port.storage.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import dev.nookworks.port.core.PreRemoveSideEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import dev.nookworks.port.storage.ValueIO;

public class CrateBlockEntity extends RandomizableContainerBlockEntity implements PreRemoveSideEffects {
    public static final int SIZE = 9;
    private static final Component DEFAULT_NAME = Component.translatable("container.nookworks.crate");

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            playSound(SoundEvents.BARREL_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            playSound(SoundEvents.BARREL_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {}

        @Override
        public boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof CrateMenu menu && menu.getCrate() == CrateBlockEntity.this;
        }
    };

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRATE.get(), pos, state);
    }

    /** Blocks nesting: no crates or shulker boxes inside a crate (also enforced for hoppers). */
    public static boolean canStore(ItemStack stack) {
        return stack.canFitInsideContainerItems();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return canStore(stack);
    }

    @Override
    protected void saveAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueOutput output = ValueIO.output(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.saveAdditional(portTag, portLookup);
        if (!this.trySaveLootTable(ValueIO.tag(output))) {
            ContainerHelper.saveAllItems(output, this.items);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueInput input = ValueIO.input(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.loadAdditional(portTag, portLookup);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(ValueIO.tag(input))) {
            ContainerHelper.loadAllItems(input, this.items);
        }
    }

    /** Contents travel with the item via the loot table, so do not spill them on removal. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {}

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CrateMenu(containerId, inventory, this);
    }

    @Override
    public void startOpen(Player user) {
        // PORT: 1.21.1's openers are players, rechecked in a fixed 5-block box (26.3: the opener's reach)
        if (!this.remove && !user.isSpectator()) {
            this.openersCounter.incrementOpeners(user, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public void stopOpen(Player user) {
        if (!this.remove && !user.isSpectator()) {
            this.openersCounter.decrementOpeners(user, this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    private void playSound(SoundEvent event) {
        BlockPos pos = this.worldPosition;
        this.level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, event, SoundSource.BLOCKS, 0.5F,
                this.level.getRandom().nextFloat() * 0.1F + 1.0F);
    }
}
