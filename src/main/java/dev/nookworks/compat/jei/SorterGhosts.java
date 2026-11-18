package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.NookworksNetwork;
import dev.nookworks.client.gui.SorterScreen;
import dev.nookworks.sorter.SorterMenu;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * An item dragged from JEI's list onto a sorter's filter slot or its Keep Out row registers it there, and onto the tag
 * picker's sample slot while it is open makes it the sample - as a click there with the item in hand would (the user,
 * 2026-11-10). The client's menu takes it at once, and the server from {@link NookworksNetwork.SetGhost}.
 */
final class SorterGhosts implements IGhostIngredientHandler<SorterScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(SorterScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (!(ingredient.getIngredient() instanceof ItemStack) || player == null || player.isSpectator()) {
            return targets; // a spectator changes nothing in a menu: no slot lights up to take it
        }
        SorterMenu menu = screen.getMenu();
        for (Slot slot : menu.slots) {
            int index = slot.index;
            if (!menu.takesGhost(index) || !slot.isActive()) {
                continue;
            }
            Rect2i area = new Rect2i(screen.left() + slot.x, screen.top() + slot.y, 16, 16);
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I dropped) {
                    if (dropped instanceof ItemStack stack && !stack.isEmpty() && menu.setGhost(index, stack)) {
                        PacketDistributor.sendToServer(new NookworksNetwork.SetGhost(menu.containerId, index, stack.copyWithCount(1)));
                    }
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {}
}
