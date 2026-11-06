package dev.nookworks.block.entity;

import java.util.List;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * Magnet Chest: a 27-slot chest that pulls dropped items within {@link #RADIUS} blocks into itself every few
 * ticks. Handy under farms and mob grinders where standing around would get you hurt. Items opted out with
 * the "PreventRemoteMovement" tag are left alone. Hoppers can empty it as usual.
 */
public class MagnetChestBlockEntity extends BaseContainerBlockEntity {
    public static final int RADIUS = 5;
    private static final int INTERVAL = 4;

    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    /** Opened and shut with a metal lid's sound, as the Repair Box is (r21: it opened in silence). */
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            MagnetChestBlockEntity.this.playSound(SoundEvents.IRON_TRAPDOOR_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            MagnetChestBlockEntity.this.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {}

        @Override
        public boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == MagnetChestBlockEntity.this;
        }
    };

    public MagnetChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGNET_CHEST.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MagnetChestBlockEntity chest) {
        // each chest on a beat of its own, not all on the same tick; a full one looks again only once something left it
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), INTERVAL) == 0 && level instanceof ServerLevel server
                && !chest.full()) {
            chest.collect(server);
        }
    }

    /**
     * Whether no slot has room for anything: every one holding a whole stack. A full chest by a farm tried every item lying
     * about again every beat (the performance audit, 2026-10-19).
     */
    private boolean full() {
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack held = this.getItem(i);
            if (held.isEmpty() || held.getCount() < Math.min(held.getMaxStackSize(), this.getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    /** Pulls every reachable item that fits. Public for tests. */
    public void collect(ServerLevel level) {
        AABB area = new AABB(this.worldPosition).inflate(RADIUS);
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, area, EntitySelector.ENTITY_STILL_ALIVE);
        var handler = VanillaContainerWrapper.of(this);
        for (ItemEntity entity : found) {
            // Still in its pickup delay: the hopper and the handheld magnet both wait, and without this a
            // player mining inside the radius watched their own drops disappear before they could reach
            // them. A farm's output loses the delay in half a second, so collection is no slower.
            // PreventRemoteMovement keeps magnets off a thing (another mod's conveyor carrying it); with
            // AllowMachineRemoteMovement too it means hand-held magnets only, and a machine - this chest - may take it.
            var data = entity.getPersistentData();
            if (data.getBooleanOr("PreventRemoteMovement", false) && !data.getBooleanOr("AllowMachineRemoteMovement", false)
                    || entity.hasPickUpDelay()) {
                continue;
            }
            ItemStack stack = entity.getItem();
            ItemStack rest = ItemUtil.insertItemReturnRemaining(handler, stack.copy(), false, null);
            if (rest.getCount() == stack.getCount()) {
                continue; // full
            }
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + 0.2, entity.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
            if (rest.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(rest);
            }
        }
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (!this.remove && !user.getLivingEntity().isSpectator()) {
            this.openersCounter.incrementOpeners(user.getLivingEntity(), this.getLevel(), this.getBlockPos(),
                    this.getBlockState(), user.getContainerInteractionRange());
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (!this.remove && !user.getLivingEntity().isSpectator()) {
            this.openersCounter.decrementOpeners(user.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public List<ContainerUser> getEntitiesWithContainerOpen() {
        return this.openersCounter.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
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

    @Override
    protected Component getDefaultName() {
        // The screen's title says what the chest does, since its screen is a plain chest's - that alone: after the
        // chest's name, the range never fit beside the sort buttons (r15: "Magnet Chest (pulls …").
        return Component.translatable("container.nookworks.magnet_chest");
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
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }
}
