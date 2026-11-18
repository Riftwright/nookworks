package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.PotRecipe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;

/**
 * Keeps the pot recipes the server sends ({@link PotRecipe}): first of all who hear of them, so JEI, which builds its
 * pages on the same event, finds them there.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class PotRecipesClient {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onRecipes(RecipesUpdatedEvent event) {
        PotRecipe.received(event.getRecipeManager().getAllRecipesFor(PotRecipe.TYPE.get()));
    }

    private PotRecipesClient() {}
}
