package dev.nookworks.compat.create.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeManager;

/** The client's recipes, for CreateRecipes' lookups asked on the client (JEI, a screen's slot rule). */
public final class ClientRecipes {
    private ClientRecipes() {}

    public static @Nullable RecipeManager manager() {
        var connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getRecipeManager();
    }
}
