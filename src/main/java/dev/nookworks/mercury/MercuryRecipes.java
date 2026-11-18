package dev.nookworks.mercury;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * The three mercury process steps as item data maps (datapacks can add entries):
 * <ul>
 * <li>{@code nookworks:retorting} (Sealed Retort): heating the item, alone or with reagents, frees mercury
 * and leaves a by-product;</li>
 * <li>{@code nookworks:amalgamation} (Stirring Tank): mercury dissolves the metal in the item into amalgam;</li>
 * <li>{@code nookworks:distillation} (Vacuum Distiller): amalgam gives up its metal, most mercury comes back.</li>
 * </ul>
 */
public final class MercuryRecipes {
    /**
     * One reagent: an item id, a tag ({@code "#c:ingots/zinc"}) or a list of item ids, as 26.3's
     * {@code RegistryCodecs.homogeneousList} reads it.
     * <p>
     * PORT: that codec looks the tag up while the data map is read, and the NeoForge 21.1 builds packs still carry
     * (21.1.219) read data maps before any tag is known: the whole map failed with "Missing tag" and the Retort had no
     * recipes. This one takes the tag's set from the registry as it is (filled when the tags are bound, as every
     * reload does), so it reads the same on every 21.1; a tag nobody defines is an empty set, not an error.
     */
    private static final Codec<HolderSet<Item>> REAGENT = Codec.either(Codec.STRING, Codec.STRING.listOf()).comapFlatMap(
            either -> either.map(MercuryRecipes::reagent, MercuryRecipes::reagents),
            set -> set.unwrapKey().<com.mojang.datafixers.util.Either<String, List<String>>>map(
                    tag -> com.mojang.datafixers.util.Either.left("#" + tag.location()))
                    .orElseGet(() -> set.size() == 1 ? com.mojang.datafixers.util.Either.left(name(set.get(0)))
                            : com.mojang.datafixers.util.Either.right(set.stream().map(MercuryRecipes::name).toList())));

    private static String name(Holder<Item> item) {
        return BuiltInRegistries.ITEM.getKey(item.value()).toString();
    }

    private static DataResult<HolderSet<Item>> reagent(String name) {
        if (name.startsWith("#")) {
            net.minecraft.resources.ResourceLocation tag = net.minecraft.resources.ResourceLocation.tryParse(name.substring(1));
            return tag == null ? DataResult.error(() -> "Not a tag: " + name)
                    : DataResult.success(BuiltInRegistries.ITEM.getOrCreateTag(net.minecraft.tags.TagKey.create(Registries.ITEM, tag)));
        }
        return reagents(List.of(name));
    }

