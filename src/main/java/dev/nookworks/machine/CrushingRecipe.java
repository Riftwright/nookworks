package dev.nookworks.machine;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What the Crusher turns an item into: data map {@code nookworks:crushing} on items
 * ({@code data/<ns>/data_maps/item/crushing.json}), so datapacks can add recipes.
 * <p>
 * {@code result} is normally an item id. It can also be a tag - {@code "#c:dusts/iron"} - and then the recipe
 * only exists while something has actually put an item in that tag. That is how the metal recipes work: on
 * their own they grind nothing, because vanilla has no iron dust, but in a pack with an industrial mod the
 * Crusher grinds iron into that mod's dust and fits into its pipeline. It deliberately does not invent a dust
 * of our own: doubling metal is what the mercury chain is for.
 */
public record CrushingRecipe(Result result, int count) {
    /** Either a fixed item or "whatever is in this tag", which may be nothing at all. */
    public sealed interface Result {
        record OfItem(Holder<Item> item) implements Result {}

        record OfTag(TagKey<Item> tag) implements Result {}
    }

    public static final Codec<Result> RESULT_CODEC = Codec.STRING.comapFlatMap(CrushingRecipe::parse, CrushingRecipe::print);

    public static final Codec<CrushingRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            RESULT_CODEC.fieldOf("result").forGetter(CrushingRecipe::result),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(CrushingRecipe::count)
    ).apply(i, CrushingRecipe::new));

    public static final DataMapType<Item, CrushingRecipe> DATA_MAP =
            DataMapType.builder(Nookworks.id("crushing"), Registries.ITEM, CODEC).synced(CODEC, false).build();

    private static DataResult<Result> parse(String value) {
        if (value.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(value.substring(1));
            return id == null ? DataResult.error(() -> "not a tag id: " + value)
                    : DataResult.success(new Result.OfTag(TagKey.create(Registries.ITEM, id)));
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            return DataResult.error(() -> "not an item id: " + value);
        }
        return BuiltInRegistries.ITEM.getHolder(id)
                .<DataResult<Result>>map(holder -> DataResult.success(new Result.OfItem(holder)))
                .orElseGet(() -> DataResult.error(() -> "unknown item: " + value));
    }

    private static String print(Result result) {
        return switch (result) {
            case Result.OfItem item -> item.item().getRegisteredName();
            case Result.OfTag tag -> "#" + tag.tag().location();
        };
    }

    /**
     * Another mod's like work, asked for an item the data map has nothing for: in the 1.21.1 port with Create, its
     * crushing (and milling) recipes, so the Crusher does a Crushing Wheel's work (dev.nookworks.compat.create.CreateRecipes).
     */
    private static volatile java.util.function.Function<ItemStack, @Nullable CrushingRecipe> foreign = stack -> null;

    public static void foreign(java.util.function.Function<ItemStack, @Nullable CrushingRecipe> lookup) {
        foreign = lookup;
    }

    /** The recipe for this item, or null when there is none - including a tag recipe nothing fills. */
    public static @Nullable CrushingRecipe of(ItemStack stack) {
        CrushingRecipe own = ownOf(stack);
        if (own != null || stack.isEmpty() || dev.nookworks.recycling.RecyclingRecipe.holdsThings(stack)) {
            return own;
        }
        // another mod's like work (the port's Create crushing and milling), for what ours does not grind - a tag recipe
        // nothing fills included (raw iron, with no iron dust about)
        CrushingRecipe other = foreign.apply(stack);
        return other != null && other.resolve().filter(item -> Features.enabled(item.value())).isPresent() ? other : null;
    }

    /** Our own recipe for this item (the data map's), leaving another mod's like work out: what #nookworks:crushable lists. */
    public static @Nullable CrushingRecipe ownOf(ItemStack stack) {
        if (stack.isEmpty() || dev.nookworks.recycling.RecyclingRecipe.holdsThings(stack)) {
            return null; // nor a chest picked up with its contents, crushed with it
        }
        CrushingRecipe recipe = stack.getItemHolder().getData(DATA_MAP);
        // nor one that makes a part's item when the part is off (flour, with the kitchen off)
        return recipe != null && recipe.resolve().filter(item -> Features.enabled(item.value())).isPresent() ? recipe : null;
    }

    /**
     * The item this makes, if there is one. A tag with nothing in it resolves to nothing.
     * <p>
     * PORT: in a 1.21.1 pack several mods fill one dust tag (Mekanism's, EnderIO's and Cyclic's iron dust all sit in
     * {@code c:dusts/iron}), and the tag's own order is the order the mods' data loaded in. The choice is made by name
     * instead, so it is the same in every world and on every server: vanilla's own if the tag has one, then ours (our
     * sulfur dust), then the item whose id comes first (enderio:powdered_iron before mekanism:dust_iron). A pack that wants
     * another writes that item in the data map instead of the tag.
     */
    public Optional<Holder<Item>> resolve() {
        return switch (this.result) {
            case Result.OfItem item -> Optional.of(item.item());
            case Result.OfTag tag -> preferred(BuiltInRegistries.ITEM.getTagOrEmpty(tag.tag()));
        };
    }

    /** The one of {@code items} a tag stands for: vanilla's, then ours, then the first id (none for none). */
    public static Optional<Holder<Item>> preferred(Iterable<Holder<Item>> items) {
        Holder<Item> best = null;
        for (Holder<Item> holder : items) {
            if (best == null || TAG_CHOICE.compare(holder, best) < 0) {
                best = holder;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Vanilla's first, then ours, then by id. */
    private static final java.util.Comparator<Holder<Item>> TAG_CHOICE = java.util.Comparator
            .comparingInt((Holder<Item> holder) -> switch (namespace(holder)) {
                case "minecraft" -> 0;
                case Nookworks.MODID -> 1;
                default -> 2;
            })
            .thenComparing(holder -> holder.unwrapKey().map(key -> key.location().toString()).orElse(""));

    private static String namespace(Holder<Item> holder) {
        return holder.unwrapKey().map(key -> key.location().getNamespace()).orElse("");
    }

    /**
     * Gear ground down gives back less than it took to make, and less again the more it is worn: 45% of the recipe's
     * count when new, times the durability left, the fraction as a chance of one more. A new diamond chestplate (the
     * recipe's 8) gives 3 or 4 diamonds, a new pickaxe 1 or 2, one nearly broken nothing. (It gave the whole 8 at any
     * wear, which took the cost out of wearing gear out.)
     */
    public static final float GEAR_RECOVERY = 0.45F;

    /** What grinding {@code input} gives this time: {@link #output()}, or for damageable gear its worn share. */
    public ItemStack outputFor(ItemStack input, RandomSource random) {
        ItemStack full = this.output();
        if (full.isEmpty() || !input.isDamageableItem()) {
            return full;
        }
        float left = 1.0F - (float) input.getDamageValue() / Math.max(1, input.getMaxDamage());
        float expected = full.getCount() * GEAR_RECOVERY * left;
        int count = (int) expected + (random.nextFloat() < expected - (int) expected ? 1 : 0);
        return count <= 0 ? ItemStack.EMPTY : full.copyWithCount(count);
    }

    public ItemStack output() {
        return this.resolve().map(item -> new ItemStack(item, Math.min(this.count, new ItemStack(item).getMaxStackSize())))
                .orElse(ItemStack.EMPTY);
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(DATA_MAP);
        }

        private Registration() {}
    }
}
