package dev.nookworks.machine;

import java.util.List;
import java.util.Optional;

import dev.nookworks.Nookworks;
import dev.nookworks.block.ElectricFurnaceBlock;
import dev.nookworks.experience.ExperienceBottling;
import dev.nookworks.experience.ExperienceBuffer;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Electric Furnace: smelts whatever a furnace smelts, by the furnace's own recipes, in half the time on 40 FE/t - a
 * coal's worth of Generator power smelts sixteen items, twice what the coal would in a furnace, so a Generator running
 * on piped oil or biodiesel is a smeltery that feeds itself. Input from any side; the results collect in its output
 * and go down into the container below. The experience a furnace would give is kept as Liquid Experience, as the
 * Sealed Retort keeps its own, for a pipe to draw off; broken, it pours out as orbs.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public class ElectricFurnaceBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 40;
    public static final int CAPACITY = 20_000;
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int EXPERIENCE_CAPACITY = 1_000;
    /** The fill box: an empty bucket or bottle in, filled with the experience kept, out. */
    public static final int FILL = 2;
    public static final int FILLED = 3;
    private static final int[] INPUT_SLOTS = {INPUT, FILL};
    private static final int[] OUTPUT_SLOTS = {OUTPUT, FILLED};

    private int progress;
    private int cookTime;
    /** When the outputs are next passed on below: a hopper's beat after a try that found no room. */
    private final OutputRetry outputRetry = new OutputRetry();
    /** The last input looked up, and what it smelts by: a lookup a tick for the same stack is wasted. */
    private ItemStack lookedUp = ItemStack.EMPTY;
    private Optional<RecipeHolder<SmeltingRecipe>> recipe = Optional.empty();
    /** The datapack reload the lookup was made under: after /reload the recipe may have changed or gone. */
    private int lookedUpUnder = -1;
    private static volatile int reloads;

    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            reloads++; // every player at once: a /reload, not a player joining
        }
    }

    /** The experience the smelting left behind: taken from, never filled from outside. */
    private final ExperienceBuffer experience =
            new ExperienceBuffer(EXPERIENCE_CAPACITY, this::setChanged);

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_FURNACE.get(), pos, state, 4, CAPACITY, 200, 0);
    }

    private Optional<RecipeHolder<SmeltingRecipe>> recipeFor(ServerLevel level, ItemStack input) {
        // a red-hot ingot is asked afresh: whether it heats changes as it cools, its stack does not (r14 re-check)
        if (input.is(ModItems.RED_HOT_VELARIUM_INGOT.get()) || !ItemStack.isSameItemSameComponents(input, this.lookedUp)
                || this.lookedUpUnder != reloads) {
            this.lookedUp = input.copyWithCount(1);
            this.lookedUpUnder = reloads;
            this.recipe = input.isEmpty() ? Optional.empty()
                    : level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
        }
        return this.recipe;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectricFurnaceBlockEntity furnace) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        furnace.experience.push(server, pos, furnace);
        if (ExperienceBottling.fill(furnace.items, FILL, FILLED, furnace.experience)) {
            furnace.setChanged();
        }
        furnace.flush(server, pos);
        boolean lit = furnace.showsLit(furnace.work(server, pos));
        if (state.getValue(ElectricFurnaceBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(ElectricFurnaceBlock.LIT, lit), 3);
        }
    }

    /** One tick of smelting; whether it ran. */
    private boolean work(ServerLevel level, BlockPos pos) {
        ItemStack input = this.items.get(INPUT);
        var holder = this.recipeFor(level, input);
        if (holder.isEmpty()) {
            this.progress = 0;
            this.status = MachineStatus.IDLE;
            return false;
        }
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        ItemStack result = holder.get().value().assemble(recipeInput);
        ItemStack out = this.items.get(OUTPUT);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, result) || out.getCount() + result.getCount() > out.getMaxStackSize())) {
            this.status = MachineStatus.OUTPUT_BLOCKED;
            return false;
        }
        if (!this.redstoneAllows()) {
            this.status = MachineStatus.REDSTONE;
            return false;
        }
        if (this.energyStored() < this.workCost(FE_PER_TICK)) {
            this.status = MachineStatus.NO_POWER;
            return false;
        }
        this.status = MachineStatus.RUNNING;
        this.cookTime = Math.max(1, MachineTuning.time(holder.get().value().cookingTime() / 2));
        this.payWork(FE_PER_TICK);
        if (++this.progress >= this.cookTime) {
            this.progress = 0;
            input.shrink(1);
            if (out.isEmpty()) {
                this.items.set(OUTPUT, result);
            } else {
                out.grow(result.getCount());
            }
            this.keepExperience(holder.get().value().experience());
            this.flush(level, pos);
        }
        this.setChanged();
        return true;
    }

    @Override
    protected void swapped(int slot) {
        if (slot == INPUT) {
            this.progress = 0;
        }
    }

    /**
     * Moves what it has made down into the container below, as much as goes - with no room there at the last try, a
     * hopper's beat later ({@link OutputRetry}).
     */
    private void flush(ServerLevel level, BlockPos pos) {
        if (!this.outputRetry.due(level)) {
            return;
        }
        boolean waiting = false;
        boolean moved = false;
        for (int slot : OUTPUT_SLOTS) {
            ItemStack out = this.items.get(slot);
            if (!out.isEmpty()) {
                waiting = true;
                // what it smelts goes out whatever is below; a filled bottle only into a container, or waits in its box
                ItemStack rest = slot == FILLED ? MachineIO.intoBelow(level, pos, out) : MachineIO.deliverBelow(level, pos, out);
                if (rest.getCount() != out.getCount()) {
                    this.items.set(slot, rest);
                    this.setChanged();
                    moved = true;
                }
            }
        }
        this.outputRetry.tried(level, waiting && !moved);
    }

    // ---- the experience a furnace would have given ----

    public ResourceHandler<FluidResource> experienceHandler() {
        return this.experience;
    }

    public int experienceStored() {
        return this.experience.stored();
    }

    /** Keeps {@code points} of experience as liquid; what no longer fits comes out on top as orbs. */
    public void keepExperience(float points) { // public for tests
        this.experience.keep(this.level, this.worldPosition, points);
    }

    /** Broken, it pours out the experience it held as orbs. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        this.experience.spill(this.level, pos);
    }

    // ---- the screen ----

    @Override
    public MachineLayout layout() {
        return MachineLayout.ELECTRIC_FURNACE;
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
        return Math.max(1, this.cookTime);
    }

    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.EXPERIENCE, this.experienceStored(), EXPERIENCE_CAPACITY));
    }

    @Override
    public Component status() {
        return this.progress > 0
                ? Component.translatable("nookworks.machine.electric_furnace.working", this.energyText(), this.progress * 100 / Math.max(1, this.cookTime))
                : Component.translatable("nookworks.machine.idle", this.energyText());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", this.progress);
        output.putInt("CookTime", this.cookTime);
        this.experience.serialize(output.child("Experience"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("Progress", 0);
        this.cookTime = input.getIntOr("CookTime", 0);
        input.child("Experience").ifPresent(this.experience::deserialize);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FILL) {
            return ExperienceBottling.fillable(stack);
        }
        return slot == INPUT && this.level != null
                && this.level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return (slot == OUTPUT || slot == FILLED) && side == Direction.DOWN;
    }
}
