package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
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

    @Invoker("updateTrades")
    void nookworks$updateTrades(ServerLevel level);

    @Accessor("numberOfRestocksToday")
    int nookworks$restocksToday();

    @Accessor("lastRestockGameTime")
    long nookworks$lastRestockGameTime();

    @Accessor("lastRestockCheckDay")
    long nookworks$lastRestockCheckDay();
}
