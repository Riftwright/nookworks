package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PlateBlockEntity;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.cooking.TrayBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.ItemResource;

/**
 * The cooking review (the user, 2026-11-14: 「a b c d全部やって」): the Frying Pan, a pot in all but what it holds and
 * cooks - a bottle of vegetable oil and never water, four things, its own recipes - set over heat as a pot is, on a Grill
 * over a campfire too; each vessel sends what is the other's to it; and a bottle set down as a bottle (a Honey Bottle)
 * goes on no plate or tray.
 */
final class FryingPanTests {
    private FryingPanTests() {}

    static final String[] NEED_KITCHEN = {"a_frying_pan_holds_a_bottle_of_oil_and_four_things", "a_frying_pan_sits_on_a_grill_over_a_campfire",
            "the_pot_and_the_pan_each_send_the_others_things_to_it", "a_honey_bottle_goes_on_no_plate_or_tray"};

    static void register() {
        NookworksGameTests.test(NEED_KITCHEN[0], 400, FryingPanTests::holds);
        NookworksGameTests.test(NEED_KITCHEN[1], FryingPanTests::onGrill);
        NookworksGameTests.test(NEED_KITCHEN[2], FryingPanTests::eachToItsOwn);
        NookworksGameTests.test(NEED_KITCHEN[3], FryingPanTests::honeyBottle);
    }

