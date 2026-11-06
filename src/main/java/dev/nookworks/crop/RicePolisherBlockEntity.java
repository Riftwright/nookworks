package dev.nookworks.crop;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.machine.CrankMotion;
import dev.nookworks.machine.Crankable;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Rice Polisher: brown rice in at the top, worked by its handle - by hand, or by a Motor - and white rice out of the
 * front, with the bran rubbed off it: one Rice Bran for every {@link #GRAINS_PER_BRAN} grains. What it makes goes into a
 * hopper or chest under it, or else one in front of it, or else it drops out of the chute onto the ground just in front
 * ({@link MachineIO#handOut}); with a block in the way it gathers inside (a sneaking empty hand takes it out) until
 * there is no more room, and then the handle will not turn - a Motor on it stops.
 * <p>
 * It takes any {@code #c:crops/rice}, so Farmer's Delight's rice polishes too.
 */
public class RicePolisherBlockEntity extends MachineBlockEntity implements Crankable {
    /** The game tick its turn sound last played in (not saved). */
    private long soundAt = Long.MIN_VALUE;

    public static final TagKey<Item> RICE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "crops/rice"));
    public static final int TURNS = 3;
    public static final int GRAINS_PER_BRAN = 4;
    public static final int INPUT = 0;
    /** What it has polished, waiting to go out. */
    public static final int RICE_OUT = 1;
    public static final int BRAN_OUT = 2;
    private static final int[] INPUT_SLOTS = {INPUT};
    private static final int[] OUTPUT_SLOTS = {RICE_OUT, BRAN_OUT};

    private int turns;
    /** Grains polished towards the next bran. */
    private int grains;

    public RicePolisherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RICE_POLISHER.get(), pos, state, 3, 0, 0, 0);
    }

    public int turns() {
        return this.turns;
    }

    @Override
    public boolean wantsTurn() {
        return canPolish(this.items.get(INPUT)) && this.room();
    }

    /** Room inside for one more grain's worth (and its bran). */
    private boolean room() {
        return this.items.get(RICE_OUT).getCount() < this.items.get(RICE_OUT).getMaxStackSize()
                && this.items.get(BRAN_OUT).getCount() < this.items.get(BRAN_OUT).getMaxStackSize();
    }

    /** Hands what it has made on, now and then: to a container below, else in front, else out of its front. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RicePolisherBlockEntity polisher) {
        if (level instanceof ServerLevel server && level.getGameTime() % 8 == 0) {
            polisher.pushOut(server);
        }
    }

    private static boolean canPolish(ItemStack stack) {
        return stack.is(RICE) && !stack.is(ModCrops.WHITE_RICE.get());
    }

    @Override
    public void turn(ServerLevel level) {
        ItemStack input = this.items.get(INPUT);
        if (!canPolish(input)) {
            this.status = MachineStatus.IDLE;
            return;
        }
        if (!this.room()) {
            this.status = MachineStatus.OUTPUT_BLOCKED;
            return;
        }
        this.status = MachineStatus.RUNNING;
        CrankMotion.announce(level, this.worldPosition, this.getBlockState());
        // grain rubbing on grain, a fifth of a second: a grindstone pitched up ran, a turn every half second under a
        // Motor, into one squeal
        if (level.getGameTime() != this.soundAt) { // one sound a tick: nookTechnology's speed gear turns it up to four times at once, and four were a thump (r21)
            this.soundAt = level.getGameTime();
            level.playSound(null, this.worldPosition, ModSounds.RICE_POLISHER_TURN.get(), SoundSource.BLOCKS, 1.0F,
                    0.9F + level.getRandom().nextFloat() * 0.2F);
        }
        if (++this.turns >= TURNS) {
            this.turns = 0;
            input.shrink(1);
            this.keep(RICE_OUT, new ItemStack(ModCrops.WHITE_RICE.get()));
            if (++this.grains >= GRAINS_PER_BRAN) {
                this.grains = 0;
                this.keep(BRAN_OUT, new ItemStack(ModCrops.RICE_BRAN.get()));
            }
            this.pushOut(level);
        }
        this.setChanged();
    }

    private final CrankMotion motion = new CrankMotion();

    @Override
    public CrankMotion motion() {
        return this.motion;
    }

    /** A turn announced by the server ({@link dev.nookworks.machine.CrankMotion#announce}): the handle goes round. */
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

    private void keep(int slot, ItemStack stack) {
        ItemStack held = this.items.get(slot);
        if (held.isEmpty()) {
            this.items.set(slot, stack);
        } else {
            held.grow(stack.getCount());
        }
    }

    /** What it has made, into a container under it, else one in front of it, else out of its front. */
    private void pushOut(ServerLevel level) {
        Direction front = this.getBlockState().getValue(RicePolisherBlock.FACING);
        for (int slot : OUTPUT_SLOTS) {
            ItemStack stack = this.items.get(slot);
            int before = stack.getCount();
            MachineIO.handOut(level, this.worldPosition, front, stack);
            if (stack.getCount() != before) {
                this.setChanged();
            }
        }
    }

    /** Everything polished and waiting, for a sneaking empty hand. */
    public List<ItemStack> takeOut() {
        List<ItemStack> out = new ArrayList<>();
        for (int slot : OUTPUT_SLOTS) {
            if (!this.items.get(slot).isEmpty()) {
                out.add(this.removeItemNoUpdate(slot));
            }
        }
        // with nothing polished, the brown rice comes back, as a Hand Quern's wheat does: a stack of seed rice put in by
        // mistake had to be polished through or the machine broken (r21, 2026-11-06)
        if (out.isEmpty() && !this.items.get(INPUT).isEmpty()) {
            out.add(this.removeItemNoUpdate(INPUT));
            this.turns = 0;
        }
        this.setChanged();
        return out;
    }

    @Override
    public Component status() {
        ItemStack input = this.items.get(INPUT);
        Component state = !canPolish(input) ? Component.translatable(MachineStatus.IDLE.translationKey())
                : !this.room() ? Component.translatable(MachineStatus.OUTPUT_BLOCKED.translationKey())
                : Component.translatable("nookworks.crank.turns", this.turns, TURNS);
        return Component.translatable("nookworks.machine.rice_polisher", input.getCount(), state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Turns", this.turns);
        output.putInt("Grains", this.grains);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.turns = input.getIntOr("Turns", 0);
        this.grains = input.getIntOr("Grains", 0);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && canPolish(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot != INPUT && side == Direction.DOWN;
    }
}
