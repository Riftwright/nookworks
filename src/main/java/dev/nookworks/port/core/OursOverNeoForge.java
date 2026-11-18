package dev.nookworks.port.core;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Our copies of vanilla's data files (the bakery's cake and pumpkin pie, made of flour) over NeoForge's own. NeoForge
 * 21.1 ships copies of some vanilla recipes (cake and pumpkin pie, to take {@code #c:eggs}); its mod pack is ordered
 * above ours, so its copy hid ours and the conditions ours carry were never read - cake from wheat stayed with the
 * bakery on. Where a file of ours lies right under NeoForge's at the top of the stack, ours is read, as 26.3 reads it.
 * Asked by {@code mixin/RecipeManagerOursOverNeoForgeMixin} before the recipes load.
 */
public final class OursOverNeoForge {
    private static final String OURS = "mod/" + Nookworks.MODID;
    private static final String NEOFORGE = "mod/neoforge";

    private OursOverNeoForge() {}

    /** {@code files}: what a {@code SimpleJsonResourceReloadListener} read from {@code directory}, by id. */
    public static void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, String directory) {
        for (var entry : files.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
                continue;
            }
            List<Resource> stack = manager.getResourceStack(id.withPath(directory + "/" + id.getPath() + ".json"));
            if (stack.size() < 2 || !stack.getLast().sourcePackId().equals(NEOFORGE)) {
                continue;
            }
            for (int i = stack.size() - 2; i >= 0; i--) {
                Resource below = stack.get(i);
                if (!below.sourcePackId().equals(NEOFORGE) && !below.sourcePackId().equals(OURS)) {
                    break; // another pack lies between: left as it stands
                }
                if (below.sourcePackId().equals(OURS)) {
                    try (Reader reader = below.openAsReader()) {
                        entry.setValue(JsonParser.parseReader(reader));
                    } catch (IOException | RuntimeException e) {
                        Nookworks.LOGGER.warn("Could not read our {} over NeoForge's", id, e);
                    }
                    break;
                }
            }
        }
    }
}
