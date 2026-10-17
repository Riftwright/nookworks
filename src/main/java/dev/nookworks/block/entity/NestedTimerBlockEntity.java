package dev.nookworks.block.entity;

import java.util.function.Consumer;

import dev.nookworks.block.NestedTimerBlock;
import dev.nookworks.machine.Readout;
import dev.nookworks.menu.NestedTimerMenu;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Nested Timer's count. Every {@link #interval()} ticks a round starts: {@link #pulses()} pulses, {@link #gap()}
 * ticks apart - so a dispenser with a water bucket pours on the first and takes the water back on the second. Pulses
 * that would fall past the end of the round are not sent. While a signal holds the timer the count runs on but nothing
 * is sent, so letting go keeps the rhythm.
 * <p>
 * Each setting is one of a list of round values ({@link #INTERVALS}, {@link #GAPS}), which is what the screen's sliders
 * step through.
 */
public class NestedTimerBlockEntity extends BlockEntity implements MenuProvider, Readout {
    /** Its settings, in the words its screen uses. */
    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(this.pulses > 1
                ? Component.translatable("nookworks.nested_timer.readout", time(this.interval), this.pulses, time(this.gap))
                : Component.translatable("nookworks.nested_timer.readout.one", time(this.interval))); // no gap to tell of
        if (this.held()) {
            lines.accept(Component.translatable("gui.nookworks.nested_timer.status.held").withColor(0xA3A9B2));
        }
    }

    /**
     * "0.5 s", "12 s", "3 min", "1 min 30 s", as the player reads a time: the screen's words, and the readout's. Here,
     * not in the screen, as the readout is made on the server.
     */
    public static Component time(int ticks) {
        String k = "gui.nookworks.nested_timer.";
        if (ticks < 1_200) {
            return Component.translatable(k + "seconds", ticks % 20 == 0 ? Integer.toString(ticks / 20) : Double.toString(ticks / 20.0));
        }
        int seconds = ticks / 20;
        return seconds % 60 == 0 ? Component.translatable(k + "minutes", seconds / 60)
                : Component.translatable(k + "minutes_seconds", seconds / 60, seconds % 60);
    }

    /** Half a second up to an hour, in the steps a person would pick. */
    public static final int[] INTERVALS = {10, 20, 40, 60, 100, 200, 300, 400, 600, 900, 1_200, 2_400, 3_600, 6_000,
            12_000, 18_000, 24_000, 36_000, 48_000, 54_000, 72_000};
    /** Half a second up to a minute. */
    public static final int[] GAPS = {10, 20, 40, 60, 100, 200, 300, 400, 600, 900, 1_200};
    public static final int MAX_PULSES = 10;
    /** How long each pulse stays on: long enough for anything to see it, as an observer's is. */
    public static final int PULSE = 2;

    private int interval = 20;
    private int pulses = 1;
    private int gap = 20;
    private int elapsed;
    private int onFor;
    /** Whether this round's first pulse went out (so the rest of the round may follow). */
    private boolean fired;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> step(INTERVALS, NestedTimerBlockEntity.this.interval);
                case 1 -> NestedTimerBlockEntity.this.pulses;
                case 2 -> step(GAPS, NestedTimerBlockEntity.this.gap);
                case 3 -> NestedTimerBlockEntity.this.held() ? 1 : 0;
                default -> NestedTimerBlockEntity.this.getBlockState().getValue(NestedTimerBlock.POWERED) ? 1 : 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 5;
        }
    };

    public NestedTimerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NESTED_TIMER.get(), pos, state);
    }

    /** The step of {@code values} nearest to {@code ticks}. */
    public static int step(int[] values, int ticks) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (Math.abs(values[i] - ticks) < Math.abs(values[best] - ticks)) {
                best = i;
            }
        }
        return best;
    }

    public int interval() {
        return this.interval;
    }

    public int pulses() {
        return this.pulses;
    }

    public int gap() {
        return this.gap;
    }

    /** Sets the interval to the nearest step, and shows it on the meter. */
    public void setInterval(int ticks) {
        this.interval = INTERVALS[step(INTERVALS, ticks)];
        this.elapsed = Math.min(this.elapsed, this.interval - 1);
        if (this.level != null && !this.level.isClientSide()) {
            BlockState state = this.getBlockState();
            int bars = NestedTimerBlock.bars(this.interval);
            if (state.getValue(NestedTimerBlock.INTERVAL) != bars) {
                this.level.setBlock(this.worldPosition, state.setValue(NestedTimerBlock.INTERVAL, bars), Block.UPDATE_CLIENTS);
            }
        }
        this.setChanged();
    }

    public void setPulses(int pulses) {
        this.pulses = Math.max(1, Math.min(MAX_PULSES, pulses));
        this.setChanged();
    }

    /** Sets the time between pulses to the nearest step. */
    public void setGap(int ticks) {
        this.gap = GAPS[step(GAPS, ticks)];
        this.setChanged();
    }

    private boolean held() {
        return this.level != null && this.getBlockState().getBlock() instanceof NestedTimerBlock timer
                && timer.held(this.level, this.worldPosition, this.getBlockState());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NestedTimerBlockEntity timer) {
        if (timer.onFor > 0) {
            if (--timer.onFor == 0) {
                timer.output(level, pos, false);
            }
        } else if (state.getValue(NestedTimerBlock.POWERED)) {
            // On with no pulse running: the world was saved mid-pulse by an older build, or the state was set from
            // outside. Let go now, or the output would stay on until the next round and swallow its rising edge.
            timer.output(level, pos, false);
        }
        if (++timer.elapsed >= timer.interval) {
            timer.elapsed = 0;
            timer.fired = !timer.held();
            if (timer.fired) {
                timer.pulse(level, pos);
            }
        } else if (timer.fired && timer.elapsed % timer.gap == 0 && timer.elapsed / timer.gap < timer.pulses && !timer.held()) {
            // A signal that arrives part-way through a round holds the rest of it, as the screen says.
            timer.pulse(level, pos);
        }
    }

    private void pulse(Level level, BlockPos pos) {
        this.onFor = PULSE;
        this.output(level, pos, true);
        level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.2F, 1.4F);
        this.setChanged();
    }

    private void output(Level level, BlockPos pos, boolean on) {
        BlockState now = level.getBlockState(pos);
        if (now.getBlock() instanceof NestedTimerBlock timer && now.getValue(NestedTimerBlock.POWERED) != on) {
            BlockState next = now.setValue(NestedTimerBlock.POWERED, on);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            timer.updateFront(level, pos, next);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nookworks.nested_timer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new NestedTimerMenu(containerId, this.data, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Interval", this.interval);
        output.putInt("Pulses", this.pulses);
        output.putInt("Gap", this.gap);
        output.putInt("Elapsed", this.elapsed);
        output.putBoolean("Fired", this.fired);
        output.putInt("OnFor", this.onFor);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.interval = INTERVALS[step(INTERVALS, input.getIntOr("Interval", 20))];
        this.pulses = Math.max(1, Math.min(MAX_PULSES, input.getIntOr("Pulses", 1)));
        this.gap = GAPS[step(GAPS, input.getIntOr("Gap", 20))];
        this.elapsed = Math.min(input.getIntOr("Elapsed", 0), this.interval - 1);
        this.fired = input.getBooleanOr("Fired", false);
        this.onFor = Math.max(0, Math.min(PULSE, input.getIntOr("OnFor", 0)));
    }
}
