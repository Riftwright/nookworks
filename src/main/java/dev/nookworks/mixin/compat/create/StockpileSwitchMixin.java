package dev.nookworks.mixin.compat.create;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.compat.thresholdSwitch.ThresholdSwitchCompat;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;

import dev.nookworks.compat.create.StorageBarrelSpace;

/**
 * Create's Threshold Switch reads a Storage Barrel's slots at their own size (two stacks and more), as it reads other
 * mods' drawers through its own list of them; asked only by stack, it took a barrel half full for full. Our entry joins
 * that list. Added only with Create installed (CompatMixins).
 */
@Mixin(value = ThresholdSwitchBlockEntity.class, remap = false)
abstract class StockpileSwitchMixin {
    @Shadow
    @Final
    @Mutable
    private static List<ThresholdSwitchCompat> COMPAT;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void nookworks$storageBarrels(CallbackInfo ci) {
        List<ThresholdSwitchCompat> all = new ArrayList<>(COMPAT);
        all.add(new StorageBarrelSpace());
        COMPAT = List.copyOf(all);
    }
}
