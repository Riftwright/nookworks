package dev.nookworks.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.nookworks.compat.create.CreateEnds;
import net.minecraft.core.Direction;

/**
 * A Create Gearbox, Clutch or Gearshift turned through one of our trains from a block many blocks away: Create works
 * out the face its turning enters at as the nearest direction to its source's place, which is right only for a
 * neighbour. Joined through a train, the face is the one the train meets it at (CreateEnds#sourceFacing). Added only
 * with Create installed (the config's plugin, CompatMixins).
 */
@Mixin(value = DirectionalShaftHalvesBlockEntity.class, remap = false)
abstract class ShaftHalvesFacingMixin {
    @ModifyReturnValue(method = "getSourceFacing()Lnet/minecraft/core/Direction;", at = @At("RETURN"))
    private Direction nookworks$enteredAt(Direction nearest) {
        return CreateEnds.sourceFacing((KineticBlockEntity) (Object) this, nearest);
    }
}
