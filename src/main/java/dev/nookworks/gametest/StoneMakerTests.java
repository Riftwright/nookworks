package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.StoneMakerBlockEntity;
import dev.nookworks.menu.tab.MachineTab;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * The Stone Maker made of what is in it (the user, 2026-11-14): a lava bucket on the right, a water bucket or blue ice on
 * the left, soul soil in its Extra Slots tab for basalt, all kept; cobblestone or stone from water by its Settings tab's choice, basalt from blue ice; no obsidian. One
 * from before keeps making cobblestone. Basalt crushed is Basalt Gravel, which sifts into iron sand, flint, and now and
 * then sulfur, amethyst and cinnabar.
 */
final class StoneMakerTests {
    private StoneMakerTests() {}

    static void register() {
        NookworksGameTests.test("stone_maker_makes_stone_of_what_is_in_it", StoneMakerTests::makes);
        NookworksGameTests.test("stone_maker_from_before_its_slots_goes_on_with_its_buckets", StoneMakerTests::fromBefore);
        NookworksGameTests.test("basalt_crushes_into_basalt_gravel_which_sifts_into_volcanic_things", StoneMakerTests::basaltGravel);
        NookworksGameTests.test("stone_maker_keeps_one_of_each_however_it_comes", StoneMakerTests::oneEach);
    }

