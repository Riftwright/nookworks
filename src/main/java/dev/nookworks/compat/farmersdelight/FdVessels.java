package dev.nookworks.compat.farmersdelight;

import java.lang.reflect.Method;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/**
 * Farmer's Delight's Cooking Pot and Skillet as an Electric Stove sees them: whether one standing on it has something to
 * cook, which is when the stove heats (and draws power), as it does for our own Iron Pot. Read through the vessels'
 * public methods by name, so nothing of Farmer's Delight's is linked; if a later version renames them, the stove heats
 * such a vessel whenever it holds anything (and says so once in the log), rather than never.
 */
public final class FdVessels {
    private static final String POT = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity";
    private static final String SKILLET = "vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity";
    /** The pot's ingredient slots come first; the dish waits in the next one (its MEAL_DISPLAY_SLOT). */
    private static final int INPUTS = 6;
    private static final int MEAL = 6;

    private static @Nullable Method potInventory;
    private static @Nullable Method skilletStack;
    private static boolean looked;
    private static boolean warned;

    private FdVessels() {}

    /** Whether a Farmer's Delight vessel stands at {@code pos}. */
    public static boolean isVessel(@Nullable BlockEntity entity) {
        if (entity == null) {
            return false;
        }
        String name = entity.getClass().getName();
        return name.equals(POT) || name.equals(SKILLET);
    }

    /** Whether the Farmer's Delight vessel at {@code pos} has something to cook; false when there is none there. */
    public static boolean wantsHeat(Level level, BlockPos pos) {
        if (!FarmersDelight.LOADED) {
            return false;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (!isVessel(entity)) {
            return false;
        }
        look(entity);
        try {
            if (entity.getClass().getName().equals(POT)) {
                return potWants(level, entity);
            }
            return skilletWants(level, entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                Nookworks.LOGGER.warn("Could not read a Farmer's Delight vessel; an Electric Stove heats it whenever it is there", e);
            }
            return true;
        }
    }

    private static synchronized void look(BlockEntity entity) {
        if (looked) {
            return;
        }
        looked = true;
        try {
            ClassLoader loader = entity.getClass().getClassLoader();
            potInventory = Class.forName(POT, false, loader).getMethod("getInventory");
            skilletStack = Class.forName(SKILLET, false, loader).getMethod("getStoredStack");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Nookworks.LOGGER.warn("Farmer's Delight's vessels are not as this version of Nookworks knows them", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean potWants(Level level, BlockEntity pot) throws ReflectiveOperationException {
        if (potInventory == null) {
            throw new NoSuchMethodException("getInventory");
        }
        IItemHandler inventory = (IItemHandler) potInventory.invoke(pot);
        boolean any = false;
        for (int slot = 0; slot < INPUTS; slot++) {
            any |= !inventory.getStackInSlot(slot).isEmpty();
        }
        RecipeType type = FarmersDelight.recipeType(FarmersDelight.COOKING);
        if (!any || type == null) {
            return false;
        }
        Optional<RecipeHolder<?>> found = level.getRecipeManager().getRecipeFor(type, (RecipeInput) new RecipeWrapper(inventory), level);
        if (found.isEmpty()) {
            return false;
        }
        // as the pot itself asks: the dish must have room where it waits
        ItemStack dish = ((Recipe) found.get().value()).getResultItem(level.registryAccess());
        ItemStack waiting = inventory.getStackInSlot(MEAL);
        return waiting.isEmpty() || ItemStack.isSameItemSameComponents(waiting, dish)
                && waiting.getCount() + dish.getCount() <= waiting.getMaxStackSize();
    }

    private static boolean skilletWants(Level level, BlockEntity skillet) throws ReflectiveOperationException {
        if (skilletStack == null) {
            throw new NoSuchMethodException("getStoredStack");
        }
        ItemStack stored = (ItemStack) skilletStack.invoke(skillet);
        return !stored.isEmpty() && level.getRecipeManager()
                .getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stored), level).isPresent();
    }
}
