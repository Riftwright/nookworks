package dev.nookworks.item;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * What a Wrench does with a click (the user, 2026-11-07): Adjust turns and sets up blocks, as the Wrench always did;
 * Recover takes a machine or fitting up into the inventory at once. Kept on the item ({@code nookworks:wrench_mode});
 * absent, it is Adjust.
 */
public enum WrenchMode implements StringRepresentable {
    ADJUST("adjust"),
    RECOVER("recover");

    public static final Codec<WrenchMode> CODEC = StringRepresentable.fromEnum(WrenchMode::values);
    public static final StreamCodec<ByteBuf, WrenchMode> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[Math.floorMod(i, values().length)],
            WrenchMode::ordinal);

    private final String name;

    WrenchMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public WrenchMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    /** The mode's name, as the tooltip and the action bar give it. */
    public Component displayName() {
        return Component.translatable("message.nookworks.wrench.mode." + this.name);
    }
}
