package dev.nookworks.poster;

import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Frames round pictures on walls (the user, 2026-11-13: 「額縁は、バニラの額縁を使う？」, then 「ダークオークの額縁、真鍮の額縁も
 * 試作してみて。もちろんポスターだけではなく、普通の額縁としても使えるようにしてみて」, and 「バニラの絵画にも額縁付けられるように
 * してほしい」). An item frame used on a poster or a vanilla painting on a wall frames it: vanilla's own (birch, as the
 * frame it hangs as), the glowing one (the picture then shows in the dark, as a glowing frame's item does), and ours of
 * dark oak and brass and their glowing forms, which hang as item frames of their own too ({@link NookItemFrame}). The
 * brass frame's rim is a moulding, thicker and with a boss at each corner (the user, 2026-11-13: 「真鍮は、もう少し額縁を
 * 厚く、存在感ある感じに」). One frame frames a picture of any size (「額縁の消費量は1枚に対して1個。ポスターや写真のサイズは
 * 無視でいい」).
 * <p>
 * The rim is drawn inside the picture's own edge, over its outermost pixel as a real frame's rebate covers the edge of a
 * print, standing out from what it lies on (the wall under a poster, a painting's board) as an item frame's rim does: the
 * picture takes no more room, so one hung snug between posts or beside another still fits. A poster keeps its frame
 * itself ({@link PosterEntity#frame}), a painting in an attachment ({@link PaintingFrames}).
 */
public final class PictureFrames {
    /** The items that frame a picture: {@code #nookworks:poster_frames}. */
    public static final TagKey<Item> FRAMES = TagKey.create(Registries.ITEM, Nookworks.id("poster_frames"));

    /**
     * How a frame looks round a picture: the block texture its rim is cut from (under {@code block/}), whether the
     * picture glows, and whether the rim is the brass moulding (two sixteenths wide and deep, a boss at each corner, from
     * a texture laid out for it) rather than a plain rim a sixteenth wide.
     */
    public record Look(ResourceLocation rim, boolean glow, boolean moulded) {}

    private static final ResourceLocation BIRCH_PLANKS = ResourceLocation.withDefaultNamespace("birch_planks");
    private static final ResourceLocation DARK_OAK_PLANKS = ResourceLocation.withDefaultNamespace("dark_oak_planks");
    private static final ResourceLocation MOULDING = Nookworks.id("brass_moulding");
    private static final Look BIRCH = new Look(BIRCH_PLANKS, false, false);
    private static final Map<ResourceLocation, Look> LOOKS = Map.of(
            ResourceLocation.withDefaultNamespace("item_frame"), BIRCH,
            ResourceLocation.withDefaultNamespace("glow_item_frame"), new Look(BIRCH_PLANKS, true, false),
            Nookworks.id("dark_oak_item_frame"), new Look(DARK_OAK_PLANKS, false, false),
            Nookworks.id("dark_oak_glow_item_frame"), new Look(DARK_OAK_PLANKS, true, false),
            Nookworks.id("brass_item_frame"), new Look(MOULDING, false, true),
            Nookworks.id("brass_glow_item_frame"), new Look(MOULDING, true, true));

    private PictureFrames() {}

    /** Whether {@code stack} frames a picture. */
    public static boolean frames(ItemStack stack) {
        return !stack.isEmpty() && stack.is(FRAMES);
    }

    /** How {@code frame} looks round a picture; vanilla's birch for a frame of another mod's put in the tag. */
    public static Look look(ItemStack frame) {
        return LOOKS.getOrDefault(BuiltInRegistries.ITEM.getKey(frame.getItem()), BIRCH);
    }

    /**
     * A click with {@code hand} on {@code picture}, framed now with {@code current} (empty, none): an item frame frames
     * it, one frame whatever its size, and sneaking with an empty hand takes the frame off, back to the hand. Both sides
     * answer alike, so the client swings the arm as the server takes the frame; only the server changes anything,
     * through {@code set}. Null: the click has nothing to do with a frame - nor for a player who may not build here (in
     * adventure mode, a map's framed pictures stay as their maker left them).
     */
    public static @Nullable InteractionResult use(Entity picture, Player player, InteractionHand hand, ItemStack current,
            Consumer<ItemStack> set) {
        if (!player.mayBuild()) {
            return null;
        }
        ItemStack held = player.getItemInHand(hand);
        if (frames(held) && current.isEmpty()) {
            if (!picture.level().isClientSide()) {
                set.accept(held.copyWithCount(1));
                held.consume(1, player);
                picture.playSound(SoundEvents.ITEM_FRAME_PLACE, 1.0F, 1.0F);
                picture.gameEvent(GameEvent.BLOCK_CHANGE, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (held.isEmpty() && player.isSecondaryUseActive() && !current.isEmpty()) {
            if (!picture.level().isClientSide()) {
                ItemStack frame = current.copy();
                set.accept(ItemStack.EMPTY);
                if (!player.hasInfiniteMaterials() && !player.getInventory().add(frame)) {
                    player.drop(frame, false);
                }
                picture.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F, 1.0F);
                picture.gameEvent(GameEvent.BLOCK_CHANGE, player);
            }
            return InteractionResult.SUCCESS;
        }
        return null;
    }
}
