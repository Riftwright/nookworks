package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.block.FertileSoilBlock;
import dev.nookworks.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Wood vinegar sprinkled on a field: the smoky smell of it keeps rabbits off the carrots (they will not go for a
 * treated bed - {@code mixin/RabbitRaidGardenGoalMixin}) and nourished soil gives up its richness half as fast, for
 * three days. One bottle does the farmland in a 5x5 square round where it is used, as far as the ground is farmland
 * or tilled nourished soil. The smell is kept per chunk, as the Fireproof Coating is, each square with the game time it
 * wears off at; it is only needed on the server.
 */
public final class WoodVinegar {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);

    /** Three days. */
    public static final long LASTS = 72_000L;
    /** How far out from the bottle's square the vinegar reaches. */
    public static final int REACH = 2;

    private record Entry(long pos, long until) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos), Codec.LONG.fieldOf("until").forGetter(Entry::until)).apply(i, Entry::new));
    }

    private static final MapCodec<Long2LongOpenHashMap> CODEC = Entry.CODEC.listOf().fieldOf("treated").xmap(list -> {
        Long2LongOpenHashMap map = new Long2LongOpenHashMap();
        list.forEach(e -> map.put(e.pos(), e.until()));
        return map;
    }, map -> {
        List<Entry> list = new ArrayList<>();
        map.long2LongEntrySet().forEach(e -> list.add(new Entry(e.getLongKey(), e.getLongValue())));
        return list;
    });

    /** Treated squares in a chunk (BlockPos#asLong) and the game time each wears off at. */
    public static final Supplier<AttachmentType<Long2LongOpenHashMap>> TREATED = ATTACHMENT_TYPES.register("wood_vinegar",
            () -> AttachmentType.builder(() -> new Long2LongOpenHashMap()).serialize(CODEC, map -> !map.isEmpty()).build());

    private WoodVinegar() {}

    /** Whether the ground at {@code pos} still smells of the vinegar. Never loads a chunk to find out. */
    public static boolean isTreated(LevelReader reader, BlockPos pos) {
        if (!(reader instanceof Level level) || level.isClientSide() || !level.isLoaded(pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        if (!chunk.hasData(TREATED)) {
            return false;
        }
        return chunk.getData(TREATED).get(pos.asLong()) > level.getGameTime();
    }

    /** Ground the vinegar takes to: farmland, and nourished soil once it is tilled. */
    public static boolean takes(BlockState state) {
        return state.is(Blocks.FARMLAND) || state.is(ModBlocks.FERTILE_SOIL.get())
                && state.getValue(FertileSoilBlock.TILLED);
    }

    /**
     * Sprinkles a bottle's worth round {@code center}: every square of farmland in the 5x5 square about it, at its
     * height or a step up or down, smells of it for three days from now. Returns how many squares took it.
     */
    public static int sprinkle(ServerLevel level, BlockPos center, Predicate<BlockPos> may) {
        long until = level.getGameTime() + LASTS;
        int treated = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-REACH, -1, -REACH), center.offset(REACH, 1, REACH))) {
            if (!level.isLoaded(p) || !takes(level.getBlockState(p)) || !may.test(p)) {
                continue;
            }
            LevelChunk chunk = level.getChunkAt(p);
            Long2LongOpenHashMap map = chunk.getData(TREATED);
            map.long2LongEntrySet().removeIf(e -> e.getLongValue() <= level.getGameTime()); // what has worn off
            map.put(p.asLong(), until);
            chunk.setData(TREATED, map);
            chunk.markUnsaved();
            treated++;
            level.sendParticles(new DustParticleOptions(0x6E4A2A, 1.0F), p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5,
                    4, 0.3, 0.02, 0.3, 0.0);
        }
        return treated;
    }
}