    /**
     * One blue ice and one soul soil, whether a pipe, an Item Warp or a shift-click brings them: 64 went in and sat there
     * unused (1.7 review). And a double-click gathering soul soil takes none out of the shut Extra Slots tab.
     */
    private static void oneEach(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, ModBlocks.STONE_MAKER.get());
        StoneMakerBlockEntity maker = helper.getBlockEntity(at, StoneMakerBlockEntity.class);
        var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, helper.absolutePos(at), Direction.UP);
        check(helper, handler != null, "the Stone Maker takes items from a pipe");
        ItemStack ice = net.neoforged.neoforge.transfer.item.ItemUtil.insertItemReturnRemaining(handler, new ItemStack(Items.BLUE_ICE, 64), false, null);
        ItemStack soil = net.neoforged.neoforge.transfer.item.ItemUtil.insertItemReturnRemaining(handler, new ItemStack(Items.SOUL_SOIL, 64), false, null);
        check(helper, ice.getCount() == 63 && maker.getItem(StoneMakerBlockEntity.COOLER).getCount() == 1,
                "one blue ice goes in from a pipe, the rest stays: " + ice + ", " + maker.getItem(StoneMakerBlockEntity.COOLER));
        check(helper, soil.getCount() == 63 && maker.getItem(StoneMakerBlockEntity.SOUL_SOIL).getCount() == 1,
                "one soul soil: " + soil + ", " + maker.getItem(StoneMakerBlockEntity.SOUL_SOIL));
        var player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MachineMenu menu = new MachineMenu(1, player.getInventory(), maker.layout(), maker.slots(), MachineMenu.data(maker), maker);
        check(helper, menu.getSlot(StoneMakerBlockEntity.COOLER).getMaxStackSize() == 1, "the screen's slot holds one too");
        check(helper, !menu.tabs().isOpen(MachineTab.ADDON), "the Extra Slots tab shut");
        menu.setCarried(new ItemStack(Items.SOUL_SOIL, 3));
        menu.clicked(maker.layout().slotCount(), 0, net.minecraft.world.inventory.ContainerInput.PICKUP_ALL, player);
        check(helper, maker.getItem(StoneMakerBlockEntity.SOUL_SOIL).getCount() == 1 && menu.getCarried().getCount() == 3,
                "a double-click gathering soul soil leaves the shut tab's: " + maker.getItem(StoneMakerBlockEntity.SOUL_SOIL) + ", carried " + menu.getCarried());
        helper.succeed();
    }

    /** A water bucket and a lava bucket in their slots, as a player puts them: the Stone Maker makes cobblestone. */
    static StoneMakerBlockEntity filled(StoneMakerBlockEntity maker) {
        maker.setItem(StoneMakerBlockEntity.COOLER, new ItemStack(Items.WATER_BUCKET));
        maker.setItem(StoneMakerBlockEntity.LAVA, new ItemStack(Items.LAVA_BUCKET));
        return maker;
    }

    private static void oneBlock(GameTestHelper helper, BlockPos pos, StoneMakerBlockEntity maker) {
        for (int i = 0; i < StoneMakerBlockEntity.TICKS_PER_ITEM; i++) {
            StoneMakerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), maker);
        }
    }

    /**
     * Empty, lava alone, water alone: nothing, and the status says it waits (its detail names what goes in). Water and
     * lava: cobblestone, the buckets kept. The Settings tab's button to stone: held while cobblestone waits in it, then
     * stone, at a half more power. Blue ice for the water: basalt, whatever the choice. What goes where by a hopper, and
     * what leaves.
     */
    private static void makes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.STONE_MAKER.get());
        StoneMakerBlockEntity maker = helper.getBlockEntity(pos, StoneMakerBlockEntity.class);
        maker.setRedstoneMode(dev.nookworks.machine.RedstoneMode.ALWAYS);
        maker.energy().set(StoneMakerBlockEntity.CAPACITY);

        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(StoneMakerBlockEntity.OUTPUT).isEmpty() && maker.guiStatus() == MachineStatus.IDLE, "empty: nothing, waiting");
        maker.setItem(StoneMakerBlockEntity.LAVA, new ItemStack(Items.LAVA_BUCKET));
        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(StoneMakerBlockEntity.OUTPUT).isEmpty(), "lava alone: nothing");
        maker.setItem(StoneMakerBlockEntity.LAVA, ItemStack.EMPTY);
        maker.setItem(StoneMakerBlockEntity.COOLER, new ItemStack(Items.WATER_BUCKET));
        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(StoneMakerBlockEntity.OUTPUT).isEmpty(), "water alone: nothing");

        maker.setItem(StoneMakerBlockEntity.LAVA, new ItemStack(Items.LAVA_BUCKET));
        int before = maker.energyStored();
        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(StoneMakerBlockEntity.OUTPUT).is(Items.COBBLESTONE), "water and lava: cobblestone: " + maker.getItem(0));
        check(helper, before - maker.energyStored() == StoneMakerBlockEntity.FE_PER_TICK * StoneMakerBlockEntity.TICKS_PER_ITEM,
                "at 20 FE a tick: " + (before - maker.energyStored()));
        check(helper, maker.getItem(StoneMakerBlockEntity.COOLER).is(Items.WATER_BUCKET) && maker.getItem(StoneMakerBlockEntity.LAVA).is(Items.LAVA_BUCKET),
                "the buckets kept, full");

        // the Settings tab's button
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        MachineMenu menu = new MachineMenu(1, player.getInventory(), maker.layout(), maker.slots(), MachineMenu.data(maker), maker);
        check(helper, menu.tabs().has(MachineTab.SETTINGS), "its screen has the Settings tab");
        check(helper, menu.clickMenuButton(player, MachineMenu.BUTTON_OPTION) && maker.makesStone() && maker.guiExtra() == 1,
                "the button: stone");
        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(0).getCount() == 1 && maker.guiStatus() == MachineStatus.OUTPUT_BLOCKED,
                "cobblestone waiting in it: stone waits for it to go: " + maker.guiStatus());
        maker.setItem(StoneMakerBlockEntity.OUTPUT, ItemStack.EMPTY);
        before = maker.energyStored();
        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(0).is(Items.STONE), "then stone: " + maker.getItem(0));
        check(helper, before - maker.energyStored() == StoneMakerBlockEntity.FE_PER_TICK_STONE * StoneMakerBlockEntity.TICKS_PER_ITEM,
                "stone at 30 FE a tick: " + (before - maker.energyStored()));

        maker.setItem(StoneMakerBlockEntity.OUTPUT, ItemStack.EMPTY);
        maker.setItem(StoneMakerBlockEntity.COOLER, new ItemStack(Items.BLUE_ICE));
        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(0).isEmpty() && maker.guiStatus() == MachineStatus.IDLE,
                "blue ice without soul soil: nothing yet (vanilla's basalt forms over soul soil): " + maker.getItem(0));
        // soul soil, in the Extra Slots tab: a shift-click opens the tab and puts one there, as a part goes (MenuTabs)
        menu.tabs().setOpen(null);
        // found by what it holds, not as the menu's last: an add-on's slots come after it (nookTechnology's speed gear)
        int soulAt = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).container == menu.getSlot(0).container // the machine's own, as the bay's first slot is
                    && menu.getSlot(i).getContainerSlot() == StoneMakerBlockEntity.SOUL_SOIL) {
                soulAt = i;
            }
        }
        check(helper, soulAt >= 0 && menu.tabs().isPart(new ItemStack(Items.SOUL_SOIL)), "its Extra Slots tab holds the soul soil's slot");
        player.getInventory().setItem(0, new ItemStack(Items.SOUL_SOIL, 5));
        int hotbar = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).container == player.getInventory() && menu.getSlot(i).getContainerSlot() == 0) {
                hotbar = i;
            }
        }
        menu.quickMoveStack(player, hotbar);
        check(helper, maker.getItem(StoneMakerBlockEntity.SOUL_SOIL).getCount() == 1 && player.getInventory().getItem(0).getCount() == 4
                && menu.tabs().isOpen(MachineTab.ADDON), "a shift-click: the tab opens and one soul soil goes in: "
                + maker.getItem(StoneMakerBlockEntity.SOUL_SOIL) + ", " + player.getInventory().getItem(0));
        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(0).is(Items.BASALT) && maker.getItem(StoneMakerBlockEntity.COOLER).is(Items.BLUE_ICE)
                && maker.getItem(StoneMakerBlockEntity.SOUL_SOIL).is(Items.SOUL_SOIL),
                "blue ice and soul soil: basalt, both kept: " + maker.getItem(0));
        check(helper, menu.clickMenuButton(player, MachineMenu.BUTTON_OPTION) && !maker.makesStone(), "and the button back to cobblestone");
        ItemStack soul = new ItemStack(Items.SOUL_SOIL);
        check(helper, StoneMakerBlockEntity.product(new ItemStack(Items.BLUE_ICE), new ItemStack(Items.LAVA_BUCKET), soul, false) == Items.BASALT
                && StoneMakerBlockEntity.product(new ItemStack(Items.BLUE_ICE), new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.SOUL_SAND), false) == null
                && StoneMakerBlockEntity.product(new ItemStack(Items.ICE), new ItemStack(Items.LAVA_BUCKET), soul, false) == null
                && StoneMakerBlockEntity.product(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.BUCKET), soul, false) == null
                && StoneMakerBlockEntity.product(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.LAVA_BUCKET), ItemStack.EMPTY, false) == Items.COBBLESTONE,
                "blue ice, not plain ice; soul soil, not soul sand; a full lava bucket; water needs no soul soil");

        // hoppers and pipes: one water bucket or blue ice on the left, one lava bucket on the right, nothing in the output
        StoneMakerBlockEntity fresh = new StoneMakerBlockEntity(helper.absolutePos(pos.east(2)), helper.getBlockState(pos));
        check(helper, fresh.canPlaceItem(StoneMakerBlockEntity.COOLER, new ItemStack(Items.WATER_BUCKET))
                && fresh.canPlaceItem(StoneMakerBlockEntity.COOLER, new ItemStack(Items.BLUE_ICE))
                && !fresh.canPlaceItem(StoneMakerBlockEntity.COOLER, new ItemStack(Items.LAVA_BUCKET))
                && fresh.canPlaceItem(StoneMakerBlockEntity.LAVA, new ItemStack(Items.LAVA_BUCKET))
                && !fresh.canPlaceItem(StoneMakerBlockEntity.LAVA, new ItemStack(Items.WATER_BUCKET))
                && fresh.canPlaceItem(StoneMakerBlockEntity.SOUL_SOIL, new ItemStack(Items.SOUL_SOIL))
                && !fresh.canPlaceItem(StoneMakerBlockEntity.SOUL_SOIL, new ItemStack(Items.SOUL_SAND))
                && !fresh.canPlaceItem(StoneMakerBlockEntity.OUTPUT, new ItemStack(Items.COBBLESTONE)), "what goes where");
        check(helper, !maker.canPlaceItem(StoneMakerBlockEntity.COOLER, new ItemStack(Items.BLUE_ICE)), "one blue ice, no more");
        check(helper, maker.canTakeItemThroughFace(StoneMakerBlockEntity.OUTPUT, new ItemStack(Items.BASALT), Direction.DOWN)
                && !maker.canTakeItemThroughFace(StoneMakerBlockEntity.LAVA, new ItemStack(Items.LAVA_BUCKET), Direction.DOWN)
                && !maker.canTakeItemThroughFace(StoneMakerBlockEntity.COOLER, new ItemStack(Items.BLUE_ICE), Direction.DOWN)
                && !maker.canTakeItemThroughFace(StoneMakerBlockEntity.SOUL_SOIL, new ItemStack(Items.SOUL_SOIL), Direction.DOWN),
                "only what it made leaves by a hopper");
        check(helper, maker.shiftClickSlot(new ItemStack(Items.LAVA_BUCKET)) == StoneMakerBlockEntity.LAVA
                && maker.shiftClickSlot(new ItemStack(Items.WATER_BUCKET)) == StoneMakerBlockEntity.COOLER
                && maker.shiftClickSlot(new ItemStack(Items.BLUE_ICE)) == StoneMakerBlockEntity.COOLER, "a shift-click finds its slot");
        helper.succeed();
    }

    /**
     * A Stone Maker saved before its slots (no choice saved, nothing in slots 1 and 2): loaded, it holds a water bucket
     * and a lava bucket - the two its recipe took - and makes cobblestone as it did. One saved since, empty, stays empty.
     */
    private static void fromBefore(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.STONE_MAKER.get());
        StoneMakerBlockEntity maker = helper.getBlockEntity(pos, StoneMakerBlockEntity.class);
        var registries = helper.getLevel().registryAccess();
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        maker.saveCustomOnly(saved);
        var tag = saved.buildResult();
        check(helper, tag.contains("makes_stone"), "its choice is saved");

        StoneMakerBlockEntity now = new StoneMakerBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        now.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        check(helper, now.getItem(StoneMakerBlockEntity.COOLER).isEmpty() && now.getItem(StoneMakerBlockEntity.LAVA).isEmpty(),
                "one saved since, empty, stays empty");

        tag.remove("makes_stone");
        maker.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        check(helper, maker.getItem(StoneMakerBlockEntity.COOLER).is(Items.WATER_BUCKET) && maker.getItem(StoneMakerBlockEntity.LAVA).is(Items.LAVA_BUCKET),
                "one from before is given its two buckets");
        maker.setRedstoneMode(dev.nookworks.machine.RedstoneMode.ALWAYS);
        maker.energy().set(StoneMakerBlockEntity.CAPACITY);
        oneBlock(helper, pos, maker);
        check(helper, maker.getItem(StoneMakerBlockEntity.OUTPUT).is(Items.COBBLESTONE), "and makes cobblestone as it did");
        helper.succeed();
    }

    /** Basalt and smooth basalt crush into Basalt Gravel, which the Sifter takes, and it gives its own things. */
    private static void basaltGravel(GameTestHelper helper) {
        for (Item basalt : new Item[] {Items.BASALT, Items.SMOOTH_BASALT}) {
            var crushed = CrushingRecipe.of(new ItemStack(basalt));
            check(helper, crushed != null && crushed.output().is(ModItems.BASALT_GRAVEL.get()), basalt + " crushes into Basalt Gravel");
        }
        check(helper, new ItemStack(ModItems.BASALT_GRAVEL.get()).is(SifterBlockEntity.SIFTABLE), "the Sifter takes it");
        var drops = LootSummaries.sifted(BuiltInRegistries.ITEM.getKey(ModItems.BASALT_GRAVEL.get()), false);
        Function<Item, Double> chance = item -> drops.stream().filter(d -> d.stack().is(item)).mapToDouble(LootSummaries.Drop::chance).sum();
        check(helper, chance.apply(Items.IRON_NUGGET) > 0.15 && chance.apply(Items.FLINT) > 0.1, "iron sand and flint most: " + drops);
        check(helper, chance.apply(ModItems.SULFUR_DUST.get()) > 0.03 && chance.apply(ModItems.SULFUR_DUST.get()) < 0.1,
                "sulfur now and then: " + chance.apply(ModItems.SULFUR_DUST.get()));
        check(helper, chance.apply(Items.CINNABAR) > 0 && chance.apply(Items.CINNABAR) < 0.01, "cinnabar rarely: " + chance.apply(Items.CINNABAR));
        check(helper, chance.apply(Items.AMETHYST_SHARD) > 0 && chance.apply(Items.OBSIDIAN) == 0, "amethyst, and no obsidian");
        var fine = LootSummaries.sifted(BuiltInRegistries.ITEM.getKey(ModItems.BASALT_GRAVEL.get()), true);
        double fineSulfur = fine.stream().filter(d -> d.stack().is(ModItems.SULFUR_DUST.get())).mapToDouble(LootSummaries.Drop::chance).sum();
        check(helper, fineSulfur > chance.apply(ModItems.SULFUR_DUST.get()), "the Brass Mesh keeps more of the fine sulfur");
        helper.succeed();
    }
}
