package dev.nookworks.feature;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A campfire a player sets down is a fire laid, not yet lit (the user, 2026-10-16: 焚火は置いたときは消えた状態であるのが
 * 普通では？火打ち石とうちがねで着火したら火がつく): flint and steel, a fire charge or a burning arrow lights it, as
 * vanilla lights one that was put out. The {@code campfiresPlacedUnlit} setting turns this off.
 * <p>
 * The state placed is unlit already (CampfireBlockMixin, on both sides, so nothing shows burning for a moment);
 * as a fallback for another mod's campfire that makes its own state, it is also done as the placement is reported, on both sides (the player's own game puts the block down too, before the
 * server says so, and would show it burning for a moment): the campfire just put down goes out before anything sees it
 * lit. Only what a player places - campfires in villages, or set by a command or a structure, are as they were - and
 * any block that is a vanilla campfire or another mod's built on it. A campfire item that names its lit state outright
 * (one copied with its block state in creative) keeps what it says.
 * <p>
 * With the setting on, a campfire item is that laid fire in the hand as well ({@link #unlit(ItemStack)}): its icon is
 * an unlit campfire (items/campfire.json and soul_campfire.json, through UnlitCampfireLook) and under a shader pack it
 * casts no light from the hand (IrisHandLightMixin), where it lit up the ground round the player until set down. With
 * it off, all of it is vanilla's (the user, 2026-11-01: 「設定をオフにしたときには完全にバニラの通り、オンにした時には、
 * しっかり消えた焚火としてアイコンや挙動も含めて扱える」).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class UnlitCampfires {
    private UnlitCampfires() {}

    /** Whether campfires a player sets down start unlit (the setting, once the server's config is in). */
    public static boolean active() {
        return NookworksConfig.SPEC.isLoaded() && NookworksConfig.CAMPFIRES_PLACED_UNLIT.get();
    }

    /**
     * Whether a campfire item stands for an unlit campfire: one that names its lit state says so itself; else it is unlit
     * while the setting is on. False for anything but a campfire.
     */
    public static boolean unlit(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof CampfireBlock)) {
            return false;
        }
        BlockItemStateProperties props = stack.get(DataComponents.BLOCK_STATE);
        Boolean lit = props == null ? null : props.get(CampfireBlock.LIT);
        return lit != null ? !lit : active();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (Permission.asking() || !(event.getEntity() instanceof Player player) || !(event.getLevel() instanceof Level level)
                || !NookworksConfig.SPEC.isLoaded() || !NookworksConfig.CAMPFIRES_PLACED_UNLIT.get()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        // NeoForge reports any block an item's use sets as placed: flint and steel lighting a campfire is "placed"
        // too, over the campfire that was there. Only a campfire where there was none is one set down.
        if (state.getBlock() instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)
                && !(event.getBlockSnapshot().getState().getBlock() instanceof CampfireBlock) && !litOnPurpose(player, state.getBlock())) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_CLIENTS);
        }
    }

    /** Whether the campfire in the player's hand says outright that it is lit. */
    private static boolean litOnPurpose(Player player, Block campfire) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            BlockItemStateProperties props = held.get(DataComponents.BLOCK_STATE);
            if (held.getItem() instanceof BlockItem item && item.getBlock() == campfire && props != null
                    && Boolean.TRUE.equals(props.get(CampfireBlock.LIT))) {
                return true;
            }
        }
        return false;
    }
}
