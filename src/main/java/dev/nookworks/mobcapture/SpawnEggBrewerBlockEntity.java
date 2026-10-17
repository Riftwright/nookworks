package dev.nookworks.mobcapture;

import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.Readout;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Spawn Egg Brewer: a filled Mob Bottle plus an egg becomes that mob's spawn egg, brewing-stand style, fuelled by
 * blaze powder. The bottle is only emptied, not used up: it stays in its slot, the way a brewing stand leaves
 * its bottles where they were. Hoppers: top feeds bottles and eggs, the sides feed blaze powder, the bottom takes
 * spawn eggs and the emptied bottles.
 */
public class SpawnEggBrewerBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, Readout {
    public static final int BOTTLE = 0;
    public static final int EGG = 1;
    public static final int FUEL = 2;
    public static final int OUTPUT = 3;
    public static final int BREW_TIME = 200;
    public static final int BREWS_PER_FUEL = 10;

    private static final int[] TOP = {BOTTLE, EGG};
    private static final int[] SIDES = {FUEL};
    private static final int[] BOTTOM = {OUTPUT, BOTTLE};

    private final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private int progress;
    private int fuel;
    private RedstoneMode redstone = RedstoneMode.DEFAULT;
    /** Whether the redstone mode is holding a brew back right now. */
    private boolean held;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> SpawnEggBrewerBlockEntity.this.progress;
                case 1 -> SpawnEggBrewerBlockEntity.this.fuel;
                case 2 -> SpawnEggBrewerBlockEntity.this.redstone.ordinal();
                case 3 -> SpawnEggBrewerBlockEntity.this.held ? 1 : 0;
                default -> SpawnEggBrewerBlockEntity.this.blocked() ? 1 : 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                SpawnEggBrewerBlockEntity.this.progress = value;
            } else if (index == 1) {
                SpawnEggBrewerBlockEntity.this.fuel = value;
            }
        }

        @Override
        public int getCount() {
            return SpawnEggBrewerMenu.DATA_COUNT;
        }
    };

    public SpawnEggBrewerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPAWN_EGG_BREWER.get(), pos, state);
    }

    public int progress() {
        return this.progress;
    }

    public int fuel() {
        return this.fuel;
    }

    public RedstoneMode redstoneMode() {
        return this.redstone;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        this.redstone = mode;
        this.setChanged();
    }

    /**
     * A bottle with a mob in it that could have gone in: a boss in a bottle a creative player made up is no more brewed
     * into an egg than it is let out.
     */
    public static boolean isFilledBottle(ItemStack stack) {
        EntityType<?> type = stack.is(ModItems.MOB_BOTTLE.get()) ? MobBottleItem.captured(stack) : null;
        return type != null && MobBottleItem.bottleable(type);
    }

    public static boolean isEgg(ItemStack stack) {
        return stack.is(ItemTags.EGGS);
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(Items.BLAZE_POWDER);
    }

    /** The spawn egg the current inputs make, or empty. */
    public ItemStack result() {
        ItemStack bottle = this.items.get(BOTTLE);
        if (!isFilledBottle(bottle) || !isEgg(this.items.get(EGG))) {
            return ItemStack.EMPTY;
        }
        EntityType<?> type = MobBottleItem.captured(bottle);
        Optional<Holder<Item>> egg = MobBottleItem.spawnEgg(type);
        return egg.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private boolean outputAccepts(ItemStack result) {
        ItemStack out = this.items.get(OUTPUT);
        return out.isEmpty() || (ItemStack.isSameItemSameComponents(out, result) && out.getCount() < out.getMaxStackSize());
    }

    /** Whether a brew is ready but the output cannot take its egg. */
    private boolean blocked() {
        ItemStack result = this.result();
        return !result.isEmpty() && !this.outputAccepts(result);
    }

    /**
     * Where a brew stands, from what is in it: as its screen shows it and Jade says it. A bottle in its slot counts only
     * with a mob in it: the empty one a brew leaves there is not a brew under way.
     */
    public static MachineStatus status(boolean held, boolean filledBottle, boolean egg, int fuel, boolean fuelItem, boolean blocked) {
        if (held) {
            return MachineStatus.REDSTONE;
        }
        if (!filledBottle || !egg) {
            return MachineStatus.IDLE;
        }
        if (blocked) {
            return MachineStatus.OUTPUT_BLOCKED;
        }
        return fuel <= 0 && !fuelItem ? MachineStatus.NO_FUEL : MachineStatus.RUNNING;
    }

    /** What may go in {@code slot}: a filled bottle, an egg, its fuel. The screen's own copy asks the same. */
    public static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case BOTTLE -> isFilledBottle(stack);
            case EGG -> isEgg(stack);
            case FUEL -> isFuel(stack);
            default -> false;
        };
    }

    /** That, in words: how far along a brew is, or what it waits for. */
    public static Component statusLine(MachineStatus status, int progress) {
        return status == MachineStatus.RUNNING
                ? Component.translatable("gui.nookworks.machine.running_percent", progress * 100 / BREW_TIME)
                : status == MachineStatus.IDLE ? Component.translatable("gui.nookworks.spawn_egg_brewer.idle")
                : Component.translatable(status.translationKey());
    }

    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(statusLine(status(this.held, isFilledBottle(this.items.get(BOTTLE)), !this.items.get(EGG).isEmpty(), this.fuel,
                !this.items.get(FUEL).isEmpty(), this.blocked()), this.progress));
        if (this.fuel > 0) {
            lines.accept(Component.translatable("nookworks.spawn_egg_brewer.fuel_left", this.fuel));
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpawnEggBrewerBlockEntity brewer) {
        ItemStack result = brewer.result();
        boolean canBrew = !result.isEmpty() && brewer.outputAccepts(result);
        // Held back by redstone, a brew waits where it is rather than starting over.
        brewer.held = canBrew && !brewer.redstone.allows(level.hasNeighborSignal(pos));
        if (brewer.held) {
            return;
        }
        if (canBrew && brewer.fuel <= 0 && isFuel(brewer.items.get(FUEL))) {
            brewer.items.get(FUEL).shrink(1);
            brewer.fuel = BREWS_PER_FUEL;
            brewer.setChanged();
        }
        if (!canBrew || brewer.fuel <= 0) {
            if (brewer.progress != 0) {
                brewer.progress = 0;
                brewer.setChanged();
            }
            return;
        }
        if (++brewer.progress < BREW_TIME) {
            brewer.setChanged(); // so a save part way through keeps the progress
            return;
        }
        brewer.progress = 0;
        brewer.fuel--;
        brewer.items.set(BOTTLE, new ItemStack(ModItems.MOB_BOTTLE.get()));
        brewer.items.get(EGG).shrink(1);
        ItemStack out = brewer.items.get(OUTPUT);
        if (out.isEmpty()) {
            brewer.items.set(OUTPUT, result);
        } else {
            out.grow(1);
        }
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
        brewer.setChanged();
    }

    // ---- Menu ----

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SpawnEggBrewerMenu(containerId, inventory, this, this.data, this);
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("Progress", this.progress);
        output.putInt("Fuel", this.fuel);
        this.redstone.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear(); // what is read fills only the slots saved: a slot emptied by /data stayed as it was
        ContainerHelper.loadAllItems(input, this.items);
        this.progress = input.getIntOr("Progress", 0);
        this.fuel = input.getIntOr("Fuel", 0);
        this.redstone = RedstoneMode.load(input);
    }

    // ---- Container ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        // An emptied bottle leaves by the bottom too, so a hopper line clears it for the next filled one.
        return slot == OUTPUT || (slot == BOTTLE && !isFilledBottle(stack));
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(slot, stack);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(this.items, slot, count);
        if (!taken.isEmpty()) {
            this.setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }
}
