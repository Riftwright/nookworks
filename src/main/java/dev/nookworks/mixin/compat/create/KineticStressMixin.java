package dev.nookworks.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.nookworks.compat.create.CreateDrives;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * A Create block entity's stress impact with the Nookworks machines it turns added (CreateDrives#loadOn): what its
 * network counts, what is saved for it, and what Create's goggles show on it. Added only with Create installed (the
 * config's plugin, CompatMixins).
 */
@Mixin(value = KineticBlockEntity.class, remap = false)
abstract class KineticStressMixin {
    @Shadow
    protected float lastStressApplied;

    @ModifyReturnValue(method = "calculateStressApplied()F", at = @At("RETURN"))
    private float nookworks$machinesTurned(float impact) {
        float load = CreateDrives.loadOn((KineticBlockEntity) (Object) this);
        if (load == 0.0F) {
            return impact;
        }
        this.lastStressApplied = impact + load;
        return impact + load;
    }

    /** On a client, the server's figure as it came (its AddedStress): what the goggles read for our load (CreateDrives#loadOn). */
    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V", at = @At("TAIL"))
    private void nookworks$serverFigure(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (clientPacket) {
            CreateDrives.clientApplied((KineticBlockEntity) (Object) this, this.lastStressApplied);
        }
    }
}
