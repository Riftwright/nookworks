package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import dev.nookworks.port.core.EntityInsideShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * 26.3's entity runs a block's {@code entityInside} only when it is in the block's entity-inside shape; 1.21.1's runs
 * it for anything in the block's cell. For a block of ours with such a shape ({@link EntityInsideShape}: the Hook's
 * sweet berry sprig, the Daemon Door's doorway) the call is left out unless the entity's box meets the shape.
 */
@Mixin(Entity.class)
abstract class EntityInsideShapeMixin {
    @WrapWithCondition(method = "checkInsideBlocks", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;entityInside(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V"))
    private boolean nookworks$insideTheShape(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(state.getBlock() instanceof EntityInsideShape shaped)) {
            return true;
        }
        return Shapes.joinIsNotEmpty(shaped.getEntityInsideCollisionShape(state, level, pos, entity).move(pos.getX(), pos.getY(), pos.getZ()),
                Shapes.create(entity.getBoundingBox()), BooleanOp.AND);
    }
}
