package dev.nookworks.experience;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.block.RepairBoxBlock;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * Repair Box: leave gear in it and it grinds the damage out, spending power and Liquid Experience.
 * <p>
 * It does not care whether a tool has Mending, which is the point: a bow, a shield or an unenchanted pickaxe
 * can be kept alive the same way, and gear that is enchanted stays enchanted because nothing here touches its
 * components. Hoppers may only take out what is finished, so a hopper line can cycle tools through it.
 * <p>
 * Two side by side are one machine (see {@link RepairBoxBlock}): the pair shows twelve slots, and each repair
 * step draws power and experience from whichever half has them, for whichever item in either half is most worn.
 * The pair does twice the work of one box, since it has twice the stores. Only one half - the one with
 * {@link ChestType#LEFT} - runs the pair, so nothing is repaired twice.
 */
public class RepairBoxBlockEntity extends MachineBlockEntity implements LidBlockEntity {
    public static final int SLOTS = 6;
    public static final int CAPACITY = 20_000;
    public static final int TANK = 4_000;

    /** Durability restored each time it works, and what that costs. */
    public static final int REPAIR_PER_STEP = 5;
    public static final int FE_PER_STEP = 200;
    public static final int MB_PER_STEP = ExperienceFluids.MB_PER_POINT;
    public static final int INTERVAL = 10;
    private static final int EVENT_OPEN_COUNT = 1;

    private static final int[] ALL_SLOTS = new int[SLOTS];

    static {
        for (int i = 0; i < SLOTS; i++) {
            ALL_SLOTS[i] = i;
        }
    }

    private final FluidStacksResourceHandler fluid = new OneTank(TANK) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.typeHolder().is(ExperienceFluids.EXPERIENCE);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            RepairBoxBlockEntity.this.setChanged(); // which tells the clients, if they would see it
        }
    };

    /**
     * How full the tank is, in twelfths, as far as players' screens need to know: the inside of an open box shows it
     * (RepairBoxRenderer). Sent when it changes, so a box being filled or drawn on is not sent every mB. On the client,
     * the last sent; on the server, the last it sent.
     */
    public static final int SHOWN_STEPS = 12;
    private int shown;
    /**
     * What lies in each slot, as players see it with the lid up: the item, its look (a name, an enchantment's glint),
     * but not its wear, so a repair in progress is not sent at every step. On the client, the last sent; on the
     * server, the last it sent.
     */
    private List<ItemStack> shownItems = List.of();

    private final ChestLidController lid = new ChestLidController();
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            RepairBoxBlockEntity.this.playSound(state, SoundEvents.IRON_TRAPDOOR_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            RepairBoxBlockEntity.this.playSound(state, SoundEvents.IRON_TRAPDOOR_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
            level.blockEvent(pos, state.getBlock(), EVENT_OPEN_COUNT, current);
        }

        @Override
        public boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof RepairBoxMenu menu && menu.shows(RepairBoxBlockEntity.this);
        }
    };

    public RepairBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REPAIR_BOX.get(), pos, state, SLOTS, CAPACITY, 500, 0);
    }

    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fluid;
    }

    public int experience() {
        return this.fluid.getAmountAsInt(0);
    }

    /** How full it looks, 0 to {@link #SHOWN_STEPS}: any experience at all is a step at least. */
    public int shownStep() {
        if (this.level != null && this.level.isClientSide()) {
            return this.shown;
        }
        int amount = this.experience();
        return amount <= 0 ? 0 : Math.max(1, Math.round(amount * (float) SHOWN_STEPS / TANK));
    }

    /** The gear lying in the box, slot by slot (empty where there is none), as {@link #shownItems} describes. */
    public List<ItemStack> shownItems() {
        if (this.level != null && this.level.isClientSide()) {
            return this.shownItems;
        }
        List<ItemStack> looks = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = this.getItem(slot);
            ItemStack look = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            look.remove(DataComponents.DAMAGE);
            looks.add(look);
        }
        return looks;
    }

    private static boolean sameLooks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        this.tellClients();
    }

    /** Sends players the fill and the gear lying in it when either has changed as they would see it. */
    private void tellClients() {
        if (this.level instanceof ServerLevel server) {
            int step = this.shownStep();
            List<ItemStack> items = this.shownItems();
            if (step != this.shown || !sameLooks(items, this.shownItems)) {
                this.shown = step;
                this.shownItems = items;
                server.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * The client is told only what it draws: how full the box is and what gear lies in it. The rest - power, wear,
     * the slots themselves - it learns from the screen, when one is open.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            output.putInt("Shown", this.shownStep());
            output.store("Items", ItemStack.OPTIONAL_CODEC.listOf(), this.shownItems());
            return output.buildResult();
        }
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        this.handleUpdateTag(input);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        this.shown = input.getIntOr("Shown", 0);
        this.shownItems = input.read("Items", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
    }

    // ---- the pair ----

    private @Nullable RepairBoxBlockEntity partner() {
        return this.level == null ? null : RepairBoxBlock.partner(this.level, this.worldPosition, this.getBlockState());
    }

    /** This box, or both halves of the double it is part of, left half first. */
    private RepairBoxBlockEntity[] halves() {
        RepairBoxBlockEntity other = this.partner();
        if (other == null) {
            return new RepairBoxBlockEntity[] {this};
        }
        return this.getBlockState().getValue(RepairBoxBlock.TYPE) == ChestType.LEFT
                ? new RepairBoxBlockEntity[] {this, other} : new RepairBoxBlockEntity[] {other, this};
    }

    /**
     * Pours the bucket or bottle of experience {@code player} holds into this box, or into the other half of its pair
     * when this one has no room for it all, and gives back the empty as a bucket emptied anywhere does. Whether it
     * went in. (Only ever in: the box's tank is offered to the bucket as one that takes in.)
     */
    public boolean pourFrom(Player player, InteractionHand hand, ItemStack stack) {
        boolean bottle = stack.is(ModItems.LIQUID_EXPERIENCE_BOTTLE.get());
        RepairBoxBlockEntity other = this.partner();
        for (RepairBoxBlockEntity box : other == null ? List.of(this) : List.of(this, other)) {
            if (bottle) {
                if (Move.insertExactly(box.fluid, FluidResource.of(ExperienceFluids.SOURCE.get()), ExperienceBottling.BOTTLE)) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    player.level().playSound(null, box.worldPosition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.2F);
                    return true;
                }
            } else if (FluidUtil.interactWithFluidHandler(player, hand, box.worldPosition, Tanks.fillOnly(box.fluid), null)) {
                return true;
            }
        }
        return false;
    }

    /** The experience this box holds, with its other half's: what its screen shows. */
    public int pairExperience() {
        int total = 0;
        for (RepairBoxBlockEntity box : this.halves()) {
            total += box.experience();
        }
        return total;
    }

    /** How much experience this box holds at most, with its other half. */
    public int pairCapacity() {
        return this.halves().length * TANK;
    }

    /** Opens the screen on this box, or on the whole pair. */
    public static void openFor(Player player, RepairBoxBlockEntity clicked) {
        RepairBoxBlockEntity[] halves = clicked.halves();
        Container slots = halves.length == 1 ? halves[0] : new CompoundContainer(halves[0], halves[1]);
        ContainerData data = dataFor(halves);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new RepairBoxMenu(id, inventory, slots, data, halves),
                Component.translatable("block.nookworks.repair_box")), buf -> buf.writeVarInt(halves.length));
    }

    /**
     * What the screen shows for a box or a pair, read live. A pair holds 40,000 FE, more than a menu's short data
     * slot carries, so each total goes as two halves.
     */
    public static ContainerData dataFor(RepairBoxBlockEntity[] halves) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index >= RepairBoxMenu.BOXES) {
                    return switch (index) {
                        case RepairBoxMenu.BOXES -> halves.length;
                        case RepairBoxMenu.REDSTONE -> halves[0].redstoneMode().ordinal();
                        default -> halves[0].status == MachineStatus.REDSTONE ? 1 : 0;
                    };
                }
                int total = 0;
                for (RepairBoxBlockEntity box : halves) {
                    total += index < 2 ? box.energyStored() : box.experience();
                }
                return index % 2 == 0 ? total & 0xFFFF : total >>> 16;
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return RepairBoxMenu.DATA_COUNT;
            }
        };
    }

    // ---- ticking ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, RepairBoxBlockEntity box) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % MachineTuning.time(INTERVAL) != 0) {
            return;
        }
        box.pull(server, pos);
        if (state.getValue(RepairBoxBlock.TYPE) == ChestType.RIGHT && box.partner() != null) {
            return; // the left half runs the pair
        }
        RepairBoxBlockEntity[] halves = box.halves();
        boolean powered = false;
        for (RepairBoxBlockEntity half : halves) {
            powered |= level.hasNeighborSignal(half.getBlockPos());
        }
        if (!box.redstone.allows(powered)) {
            box.status = MachineStatus.REDSTONE;
            return;
        }
        box.status = MachineStatus.RUNNING;
        for (int step = 0; step < halves.length; step++) {
            if (!repairOnce(halves)) {
                break;
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RepairBoxBlockEntity box) {
        box.lid.tickLid();
    }

    /** One step of work on the most worn item in the pair, paid by whichever half can. */
    private static boolean repairOnce(RepairBoxBlockEntity[] halves) {
        ItemStack worst = ItemStack.EMPTY;
        RepairBoxBlockEntity holder = null;
        for (RepairBoxBlockEntity box : halves) {
            for (ItemStack stack : box.items) {
                if (repairable(stack) && (worst.isEmpty() || stack.getDamageValue() > worst.getDamageValue())) {
                    worst = stack;
                    holder = box;
                }
            }
        }
        if (holder == null) {
            return false;
        }
        // two boxes side by side share their power and experience: a step is paid from what both hold together, as
        // their screen counts it
        int cost = MachineTuning.energy(FE_PER_STEP);
        long power = 0;
        long experience = 0;
        for (RepairBoxBlockEntity box : halves) {
            power += box.energyStored();
            experience += box.experience();
        }
        if (power < cost || experience < MB_PER_STEP) {
            return false;
        }
        int owed = cost;
        int wanted = MB_PER_STEP;
        for (RepairBoxBlockEntity box : halves) {
            int energy = Math.min(owed, box.energyStored());
            if (energy > 0) {
                box.addEnergy(-energy);
                owed -= energy;
            }
            int mb = Math.min(wanted, box.experience());
            if (mb > 0) {
                Move.extract(box.fluid, box.fluid.getResource(0), mb);
                wanted -= mb;
            }
        }
        worst.setDamageValue(Math.max(0, worst.getDamageValue() - REPAIR_PER_STEP));
        holder.setChanged();
        if (holder.level != null && !worst.isDamaged()) {
            // done with it: a light ring, so a box across the workshop says so (the user, 2026-10-10)
            holder.level.playSound(null, holder.worldPosition, ModSounds.REPAIR_BOX_DONE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (holder.level != null && holder.level.getRandom().nextInt(4) == 0) {
            // heard every few steps, not each one: a step is half a second
            holder.level.playSound(null, holder.worldPosition, ModSounds.REPAIR_BOX_MEND.get(), SoundSource.BLOCKS, 1.0F,
                    0.9F + holder.level.getRandom().nextFloat() * 0.2F);
        }
        return true;
    }

    /** Draws experience out of any neighbouring tank or pipe, so it can sit at the end of a line. */
    private void pull(ServerLevel level, BlockPos pos) {
        int room = TANK - this.experience();
        if (room <= 0) {
            return;
        }
        for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.relative(side);
            BlockEntity there = level.isLoaded(neighbour) ? level.getBlockEntity(neighbour) : null;
            if (!level.isLoaded(neighbour) || there instanceof RepairBoxBlockEntity) {
                continue; // an unloaded chunk is not read; the other half is not a source
            }
            var source = level.getCapability(Capabilities.Fluid.BLOCK, neighbour, side.getOpposite());
            if (source == null) {
                continue;
            }
            // Any experience fluid, ours or another mod's: the box takes all of #c:experience when pushed, so it
            // draws all of it too - but not across a claim's edge, where its owner may not take: asked only once there
            // is experience there to take, a lodestone or the air beside it never.
            for (int i = 0; i < source.size(); i++) {
                FluidResource held = source.getResource(i);
                if (!held.isEmpty() && held.typeHolder().is(ExperienceFluids.EXPERIENCE)) {
                    if (!DrawRights.mayMove(level, neighbour, there, pos, this)) {
                        break;
                    }
                    if (Move.transfer(source, this.fluid, held, room) > 0) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * What the box will not mend though it wears: an item whose durability counts its uses rather than its wear - a
     * vial of a few doses, a charm with so many charges - which mended would never run out (Nook of the Dead's Vandal
     * Blood, the exploit audit of 2026-10-19). Empty here; a mod or a datapack adds its own.
     */
    public static final TagKey<Item> NOT_REPAIRABLE = TagKey.create(Registries.ITEM, Nookworks.id("not_repairable"));

    public static boolean repairable(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() == 1 && stack.isDamageableItem() && stack.isDamaged()
                && !stack.is(NOT_REPAIRABLE);
    }

    // ---- the lid ----

    @Override
    public float getOpenNess(float partialTicks) {
        return this.lid.getOpenness(partialTicks);
    }

    @Override
    public boolean triggerEvent(int id, int value) {
        if (id == EVENT_OPEN_COUNT) {
            this.lid.shouldBeOpen(value > 0);
            return true;
        }
        return super.triggerEvent(id, value);
    }

    public void startOpen(Player player) {
        if (this.level != null && !this.remove && !player.isSpectator()) {
            this.openers.incrementOpeners(player, this.level, this.worldPosition, this.getBlockState(), player.blockInteractionRange() + 1.0);
        }
    }

    public void stopOpen(Player player) {
        if (this.level != null && !this.remove && !player.isSpectator()) {
            this.openers.decrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
        }
    }

    public void recheckOpen() {
        if (this.level != null && !this.remove) {
            this.openers.recheckOpeners(this.level, this.worldPosition, this.getBlockState());
        }
    }

    /** One sound for the pair, from its middle, as a double chest does it. */
    private void playSound(BlockState state, SoundEvent sound) {
        if (this.level == null || state.getValue(RepairBoxBlock.TYPE) == ChestType.LEFT) {
            return;
        }
        double x = this.worldPosition.getX() + 0.5;
        double z = this.worldPosition.getZ() + 0.5;
        if (state.getValue(RepairBoxBlock.TYPE) == ChestType.RIGHT) {
            Direction toward = RepairBoxBlock.connectedDirection(state);
            x += toward.getStepX() * 0.5;
            z += toward.getStepZ() * 0.5;
        }
        this.level.playSound(null, x, this.worldPosition.getY() + 0.5, z, sound, SoundSource.BLOCKS, 0.5F,
                this.level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    // ---- slots ----

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return repairable(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return !stack.isDamaged(); // only let a finished item leave
    }

    @Override
    public Component status() {
        return Component.translatable("nookworks.machine.repair_box", this.energyText(), this.experience(), TANK);
    }

    /** Nothing to Jade: its power and its experience are what Jade shows itself. */
    @Override
    public void jade(java.util.function.Consumer<Component> lines) {
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.fluid.serialize(output.child("Fluid"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Fluid").ifPresent(this.fluid::deserialize);
    }

    /** Broken, it pours what it held out as orbs. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            ExperienceFluids.spillAsOrbs(server, pos, this.experience());
        }
    }
}
