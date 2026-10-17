package dev.nookworks.mercury;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
     * Roasting one item in the Sealed Retort. Most entries free {@code mercury} and leave a solid
     * {@code byproduct}; an entry with no mercury is just an evaporation (sea water down to crude salt).
     * {@code remainder} is what the container is left as, the way a bucket recipe gives the bucket back.
     * <p>
     * Each of {@code reagents} is one item cooked with the charge: an item id, a tag ({@code "#c:ingots/zinc"}, so
     * any mod's zinc will do) or a list of either.
     * <p>
     * {@code experience} is what a furnace would call it: the points one charge leaves behind, which the Retort keeps
     * as Liquid Experience (20 mB a point).
     */
    public record Retorting(int mercury, Holder<Item> byproduct, int count, Optional<Holder<Item>> remainder,
            List<HolderSet<Item>> reagents, float experience) {
        public static final Codec<Retorting> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 4000).optionalFieldOf("mercury", 0).forGetter(Retorting::mercury),
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("byproduct").forGetter(Retorting::byproduct),
                Codec.intRange(0, 64).optionalFieldOf("count", 1).forGetter(Retorting::count),
                BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("remainder").forGetter(Retorting::remainder),
                RegistryCodecs.holderSet(Registries.ITEM).listOf().optionalFieldOf("reagents", List.of())
                        .forGetter(Retorting::reagents),
                Codec.floatRange(0.0F, 100.0F).optionalFieldOf("experience", 0.2F).forGetter(Retorting::experience))
                .apply(i, Retorting::new));

        /** Whether this entry needs anything in the reagent slot at all. */
        public boolean needsReagents() {
            return !this.reagents.isEmpty();
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

    public static @Nullable Retorting retorting(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(RETORTING);
    }

    public static @Nullable Amalgamation amalgamation(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(AMALGAMATION);
    }

    public static @Nullable Distillation distillation(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(DISTILLATION);
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
