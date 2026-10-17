package dev.nookworks.spawner;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Spawner Controller GUI: no slots, just the synced settings and one button id per change. */
public class SpawnerControllerMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 14;

    public static final int TOGGLE_ENABLED = 0;
    public static final int INTERVAL_DOWN = 1;
    public static final int INTERVAL_UP = 2;
    public static final int COUNT_DOWN = 3;
    public static final int COUNT_UP = 4;
    public static final int NEARBY_DOWN = 5;
    public static final int NEARBY_UP = 6;
    public static final int TOGGLE_LIGHT = 7;
    public static final int TOGGLE_PLAYER = 8;
    public static final int BUTTON_REDSTONE = 9;

    private final ContainerData data;
    private final @Nullable SpawnerControllerBlockEntity controller;

    /** Client side. */
    public SpawnerControllerMenu(int containerId) {
        this(containerId, new SimpleContainerData(DATA_COUNT), null);
    }

    public SpawnerControllerMenu(int containerId, ContainerData data, @Nullable SpawnerControllerBlockEntity controller) {
        super(ModMenus.SPAWNER_CONTROLLER.get(), containerId);
        this.data = data;
        this.controller = controller;
        this.addDataSlots(data);
    }

    public boolean enabled() {
        return this.data.get(0) != 0;
    }

    public int intervalSeconds() {
        return this.data.get(1) / 20;
    }

    public int count() {
        return this.data.get(2);
    }

    public int maxNearby() {
        return this.data.get(3);
    }

    public boolean anyLight() {
        return this.data.get(4) != 0;
    }

    public boolean needsPlayer() {
        return this.data.get(5) != 0;
    }

    public int energy() {
        return (this.data.get(6) & 0xFFFF) | (this.data.get(11) & 0xFFFF) << 16;
    }

    /** The settings as synced, for what the screen works out of them (a spawner's cost). */
    public SpawnerSettings settings() {
        return new SpawnerSettings(this.enabled(), this.data.get(1), this.count(), this.maxNearby(), this.anyLight(), this.needsPlayer());
    }

    /** FE a tick it costs now, for all its spawners. */
    public int costPerTick() {
        return (this.data.get(12) & 0xFFFF) | this.data.get(13) << 16;
    }

    public int linked() {
        return this.data.get(7);
    }

    public boolean powered() {
        return this.data.get(8) != 0;
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(this.data.get(9));
    }

    public boolean heldByRedstone() {
        return this.data.get(10) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.controller == null) {
            return true;
        }
        if (id == BUTTON_REDSTONE) {
            this.controller.setRedstoneMode(this.controller.redstoneMode().next());
            return true;
        }
        SpawnerSettings s = this.controller.settings();
        this.controller.setSettings(switch (id) {
            case TOGGLE_ENABLED -> s.withEnabled(!s.enabled());
            case INTERVAL_DOWN -> s.stepInterval(-1);
            case INTERVAL_UP -> s.stepInterval(1);
            case COUNT_DOWN -> s.withCount(s.count() - 1);
            case COUNT_UP -> s.withCount(s.count() + 1);
            case NEARBY_DOWN -> s.withMaxNearby(s.maxNearby() - 1);
            case NEARBY_UP -> s.withMaxNearby(s.maxNearby() + 1);
            case TOGGLE_LIGHT -> s.withAnyLight(!s.anyLight());
            case TOGGLE_PLAYER -> s.withNeedsPlayer(!s.needsPlayer());
            default -> s;
        });
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.controller == null || Container.stillValidBlockEntity(this.controller, player);
    }
}
