package dev.nookworks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.nookworks.client.render.MirrorScenes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Standing Mirrors keep meshes of their own of the blocks before them ({@link MirrorScenes}); a block or a section's
 * light changing on the client says so to them as well as to the world's renderer. These are the client level's own
 * calls, which Sodium leaves to the game. Missing (another mod having changed them), a mirror shows a changed block
 * once its section is compiled again for another reason ({@code require = 0}).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMirrorMixin {
    @Inject(method = "sendBlockUpdated", require = 0, at = @At("HEAD"))
    private void nookworks$blockChanged(BlockPos pos, BlockState old, BlockState current, int flags, CallbackInfo ci) {
        MirrorScenes.blockChanged(pos);
    }

    @Inject(method = "setBlocksDirty", require = 0, at = @At("HEAD"))
    private void nookworks$blocksDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        MirrorScenes.blockChanged(pos);
    }

    @Inject(method = "setSectionDirtyWithNeighbors", require = 0, at = @At("HEAD"))
    private void nookworks$sectionDirty(int x, int y, int z, CallbackInfo ci) {
        MirrorScenes.sectionsDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    @Inject(method = "setSectionRangeDirty", require = 0, at = @At("HEAD"))
    private void nookworks$sectionsDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        MirrorScenes.sectionsDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