    private static BlockHitResult top(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, top(helper, pos));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.moveTo(aside.x, aside.y, aside.z);
        return player;
    }

    private static void done(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Over heat, a pipe's water goes not in and its oil only to a bottle's worth; a hopper's eggs four at most, and no
     * chicken (the pot's); a bottle of water poured by hand is turned down, a bottle of oil goes in, a bucket of oil does
     * not fit. The eggs fry, one for one, using a little oil - in the pan's own recipe.
     */
    private static void holds(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, ModCooking.FRYING_PAN.get());
        var pan = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        check(helper, pan.pan() && pan.capacity() == 250 && pan.limit() == 4, "the pan holds 250 mB and four things");
        check(helper, Move.insert(pan.liquidHandler(), FluidResource.of(Fluids.WATER), 1_000) == 0, "a pipe's water goes not in");
        check(helper, Move.insert(pan.itemHandler(), ItemResource.of(Items.CHICKEN), 1) == 0, "nor a chicken, which the pot cooks");
        ServerPlayer player = player(helper);
        use(helper, player, pos, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER));
        check(helper, pan.amount() == 0 && player.getMainHandItem().is(Items.POTION), "a bottle of water poured by hand is turned down");
        use(helper, player, pos, new ItemStack(ModOil.VEGETABLE_OIL_BUCKET.get()));
        check(helper, pan.amount() == 0 && player.getMainHandItem().is(ModOil.VEGETABLE_OIL_BUCKET.get()), "a bucket of oil does not fit");
        use(helper, player, pos, new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()));
        check(helper, pan.amount() == 250 && player.getMainHandItem().is(Items.GLASS_BOTTLE), "a bottle of oil goes in: " + pan.amount());
        check(helper, Move.insert(pan.liquidHandler(), FluidResource.of(PotRecipe.Liquid.OIL.fluid()), 100) == 0, "and fills it");
        check(helper, Move.insert(pan.itemHandler(), ItemResource.of(Items.EGG), 6) == 4, "a hopper's eggs: four go in");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeedWhen(() -> {
            check(helper, pan.dish().is(ModCooking.FRIED_EGG.get()) && pan.dish().getCount() == 4, "four fried eggs: " + pan.dish());
            check(helper, pan.amount() == 150 && pan.count() == 0, "25 mB of oil each: " + pan.amount() + " mB left");
        });
    }

    /**
     * A Frying Pan used on a Grill over a lit campfire takes the grill's place, sits on the grate and is hot; broken, the
     * grill stays over the fire and the pan comes away.
     */
    private static void onGrill(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        helper.setBlock(pos, ModCooking.GRILL.get().defaultBlockState().setValue(GrillBlock.OVER_FIRE, true));
        ServerPlayer player = player(helper);
        use(helper, player, pos, new ItemStack(ModCooking.FRYING_PAN_ITEM.get()));
        BlockState state = helper.getBlockState(pos);
        check(helper, state.is(ModCooking.FRYING_PAN.get()) && state.getValue(IronPotBlock.ON_GRILL), "the pan sits on the grate: " + state);
        check(helper, player.getMainHandItem().isEmpty(), "and is used up");
        check(helper, IronPotBlock.heated(helper.getLevel(), helper.absolutePos(pos)), "and is hot");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(pos));
        BlockState after = helper.getBlockState(pos);
        check(helper, after.is(ModCooking.GRILL.get()) && after.getValue(GrillBlock.OVER_FIRE), "broken, the grill stays over the fire");
        check(helper, helper.getEntities(EntityType.ITEM).stream().anyMatch(e -> e.getItem().is(ModCooking.FRYING_PAN_ITEM.get())),
                "and the pan comes away");
        done(helper, player);
    }

    /**
     * A Bowl of Rice clicked on an Iron Pot (fried rice is the pan's) and a chicken on a Frying Pan (it is boiled or fried
     * in the pot) stay in hand, the vessel empty: each is the other's.
     */
    private static void eachToItsOwn(GameTestHelper helper) {
        BlockPos potAt = new BlockPos(1, 2, 1);
        BlockPos panAt = new BlockPos(3, 2, 1);
        helper.setBlock(potAt.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(panAt.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(potAt, ModCooking.IRON_POT.get());
        helper.setBlock(panAt, ModCooking.FRYING_PAN.get());
        var pot = helper.getBlockEntity(potAt, IronPotBlockEntity.class);
        var pan = helper.getBlockEntity(panAt, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.OIL, 1_000);
        pan.addLiquid(PotRecipe.Liquid.OIL, 250);
        ServerPlayer player = player(helper);
        check(helper, PotRecipe.isIngredient(helper.getLevel(), new ItemStack(ModCooking.COOKED_RICE.get()), true)
                && !PotRecipe.isIngredient(helper.getLevel(), new ItemStack(ModCooking.COOKED_RICE.get()), false), "rice is the pan's");
        use(helper, player, potAt, new ItemStack(ModCooking.COOKED_RICE.get()));
        check(helper, pot.count() == 0 && player.getMainHandItem().is(ModCooking.COOKED_RICE.get()), "the pot leaves the rice in hand");
        use(helper, player, panAt, new ItemStack(Items.CHICKEN));
        check(helper, pan.count() == 0 && player.getMainHandItem().is(Items.CHICKEN) && player.getMainHandItem().getCount() == 1,
                "the pan leaves the chicken in hand, uneaten");
        use(helper, player, panAt, new ItemStack(ModCooking.COOKED_RICE.get()));
        check(helper, pan.count() == 1, "and takes the rice");
        done(helper, player);
    }

    /**
     * A Honey Bottle, which is set down as a bottle of its own, goes on no plate or tray (the user, 2026-11-14:
     * 「ハチミツ入りの瓶を皿にのせるのはちょっと微妙」); a pumpkin pie, which is a dish of its own now, goes on both.
     */
    private static void honeyBottle(GameTestHelper helper) {
        check(helper, !PlateBlockEntity.fits(new ItemStack(Items.HONEY_BOTTLE)) && !TrayBlockEntity.fits(new ItemStack(Items.HONEY_BOTTLE)),
                "no Honey Bottle on a plate or a tray");
        check(helper, PlateBlockEntity.fits(new ItemStack(Items.PUMPKIN_PIE)) && TrayBlockEntity.fits(new ItemStack(Items.PUMPKIN_PIE)),
                "a pumpkin pie on either");
        check(helper, PlateBlockEntity.fits(new ItemStack(ModCooking.FRIED_EGG.get())), "and our dishes");
        helper.succeed();
    }
}
