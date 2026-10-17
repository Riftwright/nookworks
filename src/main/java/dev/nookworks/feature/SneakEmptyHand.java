package dev.nookworks.feature;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.HandMachineBlock;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.NukadokoBlock;
import dev.nookworks.cooking.TrayBlock;
import dev.nookworks.crop.RicePolisherBlock;
import dev.nookworks.oil.OilPressBlock;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sneaking with an empty hand is how the kitchen and the hand machines give things back: the last ingredient out of an
 * Iron Pot, everything off a Grill, a Tray or out of a Nukadoko, what a Hand Quern, Noodle Maker, Rice Polisher or Oil
 * Press has made. Vanilla skips a block's use for a sneaking player with anything in either hand - so a shield or a
 * torch held in the other hand, as everyone does, made all of these do nothing. With the main hand empty, the click
 * goes to these blocks whatever the other hand holds.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class SneakEmptyHand {
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() == InteractionHand.MAIN_HAND && player.isSecondaryUseActive() && player.getMainHandItem().isEmpty()
                && takesWithSneak(event.getLevel().getBlockState(event.getPos()).getBlock())
                && event.getUseBlock().isDefault()) { // a claim mod that said no to the block's use is not overruled
            event.setUseBlock(TriState.TRUE);
        }
    }

    /** The blocks that give something back to a sneaking empty hand. */
    public static boolean takesWithSneak(Block block) {
        return block instanceof IronPotBlock || block instanceof GrillBlock
                || block instanceof TrayBlock || block instanceof NukadokoBlock
                || block instanceof HandMachineBlock || block instanceof RicePolisherBlock
                || block instanceof OilPressBlock;
    }

    private SneakEmptyHand() {}
}
