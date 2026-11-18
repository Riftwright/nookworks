package dev.nookworks.port.transfer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.item.ItemResource}: an item with its components and no count, immutable.
 * Two are equal when their item and components are ({@link ItemStack#isSameItemSameComponents}).
 */
public final class ItemResource implements Resource, DataComponentHolder {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** One shared resource per item without components (26.3 keeps it on the Item itself). */
    private static final Map<Item, ItemResource> PLAIN = new ConcurrentHashMap<>();

    public static final ItemResource EMPTY = new ItemResource(ItemStack.EMPTY);

    public static final Codec<ItemResource> CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(i -> i.group(
            ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemResource::typeHolder),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemResource::getComponentsPatch))
            .apply(i, ItemResource::of)));
    public static final Codec<ItemResource> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC).xmap(
            found -> found.orElse(EMPTY), resource -> resource.isEmpty() ? Optional.empty() : Optional.of(resource));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemResource> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.ITEM), ItemResource::typeHolder,
            DataComponentPatch.STREAM_CODEC, ItemResource::getComponentsPatch,
            ItemResource::of);

    private final ItemStack stack;

    private ItemResource(ItemStack stack) {
        this.stack = stack;
    }

    /** The item and components of a stack (its count is dropped). */
    public static ItemResource of(ItemStack stack) {
        if (stack.isEmpty() || stack.isComponentsPatchEmpty()) {
            return of(stack.getItem());
        }
        return new ItemResource(stack.copyWithCount(1));
    }

    public static ItemResource of(ItemLike item) {
        Item value = item.asItem();
        if (value == Items.AIR) {
            return EMPTY;
        }
        return PLAIN.computeIfAbsent(value, v -> new ItemResource(new ItemStack(v)));
    }

    public static ItemResource of(ItemLike item, DataComponentPatch patch) {
        return of(item.asItem().builtInRegistryHolder(), patch);
    }

    public static ItemResource of(Holder<Item> holder) {
        return of(holder.value());
    }

    public static ItemResource of(Holder<Item> holder, DataComponentPatch patch) {
        if (holder.value() == Items.AIR || patch.isEmpty()) {
            return of(holder.value());
        }
        ItemStack made = new ItemStack(holder, 1, patch);
        // a count of one is always within the stack size: only the components need checking
        var error = ItemStack.validateComponents(made.getComponents()).error();
        if (error.isPresent()) {
            LOGGER.warn("Item resource {} with components {} is not valid: {}", holder.getRegisteredName(), patch, error.get().message());
            return EMPTY;
        }
        return new ItemResource(made);
    }

    public Item value() {
        return this.stack.getItem();
    }

    public Item getItem() {
        return this.stack.getItem();
    }

    public Holder<Item> typeHolder() {
        return this.stack.getItemHolder();
    }

    @Override
    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    /** Whether a stack is of this item with these components (its count aside). */
    public boolean matches(ItemStack other) {
        return ItemStack.isSameItemSameComponents(other, this.stack);
    }

    public boolean is(ItemLike item) {
        return this.stack.getItem() == item.asItem();
    }

    public boolean is(Item item) {
        return this.stack.getItem() == item;
    }

    public boolean is(TagKey<Item> tag) {
        return typeHolder().is(tag);
    }

    public boolean is(Holder<Item> holder) {
        return is(holder.value());
    }

    public boolean is(HolderSet<Item> set) {
        return set.contains(typeHolder());
    }

    public boolean is(ResourceKey<Item> key) {
        return typeHolder().is(key);
    }

    public boolean is(Predicate<Holder<Item>> predicate) {
        return predicate.test(typeHolder());
    }

    public Stream<TagKey<Item>> tags() {
        return typeHolder().tags();
    }

    /** Tests the resource as a one-item stack. */
    public boolean test(Predicate<ItemStack> predicate) {
        return predicate.test(this.stack);
    }

    public boolean isComponentsPatchEmpty() {
        return this.stack.isComponentsPatchEmpty();
    }

    public ItemResource withMergedPatch(DataComponentPatch patch) {
        if (isEmpty() || patch.isEmpty()) {
            return this;
        }
        ItemStack copy = this.stack.copy();
        copy.applyComponents(patch);
        return of(copy);
    }

    public <D> ItemResource with(DataComponentType<D> type, @Nullable D data) {
        if (isEmpty()) {
            return EMPTY;
        }
        if (Objects.equals(get(type), data)) {
            return this;
        }
        ItemStack copy = this.stack.copy();
        copy.set(type, data);
        return of(copy);
    }

    public <D> ItemResource with(Supplier<? extends DataComponentType<D>> type, @Nullable D data) {
        return with(type.get(), data);
    }

    public ItemResource without(DataComponentType<?> type) {
        if (isEmpty()) {
            return EMPTY;
        }
        if (get(type) == null) {
            return this;
        }
        ItemStack copy = this.stack.copy();
        copy.remove(type);
        return of(copy);
    }

    public ItemResource without(Supplier<? extends DataComponentType<?>> type) {
        return without(type.get());
    }

    @Override
    public DataComponentMap getComponents() {
        return this.stack.getComponents();
    }

    public DataComponentPatch getComponentsPatch() {
        return this.stack.getComponentsPatch();
    }

    /** A new stack of {@code count} of this (empty for 0); it may be over the item's stack size. */
    public ItemStack toStack(int count) {
        TransferPreconditions.checkNonNegative(count);
        return count == 0 ? ItemStack.EMPTY : this.stack.copyWithCount(count);
    }

    /** A new stack of one. */
    public ItemStack toStack() {
        return this.stack.copyWithCount(1);
    }

    public int getMaxStackSize() {
        return this.stack.getMaxStackSize();
    }

    public Component getHoverName() {
        return this.stack.getHoverName();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return this == obj || obj instanceof ItemResource other && ItemStack.isSameItemSameComponents(this.stack, other.stack);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(this.stack);
    }

    @Override
    public String toString() {
        return value() + " [" + getComponentsPatch().size() + "]";
    }
}
