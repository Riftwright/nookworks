package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.nookworks.ifworld.IfWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The If's weather is the overworld's: there is one weather for the whole server, and every level with a sky counts its
 * timers down each tick. The If, of the overworld's dimension type, would count them down a second time, and rain and
 * clear spells would last half as long everywhere. It leaves the counting to the overworld (and still eases its own rain
 * in and out with the shared weather).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelWeatherMixin {
    @WrapOperation(method = "advanceWeatherCycle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object nookworks$overworldCounts(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        if (rule == GameRules.ADVANCE_WEATHER && ((ServerLevel) (Object) this).dimension().equals(IfWorld.KEY)) {
            return Boolean.FALSE;
        }
        return original.call(rules, rule);
    }
}
