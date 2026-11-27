package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.inventory.GhostSlots;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.sorter.SorterTags;
import dev.nookworks.sorter.TagPicker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The tag picker shared by the Precision Sorter and add-ons' screens ({@link TagPicker}; nookTechnology's Precision
 * Sorting Tunnel, 2026-11-10: the user, 「そこがTechと食い違うと、大きくユーザー体験を損なう」), on a menu of its own with two
 * lists as the tunnel has them, and on the Precision Sorter's.
 */
final class TagPickerTests {
    private TagPickerTests() {}

    static void register() {
        test("tag_picker_lays_its_buttons_out_by_one_rule", TagPickerTests::layout);
        test("tag_picker_sets_its_sample_as_a_ghost", TagPickerTests::sample);
        test("tag_picker_registers_into_the_destination_pressed", TagPickerTests::registers);
        test("tag_picker_says_when_a_destination_is_full", TagPickerTests::full);
        test("tag_picker_ids_start_at_the_menus_first", TagPickerTests::ids);
        test("tag_picker_on_the_precision_sorter_is_the_shared_one", TagPickerTests::sorter);
        test("tag_picker_and_the_sorters_take_items_dropped_from_jei", TagPickerTests::dropped);
    }

    /** A menu as an add-on's: two lists of nine, and the picker from 200, its sample slot first. */
    private static final class TwoLists extends AbstractContainerMenu {
        final SimpleContainer right = new SimpleContainer(9);
        final SimpleContainer left = new SimpleContainer(9);
        final TagPicker picker = new TagPicker(200, List.of(
                new TagPicker.Destination(this.right, 9, () -> Component.translatable("message.test.right_full")),
                new TagPicker.Destination(this.left, 9, () -> Component.translatable("message.test.left_full"))));

        TwoLists() {
            super((MenuType<?>) null, 1);
            this.addSlot(this.picker.sampleSlot(9, 23));
        }

