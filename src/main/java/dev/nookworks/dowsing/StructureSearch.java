package dev.nookworks.dowsing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * The dowsing search for the nearest structure of a tag, as /locate structure makes it - the grid cells of each
 * structure set the structures are placed by, ring by ring out from the start, a cell's chunk asked whether the
 * structure starts there (worked out from the world's seed and noise, as vanilla's own check does; a chunk made before is
 * read for its starts, and where the check cannot tell, the chunk is made as far as its structure starts, as /locate
 * makes it - its blocks are not) - but a slice a tick ({@link DowsingSearch}). The nearest found on the
 * first ring that has any is the answer. Only structures placed by spread (fortresses, End cities, villages, most of
 * them) are looked for; strongholds' rings are not.
 */
final class StructureSearch implements DowsingSearch.Stepper {
    /** A structure set's placement and the wanted structures it places. */
    private record Placing(RandomSpreadStructurePlacement placement, Set<Holder<Structure>> structures) {}

    private final ServerLevel level;
    private final StructureManager manager;
    private final BlockPos origin;
    private final int originX;
    private final int originZ;
    private final long seed;
    private final List<Placing> placings;
    private final int lastRing;
    private int ring;
    private int placing;
    private int cell;
    private @Nullable BlockPos best;
    private double bestDistance = Double.MAX_VALUE;

    private StructureSearch(ServerLevel level, BlockPos origin, List<Placing> placings, int radius) {
        this.level = level;
        this.manager = level.structureManager();
        this.origin = origin.immutable();
        this.originX = SectionPos.blockToSectionCoord(origin.getX());
        this.originZ = SectionPos.blockToSectionCoord(origin.getZ());
        this.seed = level.getChunkSource().getGeneratorState().getLevelSeed();
        this.placings = placings;
        // rings out to the radius for the closest-set placement; a wider one simply looks a little further
        int tightest = placings.stream().mapToInt(p -> p.placement().spacing()).min().orElse(1);
        this.lastRing = Math.max(1, radius / (tightest * 16));
    }

    /** A search for {@code wanted} from {@code origin}, or null where the world places none of them. */
    static @Nullable StructureSearch of(ServerLevel level, BlockPos origin, TagKey<Structure> wanted, int radius) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(wanted);
        if (structures.isEmpty()) {
            return null;
        }
        var state = level.getChunkSource().getGeneratorState();
        Map<RandomSpreadStructurePlacement, Set<Holder<Structure>>> byPlacement = new LinkedHashMap<>();
        for (Holder<Structure> structure : structures.get()) {
            for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
                if (placement instanceof RandomSpreadStructurePlacement spread) {
                    byPlacement.computeIfAbsent(spread, p -> new LinkedHashSet<>()).add(structure);
                }
            }
        }
        if (byPlacement.isEmpty()) {
            return null;
        }
        List<Placing> placings = new ArrayList<>();
        byPlacement.forEach((placement, set) -> placings.add(new Placing(placement, set)));
        return new StructureSearch(level, origin, placings, radius);
    }

    @Override
    public DowsingSearch.@Nullable Result step(long deadline) {
        while (this.ring <= this.lastRing) {
            int cells = this.ring == 0 ? 1 : 8 * this.ring;
            while (this.placing < this.placings.size()) {
                Placing placing = this.placings.get(this.placing);
                int spacing = placing.placement().spacing();
                while (this.cell < cells) {
                    int[] offset = perimeter(this.ring, this.cell++);
                    ChunkPos chunk = placing.placement().getPotentialStructureChunk(this.seed,
                            this.originX + spacing * offset[0], this.originZ + spacing * offset[1]);
                    BlockPos found = this.startAt(placing, chunk);
                    if (found != null) {
                        double distance = horizontalDistanceSqr(this.origin, found);
                        if (distance < this.bestDistance) {
                            this.bestDistance = distance;
                            this.best = found;
                        }
                    }
                    if (System.nanoTime() > deadline) {
                        return null;
                    }
                }
                this.cell = 0;
                this.placing++;
            }
            this.placing = 0;
            this.ring++;
            if (this.best != null) {
                return new DowsingSearch.Result(DowsingSearch.Outcome.FOUND, this.best);
            }
        }
        return new DowsingSearch.Result(DowsingSearch.Outcome.NONE, null);
    }

    /** Where a wanted structure starts in {@code chunk}, by vanilla's check (the chunk read only if it was made before). */
    private @Nullable BlockPos startAt(Placing placing, ChunkPos chunk) {
        for (Holder<Structure> structure : placing.structures()) {
            StructureCheckResult check = this.manager.checkStructurePresence(chunk, structure.value(), placing.placement(), false);
            if (check == StructureCheckResult.START_NOT_PRESENT) {
                continue;
            }
            if (check == StructureCheckResult.START_PRESENT) {
                return placing.placement().getLocatePos(chunk);
            }
            ChunkAccess made = this.level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = this.manager.getStartForStructure(structure.value(), made);
            if (start != null && start.isValid()) {
                return placing.placement().getLocatePos(start.getChunkPos());
            }
        }
        return null;
    }

    /** The {@code index}th cell round the square ring {@code ring} cells out from the centre, as x and z. */
    static int[] perimeter(int ring, int index) {
        if (ring == 0) {
            return new int[] {0, 0};
        }
        int side = 2 * ring;
        int edge = index / side;
        int along = index % side;
        return switch (edge) {
            case 0 -> new int[] {-ring + along, -ring};
            case 1 -> new int[] {ring, -ring + along};
            case 2 -> new int[] {ring - along, ring};
            default -> new int[] {-ring, ring - along};
        };
    }

    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
