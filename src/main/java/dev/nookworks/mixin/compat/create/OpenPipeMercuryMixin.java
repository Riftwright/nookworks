package dev.nookworks.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.fluids.OpenEndedPipe;

import dev.nookworks.compat.create.CreateFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A Create pipe's open end draws no mercury out of the world: mercury moves only through Nookworks' own iron tanks and
 * pipes (MercuryFluids), and a pool of it at a pump's pipe would otherwise go into Create's tanks. Added only with
 * Create installed (CompatMixins).
 */
@Mixin(value = OpenEndedPipe.class, remap = false)
abstract class OpenPipeMercuryMixin {
    @Shadow
    private Level world;
    @Shadow
    private BlockPos outputPos;

    @Inject(method = "removeFluidFromSpace(Z)Lnet/neoforged/neoforge/fluids/FluidStack;", at = @At("HEAD"), cancellable = true)
    private void nookworks$noMercury(boolean simulate, CallbackInfoReturnable<FluidStack> cir) {
        if (this.world != null && this.outputPos != null && this.world.isLoaded(this.outputPos)
                && CreateFluids.keptOut(this.world.getFluidState(this.outputPos))) {
            cir.setReturnValue(FluidStack.EMPTY);
        }
    }
}
