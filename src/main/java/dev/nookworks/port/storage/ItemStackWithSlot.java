package dev.nookworks.port.storage;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * Stand-in for 26.3's {@code net.minecraft.world.ItemStackWithSlot}: a stack and the slot it is in, saved as the stack's
 * own fields with a {@code Slot} byte beside them - {@code {Slot:3b, id:"...", count:5, components:{...}}}, as 26.3 and
 * 1.21.1's own container saves both write it. The slot is always written, even slot 0.
 */
public record ItemStackWithSlot(int slot, ItemStack stack) {
    public static final Codec<ItemStackWithSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("Slot").xmap(slot -> slot.orElse(0), Optional::of).forGetter(ItemStackWithSlot::slot),
            // 1.21.1's stack codec is a record codec, so it encodes to a map: its fields sit beside the slot's
            MapCodec.assumeMapUnsafe(ItemStack.CODEC).forGetter(ItemStackWithSlot::stack))
            .apply(i, ItemStackWithSlot::new));

    public boolean isValidInContainer(int containerSize) {
        return this.slot >= 0 && this.slot < containerSize;
    }
}
