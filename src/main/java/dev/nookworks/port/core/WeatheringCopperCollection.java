package dev.nookworks.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.world.level.block.WeatheringCopper;

/**
 * 26.3's {@code WeatheringCopperCollection}: a copper block's four weathering stages, unwaxed and waxed. 1.21.1 has
 * one constant per block ({@code Blocks.WAXED_EXPOSED_COPPER}); {@link CopperBlocks} and {@link CopperItems} gather
 * them. The copper kinds 1.21.1 lacks (lanterns, bars, chains, chests, golem statues, several lightning rods) are not
 * there: see DIFFERENCES.md.
 */
public record WeatheringCopperCollection<T>(ByState<T> weathering, ByState<T> waxed) {
    /** 26.3's id prefixes of the eight stages ("", "exposed_" ... "waxed_oxidized_"), as vanilla names its copper. */
    public static final WeatheringCopperCollection<String> PREFIXES = new WeatheringCopperCollection<>(
            new ByState<>("", "exposed_", "weathered_", "oxidized_"),
            new ByState<>("waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_"));

    /** Unwaxed first, then waxed, each from unaffected to oxidized. */
    public List<T> asList() {
        List<T> all = new ArrayList<>(8);
        this.forEach(all::add);
        return List.copyOf(all);
    }

    public void forEach(Consumer<T> consumer) {
        this.weathering.forEach(consumer);
        this.waxed.forEach(consumer);
    }

    public <U> WeatheringCopperCollection<U> map(Function<T, U> mapper) {
        return new WeatheringCopperCollection<>(this.weathering.map(mapper), this.waxed.map(mapper));
    }

    public record ByState<T>(T unaffected, T exposed, T weathered, T oxidized) {
        public List<T> asList() {
            return List.of(this.unaffected, this.exposed, this.weathered, this.oxidized);
        }

        public void forEach(Consumer<T> consumer) {
            this.asList().forEach(consumer);
        }

        /** 26.3's {@code pick}: the one of the given stage. */
        public T pick(WeatheringCopper.WeatherState state) {
            return switch (state) {
                case UNAFFECTED -> this.unaffected;
                case EXPOSED -> this.exposed;
                case WEATHERED -> this.weathered;
                case OXIDIZED -> this.oxidized;
            };
        }

        public <U> ByState<U> map(Function<T, U> mapper) {
            return new ByState<>(mapper.apply(this.unaffected), mapper.apply(this.exposed),
                    mapper.apply(this.weathered), mapper.apply(this.oxidized));
        }
    }
}
