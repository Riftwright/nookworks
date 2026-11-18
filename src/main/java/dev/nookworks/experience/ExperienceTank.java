package dev.nookworks.experience;

import dev.nookworks.fluid.OneTank;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.TransactionContext;

/**
 * A tank of experience: any liquid in {@code #c:experience}, and the liquids worth what ours is as one
 * ({@link ExperienceKinds}) - EnderIO's XP Juice poured onto our Liquid Experience joins it as Liquid Experience, and a
 * pipe of theirs asking for its own juice is given it out of ours. (It held one of them and refused the rest: a tank
 * holding another mod's experience took none of ours on top of it, and a pipe of ours holding ours drew none of theirs.)
 */
public class ExperienceTank extends OneTank {
    public ExperienceTank(int capacity) {
        super(capacity);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.typeHolder().is(ExperienceFluids.EXPERIENCE);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return super.insert(index, ExperienceKinds.as(this.getResource(index), resource), amount, transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return super.extract(index, ExperienceKinds.as(this.getResource(index), resource), amount, transaction);
    }
}
