package dev.nookworks.block;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Dice set down before 0.5.0 had no block entity, and a die is drawn from its block entity now (where it lies, how it is
 * turned): loaded so, it would be invisible until clicked. A chunk read from disk with a die of that kind in it gives the
 * die its block entity there and then - lying in the middle of its block as before, its number and facing as its state
 * has them - made by the chunk itself, not through the level (which the load must not be asked about), and before the
 * chunk is sent to anyone, so the clients get the die with it. Sections with no die in their palette are passed over at
 * once.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class DiceUpgrade {
    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        // a chunk made now (not read from disk) has no old dice in it; nor has a client's
        if (!event.isNewChunk() && !event.getLevel().isClientSide()) {
            upgrade(event.getChunk());
        }
    }

    /** Gives every bare die in {@code chunk} its block entity; where they were. */
    public static List<BlockPos> upgrade(LevelChunk chunk) {
        List<BlockPos> bare = bareDice(chunk);
        for (BlockPos pos : bare) {
            chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
        }
        if (!bare.isEmpty()) {
            chunk.markUnsaved(); // kept with it from now on
        }
        return bare;
    }

    /** The dice in {@code chunk} with no block entity, nor one waiting to be made from saved data. */
    public static List<BlockPos> bareDice(LevelChunk chunk) {
        List<BlockPos> bare = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof DiceBlock)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).getBlock() instanceof DiceBlock) {
                            BlockPos pos = chunk.getPos().getBlockAt(x, baseY + y, z);
                            if (chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == null
                                    && chunk.getBlockEntityNbt(pos) == null) {
                                bare.add(pos);
                            }
                        }
                    }
                }
            }
        }
        return bare;
    }

    private DiceUpgrade() {}
}
