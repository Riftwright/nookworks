package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.port.core.ModBusEvents;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.port.transfer.DelegatingResourceHandler;
import dev.nookworks.port.transfer.ItemResource;
import dev.nookworks.port.transfer.PlayerInventoryWrapper;
import dev.nookworks.port.transfer.RangedResourceHandler;
import dev.nookworks.port.transfer.ResourceHandler;
import dev.nookworks.port.transfer.ResourceHandlerUtil;
import dev.nookworks.port.transfer.Transaction;
import dev.nookworks.port.transfer.TransactionContext;
import dev.nookworks.port.transfer.VanillaContainerWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.IModBusEvent;

/**
 * What the 1.21.1 port gives a mod built on it (Nook Technology names our stand-ins as Nookworks' own sources do):
 * the stand-in mod-bus events reach every mod's bus, and the transfer stand-ins Nookworks itself never used -
 * {@link DelegatingResourceHandler}, {@link RangedResourceHandler}, {@link ResourceHandlerUtil#insertStacking} and
 * {@link PlayerInventoryWrapper} - behave as 26.3's, against a chest and a player's inventory: amounts kept, and a
 * transaction rolled back leaving nothing changed.
 */
final class PortAddonTests {
    private PortAddonTests() {}

    static void register() {
        NookworksGameTests.test("port_addon_mod_bus_events_reach_another_mods_bus", PortAddonTests::modBus);
        NookworksGameTests.test("port_addon_delegating_handler_over_a_chest", PortAddonTests::delegating);
        NookworksGameTests.test("port_addon_insert_stacking_tops_up_then_fills", PortAddonTests::insertStacking);
        NookworksGameTests.test("port_addon_player_inventory_wrapper", PortAddonTests::playerInventory);
        NookworksGameTests.test("port_addon_player_inventory_armour_and_drops", PortAddonTests::armourAndDrops);
    }

    // ---- the mod-bus events

    /** A mod-bus event of the test's own, posted as the port posts its stand-ins. */
    public static final class Probe extends Event implements IModBusEvent {}

    private static final List<String> HEARD = new ArrayList<>();
    private static boolean listening;

    /**
     * The port's way of posting a stand-in event reaches another mod's bus as well as ours, phase by phase and mod by
     * mod in load order, as NeoForge posts its own mod-bus events (NeoForge's own container stands for the other mod:
     * a GameTest run has no second mod of ours). The stand-ins themselves are client classes, so their own posting is
     * seen on the client (the add-on's first client start).
     */
    private static void modBus(GameTestHelper helper) {
        ModContainer ours = ModList.get().getModContainerById(Nookworks.MODID).orElseThrow();
        ModContainer other = ModList.get().getModContainerById("neoforge").orElseThrow();
        IEventBus ourBus = ours.getEventBus();
        IEventBus otherBus = other.getEventBus();
        check(helper, ourBus != null && otherBus != null, "both mods have a bus");
        synchronized (HEARD) {
            if (!listening) {
                listening = true;
                otherBus.addListener(EventPriority.HIGHEST, Probe.class, e -> HEARD.add("neoforge HIGHEST"));
                otherBus.addListener(EventPriority.NORMAL, Probe.class, e -> HEARD.add("neoforge NORMAL"));
                ourBus.addListener(EventPriority.NORMAL, Probe.class, e -> HEARD.add("nookworks NORMAL"));
                ourBus.addListener(EventPriority.LOWEST, Probe.class, e -> HEARD.add("nookworks LOWEST"));
            }
            HEARD.clear();
            ModBusEvents.post(new Probe());
            List<String> normal = new ArrayList<>();
            ModList.get().forEachModInOrder(mod -> {
                if (mod == ours || mod == other) {
                    normal.add(mod.getModId() + " NORMAL");
                }
            });
            List<String> expected = new ArrayList<>();
            expected.add("neoforge HIGHEST");
            expected.addAll(normal);
            expected.add("nookworks LOWEST");
            check(helper, HEARD.equals(expected), "heard " + HEARD + ", expected " + expected);
            // the control: posted on our bus alone, as the port did before, the other mod hears nothing
            HEARD.clear();
            ourBus.post(new Probe());
            check(helper, HEARD.equals(List.of("nookworks NORMAL", "nookworks LOWEST")), "our bus alone: " + HEARD);
        }
        helper.succeed();
    }

