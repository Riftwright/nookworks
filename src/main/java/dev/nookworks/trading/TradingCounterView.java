package dev.nookworks.trading;

import java.util.List;
import java.util.Optional;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * A Trading Counter as its screen shows it, sent to the player looking as it changes: the villager behind it (its name,
 * level and experience), its offers and what the first of each costs at the counter's owner's price, which are ticked,
 * what the counter is doing, how many restocks the villager has left today, whether it has a workstation to restock
 * at, whether its trades may be re-rolled, and whether it stops a trade whose price has risen. {@code status} is the
 * ordinal of {@link TradingCounterBlockEntity.Status}, {@code reroll} of {@link TradingCounterBlockEntity.Reroll}.
 */
public record TradingCounterView(Optional<Component> villager, int level, int xp, MerchantOffers offers, List<Integer> prices,
        List<Integer> ticked, int status, int restocksLeft, boolean workstation, int reroll, boolean pauseWhenDear) {
    public static final TradingCounterView EMPTY = new TradingCounterView(Optional.empty(), 0, 0, new MerchantOffers(), List.of(), List.of(),
            TradingCounterBlockEntity.Status.NO_VILLAGER.ordinal(), 0, false, TradingCounterBlockEntity.Reroll.NO_VILLAGER.ordinal(), false);

    private static final StreamCodec<ByteBuf, List<Integer>> INTS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());
    public static final StreamCodec<RegistryFriendlyByteBuf, TradingCounterView> STREAM_CODEC = StreamCodec.of((buf, view) -> {
        ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(buf, view.villager());
        buf.writeVarInt(view.level());
        buf.writeVarInt(view.xp());
        MerchantOffers.STREAM_CODEC.encode(buf, view.offers());
        INTS.encode(buf, view.prices());
        INTS.encode(buf, view.ticked());
        buf.writeVarInt(view.status());
        buf.writeVarInt(view.restocksLeft());
        buf.writeBoolean(view.workstation());
        buf.writeVarInt(view.reroll());
        buf.writeBoolean(view.pauseWhenDear());
    }, buf -> new TradingCounterView(ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
            MerchantOffers.STREAM_CODEC.decode(buf), INTS.decode(buf), INTS.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
            buf.readVarInt(), buf.readBoolean()));

    public TradingCounterBlockEntity.Status statusOf() {
        TradingCounterBlockEntity.Status[] all = TradingCounterBlockEntity.Status.values();
        return all[Math.clamp(this.status, 0, all.length - 1)];
    }

    public TradingCounterBlockEntity.Reroll rerollOf() {
        TradingCounterBlockEntity.Reroll[] all = TradingCounterBlockEntity.Reroll.values();
        return all[Math.clamp(this.reroll, 0, all.length - 1)];
    }

    /** What tells one view from the next, cheaply: so it is sent only when something shown changed. */
    public String fingerprint() {
        StringBuilder out = new StringBuilder();
        out.append(this.villager.map(Component::getString).orElse("-")).append('|').append(this.level).append('|').append(this.xp)
                .append('|').append(this.status).append('|').append(this.restocksLeft).append('|').append(this.workstation).append('|')
                .append(this.reroll).append('|').append(this.ticked).append('|').append(this.prices).append('|').append(this.pauseWhenDear);
        // what each trade is, not only how much of it is left: another villager of the same name and prices, or a
        // librarian's book re-rolled, is a new list for the screen
        this.offers.forEach(o -> out.append(';').append(ItemStack.hashItemAndComponents(o.getBaseCostA())).append(',')
                .append(o.getBaseCostA().getCount()).append(',').append(ItemStack.hashItemAndComponents(o.getCostB())).append(',')
                .append(o.getCostB().getCount()).append(',').append(ItemStack.hashItemAndComponents(o.getResult())).append(',')
                .append(o.getResult().getCount()).append(':').append(o.getUses()).append('/').append(o.getMaxUses()));
        return out.toString();
    }
}
