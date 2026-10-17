package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.dowsing.DowsingSearch;
import dev.nookworks.dowsing.NookDowsingRodItem;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.StoneMakerBlockEntity;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.pearl.LodestoneNames;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.spawner.SpawnerLinkSenderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * The r7 review's server and load findings: what a player on a server could flood, what two players at one block ran
 * together, what a respawn lost, and what machines did every tick for nothing.
 */
final class R7ServerPerfTests {
    private R7ServerPerfTests() {}

    static void register() {
        NookworksGameTests.test("a_lodestone_box_cannot_flood_the_server_with_names", R7ServerPerfTests::lodestoneBoxCannotFlood);
        NookworksGameTests.test("two_players_typing_at_one_printing_press_never_run_their_letters_together",
                R7ServerPerfTests::pressTypistsKeepTheirOwnLetters);
        NookworksGameTests.test("a_dowsing_rod_gets_its_needle_after_its_seeker_died_and_came_back", R7ServerPerfTests::dowsingAfterRespawn);
        NookworksGameTests.test("machines_try_a_full_container_below_again_on_a_hoppers_beat", R7ServerPerfTests::machinesWaitABeat);
        NookworksGameTests.test("a_spawner_link_sender_broken_while_lit_lets_its_lease_go", R7ServerPerfTests::senderLeaseGoes);
    }

    /**
     * A rename to the name a lodestone has already is saved nowhere and sent to nobody; and a box's renames are heard at
     * most once a quarter of a second for each player - a client sending them in a stream had each asked of claim mods
     * and sent on to every player on the server.
     */
    private static void lodestoneBoxCannotFlood(GameTestHelper helper) {
        BlockPos lodestone = new BlockPos(2, 1, 2);
        helper.setBlock(lodestone, Blocks.LODESTONE);
        BlockPos at = helper.absolutePos(lodestone);
        GlobalPos where = GlobalPos.of(helper.getLevel().dimension(), at);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
        check(helper, LodestoneNames.rename(player, at, "Kitchen"), "renamed");
        LodestoneNames names = LodestoneNames.get(helper.getLevel().getServer());
        names.setDirty(false);
        check(helper, LodestoneNames.rename(player, at, "Kitchen"), "renamed to the name it has, the box is answered as before");
        check(helper, !names.isDirty(), "but there is nothing to save, nor to send every player");
        check(helper, LodestoneNames.rename(player, at, "Larder") && names.isDirty(), "a new name is saved");

        check(helper, LodestoneNames.renameFromBox(player, at, "Cellar"), "a rename from the box is heard");
        check(helper, !LodestoneNames.renameFromBox(player, at, "Attic")
                && LodestoneNames.of(where).map(Component::getString).equals(Optional.of("Cellar")),
                "another from the same player in the same tick is not: " + LodestoneNames.of(where));
        helper.runAfterDelay(6, () -> {
            check(helper, LodestoneNames.renameFromBox(player, at, "Attic")
                    && LodestoneNames.of(where).map(Component::getString).equals(Optional.of("Attic")),
                    "a quarter of a second on, it is: " + LodestoneNames.of(where));
            helper.succeed();
        });
    }

    /**
     * Each screen sends Clear and then its whole text, a letter a button; two players typing at the same press at once
     * have their buttons arrive interleaved. Each has letters of their own: the label is the last one's, whole - not
     * both runs of letters merged into a label neither typed.
     */
    private static void pressTypistsKeepTheirOwnLetters(GameTestHelper helper) {
        BlockPos pressPos = new BlockPos(1, 2, 1);
        helper.setBlock(pressPos, ModBlocks.PRINTING_PRESS.get());
        var press = helper.getBlockEntity(pressPos, PrintingPressBlockEntity.class);
        ServerPlayer alice = MockPlayers.inLevel(helper);
        ServerPlayer bob = MockPlayers.inLevel(helper);
        var hers = (PrintingPressMenu) press.createMenu(1, alice.getInventory(), alice);
        var his = (PrintingPressMenu) press.createMenu(2, bob.getInventory(), bob);
        his.clickMenuButton(bob, PrintingPressMenu.BUTTON_TEXT_CLEAR);
        hers.clickMenuButton(alice, PrintingPressMenu.BUTTON_TEXT_CLEAR);
        String herText = "Ore";
        String hisText = "Log";
        for (int i = 0; i < herText.length(); i++) {
            his.clickMenuButton(bob, PrintingPressMenu.letterButton(hisText.charAt(i)));
            hers.clickMenuButton(alice, PrintingPressMenu.letterButton(herText.charAt(i)));
        }
        check(helper, press.labelText().equals(herText), "the last to type sets the label, whole: '" + press.labelText() + "'");
        his.clickMenuButton(bob, PrintingPressMenu.BUTTON_TEXT_CLEAR);
        for (char c : "Logs".toCharArray()) {
            his.clickMenuButton(bob, PrintingPressMenu.letterButton(c));
        }
        check(helper, press.labelText().equals("Logs"), "and then the other's, whole: '" + press.labelText() + "'");
        helper.succeed();
    }

