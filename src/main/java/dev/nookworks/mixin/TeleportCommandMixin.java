package dev.nookworks.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import dev.nookworks.ifworld.IfGates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import dev.nookworks.port.core.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;

/**
 * An operator's /tp crosses into and out of the If whoever it moves (DECISIONS: crossing by anything but the mirror, the
 * door and the pearl is refused "unless by an operator"). The If's gate judged the one moved: an operator sending
 * someone else in or out was refused, though /tp said it had teleported them (r15).
 * <p>
 * (1.21.1: the move itself, {@code teleportTo} inside {@code performTeleport}, is wrapped - 26.3's mixin wrapped the
 * whole method, whose 1.21.1 form names a type closed to other packages, {@code TeleportCommand.LookAt}. Nothing else in
 * the method crosses a dimension.)
 */
@Mixin(TeleportCommand.class)
abstract class TeleportCommandMixin {
    @WrapOperation(method = "performTeleport", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z"))
    private static boolean nookworks$byAnOperator(Entity victim, ServerLevel level, double x, double y, double z,
            Set<RelativeMovement> relatives, float yRot, float xRot, Operation<Boolean> original,
            @Local(argsOnly = true) CommandSourceStack source) {
        if (Permissions.has(source, Permissions.COMMANDS_GAMEMASTER)) {
            return IfGates.passing(() -> original.call(victim, level, x, y, z, relatives, yRot, xRot));
        }
        return original.call(victim, level, x, y, z, relatives, yRot, xRot);
    }
}
