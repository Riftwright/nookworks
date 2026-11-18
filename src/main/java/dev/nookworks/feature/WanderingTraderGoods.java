package dev.nookworks.feature;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/**
 * The Wandering Trader's trade of something of ours: one of eight things, each with its own count and price.
 * <p>
 * PORT: 26.3 makes it from data, {@code data/nookworks/villager_trade/wandering_trader/nookworks_goods.json} (a trade
 * whose item is turned by {@link PickOneFunction}, listed in his uncommon trades), so a data pack could change or take
 * it out. 1.21.1's trades are code: the same trade is written here and put among his rare trades (1.21.1's pool of
 * the trades 26.3 calls uncommon: logs, ice, the enchanted pickaxe ...). The price is one emerald plus the pick's
 * extra cost, five uses, a 0.05 reputation discount, as the data file says; vanilla's default of 1 xp.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class WanderingTraderGoods {
    /** The choices, as the 26.3 data file lists them: item id, count, emeralds over the base one. */
    private static final List<Entry> CHOICES = List.of(
            new Entry("dice", 1, 2),
            new Entry("brown_rice", 4, 1),
            new Entry("corn_kernels", 4, 1),
            new Entry("onion", 2, 1),
            new Entry("sconce", 1, 2),
            new Entry("oil_lamp", 1, 2),
            new Entry("rare_seed", 1, 6),
            new Entry("glazed_flower_pot", 1, 2));

    private record Entry(String id, int count, int extraCost) {}

    private WanderingTraderGoods() {}

    @SubscribeEvent
    static void onWandererTrades(WandererTradesEvent event) {
        event.getRareTrades().add((trader, random) -> offer(random));
    }

    /** The trade as one trader would offer it: one of the choices that can be had, or null if none can. Public for tests. */
    public static @Nullable MerchantOffer offer(RandomSource random) {
        List<PickOneFunction.Choice> choices = CHOICES.stream()
                .map(c -> new PickOneFunction.Choice(
                        BuiltInRegistries.ITEM.getHolderOrThrow(ResourceKey.create(Registries.ITEM, Nookworks.id(c.id()))),
                        c.count(), c.extraCost()))
                .toList();
        PickOneFunction.Choice choice = PickOneFunction.pick(choices, random);
        if (choice == null) {
            return null; // every choice is in a part switched off
        }
        return new MerchantOffer(new ItemCost(Items.EMERALD, 1 + choice.extraCost()),
                new ItemStack(choice.item(), choice.count()), 5, 1, 0.05F);
    }
}
