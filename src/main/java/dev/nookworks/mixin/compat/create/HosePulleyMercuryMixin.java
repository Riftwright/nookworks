package dev.nookworks.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour;

import dev.nookworks.compat.create.CreateFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's Hose Pulley drains no mercury: a mercury pool is no fluid it may pull from (see OpenPipeMercuryMixin). Added
 * only with Create installed (CompatMixins). Its answer type is package-private in Create, so it is set by name.
 */
@Mixin(value = FluidDrainingBehaviour.class, remap = false)
abstract class HosePulleyMercuryMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "canPullFluidsFrom", at = @At("HEAD"), cancellable = true)
    private void nookworks$noMercury(BlockState state, BlockPos pos, CallbackInfoReturnable cir) {
        if (CreateFluids.keptOut(state.getFluidState())) {
            cir.setReturnValue(CreateFluids.none(cir));
        }
    }
}
