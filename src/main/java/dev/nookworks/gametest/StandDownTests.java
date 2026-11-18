package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.compat.FeatureGate;
import dev.nookworks.feature.ChainBreaking;
import dev.nookworks.feature.Permission;
import dev.nookworks.item.MagnetItem;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * The 1.21.1 port beside the real mods that do a job of ours (docs/compat.md, "Overlapping QoL"): with one loaded, ours
 * stands down and theirs works as if Nookworks were not there. Registered only with such a mod in run-gametest/mods
 * (FallingTree, TreeChop, Simple Magnets, Cyclic); the features' own tests say what they expect then ({@link StandDowns}).
 */
final class StandDownTests {
    private StandDownTests() {}

    static boolean any() {
        ModList mods = ModList.get();
        return mods.isLoaded("fallingtree") || mods.isLoaded("treechop") || mods.isLoaded("simplemagnets") || mods.isLoaded("cyclic");
    }

    static void register() {
        ModList mods = ModList.get();
        if (mods.isLoaded("fallingtree") || mods.isLoaded("treechop")) {
            NookworksGameTests.test("stand_down_a_tree_mod_cuts_an_oak_with_a_vanilla_axe", 60,
                    h -> cutTheirWay(h, new ItemStack(Items.DIAMOND_AXE), Blocks.OAK_LOG, Blocks.OAK_LEAVES));
            NookworksGameTests.test("stand_down_a_tree_mod_cuts_an_oak_with_our_axe", 60,
                    h -> cutTheirWay(h, new ItemStack(ModItems.VELARIUM_AXE.get()), Blocks.OAK_LOG, Blocks.OAK_LEAVES));
            NookworksGameTests.test("stand_down_a_tree_mod_cuts_our_dawnwood", 60,
                    h -> cutTheirWay(h, new ItemStack(Items.DIAMOND_AXE), ModWoods.DAWNWOOD_LOG.get(), ModWoods.DAWNWOOD_LEAVES.get()));
            NookworksGameTests.test("stand_down_logs_ours_names_mods_that_fell", StandDownTests::felling);
        }
        if (mods.isLoaded("treechop")) {
            NookworksGameTests.test("stand_down_our_questions_chop_nothing_beside_treechop", StandDownTests::questionsChopNothing);
        }
        if (mods.isLoaded("simplemagnets")) {
            NookworksGameTests.test("stand_down_simple_magnets_pulls_and_ours_stays_still", 100, StandDownTests::simpleMagnets);
        }
        if (mods.isLoaded("cyclic")) {
            NookworksGameTests.test("stand_down_an_excavate_tool_is_cyclics", StandDownTests::excavate);
        }
    }