    // ---- handlers over a chest

    private static final BlockPos CHEST = new BlockPos(1, 1, 1);

    private static ChestBlockEntity chest(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(CHEST));
        check(helper, chest != null, "a chest");
        return chest;
    }

    private static int count(net.minecraft.world.Container container, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) {
                n += container.getItem(i).getCount();
            }
        }
        return n;
    }

    /** A tank's inlet as the Tar Still writes one: takes through its parent, never gives. */
    private static final class Inlet extends DelegatingResourceHandler<ItemResource> {
        Inlet(ResourceHandler<ItemResource> delegate) {
            super(delegate);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return super.insert(resource, amount, transaction);
        }
    }

    private static void delegating(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper);
        chest.setItem(4, new ItemStack(Items.COBBLESTONE, 10));
        ResourceHandler<ItemResource> plain = VanillaContainerWrapper.of(chest);
        DelegatingResourceHandler<ItemResource> through = new DelegatingResourceHandler<>(plain);
        ItemResource cobble = ItemResource.of(Items.COBBLESTONE);
        check(helper, through.size() == 27 && through.getDelegate() == plain, "the chest's 27 slots, through the delegate");
        check(helper, through.getResource(4).equals(cobble) && through.getAmountAsLong(4) == 10, "slot 4 as the chest has it");
        check(helper, through.isValid(0, ItemResource.EMPTY), "the empty resource is valid anywhere");
        boolean refused = false;
        try {
            through.getResource(27);
        } catch (IndexOutOfBoundsException expected) {
            refused = true;
        }
        check(helper, refused, "an index past the end is refused");

        try (Transaction tx = Transaction.openRoot()) {
            check(helper, through.insert(cobble, 100, tx) == 100, "100 go in");
            check(helper, through.extract(4, cobble, 5, tx) == 5, "5 come out of slot 4");
            tx.commit();
        }
        check(helper, count(chest, Items.COBBLESTONE) == 105, "10 + 100 - 5 in the chest: " + count(chest, Items.COBBLESTONE));

        try (Transaction tx = Transaction.openRoot()) {
            through.insert(cobble, 64, tx);
            through.extract(cobble, 30, tx);
            // closed without a commit
        }
        check(helper, count(chest, Items.COBBLESTONE) == 105, "an aborted move leaves the chest as it was");

        Inlet inlet = new Inlet(plain);
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, inlet.insert(cobble, 20, tx) == 20, "the inlet takes, through its parent");
            check(helper, inlet.extract(cobble, 20, tx) == 0 && inlet.extract(4, cobble, 1, tx) == 0, "and gives nothing");
            tx.commit();
        }
        check(helper, count(chest, Items.COBBLESTONE) == 125, "125 in the chest: " + count(chest, Items.COBBLESTONE));

        RangedResourceHandler<ItemResource> lastRow = RangedResourceHandler.of(plain, 18, 27);
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, lastRow.size() == 9, "the last row, 9 slots");
            check(helper, lastRow.insert(ItemResource.of(Items.DIRT), 64 * 9 + 1, tx) == 64 * 9, "the row holds 9 stacks, no more");
            tx.commit();
        }
        boolean onlyRow = true;
        for (int i = 0; i < 27; i++) {
            onlyRow &= chest.getItem(i).is(Items.DIRT) == (i >= 18);
        }
        check(helper, onlyRow, "the dirt only in slots 18-26");
        helper.succeed();
    }

    private static void insertStacking(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper);
        chest.setItem(5, new ItemStack(Items.COBBLESTONE, 10));
        chest.setItem(9, new ItemStack(Items.COBBLESTONE, 63));
        chest.setItem(2, new ItemStack(Items.DIRT, 1));
        ResourceHandler<ItemResource> handler = VanillaContainerWrapper.of(chest);
        ItemResource cobble = ItemResource.of(Items.COBBLESTONE);

        try (Transaction tx = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.insertStacking(handler, cobble, 100, tx);
            check(helper, moved == 100, "all 100 went in: " + moved);
            // nothing committed: rolled back
        }
        check(helper, chest.getItem(5).getCount() == 10 && chest.getItem(9).getCount() == 63 && chest.getItem(0).isEmpty()
                && count(chest, Items.COBBLESTONE) == 73, "an aborted insert leaves the chest as it was");

        int moved;
        try (Transaction tx = Transaction.openRoot()) {
            moved = ResourceHandlerUtil.insertStacking(handler, cobble, 100, tx);
            tx.commit();
        }
        check(helper, moved == 100, "100 went in: " + moved);
        check(helper, chest.getItem(5).getCount() == 64 && chest.getItem(9).getCount() == 64,
                "the two stacks topped up first: " + chest.getItem(5) + ", " + chest.getItem(9));
        check(helper, chest.getItem(0).is(Items.COBBLESTONE) && chest.getItem(0).getCount() == 45 && chest.getItem(1).isEmpty(),
                "the rest (45) in the first empty slot: " + chest.getItem(0));
        check(helper, chest.getItem(2).is(Items.DIRT) && chest.getItem(2).getCount() == 1, "the dirt untouched");
        check(helper, count(chest, Items.COBBLESTONE) == 173, "73 + 100: " + count(chest, Items.COBBLESTONE));
        check(helper, ResourceHandlerUtil.insertStacking(null, cobble, 5, null) == 0, "no handler, nothing in");
        helper.succeed();
    }

    // ---- a player's inventory

    private static void playerInventory(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        inventory.selected = 3;
        inventory.setItem(3, new ItemStack(Items.STONE, 10));
        inventory.setItem(0, new ItemStack(Items.STONE, 60));
        inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.STONE, 1));
        PlayerInventoryWrapper wrapper = PlayerInventoryWrapper.of(player);
        ItemResource stone = ItemResource.of(Items.STONE);

        check(helper, PlayerInventoryWrapper.of(inventory) == wrapper, "one wrapper for the inventory");
        check(helper, wrapper.size() == 41 && wrapper.getMainSlots().size() == 36 && wrapper.getArmorSlots().size() == 4
                && wrapper.getHandSlots().size() == 2, "41 slots: 36 main, 4 armour, the offhand");
        check(helper, wrapper.getMainHandSlot().getAmountAsLong(0) == 10, "the main hand is slot 3");

        try (Transaction tx = Transaction.openRoot()) {
            check(helper, wrapper.insert(stone, 100, tx) == 100, "100 stone go in");
            // rolled back
        }
        check(helper, inventory.getItem(3).getCount() == 10 && inventory.getItem(0).getCount() == 60
                && inventory.getItem(Inventory.SLOT_OFFHAND).getCount() == 1 && inventory.getItem(1).isEmpty(),
                "an aborted insert leaves the inventory as it was");

        try (Transaction tx = Transaction.openRoot()) {
            check(helper, wrapper.insert(stone, 200, tx) == 200, "200 stone go in");
            tx.commit();
        }
        // the main hand first (10 -> 64: 54), then the offhand (1 -> 64: 63), then the main slots, stacks first
        // (slot 0: 60 -> 64: 4), then the empty slots in order (1: 64, 2: 15)
        check(helper, inventory.getItem(3).getCount() == 64 && inventory.getItem(Inventory.SLOT_OFFHAND).getCount() == 64,
                "the hands first: " + inventory.getItem(3) + ", " + inventory.getItem(Inventory.SLOT_OFFHAND));
        check(helper, inventory.getItem(0).getCount() == 64 && inventory.getItem(1).getCount() == 64
                && inventory.getItem(2).getCount() == 15 && inventory.getItem(4).isEmpty(),
                "then the main slots, topping up first: " + inventory.getItem(0) + ", " + inventory.getItem(1) + ", " + inventory.getItem(2));
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            total += inventory.getItem(i).is(Items.STONE) ? inventory.getItem(i).getCount() : 0;
        }
        check(helper, total == 271, "71 + 200 stone in all: " + total);

        // the main slots alone never reach the armour or the offhand
        inventory.clearContent();
        try (Transaction tx = Transaction.openRoot()) {
            int in = wrapper.getMainSlots().insert(ItemResource.of(Items.DIRT), 64 * 40, tx);
            check(helper, in == 64 * 36, "the main slots hold 36 stacks: " + in);
            tx.commit();
        }
        check(helper, inventory.getItem(36).isEmpty() && inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty(),
                "nothing in the armour or the offhand");

        // the player wrapper and the plain one share their journals: one transaction over both unwinds whole
        inventory.clearContent();
        inventory.setItem(7, new ItemStack(Items.STONE, 20));
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, wrapper.getSlot(7).extract(stone, 20, tx) == 20, "20 out through the player's wrapper");
            check(helper, VanillaContainerWrapper.of(inventory).insert(8, stone, 20, tx) == 20, "20 in through the plain one");
        }
        check(helper, inventory.getItem(7).getCount() == 20 && inventory.getItem(8).isEmpty(), "both undone");
        helper.succeed();
    }

    private static void armourAndDrops(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        PlayerInventoryWrapper wrapper = PlayerInventoryWrapper.of(player);

        ResourceHandler<ItemResource> head = wrapper.getArmorSlot(EquipmentSlot.HEAD);
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, head.insert(ItemResource.of(Items.STONE), 1, tx) == 0, "stone is not worn on the head");
            check(helper, head.insert(ItemResource.of(Items.DIAMOND_HELMET), 2, tx) == 1, "one helmet, no more");
            check(helper, wrapper.getArmorSlot(EquipmentSlot.FEET).insert(ItemResource.of(Items.DIAMOND_HELMET), 1, tx) == 0,
                    "a helmet is not boots");
            tx.commit();
        }
        check(helper, player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "the helmet is worn");

        ItemStack cursed = new ItemStack(Items.IRON_BOOTS);
        cursed.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.BINDING_CURSE), 1);
        player.setItemSlot(EquipmentSlot.FEET, cursed);
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, wrapper.getArmorSlot(EquipmentSlot.FEET).extract(ItemResource.of(cursed), 1, tx) == 0,
                    "Curse of Binding keeps the boots on");
            tx.commit();
        }
        check(helper, player.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "the boots still worn");

        // a full inventory: what does not fit is dropped, only once the root commits
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            inventory.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DIRT, 64));
        ItemResource gold = ItemResource.of(Items.GOLD_INGOT);
        try (Transaction tx = Transaction.openRoot()) {
            wrapper.placeItemBackInInventory(gold, 70, tx);
            // rolled back
        }
        check(helper, drops(helper, player) == 0, "an aborted placing drops nothing");
        try (Transaction tx = Transaction.openRoot()) {
            wrapper.placeItemBackInInventory(gold, 70, tx);
            check(helper, drops(helper, player) == 0, "nothing dropped before the commit");
            tx.commit();
        }
        check(helper, drops(helper, player) == 70, "70 gold dropped after it, as a player throws them: " + drops(helper, player));
        helper.succeed();
    }

    /** Gold ingots lying round the player. */
    private static int drops(GameTestHelper helper, ServerPlayer player) {
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4))) {
            if (item.getItem().is(Items.GOLD_INGOT)) {
                n += item.getItem().getCount();
            }
        }
        return n;
    }
}
