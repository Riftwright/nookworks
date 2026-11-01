package dev.nookworks.fluid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;

/**
 * A block with a window onto the liquid in it - a Copper Tank, a Sight Glass - whose client is told which liquid that
 * is, so another mod's is drawn in its own colour (client/render/WindowTint; nookTechnology's coal tar, 2026-11-01:
 * black, it looked like an empty glass in the grey every other liquid was drawn in). Water, vegetable oil and biodiesel
 * have pictures of their own, by the block's state ({@link LineContent}).
 */
public interface WindowFluid {
    /** The update tag's key: the liquid's id. */
    String KEY = "window_fluid";

    /** On the client: the liquid in the window, as last told ({@link Fluids#EMPTY} for none). */
    Fluid windowFluid();

    /** The update tag telling the client {@code fluid}. */
    static CompoundTag tag(Fluid fluid) {
        CompoundTag tag = new CompoundTag();
        if (fluid != Fluids.EMPTY) {
            tag.putString(KEY, BuiltInRegistries.FLUID.getKey(fluid).toString());
        }
        return tag;
    }

    /** What an update tag says is in the window: {@link Fluids#EMPTY} for none, or a liquid this game does not know. */
    static Fluid read(ValueInput input) {
        return input.read(KEY, BuiltInRegistries.FLUID.byNameCodec()).orElse(Fluids.EMPTY);
    }
}