        @Override
        public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }
    }

    /**
     * The one rule: the destinations' buttons first, two that fit half a row sharing one, left then right, one too wide a
     * row of its own; Back last, always a row of its own. So the Precision Sorter's three rows (Register, Keep these out,
     * Back) and the tunnel's two ([left][right], Back) put Back at the bottom alike.
     */
    private static void layout(GameTestHelper helper) {
        record Case(List<Boolean> fits, List<TagPicker.Place> places) {}
        int whole = TagPicker.Place.WHOLE;
        int left = TagPicker.Place.LEFT;
        int right = TagPicker.Place.RIGHT;
        List<Case> cases = List.of(
                // the Precision Sorter: Register fits half, Keep these out does not - Register alone, then Keep Out, then Back
                new Case(List.of(true, false), List.of(new TagPicker.Place(0, whole), new TagPicker.Place(1, whole), new TagPicker.Place(2, whole))),
                // the Precision Sorting Tunnel: left and right in one row, Back under them
                new Case(List.of(true, true), List.of(new TagPicker.Place(0, left), new TagPicker.Place(0, right), new TagPicker.Place(1, whole))),
                new Case(List.of(true), List.of(new TagPicker.Place(0, whole), new TagPicker.Place(1, whole))),
                new Case(List.of(false, true, true), List.of(new TagPicker.Place(0, whole), new TagPicker.Place(1, left),
                        new TagPicker.Place(1, right), new TagPicker.Place(2, whole))),
                new Case(List.of(true, true, true), List.of(new TagPicker.Place(0, left), new TagPicker.Place(0, right),
                        new TagPicker.Place(1, whole), new TagPicker.Place(2, whole))));
        for (Case c : cases) {
            List<TagPicker.Place> got = TagPicker.layout(c.fits());
            check(helper, got.equals(c.places()), c.fits() + ": " + got);
        }
        helper.succeed();
    }

    /**
     * Only while open, its sample slot shows; a plain click sets a copy of one of what is carried (it stays in hand), an
     * empty hand clears it, and any other click does nothing; a new sample clears the ticks; closing lets the sample go.
     * Shift-clicked, an item becomes the sample while open, and nothing otherwise; offered (an item dragged from JEI),
     * the same.
     */
    private static void sample(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        TwoLists menu = new TwoLists();
        TagPicker picker = menu.picker;
        check(helper, !menu.getSlot(0).isActive() && !picker.quickMove(new ItemStack(Items.OAK_LOG)) && !picker.offerSample(new ItemStack(Items.OAK_LOG)),
                "closed: no sample slot, nothing taken");
        picker.button(player, picker.openButton());
        check(helper, picker.open() && menu.getSlot(0).isActive(), "open: its sample slot shows");
        menu.setCarried(new ItemStack(Items.OAK_LOG, 7));
        check(helper, picker.clicked(menu, 0, ContainerInput.PICKUP) && picker.sample().is(Items.OAK_LOG) && picker.sample().getCount() == 1
                && menu.getCarried().getCount() == 7, "a click sets a copy of one; the logs stay in hand");
        int logs = picker.choices().indexOf(ItemTags.LOGS);
        check(helper, logs >= 0, "an oak log offers minecraft:logs: " + picker.choices());
        picker.button(player, picker.tickButton(logs));
        picker.button(player, picker.modButton());
        check(helper, picker.chosen(logs) && picker.modChosen() && picker.anyChosen(), "a tag and the mod ticked");
        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        for (ContainerInput input : List.of(ContainerInput.QUICK_MOVE, ContainerInput.SWAP, ContainerInput.THROW, ContainerInput.PICKUP_ALL)) {
            check(helper, picker.clicked(menu, 0, input) && picker.sample().is(Items.OAK_LOG), input + ": taken, and nothing done");
        }
        check(helper, picker.clicked(menu, 0, ContainerInput.PICKUP) && picker.sample().is(Items.IRON_INGOT) && !picker.anyChosen(),
                "a new sample, its ticks cleared");
        check(helper, !picker.clicked(menu, 1, ContainerInput.PICKUP) && !picker.clicked(menu, -999, ContainerInput.PICKUP),
                "another slot's click, or one outside, is not the sample's");
        menu.setCarried(ItemStack.EMPTY);
        picker.clicked(menu, 0, ContainerInput.PICKUP);
        check(helper, picker.sample().isEmpty() && picker.sampleMod() == null && picker.choices().isEmpty(), "an empty hand clears it");
        check(helper, picker.quickMove(new ItemStack(Items.DIAMOND, 3)) && picker.sample().is(Items.DIAMOND), "shift-clicked: the sample");
        check(helper, picker.offerSample(new ItemStack(Items.EMERALD)) && picker.sample().is(Items.EMERALD), "offered: the sample");
        picker.button(player, picker.modButton());
        picker.button(player, picker.closeButton());
        check(helper, !picker.open() && picker.sample().isEmpty() && !picker.anyChosen() && !menu.getSlot(0).isActive(),
                "closed: the sample and the ticks let go");
        picker.button(player, picker.modButton());
        picker.button(player, picker.tickButton(0));
        check(helper, !picker.anyChosen(), "closed: nothing ticks");
        helper.succeed();
    }

    /**
     * Register puts what is ticked in the destination pressed: the mod entry first, then each tag in the list's order,
     * one already there skipped, and closes. Taken is a look, not a lock (nookTechnology's tunnel): a tag in the right
     * list can still be put in the left, and is shown as held by each.
     */
    private static void registers(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        TwoLists menu = new TwoLists();
        TagPicker picker = menu.picker;
        picker.button(player, picker.openButton());
        picker.offerSample(new ItemStack(Items.OAK_LOG));
        List<TagKey<Item>> choices = picker.choices();
        int logs = choices.indexOf(ItemTags.LOGS);
        int burn = choices.indexOf(ItemTags.LOGS_THAT_BURN);
        check(helper, logs >= 0 && burn >= 0, "an oak log offers logs and logs that burn: " + choices);
        picker.button(player, picker.tickButton(burn));
        picker.button(player, picker.tickButton(logs));
        picker.button(player, picker.modButton());
        picker.button(player, picker.registerButton(0));
        List<TagKey<Item>> inOrder = new ArrayList<>(List.of(ItemTags.LOGS, ItemTags.LOGS_THAT_BURN));
        inOrder.sort((a, b) -> Integer.compare(choices.indexOf(a), choices.indexOf(b)));
        check(helper, "minecraft".equals(SorterTags.modOf(menu.right.getItem(0))) && inOrder.get(0).equals(SorterTags.of(menu.right.getItem(1)))
                && inOrder.get(1).equals(SorterTags.of(menu.right.getItem(2))) && menu.right.getItem(3).isEmpty() && menu.left.isEmpty(),
                "into the right list: the mod, then the tags in the list's order: " + menu.right.getItems());
        check(helper, !picker.open() && picker.sample().isEmpty(), "and closed");
        check(helper, picker.holds(0, ItemTags.LOGS, null) && picker.holds(0, null, "minecraft") && !picker.holds(1, ItemTags.LOGS, null)
                && picker.taken(ItemTags.LOGS, null) && picker.free(0) == 6 && picker.free(1) == 9, "held by the right list, and taken");
        // the same again, into the right: nothing more; into the left: there too
        picker.button(player, picker.openButton());
        picker.offerSample(new ItemStack(Items.OAK_LOG));
        picker.button(player, picker.tickButton(logs));
        check(helper, picker.chosen(logs), "a taken line still ticks");
        picker.button(player, picker.registerButton(0));
        check(helper, picker.free(0) == 6, "one already there is skipped");
        picker.button(player, picker.openButton());
        picker.offerSample(new ItemStack(Items.OAK_LOG));
        picker.button(player, picker.tickButton(logs));
        picker.button(player, picker.registerButton(1));
        check(helper, ItemTags.LOGS.equals(SorterTags.of(menu.left.getItem(0))) && picker.holds(1, ItemTags.LOGS, null)
                && picker.holds(0, ItemTags.LOGS, null), "the left list takes it too: a look, not a lock");
        // Register while closed puts nothing anywhere
        picker.button(player, picker.registerButton(1));
        check(helper, picker.free(1) == 8, "closed: Register does nothing");
        helper.succeed();
    }

    /** A destination full: what fits goes in, the rest is left, the player is told that destination's words, and it closes. */
    private static void full(GameTestHelper helper) {
        List<Component> said = new ArrayList<>();
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, said);
        TwoLists menu = new TwoLists();
        TagPicker picker = menu.picker;
        for (int i = 0; i < 8; i++) {
            menu.left.setItem(i, new ItemStack(Items.STONE));
        }
        picker.button(player, picker.openButton());
        picker.offerSample(new ItemStack(Items.OAK_LOG));
        picker.button(player, picker.modButton());
        picker.button(player, picker.tickButton(picker.choices().indexOf(ItemTags.LOGS)));
        picker.button(player, picker.registerButton(1));
        check(helper, "minecraft".equals(SorterTags.modOf(menu.left.getItem(8))) && picker.free(1) == 0, "the mod in the last slot");
        check(helper, said.size() == 1 && said.getFirst().getContents() instanceof TranslatableContents words
                && words.getKey().equals("message.test.left_full"), "the left list's words said: " + said);
        check(helper, !picker.open() && menu.right.isEmpty(), "closed, nothing put elsewhere");
        helper.succeed();
    }

    /** Its ids are the menu's first on: below it none are its own; its controls and ticks after it, as its constants say. */
    private static void ids(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        TagPicker picker = new TwoLists().picker;
        check(helper, !picker.button(player, 0) && !picker.button(player, 199) && !picker.ids(199) && picker.ids(200), "below 200: not its own");
        check(helper, picker.openButton() == 200 + TagPicker.OPEN && picker.closeButton() == 200 + TagPicker.CLOSE
                && picker.modButton() == 200 + TagPicker.MOD && picker.registerButton(1) == 200 + TagPicker.REGISTER + 1
                && picker.tickButton(5) == 200 + TagPicker.TICKS + 5, "its buttons where its constants say");
        check(helper, picker.button(player, 200 + TagPicker.REGISTER + 5) && picker.button(player, 200 + 15), "one of its sixteen unused is still its own");
        boolean refused = false;
        try {
            new TagPicker(200, List.of());
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(helper, refused, "a picker with nowhere to put anything is refused");
        helper.succeed();
    }

    /**
     * The Precision Sorter's picker is the shared one: the filter its first destination and the Keep Out row its second,
     * its old button ids its own from 200, an Item Sorter's not pressed.
     */
    private static void sorter(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.PRECISION_SORTER.get());
        SorterBlockEntity sorter = helper.getBlockEntity(pos, SorterBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper);
        SorterMenu menu = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        TagPicker picker = menu.picker();
        check(helper, picker.destinations().size() == 2 && picker.destinations().get(0).entries() == sorter.filter()
                && picker.destinations().get(1).entries() == sorter.deny(), "the filter, then the Keep Out row");
        check(helper, SorterMenu.BUTTON_PICKER_OPEN == picker.openButton() && SorterMenu.BUTTON_REGISTER_TAGS == picker.registerButton(0)
                && SorterMenu.BUTTON_REGISTER_DENY == picker.registerButton(1) && SorterMenu.BUTTON_CHOOSE_MOD == picker.modButton()
                && SorterMenu.BUTTON_CHOOSE + 3 == picker.tickButton(3), "its ids the picker's");
        check(helper, menu.getSlot(SorterMenu.SAMPLE_SLOT) == picker.slot(), "its sample slot where it was");
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        check(helper, menu.picking() && !menu.getSlot(0).isActive() && !menu.getSlot(SorterMenu.GEAR_SLOT).isActive()
                && !menu.getSlot(SorterMenu.DENY_SLOT).isActive(), "open: the filter, gear and Keep Out row hidden");
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_CLOSE);
        helper.setBlock(pos.east(2), ModBlocks.ITEM_SORTER.get());
        SorterBlockEntity plain = helper.getBlockEntity(pos.east(2), SorterBlockEntity.class);
        SorterMenu plainMenu = (SorterMenu) plain.createMenu(2, player.getInventory(), player);
        check(helper, !plainMenu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN) && !plainMenu.picking(), "an Item Sorter's is never opened");
        helper.succeed();
    }

    /**
     * An item dragged from JEI's list (NookworksNetwork.SetGhost, through GhostSlots.dropped as the server takes it; the
     * user, 2026-11-10: 「3もつけていいです。」): onto a filter slot or the Keep Out row it registers a copy of one, as a click
     * with it would, the player given nothing; onto the sample slot while the picker is open it is the sample. Nothing
     * lands on the gear, what the sorter holds or the inventory; nor on the filter while the picker covers it, nor the
     * sample while it is shut; nor from a menu no longer the one open, nor by a spectator. The Item Sorter's filter has
     * one row.
     */
    private static void dropped(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.PRECISION_SORTER.get());
        SorterBlockEntity sorter = helper.getBlockEntity(pos, SorterBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper);
        Vec3 beside = helper.absoluteVec(new Vec3(2.5, 2, 0.5));
        player.snapTo(beside.x, beside.y, beside.z);
        player.getAbilities().flying = true; // (one falling to the void would hang the batch)
        player.onUpdateAbilities();
        SorterMenu menu = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        int id = menu.containerId;
        check(helper, GhostSlots.dropped(player, id, 0, new ItemStack(Items.DIAMOND, 5))
                && sorter.filter().getItem(0).is(Items.DIAMOND) && sorter.filter().getItem(0).getCount() == 1
                && player.getInventory().countItem(Items.DIAMOND) == 0, "onto the filter: a copy of one, the player given none");
        check(helper, GhostSlots.dropped(player, id, SorterMenu.DENY_SLOT + 2, new ItemStack(Items.COBBLESTONE))
                && sorter.deny().getItem(2).is(Items.COBBLESTONE), "onto the Keep Out row");
        check(helper, GhostSlots.dropped(player, id, 0, new ItemStack(Items.EMERALD)) && sorter.filter().getItem(0).is(Items.EMERALD),
                "onto a filter slot that holds one, it takes its place, as a click does");
        int inventory = SorterMenu.HELD_SLOT + dev.nookworks.sorter.SorterBlockEntity.INPUT_SLOTS; // past the five held
        for (int slot : new int[] {SorterMenu.GEAR_SLOT, SorterMenu.SAMPLE_SLOT, SorterMenu.HELD_SLOT, inventory, -1, menu.slots.size()}) {
            check(helper, !GhostSlots.dropped(player, id, slot, new ItemStack(Items.STICK)), "nothing lands on slot " + slot);
        }
        check(helper, sorter.gearSlot().getItem(0).isEmpty() && menu.getSlot(inventory).getItem().isEmpty() && menu.picker().sample().isEmpty(),
                "the gear, the inventory and the sample left as they were");
        check(helper, !GhostSlots.dropped(player, id + 1, 1, new ItemStack(Items.STICK)) && sorter.filter().getItem(1).isEmpty(),
                "nor from another menu than the one open");
        check(helper, !GhostSlots.dropped(player, id, 1, ItemStack.EMPTY), "nor an empty stack");
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        check(helper, !GhostSlots.dropped(player, id, 1, new ItemStack(Items.STICK)) && sorter.filter().getItem(1).isEmpty(),
                "nor from a spectator, who may look in but change nothing (r22 CXC-008)");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); // as it joined
        player.getAbilities().flying = true;
        player.onUpdateAbilities();

        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        check(helper, !GhostSlots.dropped(player, id, 1, new ItemStack(Items.STICK)) && sorter.filter().getItem(1).isEmpty(),
                "the picker open: nothing onto the filter it covers");
        check(helper, GhostSlots.dropped(player, id, SorterMenu.SAMPLE_SLOT, new ItemStack(Items.OAK_LOG, 3))
                && menu.picker().sample().is(Items.OAK_LOG) && menu.picker().sample().getCount() == 1
                && menu.picker().choices().contains(ItemTags.LOGS), "onto the sample slot: the sample, its tags offered");
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_CLOSE);

        helper.setBlock(pos.east(2), ModBlocks.ITEM_SORTER.get());
        SorterBlockEntity plain = helper.getBlockEntity(pos.east(2), SorterBlockEntity.class);
        SorterMenu plainMenu = (SorterMenu) plain.createMenu(2, player.getInventory(), player);
        player.containerMenu = plainMenu;
        check(helper, !GhostSlots.dropped(player, 2, SorterMenu.COLUMNS, new ItemStack(Items.STICK))
                && GhostSlots.dropped(player, 2, SorterMenu.COLUMNS - 1, new ItemStack(Items.STICK))
                && GhostSlots.dropped(player, 2, SorterMenu.DENY_SLOT, new ItemStack(Items.STICK)),
                "an Item Sorter: its one row of nine and its Keep Out row");

        TwoLists lists = new TwoLists();
        check(helper, !lists.picker.ghost(0, new ItemStack(Items.OAK_LOG)), "a picker's sample slot takes no drop while shut");
        lists.picker.button(player, lists.picker.openButton());
        check(helper, !lists.picker.ghost(1, new ItemStack(Items.OAK_LOG)) && lists.picker.ghost(0, new ItemStack(Items.OAK_LOG))
                && lists.picker.sample().is(Items.OAK_LOG), "and while open, on its own slot only");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }
}
