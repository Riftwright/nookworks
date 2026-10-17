package dev.nookworks.rope;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What an item dries into hung up in the sun, and after how long of it ({@code nookworks:drying}, an item data map):
 * raw fish into Dried Fish, kelp into dried kelp. Only sunny time counts - daylight, open sky, no rain.
 */
public record Drying(Holder<Item> result, int ticks) {
    public static final Codec<Drying> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("result").forGetter(Drying::result),
            Codec.intRange(20, 1_000_000).fieldOf("ticks").forGetter(Drying::ticks)).apply(i, Drying::new));

    public static final DataMapType<Item, Drying> MAP = DataMapType.builder(Nookworks.id("drying"), Registries.ITEM, CODEC)
            .synced(CODEC, false).build();

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(MAP);
        }

        private Registration() {}
    }
}
