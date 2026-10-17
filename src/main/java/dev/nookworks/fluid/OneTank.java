package dev.nookworks.fluid;

import net.minecraft.core.NonNullList;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A tank of one fluid that reads a save only as it can be: one stack, of a fluid it takes, no more than it holds. A
 * save may say otherwise - an operator's /data, a damaged file, a capacity lowered since - and a tank read with no stack
 * at all threw on its next tick, while one read over its capacity told a comparator more than fifteen. (Power is held
 * to its store the same way, by MachineBlockEntity.readEnergy.)
 */
public class OneTank extends FluidStacksResourceHandler {
    private final int capacity;

    public OneTank(int capacity) {
        super(NonNullList.withSize(1, FluidStack.EMPTY), capacity);
        this.capacity = capacity;
    }

    @Override
    public void deserialize(ValueInput input) {
        super.deserialize(input);
        FluidResource resource = this.size() > 0 ? this.getResource(0) : FluidResource.EMPTY;
        int amount = this.size() > 0 ? this.getAmountAsInt(0) : 0;
        boolean fine = resource.isEmpty() || amount > 0 && amount <= this.capacity && this.isValid(0, resource);
        if (this.size() == 1 && fine) {
            return;
        }
        this.setStacks(NonNullList.withSize(1, FluidStack.EMPTY));
        if (!resource.isEmpty() && amount > 0 && this.isValid(0, resource)) {
            this.set(0, resource, Math.min(amount, this.capacity));
        }
    }
}