    private static DataResult<HolderSet<Item>> reagents(List<String> names) {
        List<Holder<Item>> items = new java.util.ArrayList<>();
        for (String name : names) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(name);
            Optional<Holder.Reference<Item>> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getHolder(id);
            if (item.isEmpty()) {
                return DataResult.error(() -> "Unknown item: " + name);
            }
            items.add(item.get());
        }
        return DataResult.success(HolderSet.direct(items));
    }

    /**
     * Roasting one item in the Sealed Retort. Most entries free {@code mercury} and leave a solid
     * {@code byproduct}; an entry with no mercury is just an evaporation (sea water down to crude salt).
     * {@code remainder} is what the container is left as, the way a bucket recipe gives the bucket back.
     * <p>
     * Each of {@code reagents} is one item cooked with the charge: an item id, a tag ({@code "#c:ingots/zinc"}, so
     * any mod's zinc will do) or a list of either.
     * <p>
     * {@code experience} is what a furnace would call it: the points one charge leaves behind, which the Retort keeps
     * as Liquid Experience (20 mB a point).
     * <p>
     * {@code liquid} ({@code {"id": "nookworks:wood_vinegar", "amount": 250}}) is a liquid the charge gives off, kept in the
     * Retort's liquid tank and pushed out into a pipe or tank beside it (since 1.5: logs give their wood vinegar so,
     * where they once filled a glass bottle in a reagent slot). With no room in that tank the Retort waits.
     */
    public record Retorting(int mercury, Holder<Item> byproduct, int count, Optional<Holder<Item>> remainder,
            List<HolderSet<Item>> reagents, float experience, Optional<Liquid> liquid) {
        public static final Codec<Retorting> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 4000).optionalFieldOf("mercury", 0).forGetter(Retorting::mercury),
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("byproduct").forGetter(Retorting::byproduct),
                Codec.intRange(0, 64).optionalFieldOf("count", 1).forGetter(Retorting::count),
                BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("remainder").forGetter(Retorting::remainder),
                REAGENT.listOf().optionalFieldOf("reagents", List.of())
                        .forGetter(Retorting::reagents),
                Codec.floatRange(0.0F, 100.0F).optionalFieldOf("experience", 0.2F).forGetter(Retorting::experience),
                Liquid.CODEC.optionalFieldOf("liquid").forGetter(Retorting::liquid))
                .apply(i, Retorting::new));

        /** Whether this entry needs anything in the reagent slot at all. */
        public boolean needsReagents() {
            return !this.reagents.isEmpty();
        }
    }

    /**
     * An amount of a liquid, as an entry gives it off: {@code {"id": "nookworks:wood_vinegar", "amount": 250}}. Not a
     * FluidStack, whose components are not yet bound when data maps load.
     */
    public record Liquid(Fluid fluid, int amount) {
        public static final Codec<Liquid> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("id").forGetter(Liquid::fluid),
                Codec.intRange(1, 64_000).fieldOf("amount").forGetter(Liquid::amount)).apply(i, Liquid::new));

        public Fluid getFluid() {
            return this.fluid;
        }

        public int getAmount() {
            return this.amount;
        }
    }

    public record Amalgamation(Holder<Item> result, int count, int mercury) {
        public static final Codec<Amalgamation> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("result").forGetter(Amalgamation::result),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Amalgamation::count),
                Codec.intRange(1, 4000).fieldOf("mercury").forGetter(Amalgamation::mercury)).apply(i, Amalgamation::new));

        public ItemStack output() {
            return new ItemStack(this.result, Math.min(this.count, new ItemStack(this.result).getMaxStackSize()));
        }
    }

    /**
     * Either a fixed {@code result} - {@code count} of it, with a {@code bonus_chance} of {@code bonus} more - or a
     * {@code loot_table}. Metals come out as nuggets, so the rate can be set finely and the Fortune-free silk-touched
     * ore and the raw ore Fortune multiplies come out about even (see amalgamation.json).
     * {@code velarium_chance} is the chance that the run also leaves a single Velarium Nugget behind: the only
     * source of the metal, and the reason anyone keeps distilling ordinary rock.
     */
    public record Distillation(Optional<Holder<Item>> result, int count, float bonusChance, int bonus, Optional<ResourceKey<LootTable>> lootTable,
            float velariumChance, int mercury) {
        public static final Codec<Distillation> CODEC = RecordCodecBuilder.<Distillation>create(i -> i.group(
                BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("result").forGetter(Distillation::result),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Distillation::count),
                Codec.floatRange(0, 1).optionalFieldOf("bonus_chance", 0.0F).forGetter(Distillation::bonusChance),
                Codec.intRange(1, 64).optionalFieldOf("bonus", 1).forGetter(Distillation::bonus),
                ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("loot_table").forGetter(Distillation::lootTable),
                Codec.floatRange(0, 1).optionalFieldOf("velarium_chance", 0.0F).forGetter(Distillation::velariumChance),
                Codec.intRange(0, 4000).fieldOf("mercury_returned").forGetter(Distillation::mercury)).apply(i, Distillation::new))
                .validate(Distillation::checked);

        /** An entry with neither a result nor a loot table would use the amalgam up for nothing. */
        private static DataResult<Distillation> checked(Distillation d) {
            return d.result().isPresent() || d.lootTable().isPresent() ? DataResult.success(d)
                    : DataResult.error(() -> "A distillation entry needs a result or a loot_table");
        }
    }

    public static final DataMapType<Item, Retorting> RETORTING = DataMapType.builder(Nookworks.id("retorting"), Registries.ITEM, Retorting.CODEC)
            .synced(Retorting.CODEC, false).build();
    public static final DataMapType<Item, Amalgamation> AMALGAMATION = DataMapType.builder(Nookworks.id("amalgamation"), Registries.ITEM, Amalgamation.CODEC)
            .synced(Amalgamation.CODEC, false).build();
    public static final DataMapType<Item, Distillation> DISTILLATION = DataMapType.builder(Nookworks.id("distillation"), Registries.ITEM, Distillation.CODEC)
            .synced(Distillation.CODEC, false).build();

    // none of them makes what cannot be had (a part switched off, or nothing reaching it any more: Features)

    public static @Nullable Retorting retorting(ItemStack stack) {
        Retorting recipe = stack.isEmpty() ? null : stack.getItemHolder().getData(RETORTING);
        return recipe != null && Features.enabled(recipe.byproduct().value())
                && recipe.liquid().map(liquid -> Features.enabled(liquid.getFluid().getBucket())).orElse(true) ? recipe : null;
    }

    public static @Nullable Amalgamation amalgamation(ItemStack stack) {
        Amalgamation recipe = stack.isEmpty() ? null : stack.getItemHolder().getData(AMALGAMATION);
        return recipe != null && Features.enabled(recipe.result().value()) ? recipe : null;
    }

    public static @Nullable Distillation distillation(ItemStack stack) {
        Distillation recipe = stack.isEmpty() ? null : stack.getItemHolder().getData(DISTILLATION);
        return recipe != null && recipe.result().map(result -> Features.enabled(result.value())).orElse(true) ? recipe : null;
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(RETORTING);
            event.register(AMALGAMATION);
            event.register(DISTILLATION);
        }

        private Registration() {}
    }

    private MercuryRecipes() {}
}
