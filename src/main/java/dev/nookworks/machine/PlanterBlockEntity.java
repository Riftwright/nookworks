package dev.nookworks.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.PlanterBlock;
import dev.nookworks.crop.CornBlock;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.minecraft.world.level.block.Block;
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
    /** The most an add-on's {@link ReachProvider}s widen a field by, all together: 15 wide and 15 deep at most. */
    public static final int MAX_EXTRA_SIDE = 6;
    public static final int MAX_EXTRA_FORWARD = 10;
    /** How often (ticks) the providers are asked again, besides {@link #refreshReach()}. */
    private static final int REACH_EVERY = 20;
    private static final String REACH_SIDE_KEY = "reach_side";
    private static final String REACH_FORWARD_KEY = "reach_forward";
    private static final List<ReachProvider> REACH = new CopyOnWriteArrayList<>();

    /**
     * How much an add-on widens a Planter's field: {@code side} more columns on each side, {@code forward} more rows
     * beyond it. For nookTechnology's speed gears (the user, 2026-11-14: 「tec環境では加速ギアを入れると植え付け範囲と速度が
     * 拡張される、みたいな挙動を入れたいな」); the speed itself is the beat, {@code MachineTuning.time(INTERVAL)} in
     * {@link #serverTick}, which an add-on scales as it does a machine's.
     */
    public record Reach(int side, int forward) {
        public static final Reach NONE = new Reach(0, 0);
    }

    /** Asked on the server, of a Planter: how much wider and deeper its field is ({@link Reach#NONE} or null for none). */
    @FunctionalInterface
    public interface ReachProvider {
        @Nullable Reach reach(PlanterBlockEntity planter);
    }

    /**
     * Registers what widens Planters' fields (an add-on, at its setup). Every provider's reach is added up and kept to
     * {@link #MAX_EXTRA_SIDE} / {@link #MAX_EXTRA_FORWARD}. A Planter asks again each second and at
     * {@link #refreshReach()}; what it has is synced to the client, where the outline of its field goes by it.
     */
    public static void addReachProvider(ReachProvider provider) {
        REACH.add(provider);
    }
    public static final int INTERVAL = 10;
    /**
     * A plant set is heard no oftener than this (ticks): at its own pace that is every one, but an add-on's gear has it
     * planting up to every tick, and twenty place sounds a second for a whole field are a rattle (the sound audit,
     * 2026-11-14).
     */
    public static final int HEARD_EVERY = 5;
    private long heardAt = Long.MIN_VALUE / 2;
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
    /** Its field's widening by the add-ons' {@link ReachProvider}s, as last asked (server) or told (client). Not saved. */
    private int extraSide;
    private int extraForward;
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
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), REACH_EVERY) == 0) {
            planter.refreshReach();
        }
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
        this.refused = false;
        for (BlockPos spot : this.spots()) {
            if (level.isLoaded(spot) && open(level.getBlockState(spot)) && this.plantAt(level, spot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The spots a Planter at {@code pos} facing {@code facing} plants, in the order it tries them: {@link #WIDTH} wide
     * and {@link #DEPTH} deep before it, at its own height (the soil is the block under each). The one list for the
     * planting and for the outline of it ({@code client/PlanterPreview}), so what is shown is what is planted.
     */
    public static List<BlockPos> spots(BlockPos pos, Direction facing) {
        return spots(pos, facing, 0, 0);
    }

    /** The spots of a field widened by {@code extraSide} on each side and {@code extraForward} beyond, nearest rows first. */
    public static List<BlockPos> spots(BlockPos pos, Direction facing, int extraSide, int extraForward) {
        Direction side = facing.getClockWise();
        int half = WIDTH / 2 + extraSide;
        int depth = DEPTH + extraForward;
        List<BlockPos> spots = new ArrayList<>((2 * half + 1) * depth);
        for (int d = 1; d <= depth; d++) {
            for (int w = -half; w <= half; w++) {
                spots.add(pos.relative(facing, d).relative(side, w));
            }
        }
        return spots;
    }

    /** This Planter's spots, its field widened as the add-ons have it ({@link #reach()}). */
    public List<BlockPos> spots() {
        return spots(this.worldPosition, this.getBlockState().getValue(PlanterBlock.FACING), this.extraSide, this.extraForward);
    }

    /** How much its field is widened, as last asked of the providers (server) or told (client). */
    public Reach reach() {
        return this.extraSide == 0 && this.extraForward == 0 ? Reach.NONE : new Reach(this.extraSide, this.extraForward);
    }

    /**
     * Asks the {@link ReachProvider}s again now (server only), and tells the client when the reach has changed. An add-on
     * calls it when what widens the field changes (a gear fitted or taken out); it is asked each second besides.
     */
    public void refreshReach() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        int side = 0;
        int forward = 0;
        for (ReachProvider provider : REACH) {
            Reach reach = provider.reach(this);
            if (reach != null) {
                side += Math.max(0, reach.side());
                forward += Math.max(0, reach.forward());
            }
        }
        side = Math.min(side, MAX_EXTRA_SIDE);
        forward = Math.min(forward, MAX_EXTRA_FORWARD);
        if (side != this.extraSide || forward != this.extraForward) {
            this.extraSide = side;
            this.extraForward = forward;
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** The client is told its reach alone (the outline of its field goes by it), never its stock or power. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (this.extraSide > 0) {
            tag.putInt(REACH_SIDE_KEY, this.extraSide);
        }
        if (this.extraForward > 0) {
            tag.putInt(REACH_FORWARD_KEY, this.extraForward);
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        this.toldReach(input);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        this.toldReach(input);
    }

    private void toldReach(ValueInput input) {
        this.extraSide = Math.clamp(input.getIntOr(REACH_SIDE_KEY, 0), 0, MAX_EXTRA_SIDE);
        this.extraForward = Math.clamp(input.getIntOr(REACH_FORWARD_KEY, 0), 0, MAX_EXTRA_FORWARD);
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
                if (level.getGameTime() - this.heardAt >= HEARD_EVERY) {
                    this.heardAt = level.getGameTime();
                    level.playSound(null, spot, plant.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
                }
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

    /** Its stop heard, as every machine's is (MachineAlerts). */
    @Override
    public void onLoad() {
        super.onLoad();
        dev.nookworks.machine.MachineAlerts.track(this);
        if (this.level != null && !this.level.isClientSide()) {
            // its reach from the first: the chunk sent to players carries it (getUpdateTag)
            this.refreshReach();
        }
    }
}
