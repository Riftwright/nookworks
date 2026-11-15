package dev.nookworks.poster;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Frames round vanilla's paintings (the user, 2026-11-13: 「バニラの絵画にも額縁付けられるようにしてほしい」), as round a
 * poster ({@link PictureFrames}): an item frame used on a painting frames it, sneaking with an empty hand takes the frame
 * back, and a framed painting broken drops its frame beside the painting ({@code mixin.PaintingMixin}). The frame is kept
 * in an attachment, saved with the painting (key {@code frame}) and sent to whoever sees it; {@code FramedPaintingRenderer}
 * draws its rim on the painting's face.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class PaintingFrames {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);
    /** The frame round a painting; empty, none. Saved only when there is one. */
    public static final Supplier<AttachmentType<ItemStack>> FRAME = ATTACHMENT_TYPES.register("painting_frame",
            () -> AttachmentType.builder(() -> ItemStack.EMPTY)
                    .serialize(ItemStack.CODEC.fieldOf("frame"), frame -> !frame.isEmpty())
                    .sync(ItemStack.OPTIONAL_STREAM_CODEC)
                    .build());

    private PaintingFrames() {}

    /** The frame round {@code painting}; empty for none. */
    public static ItemStack frame(Painting painting) {
        return painting.getExistingData(FRAME).orElse(ItemStack.EMPTY);
    }

    /** Sets it (an empty stack sent on as one, so whoever sees the painting sees the frame go). */
    public static void setFrame(Painting painting, ItemStack frame) {
        painting.setData(FRAME, frame.copy());
    }

    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Painting painting && painting.isAlive()) {
            Player player = event.getEntity();
            InteractionResult framed = PictureFrames.use(painting, player, event.getHand(), frame(painting),
                    frame -> setFrame(painting, frame));
            if (framed != null) {
                event.setCancellationResult(framed);
                event.setCanceled(true);
            }
        }
    }

    /**
     * Drops a framed painting's frame as it comes down, as vanilla drops the painting: not when entity drops are off, nor
     * to a player in creative. Called from {@code Painting#dropItem}, before the painting's own drop.
     */
    public static void dropFrame(Painting painting, ServerLevel level, @Nullable Entity causedBy) {
        ItemStack frame = frame(painting);
        if (frame.isEmpty()) {
            return;
        }
        setFrame(painting, ItemStack.EMPTY);
        if (level.getGameRules().get(GameRules.ENTITY_DROPS) && !(causedBy instanceof Player player && player.hasInfiniteMaterials())) {
            painting.spawnAtLocation(level, frame);
        }
    }
}
