package dev.nookworks.cooking;

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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What lies on a Wooden Tray: up to four things, one to a place - Bread Dough set down to rise, loaves, a bowl of rice
 * and a cutlet to serve. Dough rises on the tray ({@link #RISE_TICKS}) into Risen Dough, swelling as it goes; the rest
 * only sits there until it is taken off. Anything to eat goes on, and what is in {@link #GOES_ON_TRAYS} (the doughs,
 * a cake).
 */
public class TrayBlockEntity extends BlockEntity implements Readout {
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
            return fits(resource.toStack(1));
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
            lines.accept(left > 0 ? Component.translatable("nookworks.tray.rising", stack.getHoverName(),
                    (left + 1_199) / 1_200) : stack.getHoverName());
        }
    }

    /** Whether {@code stack} goes on a tray. */
    public static boolean fits(ItemStack stack) {
        return !stack.isEmpty() && (stack.has(DataComponents.FOOD) || stack.is(GOES_ON_TRAYS));
    }

    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    public boolean isFull() {
        return this.items.stream().noneMatch(ItemStack::isEmpty);
    }

    /** Puts one of {@code stack} in the first free place; dough starts rising. False when the tray is full. */
    public boolean place(Level level, @Nullable LivingEntity by, ItemStack stack) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.items.get(slot).isEmpty()) {
                ItemStack one = stack.consumeAndReturn(1, by);
                this.items.set(slot, one);
                this.risesAt[slot] = one.is(ModKitchen.BREAD_DOUGH.get()) ? level.getGameTime() + RISE_TICKS : 0;
                this.updated();
                return true;
            }
        }
        return false;
    }

    /** Takes off the last thing put on. */
    public ItemStack take() {
        for (int slot = SLOTS - 1; slot >= 0; slot--) {
            if (!this.items.get(slot).isEmpty()) {
                ItemStack out = this.items.get(slot);
                this.items.set(slot, ItemStack.EMPTY);
                this.risesAt[slot] = 0;
                this.updated();
                return out;
            }
        }
        return ItemStack.EMPTY;
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

    /** Dough whose time has come by {@code now} is Risen Dough. Public for tests, which cannot wait three minutes. */
    public void riseBy(ServerLevel level, long now) {
        boolean changed = false;
        for (int slot = 0; slot < SLOTS; slot++) {
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
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            for (ItemStack stack : this.items) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, stack.copy());
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
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
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            this.write(output);
            return output.buildResult();
        }
    }
}
