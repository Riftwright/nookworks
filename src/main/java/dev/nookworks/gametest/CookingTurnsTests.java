package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;

import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.experience.Move;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A pot or pan holding the makings of more than one dish cooks them all at once, each in its own time (the user,
 * 2026-11-14, play-testing: a pot of oil fried the doughnut and left the pork and the fish as they were until it was
 * taken out - 「油に入ってるんだから、いつか揚がると思うじゃん。なんでそこだけ時が止まるんだよ…」 - and of the ways offered,
 * frying them together). A dish done while another waits waits behind it, and comes forward as that one is taken out;
 * an empty hand takes every dish a hand takes at once.
 */
final class CookingTurnsTests {
    private CookingTurnsTests() {}

    static final String[] NEED_KITCHEN = {"mixed_fries_all_cook_at_once", "mixed_french_fries_and_karaage_cook_together",
            "mixed_in_a_pan_an_egg_and_a_pancake_fry_together", "mixed_fries_over_a_hopper_all_come_out",
            "mixed_rice_and_egg_in_a_pan_are_still_fried_rice"};

    static void register() {
        NookworksGameTests.test(NEED_KITCHEN[0], 1200, CookingTurnsTests::fries);
        NookworksGameTests.test(NEED_KITCHEN[1], 800, CookingTurnsTests::frenchFries);
        NookworksGameTests.test(NEED_KITCHEN[2], 600, CookingTurnsTests::pan);
        NookworksGameTests.test(NEED_KITCHEN[3], 1400, CookingTurnsTests::hopper);
        NookworksGameTests.test(NEED_KITCHEN[4], 400, CookingTurnsTests::wholeFirst);
    }

