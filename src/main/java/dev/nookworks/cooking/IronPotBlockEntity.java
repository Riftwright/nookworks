package dev.nookworks.cooking;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.NookworksNetwork;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What is in an Iron Pot: up to {@link #SLOTS} ingredients, up to {@link #CAPACITY} mB of water or of vegetable oil
 * (never both: bottles, buckets, or a Copper Pipe), and once cooked, the dish. Each batch uses the little liquid its
 * {@link PotRecipe} asks for; the rest keeps for the next.
 * <p>
 * An ingredient that came in a bowl or a bottle (a Bowl of Rice, Methanol) goes in with it, and taken back out it is
 * still in it. Only when the cooking uses it up does its bowl come free: the pot keeps it with the dish, and serves the
 * dish into it (fried rice into the rice's bowl) or hands it back with a dish taken out by hand. Nothing is counted
 * twice. The one way to a bowl from nothing is the pot broken with a dish of bowls still in it: the dish drops as it
 * is, bowls and all, rather than the food being lost (the user's call: a bowl is cheap, and spilt soup is not).
 * <p>
 * Cooking starts {@link #SETTLE} ticks after the last thing went in - long enough to add the rest of a recipe - and
 * belongs to the recipe it started as: change what is in the pot into another recipe and it starts over.
 */
public class IronPotBlockEntity extends BlockEntity implements Readout, DrawRights.Owned {
    public static final int SLOTS = 8;
    public static final int CAPACITY = 1_000;
    /** A bottle's worth. */
    public static final int BOTTLE = 250;
    /** Ticks the pot waits after something goes in before it starts cooking. */
    public static final int SETTLE = 40;

    /**
     * Who set it down; null for one set down before owners were kept. A stranger's pot set beside a claimed line takes
     * no oil from it.
     */
    private @Nullable UUID owner;
    /** What its owner may take from and give into across a claim's edge, as last asked. */
    private final DrawRights drawRights = new DrawRights();

    private final NonNullList<ItemStack> contents = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private ItemStack dish = ItemStack.EMPTY;
    /** What the dish is ladled out into, or null when it is taken out by hand. */
    private @Nullable Item container;
    /** Bowls and bottles the cooked ingredients came in, freed by the cooking and kept with the dish. */
    private final List<ItemStack> freed = new ArrayList<>();
    private int progress;
    /** The recipe the progress belongs to. */
    private @Nullable Identifier cooking;
    /** Ticks since the contents last changed. */
    private int settled = SETTLE;
    /** Set while the pot is being moved to another block (onto or off a grill): its contents go with it, not on the floor. */
    private boolean moving;
    /**
     * What {@link PotRecipe#find} last gave, and for what: the contents as they were (copies), the liquid and how much,
     * and the recipes it looked in. A heated pot with no dish searched every pot recipe every tick; now only when one
     * of those changes (the 10-19 performance audit).
     */
    private final NonNullList<ItemStack> foundFor = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private PotRecipe.@Nullable Liquid foundLiquid;
    private int foundAmount = -1;
    private @Nullable Object foundIn;
    private @Nullable RecipeHolder<PotRecipe> found;

    /** The water or oil, filled from outside and used only by the cooking. */
    private final FluidStacksResourceHandler liquid = new OneTank(CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return PotRecipe.Liquid.of(resource.getFluid()) != null;
        }

        /** A pipe keeps pouring only while nothing cooked waits in the pot, as a bottle does. */
        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return IronPotBlockEntity.this.dish.isEmpty() ? super.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            IronPotBlockEntity.this.changed();
            IronPotBlockEntity.this.showLiquid();
        }
    };

    public IronPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IRON_POT.get(), pos, state);
    }

    // ---- as hoppers and pipes see it ----

    /** Where the dish is, the place for bowls, and the first of what the cooking freed, among the handler's slots. */
    private static final int DISH = SLOTS;
    private static final int BOWLS = SLOTS + 1;
    private static final int FREED = SLOTS + 2;
    /** How many freed stacks a pipe can see at once: more than the pot ever frees in one cooking. */
    private static final int FREED_SHOWN = 8;

    private record Saved(List<ItemStack> contents, ItemStack dish, List<ItemStack> freed, int settled) {}

    /**
     * The pot to a hopper or a pipe. Ingredients go in (what some recipe of the pot takes, eight in all) while no dish
     * waits; once one waits, empty bowls of its kind go in, as many as it has servings without one. The dish comes out a
     * serving at a time, each into a bowl the pot holds - as a hand ladles it - or whole when it needs none; what the
     * cooking freed and no serving needs (a milk bucket, a spare bowl) comes out after. Nothing part cooked ever does.
     */
    private final class ItemHandler extends SnapshotJournal<Saved> implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return FREED + FREED_SHOWN;
        }

        @Override
        public ItemResource getResource(int index) {
            var self = IronPotBlockEntity.this;
            return index < SLOTS ? ItemResource.of(self.contents.get(index)) : index == DISH ? ItemResource.of(self.dish)
                    : index >= FREED && index - FREED < self.freed.size() ? ItemResource.of(self.freed.get(index - FREED)) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            var self = IronPotBlockEntity.this;
            return index < SLOTS ? self.contents.get(index).getCount() : index == DISH ? self.servable()
                    : index >= FREED && index - FREED < self.freed.size() ? self.freed.get(index - FREED).getCount() : 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            var self = IronPotBlockEntity.this;
            if (index < SLOTS) {
                return self.contents.get(index).getCount() + (self.dish.isEmpty() ? SLOTS - self.count() : 0);
            }
            return index == BOWLS ? self.bowlsWanted() : this.getAmountAsLong(index);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            var self = IronPotBlockEntity.this;
            return index < SLOTS ? self.level != null && PotRecipe.isIngredient(self.level, resource.toStack(1))
                    : index == BOWLS && self.container != null && resource.is(self.container);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            var self = IronPotBlockEntity.this;
            if (amount <= 0 || resource.isEmpty()) {
                return 0;
            }
            if (index == BOWLS) {
                int take = Math.min(amount, self.bowlsWanted());
                if (self.container == null || !resource.is(self.container) || take <= 0) {
                    return 0;
                }
                this.updateSnapshots(transaction);
                self.free(resource.toStack(1), take);
                return take;
            }
            if (index >= SLOTS || !self.dish.isEmpty() || self.count() >= SLOTS || index != self.slotFor(resource)
                    || !this.isValid(index, resource)) {
                return 0;
            }
            int take = Math.min(amount, SLOTS - self.count());
            this.updateSnapshots(transaction);
            ItemStack held = self.contents.get(index);
            if (held.isEmpty()) {
                self.contents.set(index, resource.toStack(take));
            } else {
                held.grow(take);
            }
            self.settled = 0; // as by hand: it waits again before it cooks
            return take;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            var self = IronPotBlockEntity.this;
            if (amount <= 0 || resource.isEmpty()) {
                return 0;
            }
            if (index == DISH) {
                int take = Math.min(amount, self.servable());
                if (take <= 0 || !resource.matches(self.dish)) {
                    return 0;
                }
                this.updateSnapshots(transaction);
                self.dish.shrink(take);
                if (self.container != null) {
                    int bowls = take;
                    for (ItemStack bowl : self.freed) {
                        int used = bowl.is(self.container) ? Math.min(bowls, bowl.getCount()) : 0;
                        bowl.shrink(used);
                        bowls -= used;
                    }
                }
                return take;
            }
            if (index < FREED || index - FREED >= self.freed.size()) {
                return 0;
            }
            ItemStack freed = self.freed.get(index - FREED);
            boolean forServing = !self.dish.isEmpty() && self.container != null && freed.is(self.container);
            if (freed.isEmpty() || forServing || !resource.matches(freed)) {
                return 0;
            }
            int take = Math.min(amount, freed.getCount());
            this.updateSnapshots(transaction);
            freed.shrink(take);
            return take;
        }

        @Override
        protected Saved createSnapshot() {
            var self = IronPotBlockEntity.this;
            return new Saved(self.contents.stream().map(ItemStack::copy).toList(), self.dish.copy(),
                    self.freed.stream().map(ItemStack::copy).toList(), self.settled);
        }

        @Override
        protected void revertToSnapshot(Saved snapshot) {
            var self = IronPotBlockEntity.this;
            for (int i = 0; i < SLOTS; i++) {
                self.contents.set(i, snapshot.contents().get(i));
            }
            self.dish = snapshot.dish();
            self.freed.clear();
            self.freed.addAll(snapshot.freed());
            self.settled = snapshot.settled();
        }

        @Override
        protected void onRootCommit(Saved originalState) {
            IronPotBlockEntity.this.freed.removeIf(ItemStack::isEmpty);
            IronPotBlockEntity.this.changed();
        }
    }

    private final ItemHandler itemHandler = new ItemHandler();

    public ResourceHandler<ItemResource> itemHandler() {
        return this.itemHandler;
    }

    /** The slot one of {@code resource} goes into, as by hand: where the same is already, else the first empty one. */
    private int slotFor(ItemResource resource) {
        int empty = -1;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack held = this.contents.get(i);
            if (!held.isEmpty() && resource.matches(held)) {
                return i;
            }
            if (held.isEmpty() && empty < 0) {
                empty = i;
            }
        }
        return empty;
    }

    /** The servings of the dish that can come out now: each into a bowl the pot holds, or all when it needs none. */
    private int servable() {
        return this.container == null ? this.dish.getCount() : Math.min(this.dish.getCount(), this.freedOf(this.container));
    }

    /** The bowls the dish still wants, one to each serving the pot holds none for. */
    private int bowlsWanted() {
        return this.container == null || this.dish.isEmpty() ? 0 : Math.max(0, this.dish.getCount() - this.freedOf(this.container));
    }

    private int freedOf(Item item) {
        return this.freed.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    public FluidStacksResourceHandler liquidHandler() {
        return this.liquid;
    }

    /** How much water or oil is in. */
    public int amount() {
        return this.liquid.getAmountAsInt(0);
    }

    /** Which it is, or null when the pot is dry. */
    public PotRecipe.@Nullable Liquid liquid() {
        return this.amount() > 0 ? PotRecipe.Liquid.of(this.liquid.getResource(0).getFluid()) : null;
    }

    public ItemStack dish() {
        return this.dish;
    }

    /** What the dish waiting in the pot is ladled out into (a bowl, a glass bottle), or null for one taken by hand. */
    public @Nullable Item container() {
        return this.container;
    }

    public List<ItemStack> contents() {
        return this.contents;
    }

    /** The bowls and bottles freed by the cooking, still in the pot. */
    public List<ItemStack> freed() {
        return this.freed;
    }

    /** Why liquid would not go in, or null when it would. */
    public enum Refusal {
        DISH_WAITING, OTHER_LIQUID, FULL
    }

    /** Whether {@code mB} of {@code what} would go in, and if not, why. */
    public @Nullable Refusal refusesLiquid(PotRecipe.Liquid what, int mB) {
        PotRecipe.Liquid held = this.liquid();
        return !this.dish.isEmpty() ? Refusal.DISH_WAITING
                : held != null && held != what ? Refusal.OTHER_LIQUID
                : this.amount() + mB > CAPACITY ? Refusal.FULL : null;
    }

    /** Pours in {@code mB} of water or oil if it all fits and the pot does not hold the other. */
    public boolean addLiquid(PotRecipe.Liquid what, int mB) {
        if (this.refusesLiquid(what, mB) != null) {
            return false;
        }
        this.liquid.set(0, FluidResource.of(what.fluid()), this.amount() + mB);
        return true;
    }

    /** Takes exactly {@code mB} of the water or oil back out (into a bottle or a bucket), or nothing. */
    public boolean drainLiquid(int mB) {
        if (this.amount() < mB || !this.dish.isEmpty()) {
            return false;
        }
        this.liquid.set(0, this.liquid.getResource(0), this.amount() - mB);
        return true;
    }

    /** Puts one of {@code stack} in, if there is room and the dish is not waiting to be taken out. */
    public boolean addIngredient(ItemStack stack) {
        if (!this.dish.isEmpty() || this.count() >= SLOTS) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            ItemStack held = this.contents.get(i);
            if (held.isEmpty()) {
                this.contents.set(i, stack.copyWithCount(1));
                this.stirred();
                return true;
            }
            if (ItemStack.isSameItemSameComponents(held, stack)) {
                held.grow(1);
                this.stirred();
                return true;
            }
        }
        return false;
    }

    /** How many ingredients are in: the pot holds eight, whatever they are. */
    public int count() {
        return this.contents.stream().mapToInt(ItemStack::getCount).sum();
    }

    /** Takes the last kind of ingredient back out, all of it (a Bowl of Rice still in its bowl). */
    public ItemStack takeIngredient() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!this.contents.get(i).isEmpty()) {
                ItemStack out = this.contents.get(i);
                this.contents.set(i, ItemStack.EMPTY);
                this.stirred();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    /** One serving of the dish, or empty. The container it goes into is the caller's (or {@link #serveIntoFreed}). */
    public ItemStack serve() {
        if (this.dish.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = this.dish.split(1);
        this.changed();
        return out;
    }

    /** Whether containers of the dish's kind wait in it, for an empty hand to serve into. */
    private boolean freedContainers() {
        return this.container != null && this.freed.stream().anyMatch(stack -> stack.is(this.container));
    }

    /** As many servings as there are freed containers of the dish's kind, each into one of them. */
    public List<ItemStack> serveIntoFreed() {
        List<ItemStack> out = new ArrayList<>();
        if (this.container == null) {
            return out;
        }
        for (ItemStack bowl : this.freed) {
            while (bowl.is(this.container) && !bowl.isEmpty() && !this.dish.isEmpty()) {
                bowl.shrink(1);
                out.add(this.dish.split(1));
            }
        }
        this.freed.removeIf(ItemStack::isEmpty);
        if (!out.isEmpty()) {
            this.changed();
        }
        return out;
    }

    /** All of the dish at once, with any freed bowls; or empty. */
    public ItemStack takeDish() {
        ItemStack out = this.dish;
        this.dish = ItemStack.EMPTY;
        this.changed();
        return out;
    }

    /** The freed bowls and bottles, all of them. */
    public List<ItemStack> takeFreed() {
        List<ItemStack> out = new ArrayList<>(this.freed);
        this.freed.clear();
        if (!out.isEmpty()) {
            this.changed();
        }
        return out;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IronPotBlockEntity pot) {
        if (level instanceof ServerLevel server) {
            pot.tick(server, pos, state);
        }
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (this.settled < SETTLE) {
            this.settled++;
        }
        var found = this.dish.isEmpty() && IronPotBlock.heated(level, pos) ? this.recipeNow(level) : null;
        boolean cooking = found != null;
        if (state.getValue(IronPotBlock.COOKING) != cooking) {
            level.setBlock(pos, state.setValue(IronPotBlock.COOKING, cooking), Block.UPDATE_CLIENTS);
        }
        if (!cooking) {
            // a recipe found during the settle wait and lost again (a bottle taken out) left its id: "Cooking 0%" for
            // ever, over why it does not cook (the round-two fix re-check)
            if (this.progress != 0 || this.cooking != null) {
                this.progress = 0;
                this.cooking = null;
                this.setChanged();
            }
            return;
        }
        Identifier id = found.id().identifier();
        if (!id.equals(this.cooking)) {
            this.cooking = id; // another recipe now: what was done belonged to the old one
            this.progress = 0;
            this.setChanged();
        }
        if (this.settled < SETTLE) {
            return; // someone is still putting things in
        }
        PotRecipe recipe = found.value();
        if (++this.progress < recipe.time()) {
            if (this.progress % 20 == 0) {
                this.setChanged(); // a save part way through keeps the progress; no need to mark the chunk every tick
            }
            return;
        }
        this.progress = 0;
        this.cooking = null;
        int batches = recipe.batches(this.contents, this.liquid(), this.amount());
        for (PotRecipe.Part part : recipe.ingredients()) {
            int left = part.count() * batches;
            for (ItemStack held : this.contents) {
                if (left > 0 && part.ingredient().test(held)) {
                    int take = Math.min(left, held.getCount());
                    ItemStackTemplate remainder = held.getCraftingRemainder();
                    if (remainder != null) {
                        this.free(remainder.create(), take); // the bowl the rice came in
                    }
                    held.shrink(take);
                    left -= take;
                }
            }
        }
        this.dish = recipe.result().stack(batches);
        this.container = recipe.container().map(Holder::value).orElse(null);
        this.liquid.set(0, FluidResource.of(recipe.liquid().fluid()), this.amount() - recipe.amount() * batches);
        this.changed();
        level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, 0.5F, 1.4F);
    }

    private void free(ItemStack one, int count) {
        for (ItemStack held : this.freed) {
            if (ItemStack.isSameItemSameComponents(held, one) && held.getCount() + count <= held.getMaxStackSize()) {
                held.grow(count);
                return;
            }
        }
        this.freed.add(one.copyWithCount(count));
    }

    /** The ingredients changed: the pot waits again before it cooks. */
    private void stirred() {
        this.settled = 0;
        this.changed();
    }

    /** Saved, and shown to the players who can see the pot (what is in it is drawn). */
    private void changed() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** {@link PotRecipe#find} for what the pot holds now: the last answer, while nothing it rests on has changed. */
    private @Nullable RecipeHolder<PotRecipe> recipeNow(ServerLevel level) {
        Object recipes = level.recipeAccess().recipeMap(); // a new one on every /reload
        PotRecipe.Liquid held = this.liquid();
        int amount = this.amount();
        boolean same = recipes == this.foundIn && held == this.foundLiquid && amount == this.foundAmount;
        for (int slot = 0; same && slot < SLOTS; slot++) {
            same = ItemStack.matches(this.contents.get(slot), this.foundFor.get(slot));
        }
        if (!same) {
            this.found = PotRecipe.find(level, this.contents, held, amount);
            this.foundIn = recipes;
            this.foundLiquid = held;
            this.foundAmount = amount;
            for (int slot = 0; slot < SLOTS; slot++) {
                this.foundFor.set(slot, this.contents.get(slot).copy());
            }
        }
        return this.found;
    }

    /** Keeps the block's LIQUID state (what the model shows in the pot) in step with what is in it. */
    private void showLiquid() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        PotRecipe.Liquid held = this.liquid();
        IronPotBlock.Contents shown = held == null ? IronPotBlock.Contents.EMPTY
                : held == PotRecipe.Liquid.WATER ? IronPotBlock.Contents.WATER : IronPotBlock.Contents.OIL;
        if (state.hasProperty(IronPotBlock.LIQUID) && state.getValue(IronPotBlock.LIQUID) != shown) {
            this.level.setBlock(this.worldPosition, state.setValue(IronPotBlock.LIQUID, shown), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * What the pot holds, as the client is sent it to show on the action bar (an empty hand's click), or null with a dish
     * in it ({@link #describe} says that, in one short line). Sent as parts: the client fits them to its screen
     * ({@code PotLines}), as the whole list of a full pot did not fit one line (cream stew, six kinds, ran past a 1280x720
     * window in English and Japanese, past 1080p's in Spanish, Portuguese and Russian).
     */
    public NookworksNetwork.@Nullable PotContents shown() {
        if (!this.dish.isEmpty()) {
            return null;
        }
        PotRecipe.Liquid held = this.liquid();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : this.contents) {
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return new NookworksNetwork.PotContents(held == null ? 0 : held == PotRecipe.Liquid.WATER ? 1 : 2, this.amount(), CAPACITY, items,
                Optional.ofNullable(this.stuck()));
    }

    /** What the pot holds, for the player who looks in. */
    public Component describe() {
        if (!this.dish.isEmpty()) {
            // with the bowls its rice came in still in it, an empty hand serves into them: it said "ladle it out with a
            // bowl" and, under it, that the bowl in it comes out once the dish is served (the kitchen play-test)
            return this.container != null
                    ? Component.translatable(this.freedContainers() ? "nookworks.iron_pot.ready_serve_freed" : "nookworks.iron_pot.ready_serve",
                            this.dish.getCount(), this.dish.getHoverName(), new ItemStack(this.container).getHoverName())
                    : Component.translatable("nookworks.iron_pot.ready", this.dish.getCount(), this.dish.getHoverName());
        }
        PotRecipe.Liquid held = this.liquid();
        MutableComponent line = Component.translatable(held == PotRecipe.Liquid.OIL ? "nookworks.iron_pot.oil"
                : held == PotRecipe.Liquid.WATER ? "nookworks.iron_pot.water" : "nookworks.iron_pot.empty", this.amount(), CAPACITY);
        for (ItemStack stack : this.contents) {
            if (!stack.isEmpty()) {
                line = line.append(Component.translatable("nookworks.list.separator")).append(Component.translatable("nookworks.iron_pot.item", stack.getHoverName(), stack.getCount()));
            }
        }
        return line;
    }

    /**
     * Why a pot over the fire, with water or oil and things in it, cooks nothing - or null when it cooks, or waits for
     * nothing it could be told: short of the liquid a recipe of all of them needs (how much), or no recipe of them yet.
     * It said nothing at all, and its contents alone did not tell a pot a bottle short of udon from one about to cook
     * (round two of r21).
     */
    public @Nullable Component stuck() {
        PotRecipe.Liquid held = this.liquid();
        if (this.level == null || !this.dish.isEmpty() || this.count() == 0 || held == null
                || !IronPotBlock.heated(this.level, this.worldPosition)
                || PotRecipe.find(this.level, this.contents, held, this.amount()) != null) {
            return null;
        }
        // only a recipe of the liquid held can be short of it: one of the other liquid matched eggs in oil to boiled eggs
        // and said "needs water" to a pot halfway to tamagoyaki (the round-two fix re-check)
        boolean mayBecome = false;
        boolean otherTakes = false;
        for (RecipeHolder<PotRecipe> holder : PotRecipe.all(this.level)) {
            PotRecipe recipe = holder.value();
            if (recipe.liquid() == held && recipe.batches(this.contents, held, Integer.MAX_VALUE) > 0) {
                return Component.translatable(held == PotRecipe.Liquid.WATER ? "nookworks.iron_pot.needs_water"
                        : "nookworks.iron_pot.needs_oil", recipe.amount()).withColor(0xE0A548);
            }
            if (this.allPartOf(recipe)) {
                mayBecome |= recipe.liquid() == held; // cream stew begun: the chicken is in, the rest to come
                otherTakes |= recipe.liquid() != held;
            }
        }
        if (!mayBecome && otherTakes) {
            return Component.translatable(held == PotRecipe.Liquid.WATER ? "nookworks.iron_pot.cooks_in_oil"
                    : "nookworks.iron_pot.cooks_in_water").withColor(0xE0A548);
        }
        return Component.translatable("nookworks.iron_pot.no_recipe").withColor(0xE0A548);
    }

    /** Whether every thing in the pot belongs to some part of {@code recipe}, however many of each. */
    private boolean allPartOf(PotRecipe recipe) {
        for (ItemStack stack : this.contents) {
            if (!stack.isEmpty() && recipe.partFor(stack) < 0) {
                return false;
            }
        }
        return true;
    }

    /** What is in it, and how far the cooking is - and the bowls or buckets its cooking has freed, to take back. */
    @Override
    public void readout(Consumer<Component> lines) {
        this.lines(lines, true);
    }

    /** To Jade, the same less the liquid and the things in it, which Jade shows itself (r15 fix check; r21). */
    @Override
    public void jade(Consumer<Component> lines) {
        this.lines(lines, false);
    }

    private void lines(Consumer<Component> lines, boolean liquid) {
        // to Jade, not the things in it: Jade lists them itself, with their pictures, and they showed twice, as on the
        // Tray before (round two of r21, in the client)
        if (liquid || !this.dish.isEmpty()) {
            lines.accept(this.describe());
        }
        // a hand takes them back - unless a dish waits for bowls, when a hand serves it first
        String freedKey = !this.dish.isEmpty() && this.container != null ? "nookworks.iron_pot.freed.after" : "nookworks.iron_pot.freed";
        for (ItemStack freed : this.freed) {
            // the dish's own containers are named in its line, as what an empty hand serves into
            if (!freed.isEmpty() && !(this.container != null && !this.dish.isEmpty() && freed.is(this.container))) {
                lines.accept(Component.translatable(freedKey, freed.getHoverName(), freed.getCount()).withColor(0xA3A9B2));
            }
        }
        int percent = this.percent();
        if (percent >= 0) {
            lines.accept(Component.translatable("nookworks.iron_pot.cooking", percent).withColor(0x7EDC7E));
        } else if (this.dish.isEmpty() && this.level != null && !IronPotBlock.heated(this.level, this.worldPosition)) {
            if (this.level.getBlockState(this.worldPosition.below()).is(BlockTags.CAMPFIRES)
                    && !this.getBlockState().getValue(IronPotBlock.ON_GRILL)) {
                // set straight on a campfire, as looks natural: say what is missing, full or empty (on its grill over a
                // campfire gone out, it is only cold)
                lines.accept(Component.translatable("nookworks.iron_pot.needs_grill").withColor(0xE0A548));
            } else if (this.count() > 0) {
                lines.accept(Component.translatable("nookworks.iron_pot.cold").withColor(0xA3A9B2));
            }
        } else if (this.dish.isEmpty() && this.count() > 0 && this.liquid() == null) {
            // over the fire with things in it and nothing to cook them in: it said nothing at all (the kitchen play-test)
            lines.accept(Component.translatable("nookworks.iron_pot.dry").withColor(0xE0A548));
        } else {
            Component why = this.stuck();
            if (why != null) {
                lines.accept(why);
            }
        }
    }

    /** For the screen-less status line: how far the cooking is, 0-100, or -1 when nothing cooks. */
    public int percent() {
        if (this.cooking == null || this.level == null) {
            return -1;
        }
        PotRecipe recipe = PotRecipe.byId(this.level, this.cooking);
        return recipe == null ? -1 : Math.min(100, this.progress * 100 / Math.max(1, recipe.time()));
    }

    /** Broken, it lets go of what it held, the dish as it is (the water or oil is lost). */
    public void dropContents(Level level, BlockPos pos) {
        List<ItemStack> all = new ArrayList<>(this.contents);
        all.add(this.dish);
        all.addAll(this.freed);
        for (ItemStack held : all) {
            // in stacks it can be held in: eight soap (one to a stack) dropped as one item were cut to one by a hopper
            ItemStack rest = held.copy();
            while (!rest.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        rest.split(Math.max(1, rest.getMaxStackSize())));
            }
        }
    }

    /** About to be moved to another block: keep the contents for the copy instead of dropping them. */
    void moving() {
        this.moving = true;
    }

    @Override
    public @Nullable UUID owner() {
        return this.owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    @Override
    public DrawRights drawRights() {
        return this.drawRights;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
        this.saveContents(output);
        output.putInt("Progress", this.progress);
        output.putInt("Settled", this.settled);
        if (this.cooking != null) {
            output.store("Cooking", Identifier.CODEC, this.cooking);
        }
    }

    /** What is in the pot: saved, and what a client needs to draw it. */
    private void saveContents(ValueOutput output) {
        ContainerHelper.saveAllItems(output, this.contents);
        if (!this.dish.isEmpty()) {
            output.store("Dish", ItemStack.CODEC, this.dish);
        }
        if (this.container != null) {
            output.store("Container", BuiltInRegistries.ITEM.byNameCodec(), this.container);
        }
        if (!this.freed.isEmpty()) {
            output.store("Freed", ItemStack.CODEC.listOf(), this.freed);
        }
        this.liquid.serialize(output.child("Liquid"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.contents.clear();
        ContainerHelper.loadAllItems(input, this.contents);
        this.dish = input.read("Dish", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.container = input.read("Container", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
        this.freed.clear();
        input.read("Freed", ItemStack.CODEC.listOf()).ifPresent(this.freed::addAll);
        this.progress = input.getIntOr("Progress", 0);
        this.settled = input.getIntOr("Settled", SETTLE);
        this.cooking = input.read("Cooking", Identifier.CODEC).orElse(null);
        input.child("Liquid").or(() -> input.child("Water")).ifPresent(this.liquid::deserialize);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            this.saveContents(output);
            return output.buildResult();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide() && !this.moving) {
            this.dropContents(this.level, pos);
        }
    }

    /** For tests: progress so far. */
    public int progress() {
        return this.progress;
    }
}
