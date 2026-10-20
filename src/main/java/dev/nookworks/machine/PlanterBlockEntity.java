package dev.nookworks.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.PlanterBlock;
import dev.nookworks.crop.CornBlock;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Planter: holds 9 stacks of seeds and saplings and, powered by FE, plants them in the field in front of it:
 * {@link #WIDTH} wide and {@link #DEPTH} deep, at its own height (so the soil is one block lower, as when the
 * planter stands on the same ground as the field). One spot every {@link #INTERVAL} ticks; anything whose
 * block is a plant and can grow on that spot is used, in slot order. Hoppers and pipes refill it from any side, and
 * none take anything out of it, as with a machine's input slots: a hopper under it drained its seeds (r8). A player
 * takes its stock back by hand, in its screen.
 * <p>
 * It plants as the player who placed it ({@link Permission}), so a claim mod lets it into its owner's fields and no
 * one else's.
 */
public class PlanterBlockEntity extends BaseContainerBlockEntity implements MachineInfo, WorldlyContainer, DrawRights.Owned {
    public static final int WIDTH = 3;
    public static final int DEPTH = 5;
    public static final int INTERVAL = 10;
    public static final int FE_PER_PLANT = 20;
    public static final int CAPACITY = 10_000;
    private static final int[] SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    private RedstoneMode redstone = RedstoneMode.DEFAULT;
    private MachineStatus status = MachineStatus.IDLE;
    private int lampLeft;
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, 500, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            MachineBlockEntity.savePower(PlanterBlockEntity.this);
        }
    };
    /** Who placed it, whose say-so it plants with; null for one placed before owners were kept. */
    private @Nullable UUID owner;
    /** What its owner may take power from across a claim's edge, as last asked ({@link DrawRights}). */
    private final DrawRights drawRights = new DrawRights();
    /** Whether the last search found a spot it could plant but was not let: the status says so rather than "no space". */
    private boolean refused;
    /**
     * The spots a claim refused it, and until when (game time) that answer is kept: each asking sets a block in the
     * chunk and posts a place event every listener hears, and a planter facing a claimed field asked for every spot
     * every beat (r16 N3). Kept five minutes or a little less, as {@link DrawRights} keeps its answers; only refusals -
     * a spot it may plant it plants, asked as it goes. Forgotten when its owner changes, and not saved.
     */
    private final Map<BlockPos, Long> refusedUntil = new HashMap<>();

    public PlanterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLANTER.get(), pos, state);
    }

    public SimpleEnergyHandler energy() {
        return this.energy;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PlanterBlockEntity planter) {
        // each on a beat of its own, not every Planter on the same tick
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), MachineTuning.time(INTERVAL)) == 0 && level instanceof ServerLevel server) {
            planter.work(server);
        }
    }

    /** Its status lamp, as a machine's ({@link MachineBlockEntity#lamp}): lit while it plants. */
    public void showLamp(Level level, BlockPos pos) {
        this.lampLeft = MachineBlockEntity.lamp(level, pos, this.getBlockState(), this.status == MachineStatus.RUNNING, this.lampLeft);
    }

    /** One beat: plants a spot if it can, and if it cannot, says why. Public for tests. */
    public void work(ServerLevel level) {
        if (!this.redstone.allows(level.hasNeighborSignal(this.worldPosition))) {
            this.status = MachineStatus.REDSTONE;
        } else if (this.energy.getAmountAsLong() < MachineTuning.energy(FE_PER_PLANT)) {
            this.status = MachineStatus.NO_POWER;
        } else if (this.plantOne(level)) {
            this.status = MachineStatus.RUNNING;
        } else {
            this.status = this.isEmpty() ? MachineStatus.IDLE : this.refused ? MachineStatus.PROTECTED : MachineStatus.NO_SPACE;
        }
    }

    @Override
    public @Nullable UUID owner() {
        return this.owner;
    }

    /** Records who placed it. */
    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.refusedUntil.clear();
        this.setChanged();
    }

    @Override
    public DrawRights drawRights() {
        return this.drawRights;
    }

    /** Plants in the first free spot that one of the stored items fits. Returns whether it planted. */
    public boolean plantOne(ServerLevel level) {
        if (this.energy.getAmountAsLong() < MachineTuning.energy(FE_PER_PLANT)) {
            return false;
        }
        Direction facing = this.getBlockState().getValue(PlanterBlock.FACING);
        Direction side = facing.getClockWise();
        this.refused = false;
        for (int d = 1; d <= DEPTH; d++) {
            for (int w = -(WIDTH / 2); w <= WIDTH / 2; w++) {
                BlockPos spot = this.worldPosition.relative(facing, d).relative(side, w);
                if (level.isLoaded(spot) && open(level.getBlockState(spot)) && this.plantAt(level, spot)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Air, or still water a water plant might go in (rice in a paddy). */
    private static boolean open(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /**
     * What planting {@code stack} at {@code spot} would put there, asked of the plant the way a hand planting it asks:
     * rice only into water with air over it, a sapling never into water. Null when it does not go there.
     */
    private static @Nullable BlockState placement(ServerLevel level, BlockPos spot, ItemStack stack) {
        if (plantFor(stack) == null || !(stack.getItem() instanceof BlockItem item)) {
            return null;
        }
        var context = new DirectionalPlaceContext(level, spot, Direction.DOWN, stack, Direction.UP);
        BlockState plant = item.getBlock().getStateForPlacement(context);
        if (plant == null || !plant.canSurvive(level, spot)) {
            return null;
        }
        boolean wet = !level.getFluidState(spot).isEmpty();
        return wet == !plant.getFluidState().isEmpty() ? plant : null; // water stays water unless the plant lives in it
    }

    private boolean plantAt(ServerLevel level, BlockPos spot) {
        for (ItemStack stack : this.items) {
            BlockState plant = placement(level, spot, stack);
            if (plant != null) {
                // A sunflower or lilac is two blocks: without its top half it is a stump that pops off.
                if (tall(plant) && !level.getBlockState(spot.above()).isAir()) {
                    continue;
                }
                if (!this.mayPlant(level, spot)) {
                    this.refused = true;
                    return false; // not ours to plant, whatever it is
                }
                if (tall(plant)) {
                    DoublePlantBlock.placeAt(level, plant, spot, 3);
                } else {
                    level.setBlock(spot, plant, 3);
                }
                level.gameEvent(GameEvent.BLOCK_PLACE, spot, GameEvent.Context.of(plant));
                level.playSound(null, spot, plant.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0);
                stack.shrink(1);
                this.energy.set((int) (this.energy.getAmountAsLong() - MachineTuning.energy(FE_PER_PLANT)));
                this.setChanged();
                return true;
            }
        }
        return false;
    }

    /** Whether its owner may plant at {@code spot}: a refusal is kept a while ({@link #refusedUntil}), and not asked again. */
    private boolean mayPlant(ServerLevel level, BlockPos spot) {
        long now = level.getGameTime();
        Long until = this.refusedUntil.get(spot);
        if (until != null && until > now) {
            return false;
        }
        this.refusedUntil.values().removeIf(at -> at <= now);
        if (Permission.mayPlace(level, spot, Direction.UP, Permission.actor(level, this.owner))) {
            return true;
        }
        this.refusedUntil.put(spot.immutable(), now + DrawRights.ASK_EVERY - Math.floorMod(spot.hashCode(), DrawRights.ASK_EVERY / 5));
        return false;
    }

    /** Two blocks tall when planted. The pitcher pod and corn are not: they grow their top halves themselves, later. */
    private static boolean tall(BlockState plant) {
        return plant.getBlock() instanceof DoublePlantBlock
                && !(plant.getBlock() instanceof PitcherCropBlock)
                && !(plant.getBlock() instanceof CornBlock);
    }

    /** The block a stack plants, if it is a seed, sapling or other plant. */
    public static @Nullable BlockState plantFor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof VegetationBlock plant
                ? plant.defaultBlockState() : null;
    }

    // ---- the screen ----

    @Override
    public MachineLayout layout() {
        return MachineLayout.PLANTER;
    }

    @Override
    public Container slots() {
        return this;
    }

    @Override
    public int guiEnergy() {
        return (int) this.energy.getAmountAsLong();
    }

    @Override
    public int guiEnergyCapacity() {
        return CAPACITY;
    }

    /** It spends its power a planting at a time, so this is per planting rather than per tick. */
    @Override
    public int guiEnergyRate() {
        return MachineTuning.energy(FE_PER_PLANT);
    }

    @Override
    public MachineStatus guiStatus() {
        return this.status;
    }

    @Override
    public RedstoneMode redstoneMode() {
        return this.redstone;
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        this.redstone = mode;
        this.setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return plantFor(stack) != null;
    }

    // ---- hoppers and pipes: every slot from every side, in only ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.nookworks.planter");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DispenserMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    /** Its power goes with its item when it is broken, as a machine's does (see {@link MachineBlockEntity#keepEnergy}). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        MachineBlockEntity.keepEnergy(this.energy, components);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        MachineBlockEntity.restoreEnergy(this.energy, components);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        this.energy.serialize(output.child("Energy"));
        this.redstone.save(output);
        if (this.owner != null) {
            output.store("Owner", UUIDUtil.CODEC, this.owner);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        MachineBlockEntity.readEnergy(input, this.energy);
        this.redstone = RedstoneMode.load(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }
}
