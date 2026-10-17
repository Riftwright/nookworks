package dev.nookworks.poster;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;

/** Registration and lookup for {@link PosterVariant}s. */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Posters {
    @SubscribeEvent
    static void register(NewDatapackRegistryEvent event) {
        // Synced, so the client can size and texture a poster it has only been told the id of.
        event.worldRegistry(PosterVariant.REGISTRY, PosterVariant.CODEC, PosterVariant.CODEC);
    }

    public static Optional<? extends HolderLookup.RegistryLookup<PosterVariant>> lookup(HolderLookup.Provider registries) {
        return registries.lookup(PosterVariant.REGISTRY);
    }

    /** Designs renamed since release: a poster saved with the old id still shows its picture. */
    private static final Map<Identifier, Identifier> RENAMED = Map.of(
            Nookworks.id("bread"), Nookworks.id("potato")); // it was always a potato

    /** The id a design goes by now. */
    public static Identifier current(Identifier id) {
        return RENAMED.getOrDefault(id, id);
    }

    /** The design {@code id} names - or, for a photo printed as a poster, one made up from the id - or null. */
    public static @Nullable PosterVariant get(Level level, @Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        PhotoPrints.Print print = PhotoPrints.parse(id);
        if (print != null) {
            return PhotoPrints.variant(print);
        }
        ResourceKey<PosterVariant> key = ResourceKey.create(PosterVariant.REGISTRY, current(id));
        return lookup(level.registryAccess())
                .flatMap(registry -> registry.get(key))
                .map(Holder::value)
                .orElse(null);
    }

    /**
     * Every poster the pack defines, smallest first (by area, then width), so the creative tab and the press show
     * them in a stable order that is easy to scan.
     */
    public static List<Identifier> ids(HolderLookup.Provider registries) {
        return lookup(registries)
                .map(registry -> registry.listElements()
                        .sorted(Comparator
                                .comparingInt((Holder.Reference<PosterVariant> design) -> design.value().width() * design.value().height())
                                .thenComparingInt(design -> design.value().width())
                                .thenComparing(design -> design.key().identifier()))
                        .map(design -> design.key().identifier())
                        .toList())
                .orElse(List.of());
    }

    /** Whether the press should accept this as an ink: a process colour, or something a design asks for. */
    public static boolean isInk(@Nullable Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (PosterVariant.PROCESS_INKS.contains(stack.getItem())) {
            return true;
        }
        if (level == null) {
            return false;
        }
        return lookup(level.registryAccess())
                .map(registry -> registry.listElements().anyMatch(design -> design.value().inkCost().contains(stack.getItem())))
                .orElse(false);
    }

    /** The design a poster item carries, or null for a blank sheet. */
    public static @Nullable Identifier idOf(ItemStack stack) {
        Identifier id = stack.get(ModDataComponents.POSTER.get());
        return id == null ? null : current(id);
    }

    public static ItemStack item(Identifier id) {
        ItemStack stack = new ItemStack(ModItems.POSTER.get());
        stack.set(ModDataComponents.POSTER.get(), id);
        return stack;
    }

    private Posters() {}
}
