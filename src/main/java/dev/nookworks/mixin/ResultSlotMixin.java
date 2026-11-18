package dev.nookworks.mixin;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.nookworks.inventory.RecipeChoice;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    /**
     * A take's makings used up a square at a time: the grids passed through on the way are not each looked through
     * by the recipe choice (RecipeChoice.taking) - a shift-click of a stack looked through every crafting recipe at
     * every square of every round.
     */
    @Inject(method = "onTake", at = @At("HEAD"))
    private void nookworks$takeBegins(Player player, ItemStack stack, CallbackInfo info) {
        if (player instanceof ServerPlayer server) {
            RecipeChoice.taking(server, true, () -> {});
        }
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void nookworks$takeEnds(Player player, ItemStack stack, CallbackInfo info) {
        if (player instanceof ServerPlayer server) {
            RecipeChoice.taking(server, false, () -> player.containerMenu.slotsChanged(this.craftSlots));
        }
    }

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void nookworks$noteTheRecipe(ItemStack carried, CallbackInfo info) {
        if (((Slot) (Object) this).container instanceof ResultContainer result && result.getRecipeUsed() != null) {
            this.nookworks$made = result.getRecipeUsed();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    // 1.21.1: onTake asks the recipe manager for the leftovers at once (26.3: its own getRemainingItems looks the recipe
    // up), so the leftovers of the recipe made are given here, as getRemainingItemsFor gives them
    @WrapOperation(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;"
            + "getRemainingItemsFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;"
            + "Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;"))
    private NonNullList nookworks$theRecipeMade(RecipeManager recipes, RecipeType type, RecipeInput input, Level level, Operation<NonNullList> original) {
        RecipeHolder<?> made = this.nookworks$made;
        if (made == null && ((Slot) (Object) this).container instanceof ResultContainer result) {
            made = result.getRecipeUsed();
        }
        this.nookworks$made = null;
        if (RecipeChoice.active() && made != null) {
            Optional<RecipeHolder> found = recipes.getRecipeFor(type, input, level, (RecipeHolder) made);
            if (found.isPresent()) {
                return ((Recipe) found.get().value()).getRemainingItems(input);
            }
        }
        return original.call(recipes, type, input, level);
    }
}
