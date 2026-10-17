package dev.nookworks.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Our fluids as they lie in the world, kept from being washed away. NeoForge's flowing fluid does as vanilla's water
 * does: any other fluid falling onto it takes its place - so water poured down onto a pool of mercury, or a mob farm's
 * water stream falling onto its pool of Liquid Experience, quietly did away with it, a bucket of mercury or fifty
 * points of experience at a time.
 * <ul>
 * <li>A <em>heavy</em> fluid - mercury, thirteen times as heavy as water - is never replaced: what falls onto it lies
 * on top, as water would on mercury.
 * <li>The others keep their sources; their flowing edges still give way to what falls on them, as water's do.
 * </ul>
 */
public final class KeptFluid {
    public static final class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn, Direction direction) {
            return false;
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        private final boolean heavy;

        public Flowing(Properties properties, boolean heavy) {
            super(properties);
            this.heavy = heavy;
        }

        @Override
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn, Direction direction) {
            return !this.heavy && super.canBeReplacedWith(state, level, pos, fluidIn, direction);
        }
    }

    private KeptFluid() {}
}
