package dev.nookworks.oil;

import java.util.function.Consumer;

import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.CrankMotion;
import dev.nookworks.machine.Crankable;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Oil Press: a screw press worked by its handle. Seeds go in (by hand or hopper); each turn of the handle - by hand,
 * or by a Motor set against it - squeezes the one on the plate a little more, and after enough turns it gives up its
 * oil (see {@link PressingRecipe}) and now and then a cake of what is left. The oil gathers in a tank of four buckets,
 * is poured out into bottles and buckets, and runs off by itself into any Copper Pipe, tank or machine beside or under
 * it. The cakes go into a container under it, else one in front, else out of the spout at its front onto the ground
 * just there ({@link MachineIO#handOut}): with a pipe under it for the oil, a hopper in the floor in front or a Magnet
 * Chest gathers them (the user, 2026-10-31). With a block in the way they wait inside, and at a stack the press stops.
 */
public class OilPressBlockEntity extends MachineBlockEntity implements Crankable {
    /** The game tick its turn sound last played in (not saved). */
    private long soundAt = Long.MIN_VALUE;
    private long finishSoundAt = Long.MIN_VALUE;

    public static final int TANK = 4_000;
    public static final int INPUT = 0;
    public static final int CAKE = 1;
    private static final int[] INPUT_SLOTS = {INPUT};
    private static final int[] CAKE_SLOTS = {CAKE};

    private int turns;
    /** What the turns so far were put into: another seed on the plate starts from nothing. */
    private ItemStack turning = ItemStack.EMPTY;

    /** What it has pressed: poured or piped out, never filled from outside. */
    private final FluidStacksResourceHandler oil = new OneTank(TANK) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.getFluid() == OilFluids.SOURCE.get();
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            OilPressBlockEntity.this.setChanged();
            OilPressBlockEntity.this.showOil();
        }
    };

    /** The oil in the tray as the model shows it ({@link OilPressBlock#OIL}). */
    public static int oilStep(int stored) {
        return stored <= 0 ? 0 : stored < TANK / 2 ? 1 : 2;
    }

    private void showOil() {
        SightGlass.show(this, OilPressBlock.OIL, oilStep(this.stored()));
    }

    /**
     * A press loaded from a world saved before the tray showed its oil, or before it showed its pipes' ports (set by
     * MachineBlockEntity's): set it right once the chunk is in.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel server) {
            server.getServer().execute(this::showOil);
        }
    }

    public OilPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OIL_PRESS.get(), pos, state, 2, 0, 0, 0);
    }

    public ResourceHandler<FluidResource> fluidHandler() {
        return this.oil;
    }

    public int stored() {
        return this.oil.getAmountAsInt(0);
    }

    public int turns() {
        return this.turns;
    }

    /** Takes {@code amount} of oil out, all or nothing: for a bottle or a bucket. */
    public boolean pour(int amount) {
        if (this.stored() < amount) {
            return false;
        }
        this.oil.set(0, FluidResource.of(OilFluids.SOURCE.get()), this.stored() - amount);
        return true;
    }

    private MachineStatus blocked() {
        PressingRecipe recipe = PressingRecipe.of(this.items.get(INPUT));
        if (recipe == null) {
            return MachineStatus.IDLE;
        }
        if (TANK - this.stored() < recipe.oil()) {
            return MachineStatus.TANK_FULL;
        }
        ItemStack cake = this.items.get(CAKE);
        return recipe.cake() > 0 && cake.getCount() >= cake.getMaxStackSize() ? MachineStatus.OUTPUT_BLOCKED : null;
    }

    @Override
    public boolean wantsTurn() {
        return this.blocked() == null;
    }

    @Override
    public void turn(ServerLevel level) {
        MachineStatus blocked = this.blocked();
        this.status = blocked == null ? MachineStatus.RUNNING : blocked;
        if (blocked != null) {
            return;
        }
        ItemStack input = this.items.get(INPUT);
        PressingRecipe recipe = PressingRecipe.of(input);
        if (this.turning.isEmpty()) {
            this.turning = input.copyWithCount(1); // just loaded, or fresh: the turns are this input's
        } else if (!ItemStack.isSameItemSameComponents(input, this.turning)) {
            this.turns = 0; // sunflowers taken off after seven turns, pumpkin seeds put on: they start over
            this.turning = input.copyWithCount(1);
        }
        CrankMotion.announce(level, this.worldPosition, this.getBlockState());
        if (level.getGameTime() != this.soundAt) { // one sound a tick: nookTechnology's speed gear turns it up to four times at once, and four were a thump (r21)
            this.soundAt = level.getGameTime();
            level.playSound(null, this.worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35F, 0.7F + level.getRandom().nextFloat() * 0.2F);
        }
        if (++this.turns >= recipe.turns()) {
            this.turns = 0;
            input.shrink(1);
            this.oil.set(0, FluidResource.of(OilFluids.SOURCE.get()), this.stored() + recipe.oil());
            if (level.getRandom().nextFloat() < recipe.cake()) {
                ItemStack cake = this.items.get(CAKE);
                if (cake.isEmpty()) {
                    this.items.set(CAKE, new ItemStack(ModOil.OIL_CAKE.get()));
                } else {
                    cake.grow(1);
                }
                this.handOutCakes(level);
            }
            if (level.getGameTime() != this.finishSoundAt) { // once a tick too: a geared Motor finishes two seeds in one
                this.finishSoundAt = level.getGameTime();
                level.playSound(null, this.worldPosition, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.6F, 0.8F);
            }
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, OilPressBlockEntity press) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (press.stored() > 0 && server.getGameTime() % 10 == 0) {
            press.push(server, pos);
        }
        if (!press.items.get(CAKE).isEmpty() && server.getGameTime() % 8 == 0) {
            press.handOutCakes(server); // ones that waited for a block in front to go
        }
    }

    /** The cakes it holds, sent on: under it, else in front, else out of its spout. */
    private void handOutCakes(ServerLevel level) {
        ItemStack cakes = this.items.get(CAKE);
        int before = cakes.getCount();
        MachineIO.handOut(level, this.worldPosition, this.getBlockState().getValue(OilPressBlock.FACING), cakes);
        if (cakes.getCount() != before) {
            this.setChanged();
        }
    }

    /**
     * Runs the oil off into anything beside or under it that takes it - not across a claim's edge where claims keep it
     * from going ({@link DrawRights#mayMove}).
     */
    private void push(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (this.stored() <= 0) {
                return;
            }
            BlockPos next = pos.relative(side);
            if (side == Direction.UP || !level.isLoaded(next)) {
                continue;
            }
            ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, next, side.getOpposite());
            if (target != null && DrawRights.mayMove(level, pos, this, next, level.getBlockEntity(next))) {
                Move.transfer(this.oil, target, this.oil.getResource(0), this.stored());
            }
        }
    }

    @Override
    public Component status() {
        return Component.translatable("nookworks.machine.oil_press", this.stored(), TANK, this.state());
    }

    /** Turns made of the turns its job needs, or what holds it up. */
    private Component state() {
        PressingRecipe recipe = PressingRecipe.of(this.items.get(INPUT));
        MachineStatus blocked = this.blocked();
        return blocked != null ? Component.translatable(blocked.translationKey())
                : Component.translatable("nookworks.crank.turns", this.turns, recipe.turns());
    }

    /** To Jade, only how far it is: its oil Jade shows itself (r15 fix check). */
    @Override
    public void jade(java.util.function.Consumer<Component> lines) {
        lines.accept(this.state());
    }

    /** The seeds put in, given back to a sneaking hand with no cakes inside: the turns made on them go with them. */
    public ItemStack takeInput() {
        this.turns = 0;
        this.turning = ItemStack.EMPTY;
        ItemStack seeds = this.removeItemNoUpdate(INPUT);
        this.setChanged();
        return seeds;
    }

    @Override
    protected void swapped(int slot) {
        if (slot == INPUT) {
            this.turns = 0;
            this.turning = ItemStack.EMPTY;
        }
    }

    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        out.accept(Tanks.held(this.oil));
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        Tanks.restore(this.oil, fluid);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Turns", this.turns);
        this.oil.serialize(output.child("Oil"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.turns = input.getIntOr("Turns", 0);
        input.child("Oil").ifPresent(this.oil::deserialize);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? CAKE_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && PressingRecipe.of(stack) != null;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == CAKE && side == Direction.DOWN;
    }
}
