package dev.nookworks.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity;
import com.simibubi.create.content.kinetics.gearbox.GearboxRenderer;

import dev.nookworks.compat.create.CreateEnds;
import net.minecraft.core.Direction;

/**
 * Client: a Create Gearbox turned through one of our trains from a block many blocks away is drawn with its shafts
 * turning from the face the train meets it at, not from the nearest direction to its source's place (as
 * ShaftHalvesFacingMixin for its rules). Without Flywheel's visuals; GearboxVisualMixin with them.
 */
@Mixin(value = GearboxRenderer.class, remap = false)
abstract class GearboxRendererMixin {
    @ModifyExpressionValue(method = "renderSafe(Lcom/simibubi/create/content/kinetics/gearbox/GearboxBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;getNearest(FFF)Lnet/minecraft/core/Direction;"))
    private Direction nookworks$enteredAt(Direction nearest, GearboxBlockEntity be) {
        return CreateEnds.sourceFacing(be, nearest);
    }
}
