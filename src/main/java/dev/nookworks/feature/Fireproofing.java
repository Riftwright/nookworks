package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 * Fireproof coating: remembers coated block positions per chunk (saved with the chunk, synced to clients)
 * and tells the fire code they do not burn. The hooks are the mixins in {@code dev.nookworks.mixin}:
 * FireBlock burn-out, fire spread odds and fire placement, and lava ignition.
 * A coating is kept by position (see {@link PositionMarks}): it goes when the block is broken or blown up, moves
 * with the block when a piston pushes it, and never carries over to a block put down on the same square.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Fireproofing {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);

    private static final MapCodec<LongOpenHashSet> CODEC = Codec.LONG.listOf().fieldOf("positions")
            .xmap(LongOpenHashSet::new, set -> new ArrayList<>(set));

    /** Coated positions (BlockPos#asLong) in a chunk. Treat as immutable: replace, never mutate in place. */
    public static final Supplier<AttachmentType<LongOpenHashSet>> COATED = ATTACHMENT_TYPES.register("fireproof_coating",
            () -> AttachmentType.builder(() -> new LongOpenHashSet())
                    .serialize(CODEC, set -> !set.isEmpty())
                    .sync(ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.collection(LongOpenHashSet::new)))
                    .build());

    private static final PositionMarks MARKS = new PositionMarks(COATED);

    public static boolean isCoated(BlockGetter level, BlockPos pos) {
        return MARKS.has(level, pos);
    }

    /** Returns false if it was already coated. */
    public static boolean coat(Level level, BlockPos pos) {
        return MARKS.set(level, pos, true);
    }

    public static boolean uncoat(Level level, BlockPos pos) {
        return MARKS.set(level, pos, false);
    }

    /**
     * A block that is put down is new, and nothing new is coated - whatever stood on that square before and
     * however it went (burnt out, blown away, taken by a machine that never tells anyone).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide() && !Permission.asking()) {
            if (event instanceof BlockEvent.EntityMultiPlaceEvent several) {
                // a door's or a bed's other half too: each square it took is new
                for (var snapshot : several.getReplacedBlockSnapshots()) {
                    uncoat(level, snapshot.getPos());
                }
            } else {
                uncoat(level, event.getPos());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide() && !Permission.asking()) {
            uncoat(level, event.getPos());
        }
    }

    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Detonate event) {
        // Only the blocks the blast actually took: with mob griefing off they all stay, and so does their coat.
        ExplosionAftermath.whenGone(event.getLevel(), event.getAffectedBlocks(), Fireproofing::uncoat);
    }

    private Fireproofing() {}
}
