package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.feature.WoodVinegar;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

/**
 * A rabbit looking for carrots to raid passes over a bed that smells of wood vinegar
 * ({@link dev.nookworks.feature.WoodVinegar}): the farmland under the carrot is not a target while it is treated.
 */
@Mixin(targets = "net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal")
public abstract class RabbitRaidGardenGoalMixin {
    @Inject(method = "isValidTarget", at = @At("HEAD"), cancellable = true)
    private void nookworks$keepOffTreatedGround(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (WoodVinegar.isTreated(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
