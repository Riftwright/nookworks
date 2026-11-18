package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.nookworks.ifworld.IfWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * The If's weather is the overworld's: there is one weather for the whole server, and every level with a sky counts its
 * timers down each tick. The If, of the overworld's dimension type, would count them down a second time, and rain and
 * clear spells would last half as long everywhere. It leaves the counting to the overworld (and still eases its own rain
 * in and out with the shared weather).
 * <p>
 * (In 1.21.1 the If's level data, a {@code DerivedLevelData}, drops what it is told to set, so a second count would
 * change nothing there; the count is still left out, as in 26.3.)
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelWeatherMixin {
    @WrapOperation(method = "advanceWeatherCycle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean nookworks$overworldCounts(GameRules rules, GameRules.Key<GameRules.BooleanValue> rule, Operation<Boolean> original) {
        if (rule == GameRules.RULE_WEATHER_CYCLE && ((ServerLevel) (Object) this).dimension().equals(IfWorld.KEY)) {
            return false;
        }
        return original.call(rules, rule);
    }
}
