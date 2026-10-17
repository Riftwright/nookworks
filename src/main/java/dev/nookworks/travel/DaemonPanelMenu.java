package dev.nookworks.travel;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Destination Panel's screen: no slots, just the dimensions the player may choose (sent as it opens). Button 0 sets
 * the door off - a door like any other - and button {@code i + 1} picks the {@code i}th place. The choice is kept in
 * the player's {@link TravelLog}, which is synced back.
 */
public class DaemonPanelMenu extends AbstractContainerMenu {
    /**
     * The most places a panel lists, as many as its screen is sent: the first of them (the overworld, the Nether, the End
     * and the If lead). Past it - a pack making dimensions by the hundred - the screen would not go at all, and the player
     * was disconnected.
     */
    public static final int MOST = 256;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ResourceKey<Level>>> CHOICES =
            ResourceKey.streamCodec(Registries.DIMENSION).apply(ByteBufCodecs.list(MOST)).cast();

    private final List<ResourceKey<Level>> choices;
    private final @Nullable BlockPos panel;

    public DaemonPanelMenu(int containerId, List<ResourceKey<Level>> choices, @Nullable BlockPos panel) {
        super(ModMenus.DESTINATION_PANEL.get(), containerId);
        this.choices = listed(choices);
        this.panel = panel;
    }

    public static DaemonPanelMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new DaemonPanelMenu(containerId, CHOICES.decode(data), null);
    }

    /**
     * The places sent as the screen opens: those the menu lists, so a button picks the same place on both sides. Public
     * for tests.
     */
    public static void write(RegistryFriendlyByteBuf buf, List<ResourceKey<Level>> choices) {
        CHOICES.encode(buf, listed(choices));
    }

    private static List<ResourceKey<Level>> listed(List<ResourceKey<Level>> choices) {
        return List.copyOf(choices.size() <= MOST ? choices : choices.subList(0, MOST));
    }

    public List<ResourceKey<Level>> choices() {
        return this.choices;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer server)) {
            return true;
        }
        if (id < 0 || id > this.choices.size()) {
            return false;
        }
        // synced back: the door's look and the screen follow it
        server.setData(TravelLog.TYPE, TravelLog.of(server).withDestination(id == 0 ? Optional.empty() : Optional.of(this.choices.get(id - 1))));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * Near the panel first, then the panel still there: the reach is asked first so that, gone to another dimension with
     * the screen open (a Lode Pearl landing), the same spot there is not loaded to be looked at (r15).
     */
    @Override
    public boolean stillValid(Player player) {
        return this.panel == null || player.isWithinBlockInteractionRange(this.panel, 4.0)
                && player.level().getBlockState(this.panel).getBlock() instanceof DaemonPanelBlock;
    }
}
