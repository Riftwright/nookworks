package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import dev.nookworks.item.WoolShearsItem;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * A vine cut with the Fine Shears stops spreading, as a glow berry vine does: that vine grows no further, down, up or
 * sideways ({@code mixin/VineBlockMixin}). Cut the ends of a curtain and it hangs as it is. The vines beside it can still
 * spread; cut those too. Breaking the vine forgets the cut; a piston carries it along.
 * <p>
 * Only the Fine Shears do it (the user, 2026-10-06): vanilla's shears, and other mods', leave a vine as vanilla does. A
 * cut is made by hand - a dispenser holding the Fine Shears shears the sheep in front and leaves a vine be, as one holding
 * vanilla's does. Vines cut before, with any shears, keep their cut.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class TrimmedVines {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);

    private static final MapCodec<LongOpenHashSet> CODEC = Codec.LONG.listOf().fieldOf("positions")
            .xmap(LongOpenHashSet::new, set -> new ArrayList<>(set));

    /** Saved with the chunk; only the server grows vines, so the client never needs it. */
    public static final Supplier<AttachmentType<LongOpenHashSet>> TRIMMED = ATTACHMENT_TYPES.register("trimmed_vines",
            () -> AttachmentType.builder(() -> new LongOpenHashSet())
                    .serialize(CODEC, set -> !set.isEmpty())
                    .build());

    private static final PositionMarks MARKS = new PositionMarks(TRIMMED);

    private TrimmedVines() {}

    public static boolean isTrimmed(BlockGetter level, BlockPos pos) {
        return MARKS.has(level, pos);
    }

    /**
     * The Fine Shears on a vine cut it. Low, as the Mob Bottle's capture is: the cut is made in here, so a claim mod
     * listening at the usual priority is heard first. Cutting is changing the block, so neither a spectator nor a player
     * in adventure mode (shears with no word on vines) does it.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        if (!(held.getItem() instanceof WoolShearsItem) || !level.getBlockState(pos).is(Blocks.VINE)) {
            return;
        }
        if (player.isSpectator() || !player.mayUseItemAt(pos, event.getHitVec().getDirection(), held) || !level.mayInteract(player, pos)) {
            return;
        }
        if (level.isClientSide()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (!MARKS.set(level, pos, true)) {
            return; // cut already
        }
        level.playSound(null, pos, SoundEvents.GROWING_PLANT_CROP, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(event.getEntity(), GameEvent.SHEAR, pos);
        held.hurtAndBreak(1, event.getEntity(), event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide() && !Permission.asking()) {
            MARKS.set(level, event.getPos(), false);
        }
    }

    /** A vine put down where one was cut is a new vine, uncut. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof Level level && !level.isClientSide() && !Permission.asking()) {
            MARKS.set(level, event.getPos(), false);
        }
    }

    /**
     * The vine at {@code pos} is gone (it lost what it hung on), or a vine grows into {@code pos} where there was none
     * (the one cut there burned, was blown up or washed away, leaving its mark): the mark goes.
     */
    public static void forget(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            MARKS.set(level, pos, false);
        }
    }
}
