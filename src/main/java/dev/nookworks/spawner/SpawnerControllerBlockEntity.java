package dev.nookworks.spawner;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.Readout;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Spawner Controller: a console set on top of a Spawner Link Receiver. Every {@link #PERIOD} ticks it finds the
 * spawners the receiver reaches, pays for them from the receiver's FE - what its settings cost
 * ({@link SpawnerSettings#energyPerTick}) for each - and applies its {@link SpawnerSettings} for a little longer than
 * one period. Without enough power the spawners simply go back to vanilla behaviour. Keeping spawners switched off
 * costs nothing: off has to hold when the power fails too, or a farm stopped with a lever would start spawning again on
 * its own the moment the generator ran dry. Its screen is lit ({@link SpawnerControllerBlock#LIT}) only while it runs:
 * on a receiver, with spawners linked, on power.
 */
public class SpawnerControllerBlockEntity extends BlockEntity implements MenuProvider, Readout {
    public static final int PERIOD = 10;
    /** The receiver's buffer, which the controller draws on. */
    public static final int CAPACITY = SpawnerLinkReceiverBlockEntity.CAPACITY;

    private SpawnerSettings settings = SpawnerSettings.DEFAULT;
    private int linked;
    private boolean powered;
    private RedstoneMode redstone = RedstoneMode.DEFAULT;
    /** Whether the redstone mode is holding the spawners off right now. */
    private boolean held;

    /** Synced to the GUI: settings, energy (in two 16-bit halves), link status, and what it costs a tick. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            SpawnerSettings s = SpawnerControllerBlockEntity.this.settings;
            return switch (index) {
                case 0 -> s.enabled() ? 1 : 0;
                case 1 -> s.interval();
                case 2 -> s.count();
                case 3 -> s.maxNearby();
                case 4 -> s.anyLight() ? 1 : 0;
                case 5 -> s.needsPlayer() ? 1 : 0;
                case 6 -> SpawnerControllerBlockEntity.this.storedEnergy() & 0xFFFF;
                case 7 -> SpawnerControllerBlockEntity.this.linked;
                case 8 -> SpawnerControllerBlockEntity.this.powered ? 1 : 0;
                case 9 -> SpawnerControllerBlockEntity.this.redstone.ordinal();
                case 10 -> SpawnerControllerBlockEntity.this.held ? 1 : 0;
                case 11 -> SpawnerControllerBlockEntity.this.storedEnergy() >>> 16;
                // in two halves, as the energy is: a slot is sent as 16 bits, and six strong spawners cost more
                case 12 -> SpawnerControllerBlockEntity.this.costPerTick() & 0xFFFF;
                case 13 -> SpawnerControllerBlockEntity.this.costPerTick() >>> 16;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return SpawnerControllerMenu.DATA_COUNT;
        }
    };

    public SpawnerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPAWNER_CONTROLLER.get(), pos, state);
    }

    /** The receiver this console stands on, or null when it is not on one (it does nothing then). */
    public @Nullable SpawnerLinkReceiverBlockEntity receiver() {
        return this.level != null && this.level.getBlockEntity(this.worldPosition.below()) instanceof SpawnerLinkReceiverBlockEntity receiver
                ? receiver : null;
    }

    /** The FE of the receiver under it: what it runs on. Null off a receiver. */
    public @Nullable SimpleEnergyHandler energy() {
        SpawnerLinkReceiverBlockEntity receiver = this.receiver();
        return receiver == null ? null : receiver.energy();
    }

    private int storedEnergy() {
        SimpleEnergyHandler energy = this.energy();
        return energy == null ? 0 : (int) energy.getAmountAsLong();
    }

    public SpawnerSettings settings() {
        return this.settings;
    }

    public void setSettings(SpawnerSettings settings) {
        this.settings = settings;
        this.setChanged();
    }

    public int linked() {
        return this.linked;
    }

    public RedstoneMode redstoneMode() {
        return this.redstone;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        this.redstone = mode;
        this.setChanged();
    }

    public boolean powered() {
        return this.powered;
    }

    /** Jade's lines, in its screen's words: how many spawners it has in hand, how they are, and what they cost. */
    @Override
    public void readout(Consumer<Component> lines) {
        String k = "gui.nookworks.spawner_controller.";
        if (this.linked == 0) {
            lines.accept(Component.translatable(k + "status.none"));
            return;
        }
        String state = !this.powered ? "no_power" : this.held ? "redstone" : !this.settings.enabled() ? "off" : "active";
        lines.accept(Component.translatable(k + "status." + state + (this.linked == 1 ? ".one" : ""), this.linked));
        if (state.equals("active")) {
            lines.accept(Component.translatable(k + "using", this.costPerTick()));
        }
    }

    /** FE a tick the spawners it holds cost it now: each at its settings, nothing while redstone holds them off. */
    public int costPerTick() {
        return this.held ? 0 : this.linked * this.settings.energyPerTick();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpawnerControllerBlockEntity controller) {
        if (level.getGameTime() % PERIOD == 0 && level instanceof ServerLevel server) {
            controller.update(server);
        }
    }

    /** One control period: collect spawners, pay, apply, light the screen or put it out. Public for tests. */
    public void update(ServerLevel level) {
        SpawnerLinkReceiverBlockEntity receiver = this.receiver();
        Set<SpawnerBlockEntity> spawners = new LinkedHashSet<>();
        if (receiver != null) {
            spawners.addAll(receiver.spawners(level));
        }
        this.linked = spawners.size();
        // Stopped by redstone, the controller switches its spawners off rather than letting go of them: a lever on
        // a mob farm should stop the mobs, not hand the spawners back to vanilla.
        this.held = !this.redstone.allows(level.hasNeighborSignal(this.worldPosition));
        SpawnerSettings applied = this.held ? this.settings.withEnabled(false) : this.settings;
        // Off, the spawners hold themselves off too, saved with them: while this console is out of reach - far off,
        // not loaded - off still holds. On, they are let go of that.
        GlobalPos self = GlobalPos.of(level.dimension(), this.worldPosition);
        for (SpawnerBlockEntity spawner : spawners) {
            ControlledSpawner control = ControlledSpawner.of(spawner.getSpawner());
            // on, it lets go only of its own hold: another console reaching the same spawner and switched off keeps it
            boolean news = applied.enabled() ? self.equals(control.nookworks$heldBy()) && control.nookworks$release()
                    : control.nookworks$holdOff(self);
            if (news) {
                SpawnerHold.changed(level, spawner);
            }
        }
        int cost = spawners.size() * PERIOD * applied.energyPerTick();
        long stored = receiver == null ? 0 : receiver.energy().getAmountAsLong();
        this.powered = !spawners.isEmpty() && stored >= cost;
        // The screen is on while the console works and has power to work on, as a furnace glows while it burns; off
        // costs nothing and holds without power, but a console with no power at all shows dark.
        this.setLit(level, this.powered && stored > 0);
        if (!this.powered) {
            return; // only running is paid for (off costs nothing), so this lets go of spawners that were running
        }
        receiver.energy().set((int) (stored - cost));
        long until = level.getGameTime() + PERIOD + 5;
        for (SpawnerBlockEntity spawner : spawners) {
            ControlledSpawner.of(spawner.getSpawner()).nookworks$control(applied, until);
        }
    }

    /** Whether this console, as it stands, holds the spawner at {@code spawner} off: it reaches it, and is off or held by redstone. */
    public boolean holdsOff(ServerLevel level, BlockPos spawner) {
        SpawnerLinkReceiverBlockEntity receiver = this.receiver();
        boolean off = !this.settings.enabled() || !this.redstone.allows(level.hasNeighborSignal(this.worldPosition));
        return off && receiver != null && receiver.spawners(level).stream().anyMatch(s -> s.getBlockPos().equals(spawner));
    }

    /** Taken away, it puts out the receiver's lines and lets go of the spawners it held off. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            lightReceiver(this.level, pos.below(), false);
        }
        SpawnerLinkReceiverBlockEntity receiver = this.receiver();
        if (this.level instanceof ServerLevel level && receiver != null) {
            GlobalPos self = GlobalPos.of(level.dimension(), pos);
            for (SpawnerBlockEntity spawner : receiver.spawners(level)) {
                ControlledSpawner control = ControlledSpawner.of(spawner.getSpawner());
                if (self.equals(control.nookworks$heldBy()) && control.nookworks$release()) {
                    SpawnerHold.changed(level, spawner);
                }
            }
        }
    }

    /** The screen, and the receiver's lines with it; running, the sender's lines too, a little past the next period. */
    private void setLit(ServerLevel level, boolean lit) {
        BlockState state = this.getBlockState();
        if (state.getValue(SpawnerControllerBlock.LIT) != lit) {
            level.setBlock(this.worldPosition, state.setValue(SpawnerControllerBlock.LIT, lit), Block.UPDATE_ALL);
        }
        lightReceiver(level, this.worldPosition.below(), lit);
        SpawnerLinkReceiverBlockEntity receiver = this.receiver();
        if (lit && receiver != null && receiver.link() != null && receiver.link().dimension().equals(level.dimension())) {
            SpawnerLinkSenderBlock.keepLit(level, receiver.link().pos(), level.getGameTime() + PERIOD * 2 + 5);
        }
    }

    private static void lightReceiver(Level level, BlockPos pos, boolean lit) {
        BlockState below = level.getBlockState(pos);
        if (below.getBlock() instanceof SpawnerLinkReceiverBlock && below.getValue(SpawnerLinkReceiverBlock.LIT) != lit) {
            level.setBlock(pos, below.setValue(SpawnerLinkReceiverBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SpawnerControllerMenu(containerId, inventory, this.data, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Settings", SpawnerSettings.CODEC, this.settings);
        this.redstone.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.settings = input.read("Settings", SpawnerSettings.CODEC).orElse(SpawnerSettings.DEFAULT);
        this.redstone = RedstoneMode.load(input);
    }
}
