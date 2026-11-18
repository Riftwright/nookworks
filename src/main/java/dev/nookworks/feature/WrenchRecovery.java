package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Wrench's Recover mode (the user, 2026-11-07): a click on a machine or fitting in {@link #PICKUP} takes it up at
 * once, into the inventory, no pickaxe needed.
 * <p>
 * It is broken as a player breaks a block, step for step as {@code ServerPlayerGameMode.destroyBlock} does: spawn
 * protection asked, the break event posted (claim mods say no there; adventure mode and a game master's block are
 * refused in it as vanilla refuses them), the block's own last words ({@code playerWillDestroy}: crumbs and sound,
 * angry piglins), then the block taken away and its loot dropped as for a player with the right tool - so liquids and
 * power stay in the item, as they do when it is mined. In creative it is handed over all the same.
 * <p>
 * What it drops - its item, what was in its slots, belts let go, a torch it held up - is caught as it comes into the
 * world near the block, and goes into the inventory instead; what does not fit is left at the player's feet. Experience
 * a machine lets out comes as orbs, as when it is mined.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class WrenchRecovery {
    /** What the Wrench's Recover mode takes up. Other mods may add their machines. */
    public static final TagKey<Block> PICKUP = TagKey.create(Registries.BLOCK, Nookworks.id("wrench_pickup"));

    /** How far from the block's middle a dropped item is still the block's (a torch on top of it, a belt let go). */
    private static final double CATCH = 2.0;

    private record Capture(ServerLevel level, Vec3 centre, List<ItemStack> items) {}

    /** Server thread only: the items now being caught, or null. */
    private static @Nullable Capture capture;

    private WrenchRecovery() {}

    /**
     * Takes the block at {@code pos} up for {@code player} with the Wrench in {@code hand}. FAIL when it may not be
     * broken there; SUCCESS once it is taken (or, on the client, at once: the server decides).
     */
    public static InteractionResult recover(ServerLevel level, BlockPos pos, ServerPlayer player, InteractionHand hand) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(PICKUP) || !level.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (CommonHooks.fireBlockBreak(level, mode, player, pos, state).isCanceled()) {
            return InteractionResult.FAIL;
        }
        Block block = state.getBlock();
        if (block instanceof GameMasterBlock && !player.canUseGameMasterBlocks() || player.blockActionRestricted(level, pos, mode)) {
            return InteractionResult.FAIL; // as destroyBlock asks again after the event
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack tool = toolFor(state);
        List<ItemStack> caught = new ArrayList<>();
        Capture was = capture;
        capture = new Capture(level, Vec3.atCenterOf(pos), caught);
        boolean removed = false;
        try {
            BlockState adjusted = block.playerWillDestroy(level, pos, state, player);
            // its crumbs and sound go to everyone else from playerWillDestroy, the breaker being left out as one whose
            // own client shows them as it mines; this one did not mine, so is sent them
            player.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state), false));
            // (1.21.1's hook is told no tool: a block that would read it reads the player's main hand)
            removed = adjusted.onDestroyedByPlayer(level, pos, player, true, level.getFluidState(pos));
            if (removed) {
                adjusted.getBlock().destroy(level, pos, adjusted);
                if (player.getAbilities().instabuild) {
                    Block.dropResources(adjusted, level, pos, blockEntity, player, tool); // creative: handed over all the same
                } else {
                    adjusted.getBlock().playerDestroy(level, player, pos, adjusted, blockEntity, tool);
                }
            }
        } finally {
            capture = was;
        }
        if (removed) {
            player.getItemInHand(hand).hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand)); // nothing taken, no wear (r1 review)
        }
        net.minecraft.world.item.Item self = block.asItem();
        caught.sort(Comparator.comparing(stack -> !stack.is(self))); // the block's own item first, then what it held
        for (ItemStack stack : caught) {
            give(player, stack);
        }
        return InteractionResult.SUCCESS;
    }

    /** Into the inventory, or what does not fit at the player's feet. */
    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            ItemEntity left = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            left.setNoPickUpDelay();
            left.setDeltaMovement(Vec3.ZERO);
            player.level().addFreshEntity(left);
        }
    }

    /** A tool right for the block, for its loot: what it is mined with, a pickaxe for anything else. */
    private static ItemStack toolFor(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return new ItemStack(Items.NETHERITE_AXE);
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return new ItemStack(Items.NETHERITE_SHOVEL);
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return new ItemStack(Items.NETHERITE_HOE);
        }
        return new ItemStack(Items.NETHERITE_PICKAXE);
    }

    /** An item coming into the world near the block being taken up is caught, for the inventory. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onJoin(EntityJoinLevelEvent event) {
        Capture now = capture;
        if (now == null || event.loadedFromDisk() || event.getLevel() != now.level() || !(event.getEntity() instanceof ItemEntity item)
                || item.position().distanceToSqr(now.centre()) > CATCH * CATCH) {
            return;
        }
        now.items().add(item.getItem().copy());
        event.setCanceled(true);
    }
}
