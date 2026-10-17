package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Fireproofing;
import dev.nookworks.registry.ModItems;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** While the player holds Fireproof Coating, coated blocks nearby give off a faint wax sparkle. */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class FireproofHints {
    private static final int RANGE = 8;
    private static int ticks;

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        // not while paused or frozen: the particles are queued and nothing ticks them, so an hour's pause kept hundreds
        // of thousands of sparkles waiting (r7 review)
        if (player == null || mc.level == null || mc.isPaused() || !mc.level.tickRateManager().runsNormally() || ++ticks % 8 != 0
                || !(player.getMainHandItem().is(ModItems.FIREPROOF_COATING.get()) || player.getOffhandItem().is(ModItems.FIREPROOF_COATING.get()))) {
            return;
        }
        BlockPos center = player.blockPosition();
        ChunkPos home = ChunkPos.containing(center);
        for (int cx = home.x() - 1; cx <= home.x() + 1; cx++) {
            for (int cz = home.z() - 1; cz <= home.z() + 1; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null || !chunk.hasData(Fireproofing.COATED)) {
                    continue;
                }
                LongOpenHashSet coated = chunk.getData(Fireproofing.COATED);
                for (long packed : coated) {
                    BlockPos pos = BlockPos.of(packed);
                    if (pos.closerThan(center, RANGE) && mc.level.getRandom().nextInt(3) == 0) {
                        mc.level.addParticle(ParticleTypes.WAX_ON,
                                pos.getX() + mc.level.getRandom().nextDouble(),
                                pos.getY() + 1.02,
                                pos.getZ() + mc.level.getRandom().nextDouble(), 0.0, 0.0, 0.0);
                    }
                }
            }
        }
    }

    private FireproofHints() {}
}
