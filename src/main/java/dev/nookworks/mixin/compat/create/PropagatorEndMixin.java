package dev.nookworks.mixin.compat.create;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.nookworks.compat.create.CreateEnds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Create's propagation of rotation with our trains in it (CreateEnds; the design: tools/port1211/design_create.md,
 * "Ends and trains"): the Create blocks at the ends of a train with no Motor are neighbours to it, the train's sign
 * between them - so speed, stress and capacity cross a Drive Wheel or an add-on's line of Shafts by Create's own rules.
 * The propagator's own two questions are answered, not the three a block entity may answer for itself: Create's belt,
 * Large Cogwheel, Chain Conveyor and Gantry Shaft answer those without asking further up, and would be left out. Each
 * hook adds to Create's answer; none cancels. Added only with Create installed (the config's plugin, CompatMixins).
 */
@Mixin(value = RotationPropagator.class, remap = false)
abstract class PropagatorEndMixin {
    @ModifyReturnValue(method = "getPotentialNeighbourLocations(Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;)Ljava/util/List;",
            at = @At("RETURN"))
    private static List<BlockPos> nookworks$trainEnds(List<BlockPos> places, KineticBlockEntity be) {
        return CreateEnds.neighbours(be, places);
    }

    @ModifyReturnValue(method = "getRotationSpeedModifier(Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;"
            + "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;)F", at = @At("RETURN"))
    private static float nookworks$throughTrain(float own, KineticBlockEntity from, KineticBlockEntity to) {
        return CreateEnds.modifier(from, to, own);
    }

    /**
     * Before Create joins a block to its neighbours (a block set down, a clutch let go, a source started): where that
     * brings a network only our Motors turn together with another that turns, ours stand down first and follow after
     * (CreateEnds#beforeJoin), so Create's rule for two networks turning against each other never breaks the block.
     */
    @Inject(method = "handleAdded(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;)V", at = @At("HEAD"))
    private static void nookworks$oursStandDown(Level level, BlockPos pos, KineticBlockEntity added, CallbackInfo ci) {
        CreateEnds.beforeJoin(level, pos, added);
    }
}
