package dev.nookworks.poster;

import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An item frame of dark oak or of brass, plain or glowing: vanilla's item frame in all it does - an item or a map in it,
 * turned by a click, a comparator reading it, set invisible - drawn in its own wood or metal ({@code NookItemFrameRenderer})
 * and dropping itself; a glowing one is vanilla's glowing frame (its item lit in the dark, its sounds). Which it is goes
 * by its entity type, so nothing more is saved than vanilla's frame saves.
 */
public class NookItemFrame extends ItemFrame {
    public NookItemFrame(EntityType<? extends ItemFrame> type, Level level) {
        super(type, level);
    }

    public NookItemFrame(EntityType<? extends ItemFrame> type, Level level, BlockPos pos, Direction direction) {
        super(type, level, pos, direction);
    }

    /** Whether this is a brass frame (else dark oak). */
    public boolean brass() {
        return this.getType() == ModEntities.BRASS_ITEM_FRAME.get() || this.getType() == ModEntities.BRASS_GLOW_ITEM_FRAME.get();
    }

    /** Whether this is a glowing frame: its item lit in the dark, as vanilla's glowing frame's. */
    public boolean glow() {
        return this.getType() == ModEntities.DARK_OAK_GLOW_ITEM_FRAME.get() || this.getType() == ModEntities.BRASS_GLOW_ITEM_FRAME.get();
    }

    @Override
    protected ItemStack getFrameItemStack() {
        Item item = this.brass()
                ? this.glow() ? ModItems.BRASS_GLOW_ITEM_FRAME.get() : ModItems.BRASS_ITEM_FRAME.get()
                : this.glow() ? ModItems.DARK_OAK_GLOW_ITEM_FRAME.get() : ModItems.DARK_OAK_ITEM_FRAME.get();
        return new ItemStack(item);
    }

    @Override
    public SoundEvent getRemoveItemSound() {
        return this.glow() ? SoundEvents.GLOW_ITEM_FRAME_REMOVE_ITEM : super.getRemoveItemSound();
    }

    @Override
    public SoundEvent getBreakSound() {
        return this.glow() ? SoundEvents.GLOW_ITEM_FRAME_BREAK : super.getBreakSound();
    }

    @Override
    public SoundEvent getPlaceSound() {
        return this.glow() ? SoundEvents.GLOW_ITEM_FRAME_PLACE : super.getPlaceSound();
    }

    @Override
    public SoundEvent getAddItemSound() {
        return this.glow() ? SoundEvents.GLOW_ITEM_FRAME_ADD_ITEM : super.getAddItemSound();
    }

    @Override
    public SoundEvent getRotateItemSound() {
        return this.glow() ? SoundEvents.GLOW_ITEM_FRAME_ROTATE_ITEM : super.getRotateItemSound();
    }
}
