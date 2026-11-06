package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerControllerExtensions;
import dev.nookworks.spawner.SpawnerControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Add-ons' pages on the Spawner Controller (nookTechnology's Mob Data Bank, 2026-11-03): an extension's slots, numbers
 * and buttons reach it through the controller's menu, the player's inventory comes with them, shift-click moves between
 * the two, and with no extension the menu has no slots, as before. A page an extension leaves out on a controller (its
 * part switched off) has no tab, slots or buttons there.
 */
final class SpawnerPagesTests {
    private SpawnerPagesTests() {}

    static void register() {
        test("the_spawner_controller_takes_addon_pages", SpawnerPagesTests::addonPages);
    }

    private static void addonPages(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 3, 1);
        helper.setBlock(at.below(), ModBlocks.SPAWNER_LINK_RECEIVER.get());
        helper.setBlock(at, ModBlocks.SPAWNER_CONTROLLER.get());
        SpawnerControllerBlockEntity controller = helper.getBlockEntity(at, SpawnerControllerBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper);
        SimpleContainer eggs = new SimpleContainer(1);
        SimpleContainerData numbers = new SimpleContainerData(2);
        numbers.set(1, 42);
        int[] pressed = {-1};
        SpawnerControllerExtensions.Extension bank = new SpawnerControllerExtensions.Extension() {
            @Override
            public Identifier id() {
                return Nookworks.id("test_bank");
            }

            @Override
            public int slotCount() {
                return 1;
            }

            @Override
            public @Nullable Container container(SpawnerControllerBlockEntity c) {
                return eggs;
            }

            @Override
            public boolean mayPlace(int index, ItemStack stack) {
                return stack.is(Items.PIG_SPAWN_EGG);
            }

            @Override
            public int dataCount() {
                return 2;
            }

            @Override
            public @Nullable ContainerData data(SpawnerControllerBlockEntity c) {
                return numbers;
            }

            @Override
            public boolean showsInventory() {
                return true;
            }

            @Override
            public void buttonPressed(SpawnerControllerMenu menu, @Nullable SpawnerControllerBlockEntity c, Player p, int button) {
                pressed[0] = button;
            }
        };
        List<SpawnerControllerExtensions.Extension> before = SpawnerControllerExtensions.all();
        try {
            SpawnerControllerExtensions.replaceForTests(List.of()); // an add-on installed (nookTechnology) has its own
            check(helper, ((SpawnerControllerMenu) controller.createMenu(1, player.getInventory(), player)).slots.isEmpty(),
                    "with no extension, no slots");
            SpawnerControllerExtensions.replaceForTests(List.of(bank));
            SpawnerControllerMenu menu = (SpawnerControllerMenu) controller.createMenu(1, player.getInventory(), player);
            SpawnerControllerMenu client = new SpawnerControllerMenu(2, player.getInventory());
            check(helper, menu.slots.size() == 1 + 36 && client.slots.size() == menu.slots.size(), "its slot and the inventory, alike on both sides");
            check(helper, menu.extensionData(bank.id()) == numbers && menu.extensionData(bank.id()).get(1) == 42, "its numbers");
            check(helper, !client.slots.getFirst().isActive(), "on the client its slot is hidden off its page");
            client.showPage(bank.id());
            check(helper, client.slots.getFirst().isActive() && client.slots.getLast().isActive(), "and shown on it, with the inventory");
            menu.clickMenuButton(player, SpawnerControllerExtensions.FIRST_BUTTON + 3);
            check(helper, pressed[0] == 3, "its button reaches it: " + pressed[0]);
            int hotbar = menu.slots.size() - 9;
            player.getInventory().setItem(0, new ItemStack(Items.PIG_SPAWN_EGG));
            menu.quickMoveStack(player, hotbar);
            check(helper, eggs.getItem(0).is(Items.PIG_SPAWN_EGG) && player.getInventory().getItem(0).isEmpty(), "shift-click puts the egg in");
            player.getInventory().setItem(0, new ItemStack(Items.DIRT));
            menu.quickMoveStack(player, hotbar);
            check(helper, player.getInventory().getItem(0).is(Items.DIRT), "and leaves out what its slot does not take");
            menu.quickMoveStack(player, 0);
            check(helper, eggs.getItem(0).isEmpty(), "and takes the egg back out");

            // r20: a second extension with no container of its own and too few numbers - what goes into its stand-in
            // comes back on closing, its numbers are as many as it says, and a shift-click fills the page shown
            SpawnerControllerExtensions.Extension bare = new SpawnerControllerExtensions.Extension() {
                @Override
                public Identifier id() {
                    return Nookworks.id("test_bare");
                }

                @Override
                public int slotCount() {
                    return 1;
                }

                @Override
                public int dataCount() {
                    return 3;
                }

                @Override
                public @Nullable ContainerData data(SpawnerControllerBlockEntity c) {
                    return new SimpleContainerData(1);
                }
            };
            SpawnerControllerExtensions.replaceForTests(List.of(bank, bare));
            SpawnerControllerMenu two = (SpawnerControllerMenu) controller.createMenu(3, player.getInventory(), player);
            check(helper, two.extensionData(bare.id()).getCount() == 3, "its numbers as many as it says");
            two.clickMenuButton(player, two.showPageButton(bare.id()));
            check(helper, bare.id().equals(two.page()), "the server is told the page shown");
            player.getInventory().setItem(0, new ItemStack(Items.PIG_SPAWN_EGG));
            two.quickMoveStack(player, two.slots.size() - 9);
            check(helper, eggs.getItem(0).isEmpty() && two.slots.get(1).getItem().is(Items.PIG_SPAWN_EGG),
                    "a shift-click fills the page shown, not the first page");
            int held = player.getInventory().countItem(Items.PIG_SPAWN_EGG);
            two.removed(player);
            // back in the inventory, or dropped at the player's feet (vanilla's clearContainer, for a player away)
            boolean back = player.getInventory().countItem(Items.PIG_SPAWN_EGG) == held + 1 || !helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(3),
                    item -> item.getItem().is(Items.PIG_SPAWN_EGG)).isEmpty();
            check(helper, back && two.slots.get(1).getItem().isEmpty(), "and what was in its stand-in comes back on closing");

