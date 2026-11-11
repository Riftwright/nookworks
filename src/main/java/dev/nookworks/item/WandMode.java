package dev.nookworks.item;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * What the Reinforced Building Wand does with a click (the user, 2026-11-07): spread the clicked block over its flat
 * surface (what the plain Building Wand always does), replace that surface with the block in the other hand, or fill a
 * box between two corners - whole, its walls only, or its edges only. Stepped by scrolling while sneaking; kept on the
 * wand.
 */
public enum WandMode implements StringRepresentable {
    SPREAD("spread"),
    REPLACE("replace"),
    FILL("fill"),
    HOLLOW("hollow"),
    FRAME("frame");

    public static final Codec<WandMode> CODEC = StringRepresentable.fromEnum(WandMode::values);
    public static final StreamCodec<ByteBuf, WandMode> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], WandMode::ordinal);

    private final String name;

    WandMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** Whether it works between two corners. */
    public boolean box() {
        return this == FILL || this == HOLLOW || this == FRAME;
    }

    /** The next mode {@code steps} along, round. */
    public WandMode step(int steps) {
        int n = values().length;
        return values()[Math.floorMod(this.ordinal() + steps, n)];
    }

    public Component title() {
        return Component.translatable("item.nookworks.building_wand.mode." + this.name);
    }
}
