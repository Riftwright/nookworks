package dev.nookworks.rope;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.block.Clicks;
import dev.nookworks.feature.Permission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Hanging things up: something that hangs ({@link HungItemEntity#hangs}) used on a rope strung between posts goes on
 * the rope where the player is looking - a rope has no block to click, so the look is followed to it, on both sides
 * alike, before anything else is done with the click - and used on a Thin Rope let down from a ceiling, it goes on its
 * end (see {@code ThinRopeBlock}). A banner hangs so too, by its bar ({@link HungBannerEntity}). A Thin Chain strung
 * between posts carries all the same, by the same rules.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class RopeHanging {
    /**
     * Hung things keep this far apart along a rope, and from the posts; a banner, about its width more
     * ({@link HungItemEntity#room}).
     */
    public static final double SPACING = 0.4;

    private RopeHanging() {}

    @SubscribeEvent
    static void clickAir(PlayerInteractEvent.RightClickItem event) {
        InteractionResult result = onSpan(event.getLevel(), event.getEntity(), event.getItemStack());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    @SubscribeEvent
    static void clickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = onSpan(event.getLevel(), event.getEntity(), event.getItemStack());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /** Hangs one of {@code stack} on the rope the player is looking at. Null when they are not looking at one. */
    public static @Nullable InteractionResult onSpan(Level level, Player player, ItemStack stack) {
        if (stack.isEmpty() || !HungItemEntity.hangs(stack) || !player.mayBuild()) {
            return null;
        }
        RopeSpans.Pick pick = RopeSpans.pick(level, player);
        if (pick == null) {
            return null;
        }
        BlockPos from = pick.knot().getPos();
        if (tooNear(level, from, pick.to(), pick.t(), pick.point(), HungItemEntity.room(stack), HungItemEntity.isBanner(stack))) {
            return InteractionResult.FAIL; // too near the post or the next thing: the click is spent, not eaten or thrown
        }
        // where it would hang, as it would hang there: what is in the way is looked for in its own shape
        HungItemEntity hung = HungItemEntity.onSpan(level, from, pick.to(), pick.t(), stack, player.position());
        String misfit = hung.misfit(level);
        if (misfit != null) {
            if (!level.isClientSide()) { // a rope low over the ground or a table: it would sink into it (said of a chain as one)
                player.sendOverlayMessage(Component.translatable(pick.knot().material(pick.to()).message(misfit)).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            // a click in the air skips vanilla's own check of the land, which only looks at a block clicked
            if (!Permission.mayPlace(server, BlockPos.containing(pick.point()), Direction.UP, player)) {
                return InteractionResult.FAIL;
            }
            server.addFreshEntity(hung);
            stack.consume(1, player);
            level.playSound(null, BlockPos.containing(pick.point()), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Whether a thing keeping {@code room} each way along the rope from {@code from} to {@code to}, put {@code t} of
     * the way along at {@code point}, would be too near a post or a thing on it: each keeps its own room, and a post as
     * much as a bottle. Where a banner is one of the two, the room is counted across the ground, as its width is.
     */
    public static boolean tooNear(Level level, BlockPos from, BlockPos to, double t, Vec3 point, double room, boolean banner) {
        Vec3 a = RopeSpans.tie(level, from);
        Vec3 b = RopeSpans.tie(level, to);
        double length = a.distanceTo(b);
        double run = Math.hypot(b.x - a.x, b.z - a.z);
        double own = banner ? run : length;
        if (t * own < room + HungItemEntity.ROOM || (1 - t) * own < room + HungItemEntity.ROOM) {
            return true;
        }
        return level.getEntitiesOfClass(HungItemEntity.class, new AABB(point, point).inflate(2 * HungBannerEntity.ROOM + 0.5)).stream()
                .anyMatch(o -> from.equals(o.spanFrom()) && to.equals(o.spanTo())
                        && Math.abs(o.along() - t) * (banner || o instanceof HungBannerEntity ? run : length) < room + o.room());
    }

    /** Hangs one of {@code stack} from the bottom end {@code ropeEnd} of a Thin Rope, if nothing hangs there yet. */
    public static InteractionResult underRope(Level level, Player player, BlockPos ropeEnd, ItemStack stack) {
        AABB below = new AABB(ropeEnd.below());
        boolean taken = !level.getEntitiesOfClass(HungItemEntity.class, below).isEmpty();
        // facing whoever hung it; a banner shows them its face
        HungItemEntity hung = HungItemEntity.underRope(level, ropeEnd, stack, HungItemEntity.facing(stack, player.getYRot()));
        String misfit = taken ? "message.nookworks.rope.end_taken" : hung.misfit(level);
        if (misfit != null) {
            if (!level.isClientSide()) {
                // something hanging there, or a block: each said as it is
                player.sendOverlayMessage(Component.translatable(misfit).withStyle(ChatFormatting.RED));
            }
            return Clicks.REFUSED;
        }
        if (level instanceof ServerLevel server) {
            if (!Permission.mayPlace(server, ropeEnd.below(), Direction.UP, player)) {
                return Clicks.REFUSED;
            }
            server.addFreshEntity(hung);
            stack.consume(1, player);
            level.playSound(null, ropeEnd, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }
}
