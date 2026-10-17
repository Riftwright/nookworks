package dev.nookworks.mixin;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.nookworks.inventory.RecipeChoice;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What a craft leaves in the grid (a bucket, a bottle) is the made recipe's: vanilla looked the grid's first recipe up
 * again, which is another one once the player has picked (dev.nookworks.inventory.RecipeChoice). The recipe is taken
 * as the craft is counted ({@code checkTakeAchievements}, first in a take and in a shift-click's each round), for vanilla
 * then clears it from the result (awarding it to the player) before it works out what is left - so read from the result
 * then, there was none, and the first recipe came back (review r12: a picked recipe gave another's leftovers).
 */
@Mixin(ResultSlot.class)
abstract class ResultSlotMixin {
    @Unique
    private @Nullable RecipeHolder<?> nookworks$made;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void nookworks$noteTheRecipe(ItemStack carried, CallbackInfo info) {
        if (((Slot) (Object) this).container instanceof ResultContainer result && result.getRecipeUsed() != null) {
            this.nookworks$made = result.getRecipeUsed();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(method = "getRemainingItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;"
            + "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;"
            + "Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private Optional nookworks$theRecipeMade(RecipeManager recipes, RecipeType type, RecipeInput input, Level level, Operation<Optional> original) {
        RecipeHolder<?> made = this.nookworks$made;
        if (made == null && ((Slot) (Object) this).container instanceof ResultContainer result) {
            made = result.getRecipeUsed();
        }
        this.nookworks$made = null;
        if (RecipeChoice.active() && made != null) {
            return recipes.getRecipeFor(type, input, level, (RecipeHolder) made);
        }
        return original.call(recipes, type, input, level);
    }
}
