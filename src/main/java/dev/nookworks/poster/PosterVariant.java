package dev.nookworks.poster;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One poster design, loaded from {@code data/<namespace>/nookworks/poster/<name>.json}. Adding a poster is a
 * datapack job: drop the artwork at {@code assets/<namespace>/textures/poster/<asset>.png} and the JSON beside
 * it - no code changes, and resource packs or other mods can add their own.
 * <p>
 * This is deliberately a registry of our own rather than vanilla's {@code painting_variant}: posters must never
 * turn up in a randomly placed painting, and keeping them apart makes that true by construction.
 *
 * @param asset  the texture, {@code namespace:name} pointing at {@code textures/poster/<name>.png}
 * @param width  width in blocks
 * @param height height in blocks
 * @param title  shown on the item; falls back to the poster's own id
 * @param inks   what the Printing Press spends on one copy, on top of the sheet of Fine Paper; leave it out
 *               and the design is printed with the four process inks, the way real colour printing works
 */
public record PosterVariant(Identifier asset, int width, int height, Optional<Component> title, List<Holder<Item>> inks) {
    public static final ResourceKey<Registry<PosterVariant>> REGISTRY = ResourceKey.createRegistryKey(Nookworks.id("poster"));

    /** Big enough for a wall mural, small enough that the bounding box stays sane. */
    public static final int MAX_SIZE = 8;

    public static final Codec<PosterVariant> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("asset").forGetter(PosterVariant::asset),
            Codec.intRange(1, MAX_SIZE).optionalFieldOf("width", 1).forGetter(PosterVariant::width),
            Codec.intRange(1, MAX_SIZE).optionalFieldOf("height", 1).forGetter(PosterVariant::height),
            ComponentSerialization.CODEC.optionalFieldOf("title").forGetter(PosterVariant::title),
            BuiltInRegistries.ITEM.holderByNameCodec().listOf().optionalFieldOf("inks", List.of()).forGetter(PosterVariant::inks))
            .apply(i, PosterVariant::new));

    /** One sheet of Fine Paper per copy, whatever the design. */
    public static final int PAPER_PER_COPY = 1;

    /**
     * Cyan, magenta, yellow and black: four-colour process printing, which is what a press with these four
     * inks can actually reproduce. A design that wants something else says so in its {@code inks}.
     */
    public static final List<Item> PROCESS_INKS = List.of(Items.DYE.pick(DyeColor.CYAN), Items.DYE.pick(DyeColor.MAGENTA),
            Items.DYE.pick(DyeColor.YELLOW), Items.DYE.pick(DyeColor.BLACK));

    /** What one copy actually costs: the declared inks, or the process set when none are declared. */
    public List<Item> inkCost() {
        return this.inks.isEmpty() ? PROCESS_INKS : this.inks.stream().map(Holder::value).toList();
    }

    /** Where the artwork actually lives, expanded from {@link #asset()}. */
    public Identifier texture() {
        return this.asset.withPath(path -> "textures/poster/" + path + ".png");
    }
}
