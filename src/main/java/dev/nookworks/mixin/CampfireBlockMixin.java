package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.feature.UnlitCampfires;
import dev.nookworks.ventilation.ChimneyBlock;
import dev.nookworks.ventilation.FlueBlock;
import dev.nookworks.ventilation.Ventilation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
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

    /**
     * Trial (the cooking review, 2026-11-14): a campfire under a Grill astride it, or under a pot or pan on that grate,
     * sends up no smoke of its own - the column came up through the pot and hid what was in it. The pot's steam and
     * the grill's own smoke still show.
     * <p>
     * And under a Range Hood or before a running Ventilation Fan (2026-11-22), its smoke is drawn off into that rather
     * than rising in its tall column past it, as a pot's is ({@link Ventilation#smoke}): what the user never saw a hood
     * do (「あのフードから煙吸えたことない」). Under a Flue it rises as ever, straight up into the flue; under a Chimney
     * stood straight on it, it goes up the chimney unseen and out of the pot, rather than spilling round its foot.
     */
    @Inject(method = "makeParticles", at = @At("HEAD"), cancellable = true)
    private static void nookworks$noSmokeUnderCookware(Level level, BlockPos pos, boolean isSignalFire, boolean smoking, CallbackInfo ci) {
        BlockState above = level.getBlockState(pos.above());
        if (GrillBlock.overFire(above) || above.getBlock() instanceof IronPotBlock && above.getValue(IronPotBlock.ON_GRILL)
                || above.getBlock() instanceof ChimneyBlock && !(above.getBlock() instanceof FlueBlock)) {
            ci.cancel();
            return;
        }
        if (level.isClientSide() && Ventilation.intakeFor(level, pos) != null) {
            RandomSource random = level.getRandom();
            Ventilation.smoke(level, pos, ParticleTypes.SMOKE, pos.getX() + 0.5 + random.nextGaussian() * 0.15, pos.getY() + 0.5,
                    pos.getZ() + 0.5 + random.nextGaussian() * 0.15, 0.0, 0.05, 0.0);
            if (random.nextBoolean()) {
                Ventilation.smoke(level, pos, ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5 + random.nextGaussian() * 0.1, pos.getY() + 0.7,
                        pos.getZ() + 0.5 + random.nextGaussian() * 0.1, 0.0, 0.04, 0.0);
            }
            ci.cancel();
        }
    }
}
