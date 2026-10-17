package dev.nookworks.cooking;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.Move;
import dev.nookworks.machine.CrankMotion;
import dev.nookworks.machine.Crankable;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A kitchen machine worked by its handle, as the Rice Polisher is - by hand or by a Motor ({@link Crankable}): what
 * goes in at the top comes out as its {@link HandWork} entry says once the handle has gone round enough, into a
 * container under it or else one in front. With nowhere to go it gathers inside (a sneaking empty hand takes it out)
 * until there is no more room, and then the handle will not turn, so a Motor on it stops instead of spilling.
 */
public abstract class HandMachineBlockEntity extends MachineBlockEntity implements Crankable {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    private static final int[] INPUT_SLOTS = {INPUT};
    private static final int[] OUTPUT_SLOTS = {OUTPUT};

    private final DataMapType<Item, HandWork> work;
    private final CrankMotion motion = new CrankMotion();
    private int turns;

    protected HandMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, DataMapType<Item, HandWork> work) {
        super(type, pos, state, 2, 0, 0, 0);
        this.work = work;
    }

    /** What the input makes, or null for nothing. */
    public @Nullable HandWork recipe() {
        return HandWork.of(this.work, this.items.get(INPUT));
    }

    /** The sound of one turn. */
    protected abstract SoundEvent turnSound();

    /** The translation key of its status line, which takes what is in and how it is going. */
    protected abstract String statusKey();

    public int turns() {
        return this.turns;
    }

    /** Room for one more piece of work's output. */
    private boolean room(HandWork recipe) {
        ItemStack out = this.items.get(OUTPUT);
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, recipe.output())
                && out.getCount() + recipe.count() <= out.getMaxStackSize();
    }

    @Override
    public boolean wantsTurn() {
        HandWork recipe = this.recipe();
        return recipe != null && this.room(recipe);
    }

    @Override
    public void turn(ServerLevel level) {
        HandWork recipe = this.recipe();
        if (recipe == null) {
            this.status = MachineStatus.IDLE;
            return;
        }
        if (!this.room(recipe)) {
            this.status = MachineStatus.OUTPUT_BLOCKED;
            return;
        }
        this.status = MachineStatus.RUNNING;
        CrankMotion.announce(level, this.worldPosition, this.getBlockState());
        level.playSound(null, this.worldPosition, this.turnSound(), SoundSource.BLOCKS, 0.35F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        if (++this.turns >= recipe.turns()) {
            this.turns = 0;
            this.items.get(INPUT).shrink(1);
            ItemStack out = this.items.get(OUTPUT);
            if (out.isEmpty()) {
                this.items.set(OUTPUT, recipe.output());
            } else {
                out.grow(recipe.count());
            }
            this.pushOut(level);
        }
        this.setChanged();
    }

    @Override
    public CrankMotion motion() {
        return this.motion;
    }

    /** A turn announced by the server ({@link CrankMotion#announce}): the handle goes round. */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == CrankMotion.EVENT) {
            if (this.level != null && this.level.isClientSide()) {
                this.motion.turned(this.level.getGameTime());
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Hands what it has made on, now and then: to a container below, else in front. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HandMachineBlockEntity machine) {
        if (level instanceof ServerLevel server && level.getGameTime() % 8 == 0) {
            machine.pushOut(server);
        }
    }

    private void pushOut(ServerLevel level) {
        ItemStack stack = this.items.get(OUTPUT);
        Direction front = this.getBlockState().getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        for (Direction side : new Direction[] {Direction.DOWN, front}) {
            if (stack.isEmpty() || !level.isLoaded(this.worldPosition.relative(side))) {
                continue;
            }
            ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, this.worldPosition.relative(side), side.getOpposite());
            if (target != null) {
                int moved = Move.insert(target, ItemResource.of(stack), stack.getCount());
                if (moved > 0) {
                    stack.shrink(moved);
                    this.setChanged();
                }
            }
        }
    }

    /**
     * Everything made and waiting, for a sneaking empty hand - or, with nothing made yet, what was put in to be worked:
     * a stack of wheat dropped in by mistake comes back out, rather than having to be ground through or the machine broken.
     */
    public ItemStack takeOut() {
        ItemStack out = this.removeItemNoUpdate(OUTPUT);
        if (out.isEmpty()) {
            out = this.removeItemNoUpdate(INPUT);
            this.turns = 0;
        }
        this.setChanged();
        return out;
    }

    @Override
    public Component status() {
        ItemStack input = this.items.get(INPUT);
        HandWork recipe = this.recipe();
        Component state = recipe == null ? Component.translatable(MachineStatus.IDLE.translationKey())
                : !this.room(recipe) ? Component.translatable(MachineStatus.OUTPUT_BLOCKED.translationKey())
                : Component.translatable("nookworks.crank.turns", this.turns, recipe.turns());
        return Component.translatable(this.statusKey(), input.getCount(), state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Turns", this.turns);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.turns = input.getIntOr("Turns", 0);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && HandWork.of(this.work, stack) != null;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT && side == Direction.DOWN;
    }
}
