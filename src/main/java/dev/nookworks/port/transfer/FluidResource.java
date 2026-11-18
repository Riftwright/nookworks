package dev.nookworks.port.transfer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.fluid.FluidResource}: a fluid with its components and no amount,
 * immutable. Two are equal when their fluid and components are ({@link FluidStack#isSameFluidSameComponents}).
 */
public final class FluidResource implements Resource, DataComponentHolder {
    /** One shared resource per fluid without components (26.3 keeps it on the Fluid itself). */
    private static final Map<Fluid, FluidResource> PLAIN = new ConcurrentHashMap<>();

    public static final FluidResource EMPTY = new FluidResource(FluidStack.EMPTY);

    public static final Codec<FluidResource> CODEC = FluidStack.fixedAmountCodec(FluidType.BUCKET_VOLUME)
            .xmap(FluidResource::of, resource -> resource.toStack(FluidType.BUCKET_VOLUME));
    public static final Codec<FluidResource> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC).xmap(
            found -> found.orElse(EMPTY), resource -> resource.isEmpty() ? Optional.empty() : Optional.of(resource));
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidResource> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.FLUID), FluidResource::typeHolder,
            DataComponentPatch.STREAM_CODEC, FluidResource::getComponentsPatch,
            FluidResource::of);

    private final FluidStack stack;

    private FluidResource(FluidStack stack) {
        this.stack = stack;
    }

    /** The fluid and components of a stack (its amount is dropped). */
    public static FluidResource of(FluidStack stack) {
        if (stack.isEmpty() || stack.isComponentsPatchEmpty()) {
            return of(stack.getFluid());
        }
        return new FluidResource(stack.copyWithAmount(FluidType.BUCKET_VOLUME));
    }

    public static FluidResource of(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return EMPTY;
        }
        return PLAIN.computeIfAbsent(fluid, f -> new FluidResource(new FluidStack(f, FluidType.BUCKET_VOLUME)));
    }

    public static FluidResource of(Fluid fluid, DataComponentPatch patch) {
        return of(fluid.builtInRegistryHolder(), patch);
    }

    public static FluidResource of(Holder<Fluid> holder) {
        return of(holder.value());
    }

    public static FluidResource of(Holder<Fluid> holder, DataComponentPatch patch) {
        if (holder.value() == Fluids.EMPTY || patch.isEmpty()) {
            return of(holder.value());
        }
        return new FluidResource(new FluidStack(holder, FluidType.BUCKET_VOLUME, patch));
    }

    public Fluid value() {
        return this.stack.getFluid();
    }

    public Fluid getFluid() {
        return this.stack.getFluid();
    }

    public Holder<Fluid> typeHolder() {
        return this.stack.getFluidHolder();
    }

    public FluidType getFluidType() {
        return this.stack.getFluidType();
    }

    @Override
    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    public boolean is(FluidType fluidType) {
        return this.stack.is(fluidType);
    }

    public boolean is(Fluid fluid) {
        return this.stack.getFluid() == fluid;
    }

    public boolean is(TagKey<Fluid> tag) {
        return typeHolder().is(tag);
    }

    public boolean is(Holder<Fluid> holder) {
        return is(holder.value());
    }

    public boolean is(HolderSet<Fluid> set) {
        return set.contains(typeHolder());
    }

    public boolean is(ResourceKey<Fluid> key) {
        return typeHolder().is(key);
    }

    public boolean is(Predicate<Holder<Fluid>> predicate) {
        return predicate.test(typeHolder());
    }

    public Stream<TagKey<Fluid>> tags() {
        return typeHolder().tags();
    }

    /** Whether a stack is of this fluid with these components (its amount aside). */
    public boolean matches(FluidStack other) {
        return FluidStack.isSameFluidSameComponents(other, this.stack);
    }

    /** Tests the resource as a bucket's worth. */
    public boolean test(Predicate<FluidStack> predicate) {
        return predicate.test(this.stack);
    }

    public boolean isComponentsPatchEmpty() {
        return this.stack.isComponentsPatchEmpty();
    }

    public FluidResource withMergedPatch(DataComponentPatch patch) {
        if (isEmpty() || patch.isEmpty()) {
            return this;
        }
        FluidStack copy = this.stack.copy();
        copy.applyComponents(patch);
        return of(copy);
    }

    public <D> FluidResource with(DataComponentType<D> type, @Nullable D data) {
        if (isEmpty()) {
            return EMPTY;
        }
        if (Objects.equals(get(type), data)) {
            return this;
        }
        FluidStack copy = this.stack.copy();
        copy.set(type, data);
        return of(copy);
    }

    public <D> FluidResource with(Supplier<? extends DataComponentType<D>> type, @Nullable D data) {
        return with(type.get(), data);
    }

    public FluidResource without(DataComponentType<?> type) {
        if (isEmpty()) {
            return EMPTY;
        }
        if (get(type) == null) {
            return this;
        }
        FluidStack copy = this.stack.copy();
        copy.remove(type);
        return of(copy);
    }

    public FluidResource without(Supplier<? extends DataComponentType<?>> type) {
        return without(type.get());
    }

    @Override
    public DataComponentMap getComponents() {
        return this.stack.getComponents();
    }

    public DataComponentPatch getComponentsPatch() {
        return this.stack.getComponentsPatch();
    }

    /** A new stack of {@code amount} of this (empty for 0). */
    public FluidStack toStack(int amount) {
        TransferPreconditions.checkNonNegative(amount);
        return amount == 0 ? FluidStack.EMPTY : this.stack.copyWithAmount(amount);
    }

    public Component getHoverName() {
        return this.stack.getHoverName();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return this == obj || obj instanceof FluidResource other && FluidStack.isSameFluidSameComponents(this.stack, other.stack);
    }

    @Override
    public int hashCode() {
        return FluidStack.hashFluidAndComponents(this.stack);
    }

    @Override
    public String toString() {
        return getFluidType() + " [" + getComponentsPatch().size() + "]";
    }
}
