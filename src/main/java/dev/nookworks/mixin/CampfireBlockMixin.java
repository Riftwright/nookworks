package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.feature.UnlitCampfires;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A campfire a player sets down is unlit from the start, on both sides alike: put out only once placed, it showed
 * burning for a few frames on the player's screen before the server's word came (the user, 2026-10-20: 一瞬着いてから
 * 消えたように見えた). See {@link UnlitCampfires}; an item naming its lit state outright still has it (BlockItem applies
 * that after this).
 */
@Mixin(CampfireBlock.class)
abstract class CampfireBlockMixin {
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void nookworks$laidUnlit(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state != null && context.getPlayer() != null && UnlitCampfires.active() && state.getValue(CampfireBlock.LIT)) {
            cir.setReturnValue(state.setValue(CampfireBlock.LIT, false));
        }
    }
}
