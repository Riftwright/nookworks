package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * What a Trading Counter needs of a villager that vanilla keeps to the class ({@code dev.nookworks.trading}): the
 * villager's own bookkeeping after a trade - its experience, a level up waiting, the experience orbs - done as vanilla
 * does it for a trade by hand; its trades drawn for its level, as vanilla draws them when it takes its job (the
 * counter's re-roll); and its restock bookkeeping, read (never written) to show how many restocks it has left today.
 */
@Mixin(Villager.class)
public interface VillagerAccessor {
    @Invoker("rewardTradeXp")
    void nookworks$rewardTradeXp(MerchantOffer offer);

    /** (1.21.1's takes no level: it reads its own, as 26.3's is handed it.) */
    @Invoker("updateTrades")
    void nookworks$updateTrades();

    @Accessor("numberOfRestocksToday")
    int nookworks$restocksToday();

    @Accessor("lastRestockGameTime")
    long nookworks$lastRestockGameTime();

    /** 1.21.1 keeps the day time of the last check (26.3: the day); the counter reads it as a day time. */
    @Accessor("lastRestockCheckDayTime")
    long nookworks$lastRestockCheckDay();
}
