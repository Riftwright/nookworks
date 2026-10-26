package dev.nookworks.recycling;

import java.util.IdentityHashMap;
import java.util.Map;

import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.OutputRetry;
import dev.nookworks.registry.ModRecycling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Compressor: nine of an item into what the crafting table makes of nine of it ({@link Compressing}), every 40 ticks at
 * 20 FE/t. Four input slots, one kind of item each: a few of one thing left waiting for its ninth (eight iron nuggets
 * from a Sifter) no longer stop the rest coming in (the user, 2026-10-20: 半端な鉄塊が入っているとすぐ詰まる). It presses
 * whichever has nine. Input from any side; results go into the container below or drop out.
 */
public class CompressorBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 20;
    public static final int TICKS_PER_PRESS = 40;
    public static final int CAPACITY = 10_000;
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    /** The input slots: 0 as before, and 2-4 added beside it (a world's slot 1 stays the output). */
    private static final int[] INPUT_SLOTS = {INPUT, 2, 3, 4};
    private static final int[] OUTPUT_SLOTS = {OUTPUT};

    /** What nine of each item press into, as last looked up (the recipes reloaded, a new manager: looked up again). */
    private static final Map<Item, ItemStack> RESULTS = new IdentityHashMap<>();
    private static RecipeManager resultsFrom;

    private int progress;
    /** The input slot being pressed, while progress is under way. */
    private int pressing = INPUT;
    private final OutputRetry outputRetry = new OutputRetry();

    public CompressorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRecycling.COMPRESSOR_ENTITY.get(), pos, state, 5, CAPACITY, 200, 0);
    }

    /** What nine of {@code stack} press into, or empty. */
    public static synchronized ItemStack resultOf(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty() || stack.getComponentsPatch().size() > 0) {
            return ItemStack.EMPTY; // named or otherwise changed things are not pressed
        }
        RecipeManager recipes = level.getServer().getRecipeManager();
        if (recipes != resultsFrom) {
            RESULTS.clear();
            resultsFrom = recipes;
        }
        return RESULTS.computeIfAbsent(stack.getItem(), item -> Compressing.result(level, new ItemStack(item))).copy();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CompressorBlockEntity press) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!press.flush(server, pos)) {
            press.status = MachineStatus.OUTPUT_BLOCKED;
            return;
        }
        int slot = press.ready(server);
        if (slot < 0) {
            press.progress = 0;
            press.status = press.anyWaiting(server) ? MachineStatus.NEEDS_NINE : MachineStatus.IDLE;
            return;
        }
        if (slot != press.pressing) {
            press.pressing = slot;
            press.progress = 0;
        }
        ItemStack in = press.items.get(slot);
        ItemStack result = resultOf(server, in);
        if (!press.redstoneAllows()) {
            press.status = MachineStatus.REDSTONE;
            return;
        }
        if (press.energyStored() < press.workCost(FE_PER_TICK)) {
            press.status = MachineStatus.NO_POWER;
            return;
        }
        press.status = MachineStatus.RUNNING;
        press.payWork(FE_PER_TICK);
        if (++press.progress >= MachineTuning.time(TICKS_PER_PRESS)) {
            press.progress = 0;
            in.shrink(Compressing.TAKES);
            press.items.set(OUTPUT, result);
            press.flush(server, pos);
        }
        press.setChanged();
    }

    /** The input slot to press: the one being pressed if it still has nine, else the first that has. -1 for none. */
    private int ready(ServerLevel level) {
        if (this.full(level, this.pressing)) {
            return this.pressing;
        }
        for (int slot : INPUT_SLOTS) {
            if (this.full(level, slot)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean full(ServerLevel level, int slot) {
        ItemStack in = this.items.get(slot);
        return in.getCount() >= Compressing.TAKES && !resultOf(level, in).isEmpty();
    }

    /** Whether something that presses is waiting for its ninth. */
    private boolean anyWaiting(ServerLevel level) {
        for (int slot : INPUT_SLOTS) {
            if (!this.items.get(slot).isEmpty() && !resultOf(level, this.items.get(slot)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInput(int slot) {
        return slot == INPUT || slot >= 2 && slot <= 4;
    }

    @Override
    protected void swapped(int slot) {
        if (slot == this.pressing) {
            this.progress = 0;
        }
    }

    /** Moves the finished item out (as the Crusher's); true when the output slot is free. */
    private boolean flush(ServerLevel level, BlockPos pos) {
        ItemStack out = this.items.get(OUTPUT);
        if (out.isEmpty()) {
            return true;
        }
        if (!this.outputRetry.due(level)) {
            return false;
        }
        ItemStack rest = MachineIO.deliverBelow(level, pos, out);
        boolean moved = rest.getCount() != out.getCount();
        if (moved) {
            this.items.set(OUTPUT, rest);
            this.setChanged();
        }
        this.outputRetry.tried(level, !moved);
        return rest.isEmpty();
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.COMPRESSOR;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.workEnergy(FE_PER_TICK);
    }

    @Override
    public int guiProgress() {
        return this.progress;
    }

    @Override
    public int guiProgressMax() {
        return MachineTuning.time(TICKS_PER_PRESS);
    }

    @Override
    public Component status() {
        return this.progress > 0
                ? Component.translatable("nookworks.machine.compressor.working", this.energyText(),
                        MachineTuning.percent(this.progress, MachineTuning.time(TICKS_PER_PRESS)))
                : Component.translatable("nookworks.machine.idle", this.energyText());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", this.progress);
        output.putInt("Pressing", this.pressing);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("Progress", 0);
        int pressing = input.getIntOr("Pressing", INPUT);
        this.pressing = isInput(pressing) ? pressing : INPUT;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    /**
     * Only what presses; in a slot already holding something, only more of it, and into an empty slot only a kind no
     * other input slot holds (one kind spread over all four would leave the others no room).
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!isInput(slot)) {
            return false;
        }
        ItemStack in = this.items.get(slot);
        if (!in.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(in, stack)) {
                return false;
            }
        } else {
            for (int other : INPUT_SLOTS) {
                if (other != slot && ItemStack.isSameItemSameComponents(this.items.get(other), stack)) {
                    return false;
                }
            }
        }
        // the client cannot look the recipe up: it goes by what the server said presses
        return this.level instanceof ServerLevel server ? !resultOf(server, stack).isEmpty() : Compressing.pressesHere(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT && side == Direction.DOWN;
    }
}
