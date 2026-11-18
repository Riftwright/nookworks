package dev.nookworks.recycling;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bare corrugated sheet, which rusts as copper weathers: four ages, the next named by the data map
 * {@code neoforge:oxidizables}; a honeycomb waxes it (stops it), an axe scrapes it back (data maps, as vanilla's copper).
 */
public class RustingSheetBlock extends CorrugatedSheetBlock implements WeatheringCopper {
    private final WeatheringCopper.WeatherState age;

    public RustingSheetBlock(WeatheringCopper.WeatherState age, Properties properties) {
        super(properties);
        this.age = age;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.changeOverTime(state, level, pos, random);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }

    @Override
    public WeatheringCopper.WeatherState getAge() {
        return this.age;
    }
}
