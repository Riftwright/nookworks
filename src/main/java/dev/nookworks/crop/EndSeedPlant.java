package dev.nookworks.crop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * What a End Seed's sprout may open into: data map {@code nookworks:end_seed} on blocks
 * ({@code data/<ns>/data_maps/block/end_seed.json}), so a datapack can change the draw. {@code weight} is the
 * plant's share of it; {@code properties} are set on the block's default state (a sweet berry bush already bearing,
 * corn ripe, a full bed of petals).
 */
public record EndSeedPlant(int weight, Map<String, String> properties) {
    public static final Codec<EndSeedPlant> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("weight").forGetter(EndSeedPlant::weight),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("properties", Map.of()).forGetter(EndSeedPlant::properties)
    ).apply(i, EndSeedPlant::new));

    public static final DataMapType<Block, EndSeedPlant> DATA_MAP =
            DataMapType.builder(Nookworks.id("end_seed"), Registries.BLOCK, CODEC).synced(CODEC, false).build(); // the client asks it too (bone meal on a bud)

    /** A plant in the draw, as it would be placed. */
    public record Entry(BlockState state, int weight) {}

    /** The block's state with these properties, or null when one of them is not the block's. */
    public @Nullable BlockState state(Block block) {
        BlockState state = block.defaultBlockState();
        for (var property : properties.entrySet()) {
            Property<?> found = block.getStateDefinition().getProperty(property.getKey());
            state = found == null ? null : with(state, found, property.getValue());
            if (state == null) {
                return null;
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> @Nullable BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(null);
    }

    /**
     * Every plant of the draw, a tall one only where there is room above for its top, and none of a part that is off
     * (the corn, with the kitchen switched off: its crop would drop corn nothing else gives then).
     */
    public static List<Entry> fitting(LevelReader level, BlockPos pos) {
        List<Entry> fitting = new ArrayList<>();
        for (var plant : BuiltInRegistries.BLOCK.getDataMap(DATA_MAP).entrySet()) {
            Block block = BuiltInRegistries.BLOCK.getValue(plant.getKey());
            BlockState state = block == null ? null : plant.getValue().state(block);
            if (state != null && Features.enabled(block.asItem()) && (!isTall(state) || BloomingCropBlock.roomAbove(level, pos))) {
                fitting.add(new Entry(state, plant.getValue().weight()));
            }
        }
        return fitting;
    }

    /** One of {@code pool}, by weight. */
    public static Entry draw(List<Entry> pool, RandomSource random) {
        // summed as a long: a datapack's large weights would overflow an int (r14)
        long roll = Math.floorMod(random.nextLong(), pool.stream().mapToLong(Entry::weight).sum());
        for (Entry entry : pool) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return pool.getLast();
    }

    /** Two blocks tall: a tall flower, a large fern, corn grown up (corn younger than that is a block). */
    public static boolean isTall(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return false;
        }
        return !(state.getBlock() instanceof CornBlock) || CornBlock.isDouble(state.getValue(CornBlock.AGE));
    }

    /**
     * Puts {@code plant} at {@code pos} in the bud's place: on the farmland if it stands there, else on dirt, the ground
     * turned back to what a flower or a bush stands on. False, and nothing changed, if it stands on neither. Public for
     * tests.
     */
    public static boolean open(Level level, BlockPos pos, BlockState plant) {
        if (plant.canSurvive(level, pos)) {
            place(level, pos, plant);
            return true;
        }
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        BlockState bud = level.getBlockState(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS); // the bud first, or the dirt pops it
        level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        if (plant.canSurvive(level, pos)) {
            place(level, pos, plant);
            return true;
        }
        level.setBlock(below, ground, Block.UPDATE_ALL);
        level.setBlock(pos, bud, Block.UPDATE_CLIENTS);
        return false;
    }

    private static void place(Level level, BlockPos pos, BlockState plant) {
        if (isTall(plant)) {
            DoublePlantBlock.placeAt(level, plant, pos, Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, plant, Block.UPDATE_ALL);
        }
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void register(RegisterDataMapTypesEvent event) {
            event.register(DATA_MAP);
        }

        private Registration() {}
    }
}
