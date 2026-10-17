package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Which logs were put there by someone, as opposed to grown.
 * <p>
 * Felling a tree used to mean "every log of this kind joined to the one you cut, as long as there are leaves
 * about", so a log cabin standing against a tree came down with it. No shape rule tells a wall from a trunk
 * reliably; knowing where the logs came from does. Tree felling (axe and Laser) never takes a log that is
 * marked here, and never starts from one.
 * <p>
 * Only placements made after this was added are known, so a cabin built before then is not protected until its
 * logs are placed again.
 * <p>
 * Nobody placed the logs of a village house either - the world generated them - so a spruce tree beside a taiga
 * house would take the house's beams with it. Logs standing inside any piece of a generated structure count as
 * built for the same reason ({@link #isBuilt}). The pieces are the houses and rooms themselves, not the whole
 * area the structure spans, and not its streets, so a tree in the fields or beside the road fells as it always did.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class PlacedLogs {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);

    private static final MapCodec<LongOpenHashSet> CODEC = Codec.LONG.listOf().fieldOf("positions")
            .xmap(LongOpenHashSet::new, set -> new ArrayList<>(set));

    /** Saved with the chunk; the client never needs it. */
    public static final Supplier<AttachmentType<LongOpenHashSet>> PLACED = ATTACHMENT_TYPES.register("placed_logs",
            () -> AttachmentType.builder(() -> new LongOpenHashSet())
                    .serialize(CODEC, set -> !set.isEmpty())
                    .build());

    private static final PositionMarks MARKS = new PositionMarks(PLACED);

    /** Whether the log at {@code pos} was placed rather than grown. */
    public static boolean isPlaced(BlockGetter level, BlockPos pos) {
        return MARKS.has(level, pos);
    }

    /** Placed by someone, or part of a generated building: either way not tree, and felling leaves it alone. */
    public static boolean isBuilt(ServerLevel level, BlockPos pos) {
        return isPlaced(level, pos) || inStructure(level, pos);
    }

    /**
     * Built, for felling a tree of {@code log}: {@link #isBuilt}, except the stump of a tree grown by bone meal before
     * 0.2.0. NeoForge reports a tree that bone meal grows on the spot as blocks "placed" at the sapling's square, which
     * marked its bottom log as laid by hand, so the tree would not fell from there. A marked log with the same wood
     * grown straight up out of it, unmarked, is that stump: nobody lays a single log under a standing trunk.
     */
    public static boolean isBuiltLog(ServerLevel level, BlockPos pos, Block log) {
        if (!isBuilt(level, pos)) {
            return false;
        }
        BlockPos above = pos.above();
        return inStructure(level, pos) || !level.getBlockState(above).is(log) || isBuilt(level, above);
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos) {
        var structures = level.structureManager();
        // Most chunks have no structure at all; that is the cheap answer and the usual one.
        if (structures.getAllStructuresAt(pos).isEmpty()) {
            return false;
        }
        ChunkPos chunk = ChunkPos.containing(pos);
        for (var start : structures.startsForStructure(chunk.x(), chunk.z(), structure -> true)) {
            for (var piece : start.getPieces()) {
                if (piece.getBoundingBox().isInside(pos) && !followsTheGround(piece)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A village street is a piece too, laid to follow the ground, and its box is the whole square of the crossing
     * two blocks deep - grass corners included. A tree on one of those corners had its bottom log inside it and
     * would not fell. Pieces that follow the ground are roads and paths; buildings are placed rigid, which is
     * what gets a house's beams counted.
     */
    private static boolean followsTheGround(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece pooled
                && pooled.getElement().getProjection()
                        == StructureTemplatePool.Projection.TERRAIN_MATCHING;
    }

    public static void mark(Level level, BlockPos pos, boolean placed) {
        MARKS.set(level, pos, placed);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof Level level) || level.isClientSide() || Permission.asking()) {
            return;
        }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent several) {
            // Several blocks from one use of an item: a bed, a door - or a sapling that bone meal grew into a tree on
            // the spot, which NeoForge reports as placed at the sapling's square. None of it is a log laid by hand,
            // so it only clears the squares (it used to mark such a tree's bottom log, and the tree would not fell).
            for (var snapshot : several.getReplacedBlockSnapshots()) {
                mark(level, snapshot.getPos(), false);
            }
            return;
        }
        // Whatever goes down clears the square first; a log going down marks it.
        mark(level, event.getPos(), event.getPlacedBlock().is(BlockTags.LOGS));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide() && !Permission.asking()) {
            mark(level, event.getPos(), false);
        }
    }

    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Detonate event) {
        ExplosionAftermath.whenGone(event.getLevel(), event.getAffectedBlocks(), (level, pos) -> mark(level, pos, false));
    }

    private PlacedLogs() {}
}
