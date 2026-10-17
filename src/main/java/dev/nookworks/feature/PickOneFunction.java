package dev.nookworks.feature;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * {@code nookworks:pick_one}: turns the stack into one of {@code choices}, picked at random, each with its own count
 * and, for a trade, its own price over the trade's base ({@code extra_cost} emeralds, as the game's own
 * {@code additional_trade_cost} component carries it). So one trade can offer one of several things, priced as each
 * should be.
 * <p>
 * The Wandering Trader has one such trade among his uncommon ones: now and then one of his offers is something of
 * ours, and never more than one - a trader in a pack full of mods should not become a stall of them.
 */
public class PickOneFunction extends LootItemConditionalFunction {
    public record Choice(Holder<Item> item, int count, int extraCost) {
        public static final Codec<Choice> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(Choice::item),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Choice::count),
                Codec.intRange(0, 64).optionalFieldOf("extra_cost", 0).forGetter(Choice::extraCost)).apply(i, Choice::new));
    }

    public static final MapCodec<PickOneFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(Choice.CODEC.listOf(1, 64).fieldOf("choices").forGetter(f -> f.choices))
            .apply(i, PickOneFunction::new));

    public static final DeferredRegister<MapCodec<? extends LootItemFunction>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Nookworks.MODID);

    static {
        FUNCTIONS.register("pick_one", () -> MAP_CODEC);
    }

    private final List<Choice> choices;

    private PickOneFunction(Optional<Holder<LootItemCondition>> condition, List<Choice> choices) {
        super(condition);
        this.choices = choices;
    }

    public List<Choice> choices() {
        return this.choices;
    }

    @Override
    public MapCodec<PickOneFunction> codec() {
        return MAP_CODEC;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        // only among what can be had: not the items of a part of the mod a pack has switched off
        List<Choice> open = this.choices.stream().filter(c -> Features.enabled(c.item().value())).toList();
        if (open.isEmpty()) {
            return stack;
        }
        Choice choice = open.get(context.getRandom().nextInt(open.size()));
        ItemStack picked = new ItemStack(choice.item(), choice.count());
        if (choice.extraCost() > 0) {
            picked.set(DataComponents.ADDITIONAL_TRADE_COST, choice.extraCost());
        }
        return picked;
    }
}
