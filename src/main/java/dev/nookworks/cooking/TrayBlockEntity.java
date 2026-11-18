package dev.nookworks.cooking;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import dev.nookworks.port.core.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import dev.nookworks.port.storage.ProblemReporter;
import dev.nookworks.port.storage.ContainerHelper;
import dev.nookworks.port.core.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.nookworks.port.storage.TagValueOutput;
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import dev.nookworks.port.transfer.ResourceHandler;
import dev.nookworks.port.transfer.ItemResource;
import dev.nookworks.port.core.PreRemoveSideEffects;
import dev.nookworks.port.storage.ValueIO;

/**
 * What lies on a Wooden Tray: up to four lots - Bread Dough set down to rise, loaves, a bowl of rice and a cutlet to
 * serve. Dough rises on the tray ({@link #RISE_TICKS}) into Risen Dough, swelling as it goes; the rest only sits there
 * until it is taken off. Anything to eat goes on, and what is in {@link #GOES_ON_TRAYS} (the doughs, a cake).
 * <p>
 * A lot is one kind of thing in one slot, with the room on the board its food takes ({@link Serving}): a quarter of it
 * for most, half for a long thing (a fish, a cutlet with its cabbage), the whole tray for a cake - and a small thing
 * lies several to a lot (cookies, onigiri). So four lots only when each takes a quarter; before, any four things went
 * on, and four cutlets ran into one another. What an old tray holds beyond that stays until it is taken off.
 * <p>
 * Lifted (an empty hand, sneaking: {@link #lift}) the tray is its item with what is on it kept on it, and set down
 * again it is as it was (the user, 2026-11-14: トレイを運べるようにする案もすばらしい) - a meal is laid in the kitchen
 * and carried to the table.
 * <p>
 * A Plate goes on as a lot too, empty or with its food ({@link Plating#onPlate}), and food set on the tray goes onto
 * a plate there that has room for it before it goes on the board. Food that is served on a plate
 * ({@link Plating#SERVED_ON_PLATES}: a cutlet, karaage, a steak) goes on only so (the user, 2026-11-14: とんかつや
 * 空揚げは、そもそも、そのままトレイに載せない気がする。お皿に盛りつけたとんかつを、皿ごとにトレイに載せるであれば分かる).
 * A plate comes off with its food, as the one thing it is.
 */
public class TrayBlockEntity extends BlockEntity implements PreRemoveSideEffects, Readout {
    public static final int SLOTS = 4;
    /** Three minutes to rise. Minecraft's air, it is agreed, is thick with wild yeast. */
    public static final int RISE_TICKS = 3_600;
    /** What goes on a tray besides anything to eat. */
    public static final TagKey<Item> GOES_ON_TRAYS = TagKey.create(Registries.ITEM, Nookworks.id("goes_on_trays"));

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    /** For dough that is rising, the game time it will have risen by; 0 for anything else. */
    private final long[] risesAt = new long[SLOTS];

