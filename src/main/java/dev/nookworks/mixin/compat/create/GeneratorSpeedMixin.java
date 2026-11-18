package dev.nookworks.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;

import dev.nookworks.compat.create.CreateEnds;

/**
 * Before Create rules on one of its own generators whose speed changes (a Hand Crank turned, a Water Wheel's stream
 * turned round, a motor's dial): it breaks a generator that turns against the network turning it. Where only our Motors
 * turn that network, and may follow, they stand down first and the generator starts as on a still network
 * (CreateEnds#beforeNewSpeed): nothing of Create's is ever broken over a turning only we gave. Nothing is cancelled;
 * with nothing of ours in the network the speed is handed on as it came. Added only with Create installed (the config's
 * plugin, CompatMixins).
 */
@Mixin(value = GeneratingKineticBlockEntity.class, remap = false)
abstract class GeneratorSpeedMixin {
    @ModifyVariable(method = "applyNewSpeed(FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float nookworks$oursStandDown(float prevSpeed, float prevSpeedAsGiven, float speed) {
        return CreateEnds.beforeNewSpeed((GeneratingKineticBlockEntity) (Object) this, prevSpeed, speed);
    }
}
