package dev.nookworks.port.core;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/**
 * 1.21.1 makes most vanilla block classes ({@code BaseEntityBlock}, {@code HorizontalDirectionalBlock}, ...) ask for
 * {@code codec()}; 26.3 dropped block codecs. Nothing in the game decodes our blocks by codec (only the data report
 * that lists every block reads them), so the codec tx/core.py writes into our block classes is the block itself: it
 * encodes to nothing and decodes to this very block.
 */
public final class BlockCodecs {
    private BlockCodecs() {}

    public static <B extends Block> MapCodec<B> of(B block) {
        return MapCodec.unit(block);
    }
}
