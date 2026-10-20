package dev.nookworks.cooking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.logging.LogUtils;

import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
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
 * A tub of Nukadoko (salted, fermenting rice bran): up to {@link #SLOTS} vegetables buried in it, each pickling for its
 * {@link PicklingRecipe}'s time and then waiting, pickled, to be dug out. Pickling is time, as dough rising on a tray
 * is: it goes on while nobody is near (the chunk unloaded), and is caught up when the tub ticks again - fire and sun
 * work only while someone is about (the user, 2026-10-01).
 */
public class NukadokoBlockEntity extends BlockEntity implements Readout {
    public static final int SLOTS = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final int[] age = new int[SLOTS];
    /** The game time the tub last ticked (0: not since it was made): what passed since is pickling time too. */
    private long ticked;

    public NukadokoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NUKADOKO.get(), pos, state);
    }

    private record Saved(List<ItemStack> items, int[] age) {}

    /** As hoppers and pipes see the tub: a vegetable that pickles goes in, and comes out pickled ({@link PlacesHandler}). */
    private final PlacesHandler<Saved> handler = new PlacesHandler<>(new PlacesHandler.Places<>() {
        @Override
        public int size() {
            return SLOTS;
        }

        @Override
        public ItemStack at(int place) {
            return NukadokoBlockEntity.this.items.get(place);
        }

        @Override
        public boolean takes(ItemResource resource) {
            return PicklingRecipe.of(resource.toStack(1)) != null;
        }

        @Override
        public void put(int place, ItemStack one) {
            NukadokoBlockEntity.this.items.set(place, one);
            NukadokoBlockEntity.this.age[place] = 0;
        }

        @Override
        public boolean done(int place) {
            return NukadokoBlockEntity.this.done(place);
        }

        @Override
        public void clear(int place) {
            NukadokoBlockEntity.this.items.set(place, ItemStack.EMPTY);
            NukadokoBlockEntity.this.age[place] = 0;
        }

        @Override
        public Saved save() {
            var self = NukadokoBlockEntity.this;
            return new Saved(self.items.stream().map(ItemStack::copy).toList(), self.age.clone());
        }

        @Override
        public void restore(Saved saved) {
            var self = NukadokoBlockEntity.this;
            for (int i = 0; i < SLOTS; i++) {
                self.items.set(i, saved.items().get(i));
            }
            System.arraycopy(saved.age(), 0, self.age, 0, SLOTS);
        }

        @Override
        public void changed() {
            NukadokoBlockEntity.this.changed();
        }
    });

    public ResourceHandler<ItemResource> itemHandler() {
        return this.handler;
    }

    public List<ItemStack> items() {
        return this.items;
    }

    /** Whether the vegetable in {@code slot} is pickled: it is its pickle now, which pickles no further. */
    public boolean done(int slot) {
        ItemStack stack = this.items.get(slot);
        return !stack.isEmpty() && PicklingRecipe.of(stack) == null;
    }

    /** Buries one of {@code stack} if it pickles and there is room. */
    public boolean bury(ItemStack stack) {
        if (PicklingRecipe.of(stack) == null) {
            return false;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.items.get(slot).isEmpty()) {
                this.items.set(slot, stack.copyWithCount(1));
                this.age[slot] = 0;
                this.changed();
                return true;
            }
        }
        return false;
    }

    /** Digs out the pickled ones, or with {@code all} everything, pickled or not. */
    public List<ItemStack> digOut(boolean all) {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!this.items.get(slot).isEmpty() && (all || this.done(slot))) {
                out.add(this.items.get(slot));
                this.items.set(slot, ItemStack.EMPTY);
                this.age[slot] = 0;
            }
        }
        if (!out.isEmpty()) {
            this.changed();
        }
        return out;
    }

    /** Saved, and sent to the players who can see the tub (what is buried in it is drawn). */
    private void changed() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NukadokoBlockEntity tub) {
        long now = level.getGameTime();
        long passed = tub.ticked == 0 ? 1 : Math.max(1, now - tub.ticked); // more than one when the chunk was away
        tub.ticked = now;
        boolean pickling = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            PicklingRecipe recipe = PicklingRecipe.of(tub.items.get(slot));
            if (recipe == null) {
                continue;
            }
            pickling = true;
            tub.age[slot] = (int) Math.min(recipe.time(), tub.age[slot] + passed);
            if (tub.age[slot] >= recipe.time()) {
                tub.items.set(slot, new ItemStack(recipe.result()));
                tub.age[slot] = 0;
                tub.changed();
            }
        }
        if (pickling && level.getGameTime() % 20 == 0) {
            tub.setChanged(); // saved now and then, not every tick of ten minutes
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (var reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, this.items, true);
            return output.buildResult();
        }
    }

    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(this.describe());
    }

    /**
     * For the player who looks in: what is buried, and how far along each is - alike ones once, with how many: four
     * carrots put in together said "Carrot (about 10 min left)" four times, past both edges of the screen (the kitchen
     * play-test, 2026-10-19).
     */
    public Component describe() {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.Map<String, Component> parts = new java.util.HashMap<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            PicklingRecipe recipe = PicklingRecipe.of(stack);
            int minutes = recipe == null ? -1 : Math.max(1, (recipe.time() - this.age[slot] + 1199) / 1200);
            String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + "/" + minutes;
            counts.merge(key, 1, Integer::sum);
            parts.computeIfAbsent(key, k -> recipe == null ? Component.translatable("nookworks.nukadoko.ready", stack.getHoverName())
                    : Component.translatable("nookworks.nukadoko.pickling", stack.getHoverName(), minutes));
        }
        MutableComponent line = null;
        for (var entry : counts.entrySet()) {
            Component part = entry.getValue() > 1
                    ? Component.literal(entry.getValue() + "× ").append(parts.get(entry.getKey())) : parts.get(entry.getKey());
            line = line == null ? part.copy() : line.append(Component.translatable("nookworks.list.separator")).append(part);
        }
        return line == null ? Component.translatable("nookworks.nukadoko.empty") : line;
    }

    /** For tests: how long the vegetable in {@code slot} has been in. */
    public int age(int slot) {
        return this.age[slot];
    }

    /** For tests, which cannot unload a chunk for ten minutes: as though the tub last ticked at {@code time}. */
    public void tickedAt(long time) {
        this.ticked = time;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
        output.putIntArray("Age", this.age);
        output.putLong("Ticked", this.ticked);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Cleared first: what is read in fills only the slots saved, and a client told a slot was emptied - pickles
        // taken out - otherwise went on drawing what had been in it.
        this.items.clear();
        Arrays.fill(this.age, 0);
        ContainerHelper.loadAllItems(input, this.items);
        int[] saved = input.getIntArray("Age").orElse(new int[SLOTS]);
        System.arraycopy(saved, 0, this.age, 0, Math.min(saved.length, SLOTS));
        this.ticked = input.getLongOr("Ticked", 0L);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            for (ItemStack stack : this.items) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, stack.copy());
            }
            this.items.clear(); // dropped, so gone from here (the dupe audit's hardening note)
        }
    }
}
