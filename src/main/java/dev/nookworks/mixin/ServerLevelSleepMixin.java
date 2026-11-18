package dev.nookworks.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The If's nights are the overworld's - one clock - so its sleepers vote with the overworld's players. Vanilla asks each
 * level's own players only (in vanilla only the overworld has beds to sleep through a night in): one player asleep in the
 * If skipped the night for the whole server, and stopped the rain, the overworld's players never asked. Now the If counts
 * nobody itself, passing every change in its beds to the overworld, which counts, tells and wakes its own players and the
 * If's together ({@link IfLevel#sleepingWith}).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelSleepMixin {
    @Inject(method = "updateSleepingPlayerList", at = @At("HEAD"), cancellable = true)
    private void nookworks$ifSleepsWithTheOverworld(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension().equals(IfWorld.KEY)) {
            level.getServer().overworld().updateSleepingPlayerList();
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = {"updateSleepingPlayerList", "announceSleepStatus", "wakeUpAllPlayers"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;players:Ljava/util/List;"))
    private List<ServerPlayer> nookworks$countedWithTheIfs(List<ServerPlayer> players) {
        return IfLevel.sleepingWith((ServerLevel) (Object) this, players);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"), index = 1)
    private List<ServerPlayer> nookworks$deepAsleepWithTheIfs(List<ServerPlayer> players) {
        return IfLevel.sleepingWith((ServerLevel) (Object) this, players);
    }
}