    /**
     * A seeker who dies while the search runs (what they carried dropped) and comes back is a new player; the rod picked
     * up again, the find goes to it - its needle set, its mark gone - not to the old player's emptied inventory, with
     * only the message reaching them.
     */
    private static void dowsingAfterRespawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer before = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)).above(64)); // clear of the arenas' orbs
        before.snapTo(stand.x, stand.y, stand.z);
        ItemStack rod = new ItemStack(ModItems.NOOK_DOWSING_ROD.get());
        long search = NookDowsingRodItem.mark(rod, 42L);
        before.setItemInHand(InteractionHand.MAIN_HAND, rod);
        BlockPos from = before.blockPosition();

        before.getInventory().clearContent(); // dropped as they died, keepInventory off
        // back where they stood (as /spawnpoint sets it), not at the world's spawn among the other arenas
        before.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), BlockPos.containing(stand), 0.0F, 0.0F),
                true), false);
        ServerPlayer after = server.getPlayerList().respawn(before, false, Entity.RemovalReason.KILLED);
        AfterTest.undo(helper, () -> {
            if (server.getPlayerList().getPlayer(after.getUUID()) == after) {
                server.getPlayerList().remove(after);
            }
        });
        check(helper, after != before && after.getUUID().equals(before.getUUID()), "come back as a new player, the same one");
        after.snapTo(stand.x, stand.y, stand.z);
        after.setItemInHand(InteractionHand.MAIN_HAND, rod); // the rod picked up again

        BlockPos cinnabar = from.offset(320, -50, -96);
        // the search's answer goes to whom it was started for: the player as they were
        NookDowsingRodItem.found(level, before, search, from, new DowsingSearch.Result(DowsingSearch.Outcome.FOUND, cinnabar));
        ItemStack held = after.getMainHandItem();
        LodestoneTracker needle = held.get(DataComponents.LODESTONE_TRACKER);
        check(helper, needle != null && needle.target().equals(Optional.of(GlobalPos.of(level.dimension(), cinnabar))),
                "the rod in their hands now points at what was found: " + needle);
        check(helper, !NookDowsingRodItem.marked(held) && held.getDamageValue() == 1, "its mark gone, and worn a step");
        helper.succeed();
    }

    /**
     * A machine over a chest with no room tries once, finds none, and then waits out a hopper's beat: room made in the
     * same tick, it does not try again at once - it was a lookup, a transaction and a look through the chest for each
     * output slot, every tick, for as long as the chest stayed full. Once the beat is out, what waited goes in.
     */
    private static void machinesWaitABeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Runnable> afterTheBeat = new ArrayList<>();

        BlockPos sifterPos = new BlockPos(1, 2, 1);
        helper.setBlock(sifterPos, ModBlocks.SIFTER.get());
        var sifter = helper.getBlockEntity(sifterPos, SifterBlockEntity.class);
        afterTheBeat.add(waitsABeat(helper, sifterPos, sifter, 1, new ItemStack(Items.DIAMOND),
                () -> SifterBlockEntity.serverTick(level, helper.absolutePos(sifterPos), helper.getBlockState(sifterPos), sifter), "the Sifter"));

        BlockPos crusherPos = new BlockPos(3, 2, 1);
        helper.setBlock(crusherPos, ModBlocks.CRUSHER.get());
        var crusher = helper.getBlockEntity(crusherPos, CrusherBlockEntity.class);
        afterTheBeat.add(waitsABeat(helper, crusherPos, crusher, CrusherBlockEntity.OUTPUT, new ItemStack(Items.GRAVEL),
                () -> CrusherBlockEntity.serverTick(level, helper.absolutePos(crusherPos), helper.getBlockState(crusherPos), crusher), "the Crusher"));

        BlockPos makerPos = new BlockPos(5, 2, 1);
        helper.setBlock(makerPos, ModBlocks.STONE_MAKER.get());
        var maker = helper.getBlockEntity(makerPos, StoneMakerBlockEntity.class);
        afterTheBeat.add(waitsABeat(helper, makerPos, maker, 0, new ItemStack(Items.COBBLESTONE, 5),
                () -> StoneMakerBlockEntity.serverTick(level, helper.absolutePos(makerPos), helper.getBlockState(makerPos), maker), "the Stone Maker"));

        BlockPos furnacePos = new BlockPos(1, 2, 4);
        helper.setBlock(furnacePos, ModBlocks.ELECTRIC_FURNACE.get());
        var furnace = helper.getBlockEntity(furnacePos, ElectricFurnaceBlockEntity.class);
        afterTheBeat.add(waitsABeat(helper, furnacePos, furnace, ElectricFurnaceBlockEntity.OUTPUT, new ItemStack(Items.IRON_INGOT, 3),
                () -> ElectricFurnaceBlockEntity.serverTick(level, helper.absolutePos(furnacePos), helper.getBlockState(furnacePos), furnace),
                "the Electric Furnace"));

        BlockPos distillerPos = new BlockPos(4, 2, 4);
        helper.setBlock(distillerPos, ModBlocks.DISTILLER.get());
        var distiller = helper.getBlockEntity(distillerPos, DistillerBlockEntity.class);
        afterTheBeat.add(waitsABeat(helper, distillerPos, distiller, 1, new ItemStack(Items.GOLD_NUGGET, 20),
                () -> DistillerBlockEntity.serverTick(level, helper.absolutePos(distillerPos), helper.getBlockState(distillerPos), distiller),
                "the Distiller (and the mercury machines with it)"));

        helper.succeedWhen(() -> afterTheBeat.forEach(Runnable::run));
    }

    /**
     * {@code machine} at {@code pos} over a chest with no room, {@code made} waiting in its output slot {@code slot}:
     * ticked, it keeps it; room made, ticked again in the same tick, it has not tried again. What is to hold once the
     * beat is out - {@code made} in the chest - is handed back to be checked then.
     */
    private static Runnable waitsABeat(GameTestHelper helper, BlockPos pos, Container machine, int slot, ItemStack made, Runnable tick,
            String name) {
        helper.setBlock(pos.below(), Blocks.CHEST);
        var chest = helper.getBlockEntity(pos.below(), ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.STONE, 64));
        }
        machine.setItem(slot, made.copy());
        tick.run();
        check(helper, ItemStack.matches(machine.getItem(slot), made), name + ": no room below, it keeps what it made: " + machine.getItem(slot));
        chest.setItem(0, ItemStack.EMPTY);
        tick.run();
        check(helper, ItemStack.matches(machine.getItem(slot), made), name + " waits out a hopper's beat before it tries again, not the next tick");
        return () -> check(helper, chest.countItem(made.getItem()) == made.getCount() && machine.getItem(slot).isEmpty(),
                name + " puts it in once the beat is out: " + machine.getItem(slot));
    }

    /**
     * A controller running through a sender lights it on a lease, kept by place; broken while lit, the sender's lease
     * goes with it. Only its own tick let a lease go, and no tick comes for a sender no longer there.
     */
    private static void senderLeaseGoes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.SPAWNER_LINK_SENDER.get());
        BlockPos at = helper.absolutePos(pos);
        SpawnerLinkSenderBlock.keepLit(level, at, level.getGameTime() + 1_000);
        check(helper, helper.getBlockState(pos).getValue(SpawnerLinkSenderBlock.LIT)
                && SpawnerLinkSenderBlock.leasedUntil(level, at) > level.getGameTime(), "lit by a controller, on a lease");
        helper.setBlock(pos, Blocks.AIR); // broken while lit
        check(helper, SpawnerLinkSenderBlock.leasedUntil(level, at) == 0, "taken away while lit, its lease goes with it");
        helper.succeed();
    }
}