            // a page left out on a controller (an add-on's part switched off): no tab, its slots shut, its buttons dead
            boolean[] on = {false};
            int[] offPressed = {-1};
            SpawnerControllerExtensions.Extension off = new SpawnerControllerExtensions.Extension() {
                @Override
                public Identifier id() {
                    return Nookworks.id("test_off");
                }

                @Override
                public int slotCount() {
                    return 1;
                }

                @Override
                public boolean shown(SpawnerControllerBlockEntity c) {
                    return on[0];
                }

                @Override
                public void buttonPressed(SpawnerControllerMenu menu, @Nullable SpawnerControllerBlockEntity c, Player p, int button) {
                    offPressed[0] = button;
                }
            };
            SpawnerControllerExtensions.replaceForTests(List.of(bank, off));
            SpawnerControllerMenu three = (SpawnerControllerMenu) controller.createMenu(4, player.getInventory(), player);
            check(helper, three.pageShown(bank.id()) && !three.pageShown(off.id()) && three.hiddenPages() == 2, "its page left out");
            check(helper, three.slots.get(0).isActive() && !three.slots.get(1).isActive(), "its slot shut, the other page's open");
            three.clickMenuButton(player, three.showPageButton(off.id()));
            check(helper, three.page() == null, "it cannot be shown");
            three.clickMenuButton(player, SpawnerControllerExtensions.FIRST_BUTTON + SpawnerControllerExtensions.BUTTONS + 1);
            check(helper, offPressed[0] == -1, "its buttons do not reach it");
            SpawnerControllerMenu threeClient = new SpawnerControllerMenu(5, player.getInventory());
            check(helper, threeClient.pageShown(off.id()), "on the client all shown until told");
            threeClient.setData(SpawnerControllerMenu.DATA_COUNT, three.hiddenPages());
            check(helper, !threeClient.pageShown(off.id()) && threeClient.pageShown(bank.id()), "and left out once told");
            on[0] = true;
            check(helper, three.pageShown(off.id()) && three.slots.get(1).isActive(), "switched back on, it is there again");
        } finally {
            SpawnerControllerExtensions.replaceForTests(before);
        }
        helper.succeed();
    }
}
