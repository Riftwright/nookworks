package dev.nookworks.client;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.ventilation.Ventilation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Draws the smoke where Ventilation sends it. Once a second it looks round the player for every mouth - a Range Hood,
 * a running Ventilation Fan, the open bottom of a Flue - with something smoking under or before it, and keeps each way
 * out of it smoking (a chimney's pot, a flue's top, a duct's open end, a fan's back) while that lasts: so a roof's
 * chimney smokes as seen from the garden, not only beside the stove. Every tick a little smoke comes out of each - a
 * slow column out of a chimney, a thin stream elsewhere - and smoke is drawn off each fire and furnace of vanilla's into
 * the mouth over it (our pots, grills and machines draw theirs themselves, Ventilation.smoke).
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class VentilationClient {
    private VentilationClient() {}

    /** How far round the player mouths are looked for: chunks across, sections up and down. */
    private static final int CHUNKS = 4;
    private static final int SECTIONS = 4;
    /** How often they are looked for, and how long each way out keeps smoking after (a little longer: no gap). */
    private static final int EVERY = 20;
    private static final int KEEP = EVERY * 2;

    /** A mouth with something smoking that it draws off. */
    private record Drawing(Ventilation.Intake intake, List<BlockPos> sources) {}

    private static List<Drawing> drawing = List.of();
    private static ClientLevel drawingIn;
    private static long nextLook;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { // out of the world: let the last one go
            drawingIn = null;
            drawing = List.of();
            return;
        }
        if (mc.player == null || mc.isPaused() || !level.tickRateManager().runsNormally()) { // /tick freeze: queued, never ticked
            return;
        }
        long now = level.getGameTime();
        if (level != drawingIn || now >= nextLook || now < nextLook - EVERY) { // a new level, its time, or time set back
            drawingIn = level;
            nextLook = now + EVERY;
            drawing = look(level, mc.player.blockPosition());
            for (Drawing d : drawing) {
                for (Vec3 out : Ventilation.outlets(level, d.intake())) {
                    Ventilation.markSmoking(level, out, Ventilation.chimneyOutlet(level, out), KEEP);
                }
            }
        }
        RandomSource random = level.getRandom();
        for (Ventilation.Smoking out : Ventilation.smokingNow(level)) {
            var at = out.at();
            if (out.chimney()) {
                if (random.nextFloat() < 0.11F) {
                    level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, at.x + random.nextGaussian() * 0.06, at.y,
                            at.z + random.nextGaussian() * 0.06, 0.0, 0.06, 0.0);
                }
            } else if (random.nextFloat() < 0.3F) {
                level.addParticle(ParticleTypes.SMOKE, at.x + random.nextGaussian() * 0.08, at.y, at.z + random.nextGaussian() * 0.08, 0.0, 0.04, 0.0);
            }
        }
        BlockPos player = mc.player.blockPosition();
        for (Drawing d : drawing) {
            for (BlockPos source : d.sources()) {
                if (source.distSqr(player) < 32 * 32 && drawsItsOwn(level.getBlockState(source)) && random.nextFloat() < 0.25F) {
                    double x = source.getX() + 0.5 + random.nextGaussian() * 0.15;
                    double y = source.getY() + 0.6;
                    double z = source.getZ() + 0.5 + random.nextGaussian() * 0.15;
                    Vec3 v = Ventilation.drawnTowards(d.intake().mouth(), x, y, z);
                    level.addParticle(ParticleTypes.SMOKE, x, y, z, v.x, v.y, v.z);
                }
            }
        }
    }

    /**
     * Whether the smoke off {@code state} is drawn here: vanilla's fire and furnaces, which know nothing of mouths. A
     * campfire's own column is turned into the mouth where it is made (CampfireBlockMixin), or rises up the flue.
     */
    private static boolean drawsItsOwn(BlockState state) {
        return state.is(BlockTags.FIRE) || state.getBlock() instanceof AbstractFurnaceBlock;
    }

    /** Every mouth near {@code centre} with something smoking that it draws off. */
    private static List<Drawing> look(ClientLevel level, BlockPos centre) {
        List<Drawing> found = new ArrayList<>();
        int cx = SectionPos.blockToSectionCoord(centre.getX());
        int cz = SectionPos.blockToSectionCoord(centre.getZ());
        int cy = SectionPos.blockToSectionCoord(centre.getY());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    int sy = chunk.getSectionYFromSectionIndex(i);
                    if (Math.abs(sy - cy) > SECTIONS || section.hasOnlyAir() || !section.maybeHas(Ventilation::mayBeIntake)) {
                        continue; // most sections hold no hood, fan or flue at all: told by their palette alone
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!Ventilation.mayBeIntake(state)) {
                                    continue;
                                }
                                pos.set(chunk.getPos().getMinBlockX() + x, SectionPos.sectionToBlockCoord(sy) + y, chunk.getPos().getMinBlockZ() + z);
                                Ventilation.Intake intake = Ventilation.intakeAt(level, pos, state);
                                if (intake != null) {
                                    List<BlockPos> sources = Ventilation.drawing(level, intake);
                                    if (!sources.isEmpty()) {
                                        found.add(new Drawing(intake, sources));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
