package dev.nookworks.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.block.StorageBlock;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Storage Barrel. Its contents spill when it is broken, as a chest's do (see {@link StorageSpill}); only the
 * Crate carries what it holds. A barrel item from before that change still unpacks its old contents when placed.
 * <p>
 * The lid follows whoever has it open, the same way a vanilla barrel's does: the counter below is what plays
 * the sounds and flips the OPEN state, and it survives a player logging out mid-look.
 */
public class StorageBlockEntity extends BlockEntity implements MenuProvider {
    private final StorageInventory inventory;
    /** How the Sort button orders this barrel, chosen in its screen. */
    private SortOrder sortOrder = SortOrder.DEFAULT;
    /** Whether Sort turns its order round; put back as the order runs by itself when another order is chosen. */
    private boolean sortReversed;

    private final ContainerOpenersCounter openers =
            new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            StorageBlockEntity.this.playSound(state, SoundEvents.BARREL_OPEN);
            StorageBlockEntity.this.setOpen(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            StorageBlockEntity.this.playSound(state, SoundEvents.BARREL_CLOSE);
            StorageBlockEntity.this.setOpen(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state,
                int previous, int now) {
        }

        @Override
        public boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof StorageMenu menu && menu.inventory() == StorageBlockEntity.this.inventory;
        }
    };

    /** Called when a player opens or closes the barrel's screen. */
    public void startOpen(Player player) {
        if (this.level != null && !this.remove && !player.isSpectator()) {
            this.openers.incrementOpeners(player, this.level, this.worldPosition, this.getBlockState(),
                    player.blockInteractionRange() + 1.0);
        }
    }

    public void stopOpen(Player player) {
        if (this.level != null && !this.remove && !player.isSpectator()) {
            this.openers.decrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
        }
    }

    /** Keeps the count honest if a player leaves while it is open. */
    public void recheckOpen() {
        if (this.level != null && !this.remove) {
            this.openers.recheckOpeners(this.level, this.worldPosition, this.getBlockState());
        }
    }

    private void setOpen(BlockState state, boolean open) {
        if (this.level != null && state.hasProperty(StorageBlock.OPEN)) {
            this.level.setBlock(this.worldPosition, state.setValue(StorageBlock.OPEN, open), 3);
        }
    }

    private void playSound(BlockState state, SoundEvent sound) {
        if (this.level == null) {
            return;
        }
        Vec3i step = state.getValue(StorageBlock.FACING).getUnitVec3i();
        this.level.playSound(null,
                this.worldPosition.getX() + 0.5 + step.getX() / 2.0,
                this.worldPosition.getY() + 0.5 + step.getY() / 2.0,
                this.worldPosition.getZ() + 0.5 + step.getZ() / 2.0,
                sound, SoundSource.BLOCKS, 0.5F,
                this.level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    public StorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE.get(), pos, state);
        StorageTier tier = state.getBlock() instanceof StorageBlock block ? block.tier : StorageTier.IRON;
        this.inventory = new StorageInventory(tier, this::onContentsChanged, () -> this.level == null ? 0L : this.level.getGameTime());
    }

    public SortOrder sortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(SortOrder order) {
        if (order != this.sortOrder) {
            this.sortOrder = order;
            this.setChanged();
        }
    }

    public boolean sortReversed() {
        return this.sortReversed;
    }

    public void setSortReversed(boolean reversed) {
        if (reversed != this.sortReversed) {
            this.sortReversed = reversed;
            this.setChanged();
        }
    }

    private void onContentsChanged() {
        this.setChanged();
        this.updateLock();
    }

    /** Whether breaking it would spill more stacks than the server allows (config {@code storageBreakStackLimit}). */
    public boolean tooFullToBreak() {
        return this.stacksToSpill() > NookworksConfig.STORAGE_BREAK_LIMIT.getAsInt();
    }

    /** How many item stacks the contents come to on the ground. */
    public long stacksToSpill() {
        long stacks = 0;
        for (int i = 0; i < this.inventory.size(); i++) {
            int count = this.inventory.getAmountAsInt(i);
            if (count > 0) {
                int max = Math.max(1, this.inventory.getResource(i).getMaxStackSize());
                stacks += (count + max - 1) / max;
            }
        }
        return stacks;
    }

    /**
     * Whatever takes the barrel away - a pickaxe, a mod's block breaker, an explosion - the contents come out, as a
     * chest's do. All but a barrel too full to break by hand (StorageBreakGuard): that one leaves in its own item,
     * contents and all, as a shulker box does, rather than as up to 138,240 stacks on the ground. Set down again, it
     * holds them as before.
     * <p>
     * That item is made here and only here, once, as the barrel goes - never in a loot drop, which a block breaker may
     * work out and throw away, or work out twice: its loot table drops nothing for a barrel that leaves whole
     * ({@link StorageSpillsCondition}). Nothing that merely reads the barrel can lose its contents or copy them. The
     * item never despawns, and fire, lava, explosions and cactus leave it be (#nookworks:whole_barrel_resists): it
     * may hold a world's worth of things.
     * <p>
     * A placing refused after the fact (a claim mod cancelling the place event) takes the barrel back up as it was put
     * down, and hands the item back as it was: what it holds is still in that item, so nothing comes out here, or a
     * barrel item refused over and over made a copy of everything each time.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && this.level.restoringBlockSnapshots) {
            return;
        }
        if (this.leavesWhole()) {
            if (!this.leftWhole && this.level instanceof ServerLevel server) {
                this.leftWhole = true; // and the loot worked out after this (a player's break) drops no barrel besides
                ItemStack whole = new ItemStack(state.getBlock());
                whole.set(ModDataComponents.STORAGE.get(), this.inventory.toContents());
                whole.set(DataComponents.CUSTOM_NAME, this.name);
                server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(WHOLE_RESISTS)
                        .ifPresent(types -> whole.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(types)));
                ItemEntity item = new ItemEntity(server, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, whole);
                item.setDefaultPickUpDelay();
                item.setUnlimitedLifetime();
                server.addFreshEntity(item);
            }
            return;
        }
        if (this.level instanceof ServerLevel server) {
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < this.inventory.size(); i++) {
                int count = this.inventory.getAmountAsInt(i);
                if (count > 0) {
                    ItemStack one = this.inventory.getResource(i).toStack(1);
                    int max = Math.max(1, one.getMaxStackSize());
                    while (count > 0) {
                        int n = Math.min(max, count);
                        stacks.add(one.copyWithCount(n));
                        count -= n;
                    }
                }
            }
            StorageSpill.spill(server, pos, stacks);
        }
    }

    /** The name goes with the barrel's item. The contents never do this way: see {@link #preRemoveSideEffects}. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.name);
    }

    /** What the item a barrel leaves in whole is spared by. */
    public static final TagKey<DamageType> WHOLE_RESISTS = TagKey.create(Registries.DAMAGE_TYPE, Nookworks.id("whole_barrel_resists"));

    /** Whether this barrel has gone, as one item holding everything. */
    private boolean leftWhole;

    /** Whether the barrel leaves as one item holding everything, rather than spilling: too full to break by hand. */
    public boolean leavesWhole() {
        return this.leftWhole || this.tooFullToBreak();
    }

    /** Mirrors {@link #tooFullToBreak()} into the LOCKED block state, which the client can see. */
    public void updateLock() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        boolean locked = this.tooFullToBreak();
        if (state.hasProperty(StorageBlock.LOCKED) && state.getValue(StorageBlock.LOCKED) != locked) {
            this.level.setBlock(this.worldPosition, state.setValue(StorageBlock.LOCKED, locked), 3);
        }
    }

    public StorageInventory inventory() {
        return this.inventory;
    }

    /** The name it was given on an anvil (as its item), or null. */
    private @Nullable Component name;

    public @Nullable Component customName() {
        return this.name;
    }

    public void setCustomName(@Nullable Component name) {
        this.name = name;
        this.setChanged();
    }

    @Override
    public Component getDisplayName() {
        return this.name != null ? this.name : this.getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new StorageMenu(containerId, playerInventory, this.inventory, this.worldPosition).watching(this, player);
    }

    /**
     * The barrel's tier, for the client to build its screen with - sent however the screen is opened. A spectator's
     * click opens it straight from here, past the block's own use; without this the client was sent nothing to read,
     * and the spectator lost the connection.
     */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(this.inventory.tier());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.inventory.serialize(output.child("Storage"));
        if (this.sortOrder != SortOrder.DEFAULT) {
            output.putString("SortOrder", this.sortOrder.name());
        }
        if (this.sortReversed) {
            output.putBoolean("sort_reversed", true);
        }
        output.storeNullable("CustomName", ComponentSerialization.CODEC, this.name);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Storage").ifPresent(this.inventory::deserialize);
        String order = input.getStringOr("SortOrder", SortOrder.DEFAULT.name());
        this.sortOrder = Arrays.stream(SortOrder.values()).filter(o -> o.name().equals(order)).findFirst().orElse(SortOrder.DEFAULT);
        this.sortReversed = input.getBooleanOr("sort_reversed", false);
        this.name = parseCustomNameSafe(input, "CustomName");
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.inventory.load(components.getOrDefault(ModDataComponents.STORAGE.get(), StorageContents.EMPTY));
        this.name = components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Storage");
        output.discard("CustomName");
        // a picked block copy is an empty barrel: with the order left in it, it counted as holding something and went
        // into no other barrel or shulker box
        output.discard("SortOrder");
        output.discard("sort_reversed");
    }
}