    public TrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAY.get(), pos, state);
    }

    private record Saved(List<ItemStack> items, long[] risesAt) {}

    /** As hoppers and pipes see the tray: what goes on a tray goes on, and comes off unless it is still rising ({@link PlacesHandler}). */
    private final PlacesHandler<Saved> handler = new PlacesHandler<>(new PlacesHandler.Places<>() {
        @Override
        public int size() {
            return SLOTS;
        }

        @Override
        public ItemStack at(int place) {
            return TrayBlockEntity.this.items.get(place);
        }

        @Override
        public boolean takes(ItemResource resource) {
            ItemStack one = resource.toStack(1);
            return fits(one) && !Plating.wantsPlate(one); // a hopper sets nothing on a plate: what wants one comes on its plate
        }

        @Override
        public boolean takesAt(int place, ItemResource resource) {
            return TrayBlockEntity.this.roomAt(place, resource.toStack(1));
        }

        @Override
        public int most(int place, ItemResource resource) {
            return Serving.of(resource.toStack(1)).tray();
        }

        @Override
        public int more(int place, ItemResource resource) {
            var self = TrayBlockEntity.this;
            return self.risesAt[place] != 0 ? 0 : Math.max(0, this.most(place, resource) - self.items.get(place).getCount());
        }

        @Override
        public void put(int place, ItemStack one) {
            var self = TrayBlockEntity.this;
            self.items.set(place, one);
            self.risesAt[place] = one.is(ModKitchen.BREAD_DOUGH.get()) && self.level != null ? self.level.getGameTime() + RISE_TICKS : 0;
        }

        @Override
        public boolean done(int place) {
            return TrayBlockEntity.this.risesAt[place] == 0;
        }

        @Override
        public void clear(int place) {
            TrayBlockEntity.this.items.set(place, ItemStack.EMPTY);
            TrayBlockEntity.this.risesAt[place] = 0;
        }

        @Override
        public Saved save() {
            var self = TrayBlockEntity.this;
            return new Saved(self.items.stream().map(ItemStack::copy).toList(), self.risesAt.clone());
        }

        @Override
        public void restore(Saved saved) {
            var self = TrayBlockEntity.this;
            for (int i = 0; i < SLOTS; i++) {
                self.items.set(i, saved.items().get(i));
            }
            System.arraycopy(saved.risesAt(), 0, self.risesAt, 0, SLOTS);
        }

        @Override
        public void changed() {
            TrayBlockEntity.this.updated();
        }
    });

    public ResourceHandler<ItemResource> itemHandler() {
        return this.handler;
    }

    public NonNullList<ItemStack> items() {
        return this.items;
    }

    /** For a look at it (Jade): each piece of dough and the minutes it has still to rise; anything else by name. */
    @Override
    public void readout(Consumer<Component> lines) {
        long now = this.level == null ? 0 : this.level.getGameTime();
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            long left = this.risesAt[slot] - now;
            lines.accept(left > 0 ? Component.translatable("nookworks.tray.rising", stack.getHoverName(), (left + 1_199) / 1_200)
                    : stack.getCount() == 1 ? stack.getHoverName()
                    : Component.translatable("nookworks.iron_pot.item", stack.getHoverName(), stack.getCount()));
        }
    }

    /**
     * To Jade, only what is still rising: Jade shows the tray's things itself, with their pictures, and they were listed
     * twice (the kitchen play-test, 2026-10-19).
     */
    @Override
    public void jade(Consumer<Component> lines) {
        long now = this.level == null ? 0 : this.level.getGameTime();
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = this.items.get(slot);
            long left = this.risesAt[slot] - now;
            if (!stack.isEmpty() && left > 0) {
                lines.accept(Component.translatable("nookworks.tray.rising", stack.getHoverName(), (left + 1_199) / 1_200));
            }
        }
    }

    /** Whether {@code stack} goes on a tray. */
    public static boolean fits(ItemStack stack) {
        // not a bottle that is set down as a bottle (a Honey Bottle): it stands on the table itself, as on no plate
        return !stack.isEmpty() && (stack.has(DataComponents.FOOD) && dev.nookworks.decor.BottleLook.of(stack) == null || stack.is(GOES_ON_TRAYS)
                || Plating.isPlate(stack));
    }

    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    /** Whether every slot holds a lot. A tray may have no room for a thing well before that: {@link #takes}. */
    public boolean isFull() {
        return this.items.stream().noneMatch(ItemStack::isEmpty);
    }

    /**
     * Whether one of {@code stack} goes on now: onto a plate there that has room for it, with a lot of the same that has
     * room, or as a new lot the board has room for - the last two not for food that is served on a plate.
     */
    public boolean takes(ItemStack stack) {
        return fits(stack) && (this.plateFor(stack) >= 0 || !Plating.wantsPlate(stack) && this.slotFor(stack) >= 0);
    }

    /** Whether {@code stack} is refused for want of a plate: food served on one, with no plate on the tray to take it. */
    public boolean wantsPlate(ItemStack stack) {
        return Plating.wantsPlate(stack) && this.plateFor(stack) < 0;
    }

    /** The slot of a plate on the tray that takes one more of {@code food} - one holding the same first, else an empty one - or -1. */
    private int plateFor(ItemStack food) {
        int empty = -1;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack there = this.items.get(slot);
            if (!Plating.isPlate(there) || !Plating.plateTakes(there, food)) {
                continue;
            }
            if (!Plating.onPlate(there).isEmpty()) {
                return slot;
            }
            if (empty < 0) {
                empty = slot;
            }
        }
        return empty;
    }

    /** Whether a new lot of {@code stack} in the empty {@code slot} leaves everything on the board with room. */
    private boolean roomAt(int slot, ItemStack stack) {
        List<ItemStack> with = new ArrayList<>(this.items);
        with.set(slot, stack);
        return Serving.packs(with);
    }

    /** The slot one more of {@code stack} goes to, or -1 when the tray has no room for it. */
    private int slotFor(ItemStack stack) {
        int most = Serving.of(stack).tray();
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack there = this.items.get(slot);
            if (!there.isEmpty() && there.getCount() < most && this.risesAt[slot] == 0 && ItemStack.isSameItemSameComponents(there, stack)) {
                return slot;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.items.get(slot).isEmpty()) {
                return this.roomAt(slot, stack) ? slot : -1;
            }
        }
        return -1;
    }

    /** Puts one of {@code stack} on, where {@link #takes} found room; dough starts rising. False when the tray has no room for it. */
    public boolean place(Level level, @Nullable LivingEntity by, ItemStack stack) {
        if (!fits(stack)) {
            return false;
        }
        int plate = this.plateFor(stack);
        if (plate >= 0) {
            ItemStack on = Plating.onPlate(this.items.get(plate));
            ItemStack one = stack.consumeAndReturn(1, by);
            if (on.isEmpty()) {
                on = one;
            } else {
                on.grow(1);
            }
            Plating.setOnPlate(this.items.get(plate), on);
            this.updated();
            return true;
        }
        int slot = Plating.wantsPlate(stack) ? -1 : this.slotFor(stack);
        if (slot < 0) {
            return false;
        }
        if (this.items.get(slot).isEmpty()) {
            ItemStack one = stack.consumeAndReturn(1, by);
            this.items.set(slot, one);
            this.risesAt[slot] = one.is(ModKitchen.BREAD_DOUGH.get()) ? level.getGameTime() + RISE_TICKS : 0;
        } else {
            stack.consume(1, by);
            this.items.get(slot).grow(1);
        }
        this.updated();
        return true;
    }

    /** Takes off the last thing put on: one of the last lot. */
    public ItemStack take() {
        for (int slot = SLOTS - 1; slot >= 0; slot--) {
            if (!this.items.get(slot).isEmpty()) {
                ItemStack out = this.items.get(slot).split(1);
                if (this.items.get(slot).isEmpty()) {
                    this.items.set(slot, ItemStack.EMPTY);
                    this.risesAt[slot] = 0;
                }
                this.updated();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The tray lifted with what is on it: its item, the things kept on it (as a shulker box keeps its own, in the
     * item's container), one to a stack while it is laden. This entity is left empty: the caller takes the block away.
     */
    public ItemStack lift() {
        ItemStack tray = new ItemStack(this.getBlockState().getBlock());
        tray.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.items));
        tray.set(DataComponents.MAX_STACK_SIZE, 1);
        this.items.clear();
        java.util.Arrays.fill(this.risesAt, 0);
        this.setChanged();
        return tray;
    }

    /** How far the dough in {@code slot} has risen, 0 to 1, at {@code time}; 0 for anything not rising. */
    public float rise(int slot, long now, float partialTicks) {
        long at = this.risesAt[slot];
        // the ticks left worked out whole, then the fraction: a float of the game time loses it in a long-kept world
        return at == 0 ? 0 : Math.clamp(1 - ((at - now) - partialTicks) / RISE_TICKS, 0, 1);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TrayBlockEntity tray) {
        if (level instanceof ServerLevel server && level.getGameTime() % 20 == 0) {
            tray.riseBy(server, level.getGameTime());
        }
    }

    /**
     * Dough whose time has come by {@code now} is Risen Dough. Public for tests, which cannot wait three minutes. Dough
     * with no time set (a tray set down again after it was carried) starts rising afresh.
     */
    public void riseBy(ServerLevel level, long now) {
        boolean changed = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.risesAt[slot] == 0 && this.items.get(slot).is(ModKitchen.BREAD_DOUGH.get())) {
                this.risesAt[slot] = now + RISE_TICKS;
                changed = true;
            }
            if (this.risesAt[slot] != 0 && now >= this.risesAt[slot] && this.items.get(slot).is(ModKitchen.BREAD_DOUGH.get())) {
                this.items.set(slot, new ItemStack(ModKitchen.RISEN_DOUGH.get()));
                this.risesAt[slot] = 0;
                changed = true;
            }
        }
        if (changed) {
            this.updated();
        }
    }

    private void updated() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        PreRemoveSideEffects.super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            for (ItemStack stack : this.items) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack.copy());
            }
            this.items.clear(); // dropped, so gone from here (the dupe audit's hardening note)
        }
    }

    /** Set down from an item that was a tray lifted with its food: the food is on it again. */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        DataComponentGetter components = input::get; // PORT: 1.21.1 hands its own DataComponentInput
        ItemContainerContents contents = components.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.copyInto(this.items);
            java.util.Arrays.fill(this.risesAt, 0);
        }
        components.get(DataComponents.MAX_STACK_SIZE); // read, so not kept on the block: it was the laden item's
    }

    /** Picked with its contents (the creative pick-block key held with Ctrl): the same item a lifted tray is. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!this.isEmpty()) {
            components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.items));
            components.set(DataComponents.MAX_STACK_SIZE, 1);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag portTag) {
        ValueOutput output = ValueIO.output(this, portTag); // PORT: 1.21.1 hands a tag
        output.discard("Items");
    }

    @Override
    protected void saveAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueOutput output = ValueIO.output(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.saveAdditional(portTag, portLookup);
        this.write(output);
    }

    private void write(ValueOutput output) {
        ContainerHelper.saveAllItems(output, this.items, true);
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.risesAt[slot] != 0) {
                output.putLong("RisesAt" + slot, this.risesAt[slot]);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueInput input = ValueIO.input(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.loadAdditional(portTag, portLookup);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        for (int slot = 0; slot < SLOTS; slot++) {
            this.risesAt[slot] = input.getLongOr("RisesAt" + slot, 0L);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client draws what is on the tray, and the dough swelling as it rises. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ProblemReporter.pathOf(this), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            this.write(output);
            return output.buildResult();
        }
    }
}
