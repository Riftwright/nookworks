package dev.nookworks.travel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Where the Dock-a-Daemon Doors stand: at most one in each dimension, and which of them is the home door - the one with
 * the panel, that opens onto wherever its player chose. The others are return doors, each leading back home. Kept for
 * the whole world (the server's data, not a dimension's), by each door's lower half; and which dimensions have ever had
 * a door, as a trip sets one down free only where none has been ({@link DaemonTravel}).
 */
public final class DaemonDoors extends SavedData {
    private static final Codec<ResourceKey<Level>> DIMENSION = ResourceKey.codec(Registries.DIMENSION);
    public static final Codec<DaemonDoors> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(DIMENSION, BlockPos.CODEC).optionalFieldOf("doors", Map.of()).forGetter(d -> d.doors),
            DIMENSION.optionalFieldOf("home").forGetter(d -> Optional.ofNullable(d.home)),
            DIMENSION.listOf().optionalFieldOf("furnished").forGetter(d -> Optional.of(List.copyOf(d.furnished)))).apply(i, DaemonDoors::new));
    public static final SavedDataType<DaemonDoors> TYPE = new SavedDataType<>(Nookworks.id("daemon_doors"), DaemonDoors::new, CODEC);

    private final Map<ResourceKey<Level>, BlockPos> doors;
    private @Nullable ResourceKey<Level> home;
    /** The dimensions a door has ever stood in. */
    private final Set<ResourceKey<Level>> furnished;

    public DaemonDoors() {
        this(Map.of(), Optional.empty(), Optional.empty());
    }

    private DaemonDoors(Map<ResourceKey<Level>, BlockPos> doors, Optional<ResourceKey<Level>> home, Optional<List<ResourceKey<Level>>> furnished) {
        this.doors = new HashMap<>(doors);
        this.home = home.filter(this.doors::containsKey).orElse(null);
        // a world from before this was kept: the dimensions with a door now
        this.furnished = new HashSet<>(furnished.orElseGet(() -> List.copyOf(this.doors.keySet())));
    }

    public static DaemonDoors get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** The door in {@code dimension} as recorded (its lower half), or null. */
    public @Nullable BlockPos doorIn(ResourceKey<Level> dimension) {
        return this.doors.get(dimension);
    }

    /** The home door, or null while there is none. */
    public @Nullable GlobalPos home() {
        return this.home == null ? null : GlobalPos.of(this.home, this.doors.get(this.home));
    }

    public boolean isHome(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.equals(this.home) && pos.equals(this.doors.get(dimension));
    }

    /**
     * The door already standing in {@code level}'s dimension, other than at {@code pos}: what refuses a second one there.
     * One recorded where its chunk is loaded and no door stands any more is forgotten; one in an unloaded chunk is taken
     * at its word.
     */
    public @Nullable BlockPos otherThan(ServerLevel level, BlockPos pos) {
        BlockPos there = this.doors.get(level.dimension());
        if (there == null || there.equals(pos)) {
            return null;
        }
        if (level.isLoaded(there) && !standsAt(level, there)) {
            this.forget(level.dimension(), there);
            return null;
        }
        return there;
    }

    /** Whether a Dock-a-Daemon Door's lower half stands at {@code pos}. */
    public static boolean standsAt(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof DaemonDoorBlock && state.getValue(DaemonDoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** A door set down at {@code pos}: the home door if there is none, else its dimension's return door. */
    public void record(ResourceKey<Level> dimension, BlockPos pos) {
        this.doors.put(dimension, pos.immutable());
        this.furnished.add(dimension);
        if (this.home == null) {
            this.home = dimension;
        }
        this.setDirty();
    }

    /** The door at {@code pos} gone. With the home door gone, the world has none until one is set down again. */
    public void forget(ResourceKey<Level> dimension, BlockPos pos) {
        if (pos.equals(this.doors.get(dimension))) {
            this.doors.remove(dimension);
            if (dimension.equals(this.home)) {
                this.home = null;
            }
            this.setDirty();
        }
    }

    /** Whether a door has ever stood in {@code dimension}: a trip sets one down free only where none has. */
    public boolean hadDoor(ResourceKey<Level> dimension) {
        return this.furnished.contains(dimension);
    }

    /** {@code dimension} as though no door had ever stood there: the If's, when it is reset, its chunks deleted. */
    public void forgetDimension(ResourceKey<Level> dimension) {
        BlockPos there = this.doors.get(dimension);
        if (there != null) {
            this.forget(dimension, there);
        }
        this.furnished.remove(dimension);
        this.setDirty();
    }

    /** Every door, for tests and for refreshing how loaded doors are drawn when the home door changes. */
    public Map<ResourceKey<Level>, BlockPos> all() {
        return Map.copyOf(this.doors);
    }
}
