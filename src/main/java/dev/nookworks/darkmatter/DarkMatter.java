package dev.nookworks.darkmatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Where Dark Matter puts out the light. Each block darkens a flat disc round itself - the squares water would spread to
 * from it, seven out, from the layer under it to three above (see {@link #BELOW}, {@link #ABOVE}) - to light 0,
 * sky and block light alike: outdoors at noon as well as at night, whatever lamps shine in from outside. A block that
 * gives light anywhere in the disc lifts the dark from all of it (see {@link DarkMatterBlockEntity}).
 * <p>
 * The dark is not a status effect: every lookup of light in the level answers 0 there ({@code mixin/LightEngineMixin}),
 * so blocks and mobs are drawn dark, monsters spawn by day, zombies do not burn, crops do not grow. What the game
 * stores and sends is untouched, so taking the block away gives the light straight back.
 * <p>
 * The light engine asks for light constantly, on the render threads too, so what it reads is a snapshot replaced whole
 * when a disc comes or goes: nothing at all while no Dark Matter is loaded, otherwise a map from chunk column to the
 * discs that reach into it.
 * <p>
 * On the server a disc is whole at once. On the client it spreads out from the block ring by ring when the block starts
 * darkening, and draws back in when a light beats it ({@link DarkMatterBlockEntity}): each disc has a reach of its own.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class DarkMatter {
    /** How far the dark reaches, counted as water spreads: seven steps along the ground. */
    public static final int RADIUS = 7;
    /**
     * From the layer under it to three above: mobs are lit at their eyes, and an enderman's are two and a half blocks
     * up - with the dark two layers deep its body stood in the dark and its face in daylight. The layer under darkens a
     * dip in the ground as well.
     */
    public static final int BELOW = 1;
    public static final int ABOVE = 3;

    private static final Object LOCK = new Object();
    /** Each level's dark discs: packed centre to how far out the dark reaches. Under {@link #LOCK}. */
    private static final Map<Level, Long2IntOpenHashMap> CENTRES = new WeakHashMap<>();
    /** What {@link #dark} reads: level, then chunk column, then the discs that reach it. Replaced whole. */
    private static volatile Map<Object, Long2ObjectMap<Disc[]>> snapshot = Map.of();

    /** A disc as the light engine reads it. */
    private record Disc(int x, int y, int z, int reach) {}

    /** Told on the client when a disc comes or goes, to draw that part of the world again. Set by the client. */
    public static volatile BiConsumer<Level, BlockPos> redraw = (level, centre) -> {};

    private DarkMatter() {}

    /** Whether Dark Matter has put out the light at {@code pos}. Cheap, and safe from any thread. */
    public static boolean dark(@Nullable Object level, BlockPos pos) {
        Map<Object, Long2ObjectMap<Disc[]>> map = snapshot;
        if (map.isEmpty() || level == null) {
            return false;
        }
        Long2ObjectMap<Disc[]> columns = map.get(level);
        if (columns == null) {
            return false;
        }
        Disc[] discs = columns.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        if (discs == null) {
            return false;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (Disc disc : discs) {
            if (y >= disc.y - BELOW && y <= disc.y + ABOVE && Math.abs(disc.x - x) + Math.abs(disc.z - z) <= disc.reach) {
                return true;
            }
        }
        return false;
    }

    /** Whether any block in the disc round {@code centre} gives light (the Dark Matter itself gives none). */
    public static boolean lit(Level level, BlockPos centre) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -BELOW; dy <= ABOVE; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int reach = RADIUS - Math.abs(dx);
                for (int dz = -reach; dz <= reach; dz++) {
                    pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (level.isLoaded(pos) && level.getBlockState(pos).getLightEmission(level, pos) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Puts out the light round {@code centre}, all of the disc, or gives it back. */
    public static void set(Level level, BlockPos centre, boolean darkening) {
        reach(level, centre, darkening ? RADIUS : -1);
    }

    /** Puts out the light round {@code centre} as far as {@code reach} steps (0: only its own column; below 0: none). */
    public static void reach(Level level, BlockPos centre, int reach) {
        boolean changed;
        synchronized (LOCK) {
            Long2IntOpenHashMap discs = CENTRES.computeIfAbsent(level, l -> new Long2IntOpenHashMap());
            long key = centre.asLong();
            if (reach < 0) {
                changed = discs.containsKey(key);
                discs.remove(key);
            } else {
                changed = !discs.containsKey(key) || discs.get(key) != reach;
                discs.put(key, Math.min(RADIUS, reach));
            }
            if (discs.isEmpty()) {
                CENTRES.remove(level);
            }
            if (changed) {
                publish();
            }
        }
        if (changed && level.isClientSide()) {
            redraw.accept(level, centre);
        }
    }

    @SubscribeEvent
    static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            forget(level);
        }
    }

    /** A level being unloaded takes its discs with it. */
    public static void forget(Level level) {
        synchronized (LOCK) {
            if (CENTRES.remove(level) != null) {
                publish();
            }
        }
    }

    private static void publish() {
        Map<Object, Long2ObjectMap<Disc[]>> next = new IdentityHashMap<>();
        CENTRES.forEach((level, discs) -> {
            Long2ObjectOpenHashMap<List<Disc>> columns = new Long2ObjectOpenHashMap<>();
            discs.long2IntEntrySet().forEach(entry -> {
                long centre = entry.getLongKey();
                Disc disc = new Disc(BlockPos.getX(centre), BlockPos.getY(centre), BlockPos.getZ(centre), entry.getIntValue());
                for (int cx = (disc.x - disc.reach) >> 4; cx <= (disc.x + disc.reach) >> 4; cx++) {
                    for (int cz = (disc.z - disc.reach) >> 4; cz <= (disc.z + disc.reach) >> 4; cz++) {
                        columns.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(disc);
                    }
                }
            });
            Long2ObjectOpenHashMap<Disc[]> frozen = new Long2ObjectOpenHashMap<>(columns.size());
            columns.forEach((column, list) -> frozen.put((long) column, list.toArray(Disc[]::new)));
            next.put(level, frozen);
        });
        snapshot = next.isEmpty() ? Map.of() : Collections.unmodifiableMap(next);
    }
}
