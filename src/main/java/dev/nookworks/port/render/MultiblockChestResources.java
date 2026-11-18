package dev.nookworks.port.render;

import java.util.function.Function;

import net.minecraft.world.level.block.state.properties.ChestType;

/** 26.3's {@code MultiblockChestResources}: one of a thing for a single chest and for each half of a pair. */
public record MultiblockChestResources<T>(T single, T left, T right) {
    public T select(ChestType chestType) {
        return switch (chestType) {
            case SINGLE -> this.single;
            case LEFT -> this.left;
            case RIGHT -> this.right;
        };
    }

    public <S> MultiblockChestResources<S> map(Function<T, S> mapper) {
        return new MultiblockChestResources<>(mapper.apply(this.single), mapper.apply(this.left), mapper.apply(this.right));
    }
}