    /**
     * A tree of {@code log} five high, a crown of {@code leaves} round its top as one grows (enough for FallingTree to know
     * it for a tree, and TreeChop): the trunk, bottom first.
     */
    private static List<BlockPos> tree(GameTestHelper helper, Block log, Block leaves) {
        BlockState crown = leaves.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 4, 1), new BlockPos(5, 6, 5))) {
            helper.setBlock(p.immutable(), crown);
        }
        List<BlockPos> trunk = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            trunk.add(new BlockPos(3, y, 3));
            helper.setBlock(new BlockPos(3, y, 3), log);
        }
        return trunk;
    }

    /**
     * The tree mod's own way with {@code axe} on a tree of {@code log}: FallingTree fells the trunk at once, TreeChop
     * chops the log a notch (its own chopped log in its place, the trunk standing until chopped enough) - and ours fells
     * nothing. With both loaded, whichever takes the break first.
     */
    private static void cutTheirWay(GameTestHelper helper, ItemStack axe, Block log, Block leaves) {
        check(helper, StandDowns.felling(), "tree felling stands down beside " + StandDowns.feller());
        List<BlockPos> trunk = tree(helper, log, leaves);
        ServerPlayer player = MiningTests.playerWith(helper, axe);
        int mark = ChainBreaking.fellings();
        player.gameMode.destroyBlock(helper.absolutePos(trunk.getFirst()));
        check(helper, ChainBreaking.fellings() == mark, "ours felled nothing");
        helper.runAfterDelay(2, () -> {
            BlockState foot = helper.getBlockState(trunk.getFirst());
            ResourceLocation footId = BuiltInRegistries.BLOCK.getKey(foot.getBlock());
            boolean felled = trunk.stream().noneMatch(p -> helper.getBlockState(p).is(log));
            boolean chopped = footId.getNamespace().equals("treechop") && trunk.stream().skip(1).allMatch(p -> helper.getBlockState(p).is(log));
            check(helper, felled || chopped, "the tree is cut as " + StandDowns.feller() + " cuts one: foot " + footId + ", felled " + felled);
            if (!ModList.get().isLoaded("treechop")) {
                check(helper, felled, "FallingTree fells the whole trunk");
            }
            helper.succeed();
        });
    }

    /** The stand-down is told once as the world starts, naming the mod; and ours stays off on both sides, as the server decides. */
    private static void felling(GameTestHelper helper) {
        check(helper, !FeatureGate.timber(), "our felling is off");
        String by = FeatureGate.firstLoaded(FeatureGate.timberConflicts());
        check(helper, "treechop".equals(by) || "fallingtree".equals(by), "and it names the mod: " + by);
        helper.succeed();
    }

    /**
     * Our claim questions about a log - may a machine take it, a rope, a load? - post a break event with nothing broken.
     * TreeChop chops on every break event; asked as a question, it must not (compat.TreeChopQuestions): the log stays a
     * log, and the answer is yes where nobody's claim says no.
     */
    private static void questionsChopNothing(GameTestHelper helper) {
        List<BlockPos> trunk = tree(helper, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        var level = helper.getLevel();
        boolean may = Permission.mayBreak(level, helper.absolutePos(trunk.getFirst()), Permission.actor(level, null));
        check(helper, may, "a machine of nobody's may take a log on unclaimed land");
        check(helper, helper.getBlockState(trunk.getFirst()).is(Blocks.OAK_LOG), "and the log is a log still, not chopped: "
                + helper.getBlockState(trunk.getFirst()));
        helper.succeed();
    }

    /**
     * Simple Magnets' magnet, switched on in the inventory, pulls a drop to the player; ours, on beside it, is standing
     * down and pulls nothing (no second pull fighting theirs).
     */
    private static void simpleMagnets(GameTestHelper helper) {
        check(helper, !FeatureGate.magnet(), "our magnet stands down beside " + FeatureGate.firstLoaded(FeatureGate.magnetConflicts()));
        Item basic = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("simplemagnets", "basicmagnet"));
        check(helper, basic != Items.AIR, "Simple Magnets' basic magnet is there");
        ItemStack theirs = new ItemStack(basic);
        try {
            @SuppressWarnings("unchecked")
            var active = (net.minecraft.core.component.DataComponentType<Boolean>) Class.forName("com.supermartijn642.simplemagnets.MagnetItem")
                    .getField("ACTIVE").get(null);
            theirs.set(active, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Simple Magnets' ACTIVE component not found", e);
        }
        ItemStack ours = new ItemStack(ModItems.MAGNET.get());
        ours.set(dev.nookworks.registry.ModDataComponents.MAGNET_ACTIVE.get(), true);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 1, 1.5)));
        player.getInventory().add(theirs);
        player.getInventory().add(ours);
        check(helper, MagnetItem.activeRange(player) > 0, "ours is switched on too");
        ItemEntity drop = new ItemEntity(helper.getLevel(), helper.absoluteVec(new Vec3(5.5, 1.2, 5.5)).x,
                helper.absoluteVec(new Vec3(5.5, 1.2, 5.5)).y, helper.absoluteVec(new Vec3(5.5, 1.2, 5.5)).z, new ItemStack(Items.COBBLESTONE));
        drop.setPickUpDelay(0);
        helper.getLevel().addFreshEntity(drop);
        double before = drop.distanceTo(player);
        helper.onEachTick(() -> player.getInventory().tick()); // what a ticking player's inventory does: their magnet's own tick
        helper.succeedWhen(() -> check(helper, drop.isRemoved() || drop.distanceTo(player) < before - 1.0,
                "their magnet pulls the drop: " + drop.distanceTo(player) + " of " + before));
    }

    /**
     * Cyclic's Excavate on a tool breaks the whole vein or trunk its own way: on such a tool ours stands aside (no
     * felling, no vein of ours), while the same tool without it works as ours does wherever ours is on.
     */
    private static void excavate(GameTestHelper helper) {
        var excavate = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath("cyclic", "excavate")));
        check(helper, excavate.isPresent(), "Cyclic's Excavate is there");
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.enchant(excavate.get(), 1);
        check(helper, FeatureGate.anotherModsArea(pick), "a tool with Excavate is Cyclic's to break with");
        check(helper, !FeatureGate.anotherModsArea(new ItemStack(Items.DIAMOND_PICKAXE)), "a plain one is not");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.IRON_ORE);
        ServerPlayer player = MiningTests.playerWith(helper, pick);
        int veins = ChainBreaking.veins();
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(2, 1, 2)));
        check(helper, ChainBreaking.veins() == veins, "ours mined no vein with it");
        helper.succeed();
    }
}
