package dev.nookworks.mixin;

import java.util.OptionalLong;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.WorldOptions;

/**
 * The If's own seed ({@link IfLevel#seedOf}). NeoForge 26.3 gives a level made from a {@code LevelStem} with a seed
 * override that seed in {@code getSeed} and in its structure check; 1.21.1 has no such override, and every level reads
 * the world's seed. Asked for while the level is still being made (its chunk map reads it), so the dimension and the
 * server - both set by then - say which level this is.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelSeedMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Unique
    private OptionalLong nookworks$ifSeed() {
        ServerLevel self = (ServerLevel) (Object) this;
        return IfWorld.KEY.equals(self.dimension()) ? IfLevel.seedOf(this.server) : OptionalLong.empty();
    }

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void nookworks$getSeed(CallbackInfoReturnable<Long> cir) {
        OptionalLong seed = this.nookworks$ifSeed();
        if (seed.isPresent()) {
            cir.setReturnValue(seed.getAsLong());
        }
    }

    /** The seed the level's structure check is made with (26.3: the override too). */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long nookworks$structureCheckSeed(WorldOptions options, Operation<Long> original) {
        OptionalLong seed = this.nookworks$ifSeed();
        return seed.isPresent() ? seed.getAsLong() : original.call(options);
    }
}
