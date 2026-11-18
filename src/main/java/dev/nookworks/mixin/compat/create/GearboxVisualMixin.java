package dev.nookworks.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity;
import com.simibubi.create.content.kinetics.gearbox.GearboxVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.nookworks.compat.create.CreateEnds;
import net.minecraft.core.Direction;

/**
 * Client, with Flywheel's visuals: as GearboxRendererMixin - a Create Gearbox turned through one of our trains from a
 * block many blocks away has its shafts drawn turning from the face the train meets it at.
 */
@Mixin(value = GearboxVisual.class, remap = false)
abstract class GearboxVisualMixin extends KineticBlockEntityVisual<GearboxBlockEntity> {
    private GearboxVisualMixin(VisualizationContext context, GearboxBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
    }

    @ModifyExpressionValue(method = "updateSourceFacing()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;getNearest(FFF)Lnet/minecraft/core/Direction;"))
    private Direction nookworks$enteredAt(Direction nearest) {
        return CreateEnds.sourceFacing(this.blockEntity, nearest);
    }
}
