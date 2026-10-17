package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import dev.nookworks.Nookworks;
import dev.nookworks.compat.jei.InfoPages;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The r15 survival playthrough (2026-10-14, AI_review/r15/playthrough): the wood vinegar advancement given for loading a
 * Distiller, "Missing a reagent" saying nothing of which, the Sifter without a JEI page, the advancement tab hidden until
 * a thing of ours was made, and eight wood vinegar that came out five methanol.
 */
final class R15PlaythroughTests {
    private R15PlaythroughTests() {}

    static void register() {
        test("wood_vinegar_advancement_is_for_a_field_sprinkled", R15PlaythroughTests::woodVinegarAdvancement);
        test("missing_reagent_is_named", R15PlaythroughTests::missingReagentNamed);
        test("sifter_has_a_jei_page", R15PlaythroughTests::sifterPage);
        test("advancement_tab_opens_with_the_first_log", R15PlaythroughTests::tabOpensWithTheFirstLog);
        test("distiller_makes_eight_methanol_of_eight_wood_vinegar_into_a_chest", R15PlaythroughTests::methanolIntoAChest);
        test("distiller_with_no_container_drops_every_methanol", R15PlaythroughTests::methanolOnTheGround);
    }

    private static BooleanSupplier done(GameTestHelper helper, ServerPlayer player, String id) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Nookworks.id(id));
        check(helper, holder != null, id + " is loaded");
        return () -> player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static BlockHitResult top(GameTestHelper helper, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false);
    }

    /**
     * "Keep Off the Beds" is for wood vinegar sprinkled on a field: vanilla's item_used_on_block gave it for any click with
     * the bottle on a block, and the playthrough had it for loading a Distiller.
     */
    private static void woodVinegarAdvancement(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BooleanSupplier done = done(helper, player, "wood_vinegar");
        BlockPos distiller = new BlockPos(1, 1, 1);
        helper.setBlock(distiller, ModBlocks.DISTILLER.get());
        ItemStack bottle = new ItemStack(ModOil.WOOD_VINEGAR.get());
        // what the game says when a block is clicked with the bottle in hand, as the Distiller was to load it
        CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, helper.absolutePos(distiller), bottle);
        check(helper, !done.getAsBoolean(), "a Distiller clicked with the bottle is no field sprinkled");
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        bottle.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, top(helper, distiller)));
        check(helper, !done.getAsBoolean() && player.getMainHandItem().is(ModOil.WOOD_VINEGAR.get()),
                "used on a block that is no field, nothing is sprinkled and it is not earned");
        BlockPos field = new BlockPos(3, 1, 3);
        helper.setBlock(field, Blocks.FARMLAND);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, top(helper, field)));
        check(helper, player.getMainHandItem().is(Items.GLASS_BOTTLE), "sprinkled: the bottle comes back empty");
        check(helper, done.getAsBoolean(), "sprinkled on farmland, it is earned");
        helper.succeed();
    }

    /** The translation keys in {@code text} and its siblings, in order. */
    private static List<String> keys(Component text) {
        List<String> keys = new ArrayList<>();
        if (text.getContents() instanceof TranslatableContents translatable) {
            keys.add(translatable.getKey());
        }
        text.getSiblings().forEach(sibling -> keys.addAll(keys(sibling)));
        return keys;
    }

    /**
     * "Missing a reagent" goes on to say which: brass from copper wants another copper ingot and a zinc one, and once
     * the copper is in, only the zinc; a charge with what it takes, or one the Retort does not take, has nothing to add.
     */
    private static void missingReagentNamed(GameTestHelper helper) {
        check(helper, Language.getInstance().has("gui.nookworks.machine.status.no_reagent.detail"), "the line is translated");
        ItemStack copper = new ItemStack(Items.COPPER_INGOT);
        ItemStack zinc = new ItemStack(ModItems.ZINC_INGOT.get());
        Component both = RetortBlockEntity.missingReagentNames(copper, List.of(ItemStack.EMPTY, ItemStack.EMPTY));
        check(helper, both != null && keys(both).equals(List.of("item.minecraft.copper_ingot", "nookworks.list.separator",
                zinc.getItem().getDescriptionId())), "nothing in: copper and zinc named, " + (both == null ? null : keys(both)));
        Component zincOnly = RetortBlockEntity.missingReagentNames(copper, List.of(ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT)));
        check(helper, zincOnly != null && keys(zincOnly).equals(List.of(zinc.getItem().getDescriptionId())),
                "the copper in (either slot): only the zinc, " + (zincOnly == null ? null : keys(zincOnly)));
        check(helper, RetortBlockEntity.missingReagentNames(copper, List.of(new ItemStack(Items.COPPER_INGOT), zinc)) == null,
                "both in: nothing missing");
        check(helper, RetortBlockEntity.missingReagentNames(new ItemStack(Items.STONE), List.of(ItemStack.EMPTY, ItemStack.EMPTY)) == null,
                "a charge the Retort does not take: nothing to say");
        // wood chips are pulped with caustic soda and sulfur: one of them in, the other named
        Component sulfur = RetortBlockEntity.missingReagentNames(new ItemStack(ModItems.WOOD_CHIPS.get()),
                List.of(new ItemStack(ModItems.CAUSTIC_SODA.get()), ItemStack.EMPTY));
        check(helper, sulfur != null && keys(sulfur).equals(List.of(ModItems.SULFUR_DUST.get().getDescriptionId())),
                "the soda in: the sulfur named, " + (sulfur == null ? null : keys(sulfur)));
        helper.succeed();
    }

    /** The Sifter has a JEI page of its own, as the Brass Mesh fitted to it does. */
    private static void sifterPage(GameTestHelper helper) {
        List<String> pages = new ArrayList<>();
        InfoPages.each((item, text) -> {
            if (item == ModItems.SIFTER.get()) {
                pages.addAll(keys(text));
            }
        });
        check(helper, pages.equals(List.of("jei.nookworks.info.sifter")), "the Sifter's page: " + pages);
        check(helper, Language.getInstance().has("jei.nookworks.info.sifter"), "and it is written");
        helper.succeed();
    }

    /**
     * The Nookworks tab is there from the first log, as vanilla's own tab is with a crafting table: it stayed hidden
     * until a rope, sconce, crate, Sifter or stone hammer was made. A crafting table opens it too; the tab's own
     * advancements still hang from what they take.
     */
    private static void tabOpensWithTheFirstLog(GameTestHelper helper) {
        for (ItemStack first : List.of(new ItemStack(Items.SPRUCE_LOG), new ItemStack(Items.CRAFTING_TABLE))) {
            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            BooleanSupplier root = done(helper, player, "root");
            check(helper, !root.getAsBoolean(), "a new player has not opened it");
            player.getInventory().add(first.copy()); // (add empties the stack it is given)
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), first);
            check(helper, root.getAsBoolean(), "opened by " + first);
            check(helper, !done(helper, player, "sifter").getAsBoolean(), "and nothing under it with it");
        }
        helper.succeed();
    }

    /** A Vacuum Distiller's tower at {@code pos}, powered and loaded with eight wood vinegar, run until they are done. */
    private static DistillerBlockEntity distil(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.DISTILLER.get());
        helper.setBlock(pos.above(1), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(2), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(3), ModBlocks.CONDENSER.get());
        var distiller = helper.getBlockEntity(pos, DistillerBlockEntity.class);
        distiller.setItem(DistillerBlockEntity.INPUT, new ItemStack(ModOil.WOOD_VINEGAR.get(), 8));
        for (int tick = 0; tick < 8 * (DistillerBlockEntity.TICKS + 5) * 4 && !distiller.getItem(DistillerBlockEntity.INPUT).isEmpty(); tick++) {
            distiller.energy().set((int) distiller.energy().getCapacityAsLong());
            DistillerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), distiller);
        }
        // the tick that finished the last one has passed it on
        DistillerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), distiller);
        check(helper, distiller.getItem(DistillerBlockEntity.INPUT).isEmpty(), "all eight distilled");
        return distiller;
    }

    /**
     * Eight wood vinegar make eight methanol, every one in the chest under the Distiller (the playthrough counted five,
     * with nothing under it and creepers going off by the tower).
     */
    private static void methanolIntoAChest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var distiller = distil(helper, pos);
        var chest = helper.getBlockEntity(pos.below(), ChestBlockEntity.class);
        int inChest = chest.countItem(ModOil.METHANOL.get());
        int inMachine = 0;
        for (int slot = 1; slot < distiller.getContainerSize(); slot++) {
            inMachine += distiller.getItem(slot).is(ModOil.METHANOL.get()) ? distiller.getItem(slot).getCount() : 0;
        }
        check(helper, inChest == 8 && inMachine == 0, "eight methanol in the chest, got " + inChest + " (and " + inMachine + " in the machine)");
        helper.succeed();
    }

    /**
     * With nothing under it to take them, the methanol comes out into the world - on top, the ground under it being solid,
     * which with the tower on it is inside the column, and the column pushes it out - and every one of the eight is
     * still there a few seconds on.
     */
    private static void methanolOnTheGround(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.below(), Blocks.STONE);
        distil(helper, pos);
        helper.runAfterDelay(60, () -> {
            int lying = 0;
            for (var item : helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(pos)).inflate(4),
                    e -> e.getItem().is(ModOil.METHANOL.get()))) {
                lying += item.getItem().getCount();
            }
            check(helper, lying == 8, "eight methanol lying about the tower, got " + lying);
            helper.succeed();
        });
    }
}
