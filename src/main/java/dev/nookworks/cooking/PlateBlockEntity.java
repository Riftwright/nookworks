package dev.nookworks.cooking;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.machine.Readout;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What is on a Plate ({@link PlateBlock}): one kind of thing to eat - one of it, or as many as a plate holds of that food
 * ({@link Serving}: a few cookies, three onigiri) - kept as vanilla keeps a container's (its Items list, the one stack).
 */
public class PlateBlockEntity extends BlockEntity implements Readout {
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public PlateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLATE.get(), pos, state);
    }

    /**
     * Whether {@code stack} goes on a plate: anything to eat (bread dough rises on a tray, not here) - but for a bottle
     * that is set down as a bottle (a Honey Bottle: {@code PlacedBottlesBlock}), which stands on the table itself (the
     * user, 2026-11-14: ハチミツ入りの瓶を皿にのせるのはちょっと微妙 ... 瓶関係は、すでに全部ブロックとして配置できるはず),
     * and for a dish in its own bowl, which is set down as it is too ({@link PlacedBowlsBlock}: a bowl on a plate was a
     * dish on a dish). A plate that held a bowl before this keeps it until it is taken off. And what is eaten as a block
     * ({@link #GOES_ON_PLATES}): a cake (the user, 2026-11-14, play-testing: 「ケーキだけは皿に載せられない…悲しいな」).
     */
    public static boolean fits(ItemStack stack) {
        return !stack.isEmpty() && (stack.has(DataComponents.FOOD) || stack.is(GOES_ON_PLATES))
                && dev.nookworks.decor.BottleLook.of(stack) == null && !Plating.inBowl(stack);
    }

    /** What is food without being an item one eats - a cake, eaten set down as a block - and goes on a plate all the same. */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> GOES_ON_PLATES = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM, dev.nookworks.Nookworks.id("goes_on_plates"));

    /** Whether one more of {@code stack} goes on now: onto an empty plate, or beside more of the same while the plate has room for it. */
    public boolean takes(ItemStack stack) {
        if (!fits(stack)) {
            return false;
        }
        ItemStack food = this.food();
        return food.isEmpty() || !Plating.plated(food) && ItemStack.isSameItemSameComponents(food, stack)
                && food.getCount() < Serving.of(stack).plate();
    }

    /** Whether the plate holds as many of its food as it can. */
    public boolean isFull() {
        ItemStack food = this.food();
        return !food.isEmpty() && (Plating.plated(food) || food.getCount() >= Serving.of(food).plate());
    }

    /**
     * Whether what is on it is a dish served on a plate: this plate is that dish's, and goes with it - also once the dish
     * has been dropped as the block went ({@link #preRemoveSideEffects}): broken by a player, the block is taken away
     * before its drops are asked for, and an empty plate here then dropped a Plate beside the dish's own (1.7 review).
     */
    public boolean plated() {
        return this.platedDropped || Plating.plated(this.food());
    }

    /** A dish on its own plate dropped as this block went; not saved, as the block is gone. */
    private boolean platedDropped;

    public ItemStack food() {
        return this.items.getFirst();
    }

    public boolean isEmpty() {
        return this.food().isEmpty();
    }

    /** Puts one of {@code stack} on it, if it {@link #takes} it. */
    public boolean place(ItemStack stack, @Nullable LivingEntity by) {
        if (!this.takes(stack)) {
            return false;
        }
        if (this.isEmpty()) {
            this.items.set(0, stack.consumeAndReturn(1, by));
        } else {
            stack.consume(1, by);
            this.food().grow(1);
        }
        this.updated();
        return true;
    }

    /** Serves {@code dish} (a dish on a plate, one of it) on this empty plate, which becomes its plate. */
    public void serve(ItemStack dish) {
        this.items.set(0, dish.copyWithCount(1));
        this.updated();
    }

    /** One of what is on it, taken off. */
    public ItemStack take() {
        ItemStack out = this.food().split(1);
        this.updated();
        return out;
    }

    /** All of what is on it. */
    public ItemStack takeAll() {
        ItemStack out = this.food();
        this.items.set(0, ItemStack.EMPTY);
        this.updated();
        return out;
    }

    /**
     * The plate lifted with what is on it: its item, the food kept on it ({@link Plating#setOnPlate}). This entity is
     * left empty: the caller takes the block away. Not for a dish served on a plate, which is that one item already.
     */
    public ItemStack lift() {
        ItemStack plate = new ItemStack(this.getBlockState().getBlock());
        Plating.setOnPlate(plate, this.food());
        this.items.set(0, ItemStack.EMPTY);
        this.setChanged();
        return plate;
    }

    /** Set down from a Plate's item with food on it: the food is on the plate again. */
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        ItemContainerContents contents = components.get(DataComponents.CONTAINER);
        if (contents != null) {
            this.items.set(0, contents.copyOne());
        }
        components.get(DataComponents.MAX_STACK_SIZE); // read, so not kept on the block: it was the laden item's
    }

    /** Picked with its food (the creative pick-block key held with Ctrl): the same item a lifted plate is. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!this.isEmpty() && !this.plated()) {
            components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.items));
            components.set(DataComponents.MAX_STACK_SIZE, 1);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("Items");
    }

    @Override
    public void readout(Consumer<Component> lines) {
        ItemStack food = this.food();
        if (!food.isEmpty()) {
            lines.accept(food.getCount() == 1 ? food.getHoverName()
                    : Component.translatable("nookworks.iron_pot.item", food.getHoverName(), food.getCount()));
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
            this.platedDropped = Plating.plated(this.food());
            Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, this.food().copy());
            this.items.clear();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client draws what is on it. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, this.items, true);
            return output.buildResult();
        }
    }
}
