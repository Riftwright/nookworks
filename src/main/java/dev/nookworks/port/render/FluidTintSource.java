package dev.nookworks.port.render;

import net.minecraft.world.level.material.FluidState;

/** 26.3 NeoForge's {@code FluidTintSource}: the colour a liquid's textures are multiplied by. */
public interface FluidTintSource {
    int color(FluidState state);
}
