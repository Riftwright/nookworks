package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import dev.nookworks.port.core.StructureRotation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * NeoForge 26.3's {@code CommonHooks.applyStructureRotation}: a structure placed turned or mirrored tells each block
 * entity it set down how, right after loading it - our fluid pipes' pull sides, the dice's pose, the Drive Wheels' belt
 * offsets turn with it. 1.21.1 has no such hook: the block entities of ours that need it implement
 * {@link StructureRotation}, told here, in the same place and order. A rotation NeoForge 26.3 stored in the block
 * entity's data ({@code neoforge:structure_rotation}, from a template made of turned parts) is added on, as there.
 */
@Mixin(StructureTemplate.class)
abstract class StructureTemplateRotationMixin {
    @WrapOperation(method = "placeInWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private void nookworks$applyStructureRotation(BlockEntity blockEntity, CompoundTag nbt, HolderLookup.Provider registries,
            Operation<Void> original, @Local(argsOnly = true) StructurePlaceSettings settings) {
        original.call(blockEntity, nbt, registries);
        if (blockEntity instanceof StructureRotation turned) {
            Rotation rotation = settings.getRotation();
            if (nbt.contains("neoforge:structure_rotation", CompoundTag.TAG_STRING)) {
                String before = nbt.getString("neoforge:structure_rotation");
                for (Rotation turn : Rotation.values()) {
                    if (turn.getSerializedName().equals(before)) {
                        rotation = rotation.getRotated(turn);
                    }
                }
            }
            turned.applyStructureRotation(settings.getMirror(), rotation);
        }
    }
}
