package dev.nookworks.cooking;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
 * What lies on a Grill: four places, each cooked as a campfire cooks it (the same recipes, the same times). Unlike a
 * campfire's, a cooked piece stays on the grill, done, until someone takes it off; off the heat, a piece cools back
 * as it would on a dead campfire.
 */
public class GrillBlockEntity extends BlockEntity implements Readout {
    public static final int SLOTS = 4;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final int[] progress = new int[SLOTS];
    private final int[] time = new int[SLOTS];
    /** Cooked, and left on the grill. */
    private final boolean[] done = new boolean[SLOTS];

    public GrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRILL.get(), pos, state);
    }

    private record Saved(List<ItemStack> items, int[] progress, int[] time, boolean[] done) {}

    /** As hoppers and pipes see the grill: what a campfire cooks goes on, and comes off cooked ({@link PlacesHandler}). */
    private final PlacesHandler<Saved> handler = new PlacesHandler<>(new PlacesHandler.Places<>() {
        @Override
        public int size() {
            return SLOTS;
        }

        @Override
        public ItemStack at(int place) {
            return GrillBlockEntity.this.items.get(place);
        }

        @Override
        public boolean takes(ItemResource resource) {
            return GrillBlockEntity.this.level instanceof ServerLevel server && recipeFor(server, resource.toStack(1)).isPresent();
        }

        @Override
        public void put(int place, ItemStack one) {
            var self = GrillBlockEntity.this;
            self.items.set(place, one);
            self.time[place] = self.level instanceof ServerLevel server
                    ? recipeFor(server, one).map(r -> r.value().cookingTime()).orElse(600) : 600;
            self.progress[place] = 0;
            self.done[place] = false;
        }

        @Override
        public boolean done(int place) {
            return GrillBlockEntity.this.done[place];
        }

        @Override
        public void clear(int place) {
            var self = GrillBlockEntity.this;
            self.items.set(place, ItemStack.EMPTY);
            self.done[place] = false;
            self.progress[place] = 0;
        }

        @Override
        public Saved save() {
            var self = GrillBlockEntity.this;
            return new Saved(self.items.stream().map(ItemStack::copy).toList(), self.progress.clone(), self.time.clone(), self.done.clone());
        }

        @Override
        public void restore(Saved saved) {
            var self = GrillBlockEntity.this;
            for (int i = 0; i < SLOTS; i++) {
                self.items.set(i, saved.items().get(i));
            }
            System.arraycopy(saved.progress(), 0, self.progress, 0, SLOTS);
            System.arraycopy(saved.time(), 0, self.time, 0, SLOTS);
            System.arraycopy(saved.done(), 0, self.done, 0, SLOTS);
        }

        @Override
        public void changed() {
            GrillBlockEntity.this.updated();
        }
    });

    public ResourceHandler<ItemResource> itemHandler() {
        return this.handler;
    }

    public NonNullList<ItemStack> items() {
        return this.items;
    }

    /** Each piece on the grate, and whether it is done or how far along. */
    @Override
    public void readout(Consumer<Component> lines) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = this.items.get(slot);
            if (!stack.isEmpty()) {
                lines.accept(this.done[slot] ? Component.translatable("nookworks.grill.done", stack.getHoverName()).withColor(0x7EDC7E)
                        : Component.translatable("nookworks.grill.cooking", stack.getHoverName(),
                                this.time[slot] > 0 ? this.progress[slot] * 100 / this.time[slot] : 0));
            }
        }
    }

    public boolean done(int slot) {
        return this.done[slot];
    }

    /** The campfire recipe for {@code stack}, if it has one. */
    static Optional<RecipeHolder<CampfireCookingRecipe>> recipeFor(ServerLevel level, ItemStack stack) {
        return level.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack), level);
    }

    /** Puts one of {@code stack} in the first free place, if a campfire would cook it. */
    public boolean place(ServerLevel level, @Nullable LivingEntity by, ItemStack stack) {
        Optional<RecipeHolder<CampfireCookingRecipe>> recipe = recipeFor(level, stack);
        if (recipe.isEmpty()) {
            return false;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.items.get(slot).isEmpty()) {
                this.items.set(slot, stack.consumeAndReturn(1, by));
                this.time[slot] = recipe.get().value().cookingTime();
                this.progress[slot] = 0;
                this.done[slot] = false;
                this.updated();
                return true;
            }
        }
        return false;
    }

    /** Takes a piece off: a cooked one first, else the last one put on. */
    public ItemStack take() {
        int pick = -1;
        for (int slot = SLOTS - 1; slot >= 0; slot--) {
            if (!this.items.get(slot).isEmpty() && (pick < 0 || this.done[slot] && !this.done[pick])) {
                pick = slot;
            }
        }
        if (pick < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = this.items.get(pick);
        this.items.set(pick, ItemStack.EMPTY);
        this.done[pick] = false;
        this.progress[pick] = 0;
        this.updated();
        return out;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GrillBlockEntity grill) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean hot = GrillBlock.hot(level, pos);
        boolean changed = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = grill.items.get(slot);
            if (stack.isEmpty() || grill.done[slot]) {
                continue;
            }
            if (!hot) {
                if (grill.progress[slot] > 0) {
                    grill.progress[slot] = Math.max(0, grill.progress[slot] - 2);
                    changed = true;
                }
                continue;
            }
            changed = true;
            if (++grill.progress[slot] >= grill.time[slot]) {
                SingleRecipeInput input = new SingleRecipeInput(stack);
                ItemStack cooked = recipeFor(server, stack).map(r -> r.value().assemble(input)).orElse(stack);
                grill.items.set(slot, cooked);
                grill.done[slot] = true;
                grill.updated();
            }
        }
        if (changed && level.getGameTime() % 20 == 0) {
            grill.setChanged(); // saved now and then while it cooks; a piece done is saved and sent at once (updated())
        }
    }

    private void updated() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : this.items) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, stack.copy());
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            this.dropContents(this.level, pos);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
        output.putIntArray("Progress", this.progress);
        output.putIntArray("Time", this.time);
        int[] done = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            done[i] = this.done[i] ? 1 : 0;
        }
        output.putIntArray("Done", done);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        input.getIntArray("Progress").ifPresent(a -> System.arraycopy(a, 0, this.progress, 0, Math.min(SLOTS, a.length)));
        input.getIntArray("Time").ifPresent(a -> System.arraycopy(a, 0, this.time, 0, Math.min(SLOTS, a.length)));
        input.getIntArray("Done").ifPresent(a -> {
            for (int i = 0; i < Math.min(SLOTS, a.length); i++) {
                this.done[i] = a[i] != 0;
            }
        });
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client needs what lies on the grill (to draw it) and which pieces are done (for the smoke). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, this.items, true);
            int[] done = new int[SLOTS];
            for (int i = 0; i < SLOTS; i++) {
                done[i] = this.done[i] ? 1 : 0;
            }
            output.putIntArray("Done", done);
            return output.buildResult();
        }
    }
}
