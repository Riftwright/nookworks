package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Lubricant from the Oil Can: a door, trapdoor, fence gate or piston given a drop of it opens, shuts and pushes
 * without a sound. Remembered by position per chunk, saved and synced like the fireproof coating (see
 * {@link PositionMarks}), so it goes when the block is broken and never carries over to a new one. The silencing
 * itself cancels the hinge sounds - opening, shutting, a piston pushing and pulling - played at a lubricated block of
 * the {@link #LUBRICATABLE} tag, on the server and on the client alike (a player hears their own door from their own
 * client). Everything else the block does still sounds: breaking it, placing it, knocking on it.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Lubrication {
    public static final TagKey<Block> LUBRICATABLE = TagKey.create(Registries.BLOCK, Nookworks.id("lubricatable"));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);

    private static final MapCodec<LongOpenHashSet> CODEC = Codec.LONG.listOf().fieldOf("positions")
            .xmap(LongOpenHashSet::new, set -> new ArrayList<>(set));

    /** Lubricated positions (BlockPos#asLong) in a chunk. Treat as immutable: replace, never mutate in place. */
    public static final Supplier<AttachmentType<LongOpenHashSet>> LUBRICATED = ATTACHMENT_TYPES.register("lubricated",
            () -> AttachmentType.builder(() -> new LongOpenHashSet())
                    .serialize(CODEC, set -> !set.isEmpty())
                    .sync(ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.collection(LongOpenHashSet::new)))
                    .build());

    private static final PositionMarks MARKS = new PositionMarks(LUBRICATED);

    public static boolean isLubricated(BlockGetter level, BlockPos pos) {
        return MARKS.has(level, pos);
    }

    /** Returns false if it already was. */
    public static boolean lubricate(Level level, BlockPos pos) {
        return MARKS.set(level, pos, true);
    }

    public static boolean clean(Level level, BlockPos pos) {
        return MARKS.set(level, pos, false);
    }

    /** The sounds oil stops: every door, trapdoor and fence gate opening and shutting, and a piston moving. */
    private static @Nullable Set<SoundEvent> hinges;

    private static Set<SoundEvent> hinges() {
        if (hinges == null) {
            Set<SoundEvent> sounds = new HashSet<>();
            BlockSetType.values().forEach(type -> {
                sounds.add(type.doorOpen());
                sounds.add(type.doorClose());
                sounds.add(type.trapdoorOpen());
                sounds.add(type.trapdoorClose());
            });
            WoodType.values().forEach(type -> {
                sounds.add(type.fenceGateOpen());
                sounds.add(type.fenceGateClose());
            });
            sounds.add(SoundEvents.PISTON_EXTEND);
            sounds.add(SoundEvents.PISTON_CONTRACT);
            hinges = sounds;
        }
        return hinges;
    }

    @SubscribeEvent
    static void onSound(PlayLevelSoundEvent.AtPosition event) {
        if (event.getSource() != SoundSource.BLOCKS || event.getSound() == null || !hinges().contains(event.getSound().value())) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getPosition());
        Level level = event.getLevel();
        if (level.isLoaded(pos) && level.getBlockState(pos).is(LUBRICATABLE) && isLubricated(level, pos)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide() && !Permission.asking()) {
            if (event instanceof BlockEvent.EntityMultiPlaceEvent several) {
                // a door's or a bed's other half too: each square it took is new
                for (var snapshot : several.getReplacedBlockSnapshots()) {
                    clean(level, snapshot.getPos());
                }
            } else {
                clean(level, event.getPos());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide() && !Permission.asking()) {
            clean(level, event.getPos());
            // A door's other half goes with it, without an event of its own: its mark must not wait for the next door.
            var state = event.getState();
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                boolean lower = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        == DoubleBlockHalf.LOWER;
                clean(level, lower ? event.getPos().above() : event.getPos().below());
            }
        }
    }

    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Detonate event) {
        ExplosionAftermath.whenGone(event.getLevel(), event.getAffectedBlocks(), Lubrication::clean);
    }

    private Lubrication() {}
}
