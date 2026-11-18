package dev.nookworks.port.core;

import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 26.3's {@code new BlockEntityType<>(factory, blocks...)} (and its {@code Set<Block>} form) on 1.21.1, where the
 * constructor also takes a data-fixer type and the usual way is {@code BlockEntityType.Builder.of(...).build(null)}.
 * tx/core.py turns {@code new BlockEntityType<>(} into {@code BlockEntityTypeOf.create(}. 26.3's
 * {@code onlyOpCanSetNbt} flag has no 1.21.1 counterpart (vanilla keeps its own list of such types there), so the
 * forms with it are not given: a person decides.
 */
public final class BlockEntityTypeOf {
    private BlockEntityTypeOf() {}

    public static <T extends BlockEntity> BlockEntityType<T> create(BlockEntityType.BlockEntitySupplier<? extends T> factory,
            Block... validBlocks) {
        if (validBlocks.length == 0) {
            // as 26.3 does: a type for no block at all is a mistake unless asked for with an empty set
            throw new IllegalArgumentException("Block entity type instantiated without valid blocks");
        }
        return new BlockEntityType<>(factory, Set.of(validBlocks), null);
    }

    public static <T extends BlockEntity> BlockEntityType<T> create(BlockEntityType.BlockEntitySupplier<? extends T> factory,
            Set<Block> validBlocks) {
        return new BlockEntityType<>(factory, validBlocks, null);
    }
}
