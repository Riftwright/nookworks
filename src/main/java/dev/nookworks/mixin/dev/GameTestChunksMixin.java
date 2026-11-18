package dev.nookworks.mixin.dev;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.StructureBlockEntity;

/**
 * Test runs only ({@link DevMixins}): a test starts once its arena's chunks are ticking <em>and their entities are
 * loaded</em>, as 26.3's runner waits ({@code GameTestInfo.tick}: {@code areEntitiesActuallyLoadedAndTicking}).
 * PORT: 1.21.1's waits for the chunks to tick only, while the entities of a new chunk are read on another thread, in
 * real time; until they are in, 1.21.1 ticks no block entity in that chunk ({@code LevelChunk.isTicking}) and
 * {@code areEntitiesLoaded} says no. Under the load of a full run a test now and then started that way: a Nested
 * Timer missed its first pulses, a rope knot held back a signal for its lines' lights to come in.
 */
@Mixin(GameTestInfo.class)
abstract class GameTestChunksMixin {
    @Shadow
    @Final
    private ServerLevel level;
    @Shadow
    private boolean chunksLoaded;
    @Shadow
    private @Nullable StructureBlockEntity structureBlockEntity;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void nookworks$waitForEntities(GameTestRunner runner, CallbackInfo ci) {
        if (this.chunksLoaded || this.structureBlockEntity == null || ((GameTestInfo) (Object) this).isDone()) {
            return;
        }
        if (!StructureUtils.getStructureBoundingBox(this.structureBlockEntity).intersectingChunks()
                .allMatch(chunk -> this.level.areEntitiesLoaded(chunk.toLong()))) {
            ci.cancel();
        }
    }
}
