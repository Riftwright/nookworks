package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;
import java.util.function.IntSupplier;

import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.GrillBlockEntity;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** The Iron Pot and the Grill as a player meets them: set down, looked at, filled, emptied, broken. */
final class CookingTests {
    static void register() {
        NookworksGameTests.test("pot_takes_the_grills_place", CookingTests::potTakesTheGrillsPlace);
        NookworksGameTests.test("pot_on_a_grill_stands_up_and_sits_down", 60, CookingTests::potStandsUpAndSitsDown);
        NookworksGameTests.test("pot_counts_its_bowls", 600, CookingTests::potCountsItsBowls);
        NookworksGameTests.test("pot_starts_over_for_another_recipe", 900, CookingTests::potStartsOverForAnotherRecipe);
        NookworksGameTests.test("pot_pours_back_out", CookingTests::potPoursBackOut);
        NookworksGameTests.test("grill_lets_other_things_be_used", CookingTests::grillLetsOtherThingsBeUsed);
    }

    private static BlockState grillOverFire() {
        return ModCooking.GRILL.get().defaultBlockState().setValue(GrillBlock.OVER_FIRE, true);
    }

    private static BlockHitResult hitTop(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
    }

    /**
     * An Iron Pot used on a grill over a fire takes the grill's place: one block, what a player looking at the pot from
     * the side actually hits. Broken, the pot comes away and the grill stays over the fire.
     */
    private static void potTakesTheGrillsPlace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.below(), Blocks.CAMPFIRE);
        helper.setBlock(pos, grillOverFire());
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModCooking.IRON_POT_ITEM.get()));
        helper.getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, pos));
        BlockState state = helper.getBlockState(pos);
        check(helper, state.is(ModCooking.IRON_POT.get()) && state.getValue(IronPotBlock.ON_GRILL), "the pot takes the grill's place");
        check(helper, player.getMainHandItem().isEmpty(), "and is used up");
        check(helper, IronPotBlock.heated(helper.getLevel(), helper.absolutePos(pos)), "over the lit campfire it is hot");

        // A player standing beside it, eyes level with the pot's rim, looking at its side.
        Vec3 target = Vec3.atLowerCornerOf(helper.absolutePos(pos)).add(0.5, 5.0 / 16, 0.5);
        Vec3 eye = target.add(-3.0, 0.0, 0.0);
        BlockHitResult seen = helper.getLevel().clip(new ClipContext(eye, target.add(1.0, 0, 0), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        check(helper, seen.getType() == HitResult.Type.BLOCK && seen.getBlockPos().equals(helper.absolutePos(pos)),
                "looked at from the side, the pot is what is hit, got " + seen.getBlockPos());

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(pos));
        BlockState after = helper.getBlockState(pos);
        check(helper, after.is(ModCooking.GRILL.get()) && after.getValue(GrillBlock.OVER_FIRE), "broken, the grill stays over the fire");
        check(helper, helper.getEntities(EntityTypes.ITEM).stream()
                .anyMatch(e -> e.getItem().is(ModCooking.IRON_POT_ITEM.get())), "and the pot comes away");
        check(helper, helper.getEntities(EntityTypes.ITEM).stream()
                .noneMatch(e -> e.getItem().is(ModCooking.GRILL_ITEM.get())), "with no second grill");
        helper.succeed();
    }

    /**
     * Take the campfire from under a pot on its grill and the grill stands up on its legs with the pot, and all in it, on
     * top; put a campfire back and the pot sits down on the grate again. A grill with food on it will not take a pot.
     */
    private static void potStandsUpAndSitsDown(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
        helper.setBlock(pos, ModCooking.IRON_POT.get().onGrill(Direction.NORTH));
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        check(helper, pot.addLiquid(PotRecipe.Liquid.WATER, 500) && pot.addIngredient(new ItemStack(ModCrops.WHITE_RICE.get())),
                "water and rice in");
        check(helper, !IronPotBlock.heated(helper.getLevel(), helper.absolutePos(pos)), "a dead fire heats nothing");
        helper.destroyBlock(pos.below());
        helper.runAfterDelay(3, () -> {
            check(helper, helper.getBlockState(pos).is(ModCooking.GRILL.get()) && !helper.getBlockState(pos).getValue(GrillBlock.OVER_FIRE),
                    "the grill stands up on its own");
            BlockState up = helper.getBlockState(pos.above());
            check(helper, up.is(ModCooking.IRON_POT.get()) && !up.getValue(IronPotBlock.ON_GRILL), "with the pot on top");
            var moved = helper.getBlockEntity(pos.above(), IronPotBlockEntity.class);
            check(helper, moved.amount() == 500 && moved.count() == 1, "still holding its water and rice");
            check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "nothing spilt");
            helper.setBlock(pos.below(), Blocks.CAMPFIRE);
            helper.runAfterDelay(3, () -> {
                BlockState down = helper.getBlockState(pos);
                check(helper, down.is(ModCooking.IRON_POT.get()) && down.getValue(IronPotBlock.ON_GRILL), "a fire again, and down it sits");
                check(helper, helper.getBlockState(pos.above()).isAir(), "leaving the block above");
                check(helper, helper.getBlockEntity(pos, IronPotBlockEntity.class).amount() == 500, "with its water");

                BlockPos other = new BlockPos(5, 2, 5);
                helper.setBlock(other.below(), Blocks.CAMPFIRE);
                helper.setBlock(other, grillOverFire());
                var grill = helper.getBlockEntity(other, GrillBlockEntity.class);
                grill.place(helper.getLevel(), null, new ItemStack(Items.BEEF));
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModCooking.IRON_POT_ITEM.get()));
                helper.getBlockState(other).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, other));
                check(helper, helper.getBlockState(other).is(ModCooking.GRILL.get()) && player.getMainHandItem().getCount() == 1,
                        "beef on the grate: no pot goes on");
                helper.succeed();
            });
        });
    }

    /**
     * A Bowl of Rice goes into the pot in its bowl and, taken back out, is still in it: no bowl comes from nowhere. Fried
     * into fried rice, its bowl is freed and the fried rice is served back into it by hand.
     */
    private static void potCountsItsBowls(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        IntSupplier bowls = () -> player.getInventory().countItem(Items.BOWL);
        BlockState pot = helper.getBlockState(pos);
        for (int round = 0; round < 3; round++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModCooking.COOKED_RICE.get()));
            pot.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, pos));
            check(helper, bowls.getAsInt() == 0, "putting the rice in gives no bowl");
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            pot.useWithoutItem(helper.getLevel(), player, hitTop(helper, pos));
            player.setShiftKeyDown(false);
            check(helper, player.getInventory().countItem(ModCooking.COOKED_RICE.get()) == 1 && bowls.getAsInt() == 0,
                    "taking it back gives the rice in its bowl, and no other");
            player.getInventory().clearContent();
        }
        var entity = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        entity.addLiquid(PotRecipe.Liquid.OIL, 250);
        entity.addIngredient(new ItemStack(ModCooking.COOKED_RICE.get()));
        entity.addIngredient(new ItemStack(Items.EGG));
        helper.succeedWhen(() -> {
            check(helper, entity.dish().is(ModCooking.FRIED_RICE.get()), "the rice fries");
            check(helper, entity.freed().stream().mapToInt(ItemStack::getCount).sum() == 1, "and its bowl is freed, in the pot");
            check(helper, entity.describe().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents said
                    && said.getKey().equals("nookworks.iron_pot.ready_serve_freed"), "it says an empty hand serves into the bowl in it");
            pot.useWithoutItem(helper.getLevel(), player, hitTop(helper, pos));
            check(helper, player.getInventory().countItem(ModCooking.FRIED_RICE.get()) == 1 && bowls.getAsInt() == 0
                    && entity.freed().isEmpty() && entity.dish().isEmpty(), "served into that bowl: one bowl in, one bowl out");
        });
    }

    /**
     * Cooking waits a moment after the last thing goes in, and belongs to its recipe: soap under way becomes biodiesel,
     * started from nothing, when methanol goes in - it does not finish at once on the soap's progress.
     */
    private static void potStartsOverForAnotherRecipe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.OIL, 1_000);
        pot.addIngredient(new ItemStack(ModItems.CAUSTIC_SODA.get()));
        helper.runAfterDelay(IronPotBlockEntity.SETTLE - 5, () -> check(helper, pot.progress() == 0, "it waits after something goes in"));
        helper.runAfterDelay(IronPotBlockEntity.SETTLE + 300, () -> {
            check(helper, pot.progress() > 250 && pot.dish().isEmpty(), "soap well under way, " + pot.progress());
            // four methanol to the one caustic soda, the catalyst for a bucket of oil
            for (int i = 0; i < 4; i++) {
                pot.addIngredient(new ItemStack(ModOil.METHANOL.get()));
            }
            helper.runAfterDelay(5, () -> check(helper, pot.progress() == 0 && pot.dish().isEmpty(), "methanol in: biodiesel, from the start"));
        });
        helper.succeedWhen(() -> {
            check(helper, pot.dish().is(ModOil.BIODIESEL_BOTTLE.get()), "and in its time, biodiesel");
            check(helper, pot.freed().stream().anyMatch(s -> s.is(Items.GLASS_BOTTLE)), "the methanol's bottle freed to take it");
        });
    }

    /** An empty bucket or bottle takes the pot's water or oil back out; a full pot refuses more, and says why. */
    private static void potPoursBackOut(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockState state = helper.getBlockState(pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, pos));
        check(helper, pot.amount() == 1_000 && player.getMainHandItem().is(Items.BUCKET), "a bucket of water in");
        check(helper, pot.refusesLiquid(PotRecipe.Liquid.WATER, 250) == IronPotBlockEntity.Refusal.FULL, "full: no more");
        check(helper, pot.refusesLiquid(PotRecipe.Liquid.OIL, 250) == IronPotBlockEntity.Refusal.OTHER_LIQUID, "and no oil into water");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, pos));
        check(helper, pot.amount() == 750 && player.getMainHandItem().is(Items.POTION), "a bottle of water back out");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hitTop(helper, pos));
        check(helper, pot.amount() == 750 && player.getMainHandItem().is(Items.BUCKET), "not enough left to fill a bucket");
        helper.succeed();
    }

    /**
     * A standing grill is a little table: what a campfire does not cook is used as on any block (placed on it), rather
     * than swallowed by the grill. What it does cook goes on the grate.
     */
    private static void grillLetsOtherThingsBeUsed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModCooking.GRILL.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var result = helper.getBlockState(pos).useItemOn(new ItemStack(Items.LANTERN), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                hitTop(helper, pos));
        check(helper, result instanceof InteractionResult.TryEmptyHandInteraction,
                "a lantern is not the grill's to take: " + result);
        var beef = helper.getBlockState(pos).useItemOn(new ItemStack(Items.BEEF), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                hitTop(helper, pos));
        check(helper, beef.consumesAction(), "beef goes on the grate");
        check(helper, helper.getBlockEntity(pos, GrillBlockEntity.class).items().get(0).is(Items.BEEF), "and lies there");
        helper.succeed();
    }

    private CookingTests() {}
}
