package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.feature.RawFoods;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A click on a block with something we made edible that is also planted or placed - a mushroom, kelp, sugar cane -
 * only ever places it. Vanilla falls back to eating a placeable food when the placing fails (too bright for the
 * mushroom, the wrong ground), which would have a player swallow a red mushroom they meant to plant. Used in the air,
 * it is eaten as before. See {@link dev.nookworks.feature.RawFoods}.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Shadow
    public abstract InteractionResult place(BlockPlaceContext context);

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void nookworks$placeOnly(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getItemInHand().is(RawFoods.EATEN_ONLY_IN_THE_AIR)) {
            cir.setReturnValue(this.place(new BlockPlaceContext(context)));
        }
    }
}
