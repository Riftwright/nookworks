package dev.nookworks.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.items.VanillaInventoryCodeHooks;

/**
 * A vanilla hopper, dropper or crafter reaches a block of ours that is a {@code Container} (or a
 * {@code WorldlyContainerHolder}) through that Container and its faces, as 26.3's hopper does: it asks the Container
 * before the capability, and the mod relies on it - a hopper keeps a machine's faces (fuel in at the side, out at the
 * bottom), while other mods' pipes see any face. NeoForge 21.1's hooks ask the {@code IItemHandler} capability first;
 * here our own capability's answer (the port's bridge out, {@code port.transfer.LegacyOut}) is left unasked for such a
 * block, and then - since 26.3 and vanilla take a block's Container before any entity there - no entity's either, so the
 * hooks find nothing and vanilla's own Container code runs.
 */
@Mixin(VanillaInventoryCodeHooks.class)
abstract class VanillaInventoryCodeHooksMixin {
    @WrapOperation(method = "getItemHandlerAt", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getCapability(Lnet/neoforged/neoforge/capabilities/BlockCapability;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static @Nullable Object nookworks$containerFirst(Level level, BlockCapability<?, ?> capability, BlockPos pos,
            @Nullable BlockState state, @Nullable BlockEntity blockEntity, @Nullable Object side, Operation<Object> original) {
        Object handler = original.call(level, capability, pos, state, blockEntity, side);
        if (nookworks$isContainer(state, blockEntity) && nookworks$ours(handler)) {
            return null; // ours: the Container's faces instead (the class is the bridge's, not public to this package)
        }
        return handler;
    }

    @WrapOperation(method = "getItemHandlerAt", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;"
                    + "Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> nookworks$blockBeforeEntities(Level level, @Nullable Entity except, AABB box,
            Predicate<? super Entity> filter, Operation<List<Entity>> original, @Local BlockPos pos, @Local @Nullable BlockState state,
            @Local @Nullable BlockEntity blockEntity, @Local(argsOnly = true) Direction side) {
        // reached with no capability at the block: where that is because ours was left out above, the block's Container
        // comes before any entity, as in 26.3 (asked again only for a Container: the case above, or a rare other mod's)
        if (nookworks$isContainer(state, blockEntity) && nookworks$ours(level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, side))) {
            return List.of();
        }
        return original.call(level, except, box, filter);
    }

    @Unique
    private static boolean nookworks$ours(@Nullable Object handler) {
        return handler != null && handler.getClass().getName().startsWith("dev.nookworks.port.transfer.LegacyOut$");
    }

    @Unique
    private static boolean nookworks$isContainer(@Nullable BlockState state, @Nullable BlockEntity blockEntity) {
        return blockEntity instanceof Container || state != null && state.getBlock() instanceof WorldlyContainerHolder;
    }
}
