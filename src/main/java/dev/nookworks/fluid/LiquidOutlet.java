package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A machine's liquid on its way out: pushed by the machine itself into what takes it beside it - a Copper Pipe whose line
 * leads to something that takes it (an empty line not finished yet, or one that only brings something in, is left
 * alone), a Copper Tank, another mod's tank or machine - never across a claim's edge ({@link DrawRights#mayMove}). The
 * Vacuum Distiller's methanol (not up, into its column) and the Sealed Retort's wood vinegar go out so.
 */
public final class LiquidOutlet {
    /** How far along a pipe line it looks for something that takes the liquid, as the Stirring Tank does for biodiesel. */
    private static final int LINE_LOOK = 512;

    private final boolean up;
    private final long[] askedAt = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2,
            Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    private final boolean[] leads = new boolean[6];

    /** {@code up}: whether the block above may take it too (not a Distiller's column). */
    public LiquidOutlet(boolean up) {
        this.up = up;
    }

    /** Pours what {@code tank} holds into what takes it around {@code pos}, as much as each will take. */
    public void push(ServerLevel level, BlockPos pos, BlockEntity machine, FluidStacksResourceHandler tank) {
        for (Direction side : Direction.values()) {
            int stored = tank.getAmountAsInt(0);
            if (stored <= 0) {
                return;
            }
            Fluid fluid = tank.getResource(0).getFluid();
            ResourceHandler<FluidResource> target = this.target(level, pos, machine, side, fluid, stored);
            if (target != null) {
                Move.transfer(tank, target, FluidResource.of(fluid), stored);
            }
        }
    }

    /** What on {@code side} takes {@code fluid}, of which the machine holds {@code held}, or null. */
    private @Nullable ResourceHandler<FluidResource> target(ServerLevel level, BlockPos pos, BlockEntity machine, Direction side,
            Fluid fluid, int held) {
        BlockPos next = pos.relative(side);
        if (side == Direction.UP && !this.up || !level.isLoaded(next)) {
            return null;
        }
        ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, next, side.getOpposite());
        BlockEntity there = level.getBlockEntity(next);
        if (target == null || !DrawRights.mayMove(level, pos, machine, next, there)) {
            return null;
        }
        FluidResource resource = FluidResource.of(fluid);
        if (there instanceof FluidPipeBlockEntity pipe && pipe.stored() <= 0) {
            return this.leadsOn(level, pos, side, pipe, resource) ? target : null;
        }
        // a cauldron beside it takes a bucket once the machine holds one (Tanks.wouldTake)
        return Tanks.wouldTake(target, resource, Math.max(1, held)) ? target : null;
    }

    /** {@link FluidPipeBlockEntity#leadsToTakerOf}, for the empty pipe on each side, asked once a second at most. */
    private boolean leadsOn(ServerLevel level, BlockPos pos, Direction side, FluidPipeBlockEntity pipe, FluidResource resource) {
        int i = side.ordinal();
        long now = level.getGameTime();
        if (now - this.askedAt[i] >= 20 || now < this.askedAt[i]) {
            this.askedAt[i] = now;
            this.leads[i] = pipe.leadsToTakerOf(level, resource, pos, LINE_LOOK);
        }
        return this.leads[i];
    }
}
