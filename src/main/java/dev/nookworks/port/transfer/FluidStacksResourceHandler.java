package dev.nookworks.port.transfer;

import net.minecraft.core.NonNullList;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.fluid.FluidStacksResourceHandler}: tanks of fluid stacks, each holding up
 * to {@link #capacity} of one fluid.
 */
public class FluidStacksResourceHandler extends StacksResourceHandler<FluidStack, FluidResource> {
    protected int capacity;

    public FluidStacksResourceHandler(int size, int capacity) {
        super(size, FluidStack.EMPTY, FluidStack.OPTIONAL_CODEC);
        this.capacity = capacity;
    }

    public FluidStacksResourceHandler(NonNullList<FluidStack> stacks, int capacity) {
        super(stacks, FluidStack.EMPTY, FluidStack.OPTIONAL_CODEC);
        this.capacity = capacity;
    }

    @Override
    public FluidResource getResourceFrom(FluidStack stack) {
        return FluidResource.of(stack);
    }

    @Override
    public int getAmountFrom(FluidStack stack) {
        return stack.getAmount();
    }

    @Override
    protected FluidStack getStackFrom(FluidResource resource, int amount) {
        return resource.toStack(amount);
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return this.capacity;
    }

    @Override
    protected FluidStack copyOf(FluidStack stack) {
        return stack.copy();
    }

    @Override
    public boolean matches(FluidStack stack, FluidResource resource) {
        return resource.matches(stack);
    }
}
