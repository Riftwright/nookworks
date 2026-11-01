package dev.nookworks.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.nookworks.block.StorageBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A Storage Upgrade: used on a barrel that is already in place - wired into machines and pipes, and full - it turns it
 * into the next tier where it stands, everything in it kept, in the same slots, facing the same way, with its name. The
 * Iron one takes a plain wooden barrel to an Iron Storage Barrel; each after it takes the tier before to its own. What
 * a Storage Barrel cannot hold (a bundle, a barrel with things in it) comes out of the wooden one onto the floor.
 * <p>
 * An upgrade costs exactly what the next tier's recipe adds to the barrel before it - no more, no less - so it does
 * not matter which way a barrel is made bigger.
 */
public class StorageUpgradeItem extends Item {
    private final StorageTier to;
    private final Supplier<? extends StorageBlock> block;

    public StorageUpgradeItem(Properties properties, StorageTier to, Supplier<? extends StorageBlock> block) {
        super(properties);
        this.to = to;
        this.block = block;
    }

    public StorageTier tier() {
        return this.to;
    }

    /** The tier this upgrade works on, or null for the plain wooden barrel. */
    public StorageTier from() {
        return this.to.ordinal() == 0 ? null : StorageTier.values()[this.to.ordinal() - 1];
    }

    /** Whether this upgrade takes {@code state} up a tier. */
    public boolean fits(BlockState state) {
        StorageTier from = this.from();
        return from == null ? state.is(Blocks.BARREL) : state.getBlock() instanceof StorageBlock storage && storage.tier == from;
    }

    /** Says what it goes on: a plain barrel, or the tier before. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        StorageTier from = this.from();
        Component on = from == null ? Blocks.BARREL.getName()
                : Component.translatable("block.nookworks." + from.getSerializedName() + "_storage_barrel");
        builder.accept(Component.translatable("tooltip.nookworks.storage_upgrade", on).withStyle(ChatFormatting.GRAY));
    }

    /** Before the barrel is used: otherwise it would open, and the upgrade go in among its contents. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (!this.fits(state)) {
            if (player != null && !level.isClientSide() && (state.is(Blocks.BARREL) || state.getBlock() instanceof StorageBlock)) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.storage_upgrade.wrong_tier"));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }
        if (player != null && !player.mayUseItemAt(pos, context.getClickedFace(), stack)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            if (!upgrade(server, pos, this.block.get(), player)) {
                return InteractionResult.FAIL;
            }
            server.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Turns the barrel at {@code pos} into {@code into}, facing as it did, with everything it held in the same slots
     * and the name it was given. A wooden barrel may hold what a Storage Barrel cannot (a bundle, a barrel with things in
     * it): that comes out on the floor. False when it cannot (someone else's loot still unopened in it is opened
     * first, so nothing is lost). A locked wooden barrel is upgraded only by whoever could open it.
     */
    public static boolean upgrade(ServerLevel level, BlockPos pos, StorageBlock into, Player player) {
        BlockState old = level.getBlockState(pos);
        Direction facing = old.hasProperty(BarrelBlock.FACING) ? old.getValue(BarrelBlock.FACING) : Direction.NORTH;
        List<StorageContents.Entry> entries = new ArrayList<>();
        List<ItemStack> refused = new ArrayList<>();
        SortOrder order = SortOrder.DEFAULT;
        boolean reversed = false;
        Component name;
        if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
            if (player == null ? barrel.isLocked() : !barrel.canOpen(player)) {
                if (player != null) {
                    BaseContainerBlockEntity.sendChestLockedNotifications(Vec3.atCenterOf(pos), player, barrel.getDisplayName());
                }
                return false; // the lock would have been the way in
            }
            barrel.unpackLootTable(player);
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                ItemStack held = barrel.getItem(i);
                if (held.isEmpty()) {
                    continue;
                }
                if (StorageInventory.accepts(held)) {
                    entries.add(new StorageContents.Entry(i, held.copyWithCount(1), held.getCount()));
                } else {
                    refused.add(held.copy());
                }
            }
            name = barrel.getCustomName();
            barrel.clearContent(); // taken out first, so replacing the block spills nothing
        } else if (level.getBlockEntity(pos) instanceof StorageBlockEntity storage) {
            entries.addAll(storage.inventory().toContents().entries());
            order = storage.sortOrder();
            reversed = storage.sortReversed();
            name = storage.customName();
            storage.inventory().load(StorageContents.EMPTY);
        } else {
            return false;
        }
        level.setBlock(pos, into.defaultBlockState().setValue(StorageBlock.FACING, facing), 3);
        if (level.getBlockEntity(pos) instanceof StorageBlockEntity upgraded) {
            upgraded.inventory().load(new StorageContents(List.copyOf(entries)));
            upgraded.setSortOrder(order);
            upgraded.setSortReversed(reversed);
            upgraded.setCustomName(name);
            upgraded.setChanged();
            upgraded.updateLock();
        }
        for (ItemStack stack : refused) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        }
        return true;
    }
}
