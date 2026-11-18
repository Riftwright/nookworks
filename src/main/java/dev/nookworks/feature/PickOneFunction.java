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
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * {@code nookworks:pick_one}: turns the stack into one of {@code choices}, picked at random, each with its own count
 * and, for a trade, its own price over the trade's base ({@code extra_cost} emeralds, as the game's own
 * {@code additional_trade_cost} component carries it). So one trade can offer one of several things, priced as each
 * should be.
 * <p>
 * The Wandering Trader has one such trade among his uncommon ones: now and then one of his offers is something of
 * ours, and never more than one - a trader in a pack full of mods should not become a stall of them.
 * <p>
 * PORT: 1.21.1's trades are code, not data, and its stacks have no {@code additional_trade_cost}: the trader's trade is
 * {@link WanderingTraderGoods}, which picks among the same choices and prices them itself. This function stays for loot
 * tables, where the extra cost has no meaning and is left out.
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

    public static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Nookworks.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<PickOneFunction>> TYPE =
            FUNCTIONS.register("pick_one", () -> new LootItemFunctionType<>(MAP_CODEC));

    private final List<Choice> choices;

    private PickOneFunction(List<LootItemCondition> condition, List<Choice> choices) {
        super(condition);
        this.choices = choices;
    }

    public List<Choice> choices() {
        return this.choices;
    }

    @Override
    public LootItemFunctionType<PickOneFunction> getType() {
        return TYPE.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        Choice choice = pick(this.choices, context.getRandom());
        if (choice == null) {
            return stack;
        }
        // PORT: 26.3 puts choice.extraCost() on the stack as additional_trade_cost for a trade; 1.21.1 has no such
        // component, and WanderingTraderGoods prices its pick itself
        return new ItemStack(choice.item(), choice.count());
    }

    /** One of {@code choices} at random, or null when none can be had. */
    static @org.jspecify.annotations.Nullable Choice pick(List<Choice> choices, net.minecraft.util.RandomSource random) {
        // only among what can be had: not the items of a part of the mod a pack has switched off
        List<Choice> open = choices.stream().filter(c -> Features.enabled(c.item().value())).toList();
        if (open.isEmpty()) {
            return null;
        }
        return open.get(random.nextInt(open.size()));
    }
}
