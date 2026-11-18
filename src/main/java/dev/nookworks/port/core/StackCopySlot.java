package dev.nookworks.port.core;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A menu slot over a store that hands out copies (a Storage Barrel's, whose stacks are far larger than an item stack
 * may be): the game changes the stack a slot gave it in place and then calls {@link #setChanged()}, so the copy last
 * handed out is kept and written back then.
 * <p>
 * PORT: 26.3's NeoForge has this class with a slot index ({@code StackCopySlot(int, int, int)}); NeoForge 21.1 got that
 * constructor only late in its line (21.1.219, which Create and Farmer's Delight ask for, has the one without an
 * index, where every slot would say it is slot 0 - what another mod's sorting or locking goes by). Ours is written from
 * what the mod's own subclass needs, so one jar runs on every 21.1 from 219 up.
 */
public abstract class StackCopySlot extends Slot {
    private static final Container NO_CONTAINER = new SimpleContainer(0);

    /** The stack last handed out or set, written back when the game says it changed it. */
    private @Nullable ItemStack handedOut;

    /** {@code slot}: the index in the store behind it, whatever that is. */
    protected StackCopySlot(int slot, int x, int y) {
        super(NO_CONTAINER, slot, x, y);
    }

    /** A copy of what the store holds here. */
    protected abstract ItemStack getStackCopy();

    /** Writes {@code stack} into the store here. */
    protected abstract void setStackCopy(ItemStack stack);

    @Override
    public final ItemStack getItem() {
        ItemStack copy = this.getStackCopy();
        this.handedOut = copy;
        return copy;
    }

    @Override
    public final void set(ItemStack stack) {
        this.setStackCopy(stack);
        this.handedOut = stack;
    }

    @Override
    public final void setChanged() {
        if (this.handedOut != null) {
            this.set(this.handedOut);
        }
    }

    @Override
    public final ItemStack remove(int amount) {
        ItemStack rest = this.getStackCopy().copy();
        ItemStack taken = rest.split(amount);
        this.set(rest);
        this.handedOut = null;
        return taken;
    }
}
