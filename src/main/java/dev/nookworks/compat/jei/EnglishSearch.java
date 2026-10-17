package dev.nookworks.compat.jei;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.nookworks.Nookworks;
import dev.nookworks.client.NookworksClientConfig;
import dev.nookworks.compat.EnglishNames;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;

/**
 * Every item's English name given to JEI as a search alias while the game is in another language (the user, 2026-10-11:
 * with the game in Japanese, "sword" should find every sword in JEI's search, with no switching of languages). The names
 * come from every mod's en_us.json, read from the resources as loaded; JEI starts again with a change of language, and
 * so does this. Items whose name is made from their contents - potions, tipped arrows - are named as the game names them;
 * an enchanted book is also found by its enchantments. A client setting turns it off (JEI then shows no "Search
 * Aliases" lines either).
 */
final class EnglishSearch {
    private EnglishSearch() {}

    static void register(IIngredientAliasRegistration registration) {
        if (NookworksClientConfig.SPEC.isLoaded() && !NookworksClientConfig.ENGLISH_SEARCH.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getLanguageManager().getSelected().startsWith("en_")) {
            return; // the names are English already
        }
        Map<String, String> english = englishTexts(mc.getResourceManager());
        List<ItemStack> stacks = new ArrayList<>();
        if (mc.level != null) {
            if (CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess())) {
                refreshCreativeSearch(mc);
            }
            BuiltInRegistries.CREATIVE_MODE_TAB.forEach(tab -> stacks.addAll(tab.getSearchTabDisplayItems())); // potions and books one by one
        }
        BuiltInRegistries.ITEM.forEach(item -> stacks.add(item.getDefaultInstance())); // those in no tab too
        Set<String> given = new HashSet<>();
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            String shown = stack.getHoverName().getString();
            Set<String> names = new LinkedHashSet<>();
            names.add(EnglishNames.of(stack.getHoverName(), english));
            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null) {
                stored.entrySet().forEach(e -> names.add(EnglishNames.of(Enchantment.getFullname(e.getKey(), e.getIntValue()), english)));
            }
            int same = ItemStack.hashItemAndComponents(stack);
            for (String name : names) {
                if (!name.isBlank() && !name.equalsIgnoreCase(shown) && given.add(same + "|" + name)) {
                    registration.addAlias(stack, name);
                    count++;
                }
            }
        }
        Nookworks.LOGGER.debug("JEI: {} English names given to search by", count);
    }

    /**
     * The creative screen's name and tag search, filled again from the tabs just rebuilt - as the screen fills them, and
     * only when it rebuilds them itself. Rebuilt here first with the same settings (an operator showing the operator
     * tab), the screen found nothing to rebuild and its search stayed empty (r15: "dirt" found nothing).
     */
    private static void refreshCreativeSearch(Minecraft mc) {
        if (mc.getConnection() == null || mc.level == null) {
            return;
        }
        SessionSearchTrees trees = mc.getConnection().searchTrees();
        CreativeModeTabs.allTabs().stream().filter(CreativeModeTab::hasSearchBar).forEach(tab -> {
            List<ItemStack> list = List.copyOf(tab.getDisplayItems());
            trees.updateCreativeTooltips(mc.level.registryAccess(), list, CreativeModeTabSearchRegistry.getNameSearchKey(tab));
            trees.updateCreativeTags(list, CreativeModeTabSearchRegistry.getTagSearchKey(tab));
        });
    }

    /** The English texts of the game and every mod, as the resource packs have them, the higher pack winning. */
    static Map<String, String> englishTexts(ResourceManager resources) {
        Map<String, String> english = new HashMap<>();
        for (List<Resource> stack : resources.listResourceStacks("lang", id -> id.getPath().equals("lang/en_us.json")).values()) {
            for (Resource resource : stack) {
                try (Reader in = resource.openAsReader()) {
                    for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(in).getAsJsonObject().entrySet()) {
                        if (e.getValue().isJsonPrimitive()) {
                            english.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                } catch (Exception e) {
                    Nookworks.LOGGER.debug("JEI English names: skipped a language file ({})", e.toString());
                }
            }
        }
        return english;
    }
}
