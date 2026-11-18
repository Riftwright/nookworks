package dev.nookworks.travel;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * What other mods' own portals allow, which the Dock-a-Daemon Door keeps to: it does no more than their portal does
 * (Nookworks is a guest in the pack, the owner's rule of 2026-11-17).
 * <ul>
 * <li>The Twilight Forest's portal joins it to one dimension, its "origin" (the Overworld unless its settings say
 * otherwise); portals made anywhere else it calls "may be considered an exploit" and allows only with its own setting
 * (allowPortalsInOtherDimensions, read from the 4.8 jar). TofuCraft's Tofu Stick lights a portal only in the Overworld or
 * the Tofu World, and its portal goes between those two (1.21.1 jar, 12.20). So a door opens onto either only from a
 * home door in that dimension; the way back is never refused (no one is stranded there).</li>
 * <li>A mod's own dimension has that mod's structures - the Twilight Forest's are the ones its progression locks
 * (a boss's tower that may not be built in until the boss before is beaten) - and a return door is never set down in
 * one of them, locked or not. The Twilight Forest refuses a player's building there by its click and break events, not
 * the place event our trips ask claim mods by, and asking it by a break would spawn its hint monsters.</li>
 * </ul>
 * Everything about the other mods is read by name: nothing here needs their jars.
 */
public final class OtherPortals {
    public static final ResourceKey<Level> TWILIGHT_FOREST = key("twilightforest", "twilight_forest");
    public static final ResourceKey<Level> TOFU_WORLD = key("tofucraft", "tofu_world");

    private OtherPortals() {}

    private static ResourceKey<Level> key(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /**
     * The only dimension a door may open onto {@code to} from, as that mod's own portal only joins {@code to} to it; null
     * for a dimension with no such rule (or the Twilight Forest with its own setting allowing portals anywhere).
     */
    public static @Nullable ResourceKey<Level> onlyFrom(ResourceKey<Level> to) {
        if (to.equals(TOFU_WORLD)) {
            return Level.OVERWORLD;
        }
        if (to.equals(TWILIGHT_FOREST)) {
            try {
                Class<?> config = Class.forName("twilightforest.config.TFConfig");
                if (config.getField("allowPortalsInOtherDimensions").getBoolean(null)) {
                    return null;
                }
                Object origin = config.getField("originDimension").get(null);
                ResourceLocation id = origin instanceof String name ? ResourceLocation.tryParse(name) : null;
                return id == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, id);
            } catch (ReflectiveOperationException | LinkageError e) {
                return Level.OVERWORLD; // its settings not to be read: its default
            }
        }
        return null;
    }

    /**
     * Whether {@code pos} in {@code level} is inside a piece of a structure of the mod whose dimension it is (not
     * vanilla's dimensions, nor ours): no return door is set down there.
     */
    public static boolean inTheirStructure(ServerLevel level, net.minecraft.core.BlockPos pos) {
        String namespace = level.dimension().location().getNamespace();
        if (namespace.equals(ResourceLocation.DEFAULT_NAMESPACE) || namespace.equals(dev.nookworks.Nookworks.MODID)) {
            return false;
        }
        return level.structureManager().getStructureWithPieceAt(pos, (net.minecraft.core.Holder<Structure> holder) -> holder.unwrapKey()
                .map(id -> id.location().getNamespace().equals(namespace)).orElse(false)).isValid();
    }
}
