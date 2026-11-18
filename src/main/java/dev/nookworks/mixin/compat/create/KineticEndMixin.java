package dev.nookworks.mixin.compat.create;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.nookworks.compat.create.CreateEnds;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * A Create block entity as an end of our trains meets it (CreateEnds; the design: tools/port1211/design_create.md,
 * "Ends and trains"): it carries what our trains make of it, is a source of its network where a Motor's end drives it
 * (its generated speed and capacity, where Create's own are none), keeps that with its own data, and shows Create's
 * Generator Stats for it. Every hook adds to what Create asks; none cancels. Added only with Create installed (the
 * config's plugin, CompatMixins).
 */
@Mixin(value = KineticBlockEntity.class, remap = false)
abstract class KineticEndMixin implements CreateEnds.Holder {
    @Shadow
    protected float stress;
    @Shadow
    protected float capacity;
    @Shadow
    protected float lastCapacityProvided;

    @Unique
    private CreateEnds.State nookworks$endsState;

    @Override
    public CreateEnds.State nookworks$ends() {
        return this.nookworks$endsState;
    }

    @Override
    public void nookworks$ends(CreateEnds.State state) {
        this.nookworks$endsState = state;
    }

    @Override
    public float nookworks$stress() {
        return this.stress;
    }

    @Override
    public float nookworks$capacity() {
        return this.capacity;
    }

    /** Create's own generators answer for themselves (they override this); every other block answers what a Motor's end gives it. */
    @ModifyReturnValue(method = "getGeneratedSpeed()F", at = @At("RETURN"))
    private float nookworks$givenSpeed(float own) {
        return CreateEnds.generated((KineticBlockEntity) (Object) this, own);
    }

    @ModifyReturnValue(method = "calculateAddedStressCapacity()F", at = @At("RETURN"))
    private float nookworks$givenCapacity(float own) {
        float with = CreateEnds.capacity((KineticBlockEntity) (Object) this, own);
        if (with != own) {
            this.lastCapacityProvided = with;
        }
        return with;
    }

    /**
     * Create's check of a block's source, every few seconds: a block whose turning comes in from a block of ours has no
     * kinetic block at its source's place, and is checked as a source of its network is (it stops when it gives no more).
     */
    @ModifyExpressionValue(method = "validateKinetics()V",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;hasSource()Z"))
    private boolean nookworks$sourceOfOurs(boolean has) {
        return has && !CreateEnds.turnedFromOurs((KineticBlockEntity) (Object) this);
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V", at = @At("TAIL"))
    private void nookworks$keep(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        CreateEnds.write((KineticBlockEntity) (Object) this, tag, clientPacket);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V", at = @At("TAIL"))
    private void nookworks$kept(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        CreateEnds.read((KineticBlockEntity) (Object) this, tag, clientPacket);
    }

    @ModifyReturnValue(method = "addToGoggleTooltip(Ljava/util/List;Z)Z", at = @At("RETURN"))
    private boolean nookworks$generatorStats(boolean added, List<Component> tooltip, boolean isPlayerSneaking) {
        return CreateEnds.goggles((KineticBlockEntity) (Object) this, tooltip, added);
    }
}
