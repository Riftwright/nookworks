package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.nookworks.feature.ChainBreaking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;

/**
 * Notes which block a player is breaking, so tree felling and vein mining start only from the player's own break and
 * not from a break event another mod posts for a block its area tool takes meanwhile (see {@link ChainBreaking#byHand}).
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @WrapMethod(method = "destroyBlock")
    private boolean nookworks$noteBreak(BlockPos pos, Operation<Boolean> original) {
        return ChainBreaking.destroying(this.player, pos, () -> original.call(pos));
    }
}
