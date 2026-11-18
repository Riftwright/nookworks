package dev.nookworks.port.core;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * 26.3 NeoForge's {@code IBlockEntityExtension.applyStructureRotation(Mirror, Rotation)}: a structure template, after
 * loading a block entity it set down turned or mirrored, tells it how (so it can turn the sides or offsets it keeps).
 * 1.21.1 has no such hook: a block entity of ours that needs it implements this, and a mixin on
 * {@code StructureTemplate.placeInWorld} (after {@code blockEntity.loadWithComponents(nbt, registries)}) calls it with
 * the placement settings' mirror and rotation. Until that mixin is in, only direct calls (tests) reach it.
 */
public interface StructureRotation {
    void applyStructureRotation(Mirror mirror, Rotation rotation);
}
