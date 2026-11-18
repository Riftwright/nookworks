package dev.nookworks.port.core;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.item.DyeColor;

/**
 * 26.3's {@code ColorCollection}: one thing per dye colour, in dye order. 1.21.1 has one constant per colour
 * ({@code Blocks.RED_BED}); {@link ColorBlocks} and {@link ColorItems} gather them into 26.3's collections
 * ({@code Blocks.BED.pick(DyeColor.RED)}), and tx/core.py points the mod's uses there.
 */
public record ColorCollection<T>(T white, T orange, T magenta, T lightBlue, T yellow, T lime, T pink, T gray,
        T lightGray, T cyan, T purple, T blue, T brown, T green, T red, T black) {

    public static final ColorCollection<DyeColor> VALUES = new ColorCollection<>(DyeColor.WHITE, DyeColor.ORANGE,
            DyeColor.MAGENTA, DyeColor.LIGHT_BLUE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY,
            DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE, DyeColor.BLUE, DyeColor.BROWN, DyeColor.GREEN,
            DyeColor.RED, DyeColor.BLACK);

    public static <T> ColorCollection<T> create(T value) {
        return new ColorCollection<>(value, value, value, value, value, value, value, value, value, value, value, value,
                value, value, value, value);
    }

    public T pick(DyeColor color) {
        return switch (color) {
            case WHITE -> this.white;
            case ORANGE -> this.orange;
            case MAGENTA -> this.magenta;
            case LIGHT_BLUE -> this.lightBlue;
            case YELLOW -> this.yellow;
            case LIME -> this.lime;
            case PINK -> this.pink;
            case GRAY -> this.gray;
            case LIGHT_GRAY -> this.lightGray;
            case CYAN -> this.cyan;
            case PURPLE -> this.purple;
            case BLUE -> this.blue;
            case BROWN -> this.brown;
            case GREEN -> this.green;
            case RED -> this.red;
            case BLACK -> this.black;
        };
    }

    /** The sixteen, in dye order. */
    public List<T> asList() {
        return List.of(this.white, this.orange, this.magenta, this.lightBlue, this.yellow, this.lime, this.pink,
                this.gray, this.lightGray, this.cyan, this.purple, this.blue, this.brown, this.green, this.red,
                this.black);
    }

    public void forEach(Consumer<T> consumer) {
        this.asList().forEach(consumer);
    }

    public <U> ColorCollection<U> map(Function<T, U> mapper) {
        return new ColorCollection<>(mapper.apply(this.white), mapper.apply(this.orange), mapper.apply(this.magenta),
                mapper.apply(this.lightBlue), mapper.apply(this.yellow), mapper.apply(this.lime),
                mapper.apply(this.pink), mapper.apply(this.gray), mapper.apply(this.lightGray),
                mapper.apply(this.cyan), mapper.apply(this.purple), mapper.apply(this.blue), mapper.apply(this.brown),
                mapper.apply(this.green), mapper.apply(this.red), mapper.apply(this.black));
    }
}
