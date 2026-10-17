package dev.nookworks.mixin;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.nookworks.ifworld.IfGates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.LookAt;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;

/**
 * An operator's /tp crosses into and out of the If whoever it moves (DECISIONS: crossing by anything but the mirror, the
 * door and the pearl is refused "unless by an operator"). The If's gate judged the one moved: an operator sending
 * someone else in or out was refused, though /tp said it had teleported them (r15).
 */
@Mixin(TeleportCommand.class)
abstract class TeleportCommandMixin {
    @WrapMethod(method = "performTeleport")
    private static void nookworks$byAnOperator(CommandSourceStack source, Entity victim, ServerLevel level, double x, double y, double z,
            Set<Relative> relatives, float yRot, float xRot, @Nullable LookAt lookAt, Operation<Void> original) {
        if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            IfGates.passing(() -> original.call(source, victim, level, x, y, z, relatives, yRot, xRot, lookAt));
        } else {
            original.call(source, victim, level, x, y, z, relatives, yRot, xRot, lookAt);
        }
    }
}