    private static IronPotBlockEntity vessel(GameTestHelper helper, BlockPos pos, boolean pan, int oil) {
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, pan ? ModCooking.FRYING_PAN.get() : ModCooking.IRON_POT.get());
        var vessel = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        vessel.addLiquid(PotRecipe.Liquid.OIL, oil);
        return vessel;
    }

    private static void put(GameTestHelper helper, IronPotBlockEntity vessel, Item item, int count) {
        check(helper, Move.insert(vessel.itemHandler(), ItemResource.of(item), count) == count, count + " of " + item + " go in");
    }

    /** How many of {@code item} are done in the vessel, in front or behind. */
    private static int done(IronPotBlockEntity vessel, Item item) {
        int count = vessel.dish().is(item) ? vessel.dish().getCount() : 0;
        for (IronPotBlockEntity.Waiting waiting : vessel.later()) {
            count += waiting.dish().is(item) ? waiting.dish().getCount() : 0;
        }
        return count;
    }

    /**
     * Two Breaded Pork, a Coated Fish and a Doughnut Dough in one pot of oil, and nobody taking anything out: all three
     * are fried, the tonkatsu two at once; the oil each batch asks for is used (150 mB). While the first dish waits, the
     * rest cook on. Saved and loaded, the dishes behind the first stay; and an empty hand takes all of them at once.
     */
    private static void fries(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        var pot = vessel(helper, pos, false, 1_000);
        put(helper, pot, ModCooking.BREADED_PORK.get(), 2);
        put(helper, pot, ModCooking.COATED_FISH.get(), 1);
        put(helper, pot, ModCooking.DONUT_DOUGH.get(), 1);
        check(helper, pot.stuck() == null, "the pot has something to cook: " + pot.stuck());
        boolean[] cookedOnBesideADish = {false};
        helper.onEachTick(() -> cookedOnBesideADish[0] |= !pot.dish().isEmpty() && pot.count() > 0 && pot.percent() > 0);
        helper.succeedWhen(() -> {
            check(helper, pot.count() == 0, "everything fried: " + pot.contents());
            check(helper, done(pot, ModCooking.TONKATSU.get()) == 2 && done(pot, ModCooking.FRIED_FISH.get()) == 1
                    && done(pot, ModKitchen.DONUT.get()) == 1, "two tonkatsu, a fried fish and a doughnut: " + pot.dish() + " " + pot.later());
            check(helper, pot.amount() == 850, "150 mB of oil used: " + pot.amount() + " mB left");

            var registries = helper.getLevel().registryAccess();
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
            pot.saveCustomOnly(saved);
            pot.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved.buildResult()));
            check(helper, pot.later().size() == 2 && done(pot, ModCooking.TONKATSU.get()) == 2 && done(pot, ModKitchen.DONUT.get()) == 1,
                    "saved and loaded, the dishes behind the first stay: " + pot.dish() + " " + pot.later());

            ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, new ArrayList<>());
            Vec3 near = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
            player.snapTo(near.x, near.y, near.z);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BlockPos at = helper.absolutePos(pos);
            player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false));
            var inventory = player.getInventory();
            check(helper, inventory.countItem(ModCooking.TONKATSU.get()) == 2 && inventory.countItem(ModCooking.FRIED_FISH.get()) == 1
                    && inventory.countItem(ModKitchen.DONUT.get()) == 1, "one empty-hand click takes them all: " + inventory.getNonEquipmentItems());
            check(helper, pot.dish().isEmpty() && pot.later().isEmpty(), "and the pot holds no dish");
            check(helper, cookedOnBesideADish[0], "the rest cooked on while the first dish waited");
        });
    }

    /**
     * French fries are of two kinds of thing (two potatoes and salt): with a Coated Chicken in after them, the fries and
     * the karaage both. The potato over makes nothing alone and holds nothing up, and the pot says it makes nothing.
     */
    private static void frenchFries(GameTestHelper helper) {
        var pot = vessel(helper, new BlockPos(1, 2, 1), false, 1_000);
        put(helper, pot, Items.POTATO, 3);
        put(helper, pot, ModItems.SALT.get(), 1);
        put(helper, pot, ModCooking.COATED_CHICKEN.get(), 1);
        helper.succeedWhen(() -> {
            check(helper, done(pot, ModCooking.FRENCH_FRIES.get()) == 2 && done(pot, ModCooking.KARAAGE.get()) == 1,
                    "fries and karaage: " + pot.dish() + " " + pot.later());
            check(helper, pot.count() == 1, "a potato over: " + pot.contents());
            check(helper, pot.stuck() != null, "the potato left alone makes nothing, and the pot says so");
        });
    }

    /** In a Frying Pan, an egg and Pancake Batter: the fried egg and the pancakes both, in one bottle of oil. */
    private static void pan(GameTestHelper helper) {
        var pan = vessel(helper, new BlockPos(1, 2, 1), true, 250);
        put(helper, pan, Items.EGG, 1);
        put(helper, pan, ModKitchen.PANCAKE_BATTER.get(), 1);
        helper.succeedWhen(() -> check(helper, done(pan, ModCooking.FRIED_EGG.get()) == 1 && done(pan, ModKitchen.PANCAKE.get()) == 1
                && pan.count() == 0, "a fried egg and pancakes: " + pan.dish() + " " + pan.later()));
    }

    /**
     * Over a hopper over the heat, the same mixed pot: the hopper takes each dish as it comes forward - all three end up
     * in the hopper with nobody's hand in it.
     */
    private static void hopper(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 3, 1);
        helper.setBlock(pos.below(2), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos.below(), Blocks.HOPPER);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.OIL, 1_000);
        put(helper, pot, ModCooking.BREADED_PORK.get(), 1);
        put(helper, pot, ModCooking.COATED_FISH.get(), 1);
        put(helper, pot, ModCooking.DONUT_DOUGH.get(), 1);
        helper.succeedWhen(() -> {
            Container below = helper.getBlockEntity(pos.below(), net.minecraft.world.level.block.entity.HopperBlockEntity.class);
            check(helper, below.countItem(ModCooking.TONKATSU.get()) == 1 && below.countItem(ModCooking.FRIED_FISH.get()) == 1
                    && below.countItem(ModKitchen.DONUT.get()) == 1, "tonkatsu, fried fish and a doughnut in the hopper");
            check(helper, pot.count() == 0 && pot.dish().isEmpty() && pot.later().isEmpty(), "and the pot empty");
        });
    }

    /** What every thing in the vessel belongs to still comes first: a Bowl of Rice and an egg are fried rice, not a fried egg. */
    private static void wholeFirst(GameTestHelper helper) {
        var pan = vessel(helper, new BlockPos(1, 2, 1), true, 250);
        put(helper, pan, Items.EGG, 1);
        put(helper, pan, ModCooking.COOKED_RICE.get(), 1);
        helper.succeedWhen(() -> check(helper, pan.dish().is(ModCooking.FRIED_RICE.get()) && pan.count() == 0
                && pan.later().isEmpty(), "fried rice alone: " + pan.dish() + " " + pan.later()));
    }
}
