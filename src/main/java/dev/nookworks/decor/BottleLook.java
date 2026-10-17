package dev.nookworks.decor;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Which bottles can be set down (sneak and right-click the top of a block) and the colour of what is in them: data map
 * {@code nookworks:placed_bottle} on items, so a datapack can add another mod's. {@code color} ("#rrggbb") is the
 * liquid's; with none the bottle stands empty. A potion's own colour wins over it (water, every potion and its splash
 * and lingering kinds).
 */
public record BottleLook(Optional<Integer> color) {
    private static final Codec<Integer> HEX = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "not a colour: " + s);
        }
    }, c -> String.format("#%06x", c & 0xFFFFFF));
    public static final Codec<BottleLook> CODEC = RecordCodecBuilder.create(i -> i.group(
            HEX.optionalFieldOf("color").forGetter(BottleLook::color)
    ).apply(i, BottleLook::new));

    public static final DataMapType<Item, BottleLook> DATA_MAP =
            DataMapType.builder(Nookworks.id("placed_bottle"), Registries.ITEM, CODEC).synced(CODEC, false).build();

    /** The look of {@code stack}, or null when it is not a bottle that can be set down. */
    public static @Nullable BottleLook of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.typeHolder().getData(DATA_MAP);
    }

    /** The liquid's colour, opaque RGB; -1 for an empty bottle. */
    public static int liquid(ItemStack stack) {
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            return potion.getColor() | 0xFF000000;
        }
        BottleLook look = of(stack);
        return look == null || look.color().isEmpty() ? -1 : look.color().get() | 0xFF000000;
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
