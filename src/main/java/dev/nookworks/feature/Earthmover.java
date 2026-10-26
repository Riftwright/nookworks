package dev.nookworks.feature;

import dev.nookworks.Nookworks;
import dev.nookworks.item.Hammers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * "Earthmover" hammer enchantment (data/nookworks/enchantment/earthmover.json): the hammer also digs
 * shovel and axe blocks (dirt, sand, gravel, logs, planks...) at its pickaxe speed, and sweeps them in 3x3.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Earthmover {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, Nookworks.id("earthmover"));

    /**
     * Whether Earthmover is offered by the enchanting table and the trades: only while the hammers are a part of the game,
     * there being nothing else it goes on (a librarian sold its book with the hammers switched off). Hammers standing
     * down for another mod keep it: they still dig dirt and wood with it, a block at a time.
     */
    public static boolean offered() {
        return Features.enabled("hammers");
    }

    public static boolean has(ItemStack stack) {
        return stack.getEnchantments().keySet().stream().anyMatch(holder -> holder.is(KEY));
    }

    /** Blocks the enchantment adds on top of what the hammer mines anyway. */
    static boolean isExtraBlock(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_AXE);
    }

    /** Whether this hammer should treat the block as its own: pickaxe blocks always, dirt and wood with Earthmover. */
    static boolean hammerHandles(ItemStack tool, BlockState state) {
        return tool.isCorrectToolForDrops(state) || (has(tool) && isExtraBlock(state));
    }

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack tool = player.getMainHandItem();
        BlockState state = event.getState();
        if (!tool.is(Hammers.TAG) || tool.isCorrectToolForDrops(state) || !isExtraBlock(state) || !has(tool)) {
            return;
        }
        // The original speed is the bare-hand speed (1.0) times haste/underwater factors; scale it up to the
        // hammer's own speed plus Efficiency, as if the block were a pickaxe block.
        float hammerSpeed = tool.getDestroySpeed(Blocks.STONE.defaultBlockState());
        float efficiency = (float) player.getAttributeValue(Attributes.MINING_EFFICIENCY);
        event.setNewSpeed(event.getNewSpeed() * (hammerSpeed + efficiency));
    }

    private Earthmover() {}
}
