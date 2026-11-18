package dev.nookworks.menu;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.entity.NestedTimerBlockEntity;
import dev.nookworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Nested Timer GUI: no slots, three sliders. A slider sets its value with one button id: the setting times
 * {@link #PER_SETTING} plus the step it was moved to.
 */
public class NestedTimerMenu extends AbstractContainerMenu {
    public static final int INTERVAL = 0;
    public static final int PULSES = 1;
    public static final int GAP = 2;
    public static final int PER_SETTING = 64;

    private final ContainerData data;
    private final @Nullable NestedTimerBlockEntity timer;

    /** Client side. */
    public NestedTimerMenu(int containerId) {
        this(containerId, new SimpleContainerData(5), null);
    }

    public NestedTimerMenu(int containerId, ContainerData data, @Nullable NestedTimerBlockEntity timer) {
        super(ModMenus.NESTED_TIMER.get(), containerId);
        this.data = data;
        this.timer = timer;
        this.addDataSlots(data);
    }

    /** The interval's step in {@link NestedTimerBlockEntity#INTERVALS}. */
    public int intervalStep() {
        return this.data.get(0);
    }

    public int pulses() {
        return Math.max(1, this.data.get(1));
    }

    /** The gap's step in {@link NestedTimerBlockEntity#GAPS}. */
    public int gapStep() {
        return this.data.get(2);
    }

    /** Whether a pulse is going out right now. */
    public boolean output() {
        return this.data.get(4) != 0;
    }

    public boolean held() {
        return this.data.get(3) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.timer == null || id < 0) {
            return false;
        }
        int step = id % PER_SETTING;
        switch (id / PER_SETTING) {
            case INTERVAL -> {
                if (step >= NestedTimerBlockEntity.INTERVALS.length) {
                    return false;
                }
                this.timer.setInterval(NestedTimerBlockEntity.INTERVALS[step]);
            }
            case PULSES -> this.timer.setPulses(step + 1);
            case GAP -> {
                if (step >= NestedTimerBlockEntity.GAPS.length) {
                    return false;
                }
                this.timer.setGap(NestedTimerBlockEntity.GAPS[step]);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.timer == null || Container.stillValidBlockEntity(this.timer, player);
    }
}
