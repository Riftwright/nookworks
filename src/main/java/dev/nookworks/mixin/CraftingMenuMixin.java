package dev.nookworks.mixin;

import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.nookworks.inventory.RecipeChoice;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The crafting grid's recipe, picked among all it matches (dev.nookworks.inventory.RecipeChoice) rather than the first
 * found. CraftingMenu.slotChangedCraftingGrid serves the crafting table and the inventory's grid alike, and any other
 * mod's table that uses it.
 */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;"
            + "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;"))
    private static Optional nookworks$pickedRecipe(RecipeManager recipes, RecipeType type, RecipeInput input, Level level, RecipeHolder hint,
            Operation<Optional> original, @Local(argsOnly = true) AbstractContainerMenu menu, @Local(argsOnly = true) Player player) {
        if (type == RecipeType.CRAFTING && RecipeChoice.active() && level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
                && input instanceof CraftingInput grid) {
            return RecipeChoice.choose(server, serverPlayer, menu, grid, hint);
        }
        if (type == RecipeType.CRAFTING && player instanceof ServerPlayer serverPlayer) {
            RecipeChoice.withdraw(serverPlayer, menu.containerId); // the setting turned off: the button goes
        }
        return original.call(recipes, type, input, level, hint);
    }
}
