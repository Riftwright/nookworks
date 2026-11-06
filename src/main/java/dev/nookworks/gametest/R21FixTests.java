package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.block.DiceBlock;
import dev.nookworks.block.DiceTumble;
import dev.nookworks.block.entity.DiceBlockEntity;
import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.HandMachineBlockEntity;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.feature.ExplosionAftermath;
import dev.nookworks.feature.Features;
import dev.nookworks.feature.PartGraph;
import dev.nookworks.feature.PlacedLogs;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.item.Hammers;
import dev.nookworks.machine.ElectrolyticCellBlockEntity;
import dev.nookworks.machine.GaugePouring;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.quench.Quenching;
import dev.nookworks.recycling.RecyclingRecipe;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HookBlock;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerControllerExtensions;
import dev.nookworks.spawner.SpawnerControllerMenu;
import dev.nookworks.spawner.SpawnerLinkSenderBlockEntity;
import dev.nookworks.spawner.SpawnerPickup;
import dev.nookworks.storage.StorageSpill;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Fixes from the r21 release review (2026-11-06, AI_review/r21): a shift-click into a page slot that takes nothing no
 * longer holds the server in a loop, the Recycler keeps off machines still holding liquid, an add-on's bottle takes a
 * mob at any health by our click too, a pot set on a grill is its setter's, buckets and bottles pour where a tank is,
 * hand machines give back what was put in and say why they refuse, and the smaller things below. Then round two's: an
 * Iron Pot says why it cooks nothing and tips out what a bottle cannot hold, a served dish leaves the hand, refused hangs
 * say why, a block burnt away forgets its marks, a Mercury Bucket pours into a mercury machine, a Stirring Tank fed from
 * above keeps its biodiesel out of the feed.
 */
final class R21FixTests {
    private R21FixTests() {}

    static void register() {
        test("a_shift_click_into_a_closed_page_slot_ends", R21FixTests::closedPageSlot);
        test("the_recycler_keeps_off_a_machine_holding_liquid", R21FixTests::recyclerKeepsOffLiquid);
        test("an_add_ons_bottle_takes_any_health_by_our_click", R21FixTests::anyHealthBottle);
        test("a_pot_set_on_a_grill_is_its_setters", R21FixTests::potOnGrillOwned);
        test("a_copper_tank_turns_a_mercury_bucket_down", R21FixTests::tankRefusesMercury);
        test("a_water_bucket_pours_into_the_retort", R21FixTests::retortTakesWater);
        test("an_oil_bottle_pours_into_the_generator", R21FixTests::generatorTakesOil);
        test("hand_machines_give_back_and_say_why", R21FixTests::handMachinesGiveBack);
        test("a_still_hot_add_on_ingot_is_not_heated_again", R21FixTests::hotStampRefused);
        test("a_machine_takes_what_a_barrel_still_has_to_spill", R21FixTests::takePendingSpill);
        test("a_brewed_enderman_drops_the_block_it_carried", R21FixTests::endermanBlockDrops);
        test("a_die_set_down_turned_forgets_its_roll", R21FixTests::diceForgetsRoll);
        test("small_r21_rules_hold", R21FixTests::smallRules);
        test("an_iron_pot_over_the_fire_says_why_it_cooks_nothing", R21FixTests::potSaysWhy);
        test("an_iron_pot_tips_out_what_a_bottle_cannot_hold", R21FixTests::potTipsOut);
        test("a_served_dish_leaves_the_hand_free", 900, R21FixTests::servedDishLeavesHand);
        test("a_bulb_with_no_room_under_the_rope_says_so", R21FixTests::bulbSaysNoRoom);
        test("a_mark_goes_with_a_block_burnt_away", R21FixTests::markGoesWithBlock);
        test("a_mercury_bucket_pours_into_a_mercury_machine", R21FixTests::mercuryBucketPours);
        test("a_stirring_tank_fed_from_above_sends_no_biodiesel_up", 500, R21FixTests::topFeedKept);
        test("berries_off_a_hook_go_to_the_hand", R21FixTests::hookBerriesToHand);
        test("a_gate_swung_by_a_wind_charge_is_not_gone", R21FixTests::swungGateNotGone);
        test("a_sender_set_by_an_unknown_machine_has_no_owner", R21FixTests::senderOwnerOfFake);
        test("an_iron_pot_names_the_liquid_its_things_cook_in", R21FixTests::potNamesTheLiquid);
        test("an_iron_pot_cooking_keeps_its_liquid_from_a_bottle", R21FixTests::cookingPotKeepsLiquid);
        test("a_stirring_tank_sends_no_biodiesel_into_a_line_going_nowhere", 600, R21FixTests::lineGoingNowhere);
        test("too_hot_is_the_velarium_ingots_to_give", R21FixTests::tooHotFromVelariumOnly);
        test("round_three_rules_hold", R21FixTests::roundThreeRules);
    }

    private static ServerPlayer clicker(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        return player;
    }

    private static void done(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Right-clicks the middle of {@code face} of {@code pos} holding {@code stack}, as the game makes a click. */
    private static void click(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        Vec3 point = Vec3.atCenterOf(at).relative(face, 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, at, false));
    }

    /**
     * An add-on's page slot that holds nothing for now (nookTechnology's Data Bank with no power) takes a shift-clicked
     * egg in "moving" none of it: vanilla's shift-click asked again for ever, and the server hung. Driven as the game
     * drives a click, through the menu's clicked(); a slot asked more than a few times breaks the loop and fails.
     */
    private static void closedPageSlot(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 3, 1);
        helper.setBlock(at.below(), ModBlocks.SPAWNER_LINK_RECEIVER.get());
        helper.setBlock(at, ModBlocks.SPAWNER_CONTROLLER.get());
        SpawnerControllerBlockEntity controller = helper.getBlockEntity(at, SpawnerControllerBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper);
        int[] asked = {0};
        Container dark = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return 0; // as the bank does while it has no power
            }
        };
        SpawnerControllerExtensions.Extension bank = new SpawnerControllerExtensions.Extension() {
            @Override
            public Identifier id() {
                return Nookworks.id("test_dark_bank");
            }

            @Override
            public int slotCount() {
                return 1;
            }

            @Override
            public @Nullable Container container(SpawnerControllerBlockEntity c) {
                return dark;
            }

            @Override
            public boolean mayPlace(int index, ItemStack stack) {
                if (++asked[0] > 50) {
                    throw new IllegalStateException("the shift-click asked again and again: it would never end");
                }
                return stack.is(Items.PIG_SPAWN_EGG);
            }

            @Override
            public boolean showsInventory() {
                return true;
            }
        };
        List<SpawnerControllerExtensions.Extension> before = SpawnerControllerExtensions.all();
        try {
            SpawnerControllerExtensions.replaceForTests(List.of(bank));
            SpawnerControllerMenu menu = (SpawnerControllerMenu) controller.createMenu(1, player.getInventory(), player);
            int hotbar = menu.slots.size() - 9;
            player.getInventory().setItem(0, new ItemStack(Items.PIG_SPAWN_EGG, 3));
            menu.clicked(hotbar, 0, ContainerInput.QUICK_MOVE, player);
            check(helper, player.getInventory().getItem(0).getCount() == 3 && dark.getItem(0).isEmpty(),
                    "the eggs stay where they were: " + player.getInventory().getItem(0));
            check(helper, asked[0] <= 2, "and the slot was asked once, not over and over: " + asked[0]);
        } finally {
            SpawnerControllerExtensions.replaceForTests(before);
        }
        done(helper, player);
    }

    /** A Retort picked up with water in it is no scrap: the Recycler would shred the water with it, as mercury. */
    private static void recyclerKeepsOffLiquid(GameTestHelper helper) {
        ItemStack plain = new ItemStack(ModItems.RETORT.get());
        ItemStack holding = plain.copy();
        holding.set(ModDataComponents.HELD_FLUIDS.get(), List.of(SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 1000))));
        check(helper, RecyclingRecipe.of(plain) != null, "an empty Retort is recycled");
        check(helper, RecyclingRecipe.of(holding) == null && RecyclingRecipe.holdsThings(holding), "one still holding water is not");
        // an add-on's word on its own filled things (a filled Mob Data Bank): asked too
        RecyclingRecipe.holdsThingsWhen(stack -> "r21 holds".equals(stack.getHoverName().getString()));
        ItemStack marked = plain.copy();
        marked.set(DataComponents.CUSTOM_NAME, Component.literal("r21 holds"));
        check(helper, RecyclingRecipe.of(marked) == null && RecyclingRecipe.of(plain) != null, "an add-on's filled one is refused too");
        helper.succeed();
    }

    /**
     * A bottle an add-on marks as taking any health (nookTechnology's Reinforced Mob Bottle, through anyHealthWhen) takes
     * a cow at full health by our own catch - the click on a mob and the Trading Counter's reach - and keeps its name.
     */
    private static void anyHealthBottle(GameTestHelper helper) {
        MobBottleItem.anyHealthWhen(stack -> "r21 any health".equals(stack.getHoverName().getString()));
        ServerPlayer player = clicker(helper);
        Cow cow = helper.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(2, 2, 2));
        ItemStack bottle = new ItemStack(ModItems.MOB_BOTTLE.get());
        bottle.set(DataComponents.CUSTOM_NAME, Component.literal("r21 any health"));
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        check(helper, MobBottleItem.takesAnyHealth(bottle) && !MobBottleItem.takesAnyHealth(new ItemStack(ModItems.MOB_BOTTLE.get())),
                "the add-on's word is asked of its bottle only");
        MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, cow);
        ItemStack held = player.getMainHandItem();
        check(helper, MobBottleItem.captured(held) == EntityTypes.COW, "the healthy cow went in: " + held);
        check(helper, held.has(DataComponents.CUSTOM_NAME), "and the bottle kept its name");
        done(helper, player);
    }

    /** An Iron Pot set on a grill over a fire is its setter's, as one set down by hand: an unowned one drew from any pipe. */
    private static void potOnGrillOwned(GameTestHelper helper) {
        BlockPos grill = new BlockPos(2, 3, 2);
        helper.setBlock(grill.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        helper.setBlock(grill, ModCooking.GRILL.get().defaultBlockState().setValue(GrillBlock.OVER_FIRE, true));
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(ModCooking.IRON_POT_ITEM.get()), grill, Direction.UP);
        var pot = helper.getLevel().getBlockEntity(helper.absolutePos(grill)) instanceof IronPotBlockEntity p ? p : null;
        check(helper, pot != null, "the pot stands on the grill: " + helper.getBlockState(grill));
        check(helper, player.getUUID().equals(pot.owner()), "and is its setter's: " + pot.owner());
        done(helper, player);
    }

    /** A mercury bucket held to a Copper Tank is turned down, not poured out as a pool beside it. */
    private static void tankRefusesMercury(GameTestHelper helper) {
        BlockPos tank = new BlockPos(2, 2, 2);
        helper.setBlock(tank, ModBlocks.COPPER_TANK.get());
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(ModItems.MERCURY_BUCKET.get()), tank, Direction.NORTH);
        check(helper, player.getMainHandItem().is(ModItems.MERCURY_BUCKET.get()), "the bucket keeps its mercury: " + player.getMainHandItem());
        check(helper, helper.getBlockState(tank.north()).isAir(), "and none was poured beside the tank: " + helper.getBlockState(tank.north()));
        check(helper, helper.getBlockEntity(tank, CopperTankBlockEntity.class).amount() == 0, "nor into it");
        done(helper, player);
    }

    /** A water bucket clicked on a Sealed Retort fills its water tank, as at the Stirring Tank. */
    private static void retortTakesWater(GameTestHelper helper) {
        BlockPos retort = new BlockPos(2, 2, 2);
        helper.setBlock(retort, ModBlocks.RETORT.get());
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(Items.WATER_BUCKET), retort, Direction.UP);
        check(helper, helper.getBlockEntity(retort, RetortBlockEntity.class).waterStored() == 1000,
                "the water went in: " + helper.getBlockEntity(retort, RetortBlockEntity.class).waterStored());
        check(helper, player.getMainHandItem().is(Items.BUCKET), "and the bucket came back empty: " + player.getMainHandItem());
        done(helper, player);
    }

    /** A bottle of vegetable oil clicked on a Generator pours into its fuel tank. */
    private static void generatorTakesOil(GameTestHelper helper) {
        BlockPos generator = new BlockPos(2, 2, 2);
        helper.setBlock(generator, ModBlocks.GENERATOR.get());
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()), generator, Direction.UP);
        check(helper, helper.getBlockEntity(generator, GeneratorBlockEntity.class).fuelStored() == OilFluids.BOTTLE,
                "the oil went in: " + helper.getBlockEntity(generator, GeneratorBlockEntity.class).fuelStored());
        check(helper, player.getMainHandItem().is(Items.GLASS_BOTTLE), "and the bottle came back: " + player.getMainHandItem());
        done(helper, player);
    }

    /**
     * The Rice Polisher gives back brown rice put in by mistake, as the Hand Quern its wheat; a Hand Quern holding wheat
     * turns bread down, the bread kept in hand.
     */
    private static void handMachinesGiveBack(GameTestHelper helper) {
        BlockPos polisher = new BlockPos(1, 2, 1);
        helper.setBlock(polisher, ModCrops.RICE_POLISHER.get());
        RicePolisherBlockEntity rice = helper.getBlockEntity(polisher, RicePolisherBlockEntity.class);
        rice.setItem(RicePolisherBlockEntity.INPUT, new ItemStack(ModCrops.BROWN_RICE.get(), 64));
        List<ItemStack> back = rice.takeOut();
        check(helper, back.size() == 1 && back.get(0).is(ModCrops.BROWN_RICE.get()) && back.get(0).getCount() == 64,
                "the 64 brown rice came back: " + back);

        BlockPos quern = new BlockPos(3, 2, 1);
        helper.setBlock(quern, ModKitchen.HAND_QUERN.get());
        HandMachineBlockEntity hand = helper.getBlockEntity(quern, HandMachineBlockEntity.class);
        hand.setItem(HandMachineBlockEntity.INPUT, new ItemStack(Items.WHEAT, 3));
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(Items.BREAD, 2), quern, Direction.UP);
        check(helper, player.getMainHandItem().is(Items.BREAD) && player.getMainHandItem().getCount() == 2,
                "the bread stays in hand: " + player.getMainHandItem());
        check(helper, hand.getItem(HandMachineBlockEntity.INPUT).is(Items.WHEAT), "and the wheat in the quern");
        done(helper, player);
    }

    /** Whatever an item is, a heating stamp still within the hot time keeps it out of the fire (an add-on's red-hot ingot). */
    private static void hotStampRefused(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        ItemStack hot = new ItemStack(Items.IRON_INGOT);
        hot.set(ModDataComponents.HEATED_AT.get(), now);
        ItemStack cool = new ItemStack(Items.IRON_INGOT);
        cool.set(ModDataComponents.HEATED_AT.get(), now - Quenching.HOT_TICKS - 1);
        check(helper, Quenching.stampedHot(hot, now) && !Quenching.stampedHot(cool, now) && !Quenching.stampedHot(new ItemStack(Items.IRON_INGOT), now),
                "hot while stamped within the time, cool after it or unstamped");
        helper.succeed();
    }

    /** A machine breaking a barrel takes what is still to spill there, rather than leaving it to fall over the next ticks. */
    private static void takePendingSpill(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < StorageSpill.PER_TICK + 36; i++) {
            stacks.add(new ItemStack(Items.COBBLESTONE, 64));
        }
        StorageSpill.spill(level, pos, stacks);
        int waiting = StorageSpill.pending(level, pos);
        List<ItemStack> taken = StorageSpill.takePending(level, pos);
        check(helper, waiting >= 36 && taken.size() == waiting, "what waited was taken: " + waiting + " / " + taken.size());
        check(helper, StorageSpill.pending(level, pos) == 0 && StorageSpill.takePending(level, pos).isEmpty(), "and nothing waits there any more");
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** The block an enderman carried comes out when its bottle is emptied for an egg, as its death would let it fall. */
    private static void endermanBlockDrops(GameTestHelper helper) {
        var level = helper.getLevel();
        Enderman enderman = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.MOB_SUMMONED);
        enderman.setCarriedBlock(Blocks.MELON.defaultBlockState());
        ItemStack bottle = MobBottleItem.filled(enderman);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.5, 2.5));
        MobBottleItem.spillCarried(level, bottle, at);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(at)).inflate(2),
                item -> item.getItem().is(Items.MELON));
        check(helper, !drops.isEmpty(), "the melon it carried came out");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** A rolled die set down turned by a structure forgets its last roll and lies as its turned state has it. */
    private static void diceForgetsRoll(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.DICE.get());
        DiceBlockEntity die = helper.getBlockEntity(pos, DiceBlockEntity.class);
        var state = helper.getBlockState(pos);
        die.start(42L, helper.getLevel().getGameTime(), DiceTumble.centred(state.getValue(DiceBlock.VALUE), state.getValue(DiceBlock.FACING)), Direction.EAST);
        check(helper, die.tumble() != null, "it has rolled");
        die.applyStructureRotation(Mirror.NONE, Rotation.NONE);
        check(helper, die.tumble() != null, "set down as it was, it keeps its roll");
        die.applyStructureRotation(Mirror.NONE, Rotation.CLOCKWISE_90);
        check(helper, die.tumble() == null, "set down turned, it forgets it");
        helper.succeed();
    }

    /** The smaller rules: the hammers pick up spawners through their own tag, wooden furniture burns, a full tank is amber. */
    private static void smallRules(GameTestHelper helper) {
        check(helper, new ItemStack(ModItems.IRON_HAMMER.get()).is(SpawnerPickup.TOOLS) && new ItemStack(ModItems.IRON_HAMMER.get()).is(Hammers.TAG),
                "a hammer is a spawner pickup tool");
        var chair = BuiltInItem.of("oak_chair");
        var shelf = BuiltInItem.of("oak_wall_shelf");
        check(helper, new ItemStack(chair).has(DataComponents.COOKING_FUEL), "an oak chair burns");
        check(helper, !new ItemStack(shelf).has(DataComponents.COOKING_FUEL), "a wall shelf, on iron brackets, does not");
        check(helper, !new ItemStack(BuiltInItem.of("crate")).has(DataComponents.COOKING_FUEL), "nor a crate, which keeps its contents");
        check(helper, MachineStatus.TANK_FULL.tone() == MachineStatus.Tone.WARN, "a full tank reads amber");
        helper.succeed();
    }

    // ---- round two ----

    /** An Iron Pot clicked onto a grill over a lit campfire, by {@code player}. */
    private static IronPotBlockEntity heatedPot(GameTestHelper helper, ServerPlayer player, BlockPos grill) {
        helper.setBlock(grill.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        helper.setBlock(grill, ModCooking.GRILL.get().defaultBlockState().setValue(GrillBlock.OVER_FIRE, true));
        click(helper, player, new ItemStack(ModCooking.IRON_POT_ITEM.get()), grill, Direction.UP);
        return helper.getBlockEntity(grill, IronPotBlockEntity.class);
    }

    private static boolean says(@Nullable Component line, String key) {
        return line != null && line.getContents() instanceof TranslatableContents words && words.getKey().equals(key);
    }

    /** Udon a bottle of water short says how much water it needs; with an egg in too, that nothing cooks from these. */
    private static void potSaysWhy(GameTestHelper helper) {
        ServerPlayer player = clicker(helper);
        IronPotBlockEntity pot = heatedPot(helper, player, new BlockPos(2, 3, 2));
        pot.addLiquid(PotRecipe.Liquid.WATER, OilFluids.BOTTLE);
        pot.addIngredient(new ItemStack(ModKitchen.RAW_UDON.get()));
        pot.addIngredient(new ItemStack(ModKitchen.RAW_UDON.get()));
        pot.addIngredient(new ItemStack(Items.DRIED_KELP));
        Component why = pot.stuck();
        check(helper, says(why, "nookworks.iron_pot.needs_water")
                && "500".equals(String.valueOf(((TranslatableContents) why.getContents()).getArgs()[0])),
                "a bottle of water under udon's 500 mB says how much it needs: " + why);
        List<String> lines = new ArrayList<>();
        pot.readout(line -> lines.add(line.getString()));
        check(helper, lines.contains(why.getString()), "the readout (Jade's) says it too: " + lines);
        check(helper, pot.shown() != null && pot.shown().note().isPresent(), "and so does an empty hand's click");
        pot.addIngredient(new ItemStack(Items.EGG));
        check(helper, says(pot.stuck(), "nookworks.iron_pot.no_recipe"), "an egg in with the udon: nothing cooks from these: " + pot.stuck());
        done(helper, player);
    }

    /** Under a bottle of water left in a pot is tipped out by an empty bottle; bottles' worth is not, for a bucket. */
    private static void potTipsOut(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, ModCooking.IRON_POT.get());
        IronPotBlockEntity pot = helper.getBlockEntity(at, IronPotBlockEntity.class);
        pot.addLiquid(PotRecipe.Liquid.WATER, 200);
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(Items.GLASS_BOTTLE), at, Direction.UP);
        check(helper, pot.amount() == 0 && player.getMainHandItem().is(Items.GLASS_BOTTLE) && player.getMainHandItem().getCount() == 1,
                "200 mB is tipped out, the bottle left empty: " + pot.amount() + ", " + player.getMainHandItem());
        pot.addLiquid(PotRecipe.Liquid.WATER, 600);
        click(helper, player, new ItemStack(Items.BUCKET), at, Direction.UP);
        check(helper, pot.amount() == 600 && player.getMainHandItem().is(Items.BUCKET),
                "600 mB, two bottles' worth, is kept from a bucket: " + pot.amount() + ", " + player.getMainHandItem());
        done(helper, player);
    }

    /** Rice served into the last bowl in hand leaves the hand free, and the next click does not put it back in. */
    private static void servedDishLeavesHand(GameTestHelper helper) {
        ServerPlayer player = clicker(helper);
        BlockPos grill = new BlockPos(2, 3, 2);
        IronPotBlockEntity pot = heatedPot(helper, player, grill);
        pot.addLiquid(PotRecipe.Liquid.WATER, IronPotBlockEntity.CAPACITY);
        pot.addIngredient(new ItemStack(ModCrops.WHITE_RICE.get()));
        helper.startSequence().thenWaitUntil(() -> check(helper, !pot.dish().isEmpty(), "the rice cooks")).thenExecute(() -> {
            click(helper, player, new ItemStack(Items.BOWL), grill, Direction.UP);
            check(helper, player.getMainHandItem().isEmpty(), "the bowl of rice is off the hand: " + player.getMainHandItem());
            check(helper, player.getInventory().countItem(ModCooking.COOKED_RICE.get()) == 1, "and in the inventory");
            click(helper, player, ItemStack.EMPTY, grill, Direction.UP);
            check(helper, pot.count() == 0 && player.getInventory().countItem(ModCooking.COOKED_RICE.get()) == 1,
                    "the next click puts nothing back in the pot: " + pot.count());
        }).thenExecute(() -> done(helper, player)).thenSucceed();
    }

    /** A Filament Bulb held to a Thin Rope whose end has a block under it says there is no room, as a bottle does. */
    private static void bulbSaysNoRoom(GameTestHelper helper) {
        BlockPos rope = new BlockPos(2, 3, 2);
        helper.setBlock(rope.above(), Blocks.STONE);
        helper.setBlock(rope, ModBlocks.THIN_ROPE.get());
        helper.setBlock(rope.below(), Blocks.OAK_PLANKS);
        List<Component> said = new ArrayList<>();
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, said);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        click(helper, player, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()), rope, Direction.NORTH);
        check(helper, said.stream().anyMatch(line -> says(line, "message.nookworks.rope.no_room")), "it says why: " + said);
        check(helper, player.getMainHandItem().is(ModRope.FILAMENT_BULB_ITEM.get()), "and the bulb stays in hand");
        done(helper, player);
    }

    /** A placed log turned to fire, or taken away without a break, forgets it was placed; changes beside it do not. */
    private static void markGoesWithBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos log = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos burnt = helper.absolutePos(new BlockPos(4, 2, 1));
        level.setBlock(log, Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(burnt, Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_ALL);
        PlacedLogs.mark(level, log, true);
        PlacedLogs.mark(level, burnt, true);
        level.setBlock(burnt, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
        check(helper, !PlacedLogs.isPlaced(level, burnt), "a placed log burnt to fire forgets it was placed");
        level.setBlock(log.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(log.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        check(helper, PlacedLogs.isPlaced(level, log), "one beside a block put down and taken keeps its mark");
        level.removeBlock(log, false);
        check(helper, !PlacedLogs.isPlaced(level, log), "and forgets it when taken away without a break");
        level.setBlock(burnt, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.succeed();
    }

    /** A Mercury Bucket clicked on a Stirring Tank pours in, as at its gauge, and the bucket comes back empty. */
    private static void mercuryBucketPours(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, ModBlocks.STIRRING_TANK.get());
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(ModItems.MERCURY_BUCKET.get()), at, Direction.UP);
        var tank = helper.getBlockEntity(at, StirringTankBlockEntity.class);
        check(helper, tank.mercuryStore().amount() == 1000, "the mercury went in: " + tank.mercuryStore().amount());
        check(helper, player.getMainHandItem().is(Items.BUCKET), "and the bucket came back empty: " + player.getMainHandItem());
        BlockPos cellAt = new BlockPos(4, 2, 2);
        helper.setBlock(cellAt, ModBlocks.ELECTROLYTIC_CELL.get());
        click(helper, player, new ItemStack(ModItems.MERCURY_BUCKET.get()), cellAt, Direction.UP);
        check(helper, helper.getBlockEntity(cellAt, ElectrolyticCellBlockEntity.class).mercuryStore().amount() == 1000
                && player.getMainHandItem().is(Items.BUCKET), "the Electrolytic Cell takes a bucket as the Stirring Tank does");
        BlockPos retortAt = new BlockPos(2, 2, 4);
        helper.setBlock(retortAt, ModBlocks.RETORT.get());
        click(helper, player, new ItemStack(ModItems.MERCURY_BUCKET.get()), retortAt, Direction.UP);
        var retort = helper.getBlockEntity(retortAt, RetortBlockEntity.class);
        check(helper, player.getMainHandItem().is(ModItems.MERCURY_BUCKET.get()) && retort.mercuryStore().amount() == 0,
                "the Retort, which makes mercury, turns a bucket down by a click");
        var menu = new MachineMenu(1, player.getInventory(), retort.layout(), retort.slots(), MachineMenu.data(retort), retort);
        int gauge = -1;
        for (int i = 0; i < retort.guiTanks().size(); i++) {
            if (retort.guiTanks().get(i).kind() == GuiTank.Kind.MERCURY) {
                gauge = i;
            }
        }
        menu.setCarried(new ItemStack(ModItems.MERCURY_BUCKET.get()));
        check(helper, gauge >= 0 && !GaugePouring.clicked(player, menu, gauge) && retort.mercuryStore().amount() == 0
                && menu.getCarried().is(ModItems.MERCURY_BUCKET.get()), "and at its gauge alike");
        menu.setCarried(ItemStack.EMPTY);
        done(helper, player);
    }

    /**
     * Oil put in from above makes the top a feed: the biodiesel stirred from it is not pushed up into the tank there. With
     * that tank taken away and set down again, the top sends biodiesel out as before.
     */
    private static void topFeedKept(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at, ModBlocks.STIRRING_TANK.get());
        helper.setBlock(at.above(), ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(at, StirringTankBlockEntity.class);
        tank.energy().set((int) tank.energy().getCapacityAsLong());
        tank.setItem(0, new ItemStack(ModOil.METHANOL.get(), 2));
        tank.setItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()));
        var fromAbove = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at), Direction.UP);
        try (Transaction tx = Transaction.openRoot()) {
            int poured = fromAbove.insert(FluidResource.of(OilFluids.SOURCE.get()), StirringTankBlockEntity.OIL_PER_BATCH, tx);
            tx.commit();
            check(helper, poured == StirringTankBlockEntity.OIL_PER_BATCH, "oil comes in from above: " + poured);
        }
        check(helper, tank.topFeeds(), "and the top is a feed");
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, tank.biodieselStored() == StirringTankBlockEntity.OIL_PER_BATCH,
                        "a batch is stirred: " + tank.biodieselStored()))
                .thenExecuteAfter(5, () -> {
                    check(helper, helper.getBlockEntity(at.above(), CopperTankBlockEntity.class).amount() == 0
                            && tank.biodieselStored() == StirringTankBlockEntity.OIL_PER_BATCH, "none of it went up into the feed");
                    helper.setBlock(at.above(), Blocks.AIR);
                })
                .thenExecuteAfter(3, () -> {
                    check(helper, !tank.topFeeds(), "the feed taken away, the top is free");
                    helper.setBlock(at.above(), ModBlocks.COPPER_TANK.get());
                })
                .thenWaitUntil(() -> check(helper, helper.getBlockEntity(at.above(), CopperTankBlockEntity.class).amount()
                        == StirringTankBlockEntity.OIL_PER_BATCH, "a tank set on it now takes the biodiesel"))
                .thenSucceed();
    }

    /** Berries taken off a Hook go into the inventory - popped onto the ground, they were lost with block_drops off. */
    private static void hookBerriesToHand(GameTestHelper helper) {
        BlockPos hook = new BlockPos(2, 3, 2);
        helper.setBlock(hook.above(), Blocks.STONE);
        helper.setBlock(hook, ModRope.HOOK.get().defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.CEILING)
                .setValue(HookBlock.SPRIG, HookBlock.Sprig.SWEET_BERRIES));
        ServerPlayer player = clicker(helper);
        click(helper, player, ItemStack.EMPTY, hook, Direction.DOWN);
        check(helper, helper.getBlockState(hook).getValue(HookBlock.SPRIG) == HookBlock.Sprig.NONE, "the berries come off");
        check(helper, player.getInventory().countItem(Items.SWEET_BERRIES) == 1, "into the inventory");
        check(helper, player.getMainHandItem().isEmpty(), "off the hand, so the next click does not hang them again");
        check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.absoluteAABB(new AABB(0, 0, 0, 5, 6, 5))).isEmpty(),
                "with none dropped");
        done(helper, player);
    }

    /** A fence gate a blast swings open (a wind charge's) is not gone, so its oil and coating stay; one blown away is. */
    private static void swungGateNotGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos gate = new BlockPos(2, 2, 2);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE);
        List<BlockPos> gone = new ArrayList<>();
        ExplosionAftermath.whenGone(level, List.of(helper.absolutePos(gate)), (in, pos) -> gone.add(pos));
        helper.setBlock(gate, helper.getBlockState(gate).setValue(FenceGateBlock.OPEN, true));
        helper.startSequence().thenExecuteAfter(3, () -> {
            check(helper, gone.isEmpty(), "a gate swung open is not gone: " + gone);
            ExplosionAftermath.whenGone(level, List.of(helper.absolutePos(gate)), (in, pos) -> gone.add(pos));
            helper.setBlock(gate, Blocks.AIR);
        }).thenExecuteAfter(3, () -> check(helper, gone.size() == 1, "one blown away is: " + gone)).thenSucceed();
    }

    /** A Spawner Link Sender set down by a machine of no known player owns nothing; one a player sets down is theirs. */
    private static void senderOwnerOfFake(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, ModBlocks.SPAWNER_LINK_SENDER.get());
        BlockPos abs = helper.absolutePos(at);
        ItemStack item = new ItemStack(ModBlocks.SPAWNER_LINK_SENDER.get());
        FakePlayer machine = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[r21 machine]"));
        ModBlocks.SPAWNER_LINK_SENDER.get().setPlacedBy(level, abs, level.getBlockState(abs), machine, item);
        check(helper, helper.getBlockEntity(at, SpawnerLinkSenderBlockEntity.class).owner() == null,
                "set by an unknown machine it owns nothing, for the first to pair with it");
        ItemStack receiver = new ItemStack(ModItems.SPAWNER_LINK_RECEIVER.get());
        machine.setItemInHand(InteractionHand.MAIN_HAND, receiver);
        machine.gameMode.useItemOn(machine, level, receiver, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        check(helper, helper.getBlockEntity(at, SpawnerLinkSenderBlockEntity.class).owner() == null,
                "nor is it adopted by an unknown machine pairing with it: " + helper.getBlockEntity(at, SpawnerLinkSenderBlockEntity.class).owner());
        ServerPlayer player = clicker(helper);
        ModBlocks.SPAWNER_LINK_SENDER.get().setPlacedBy(level, abs, level.getBlockState(abs), player, item);
        check(helper, player.getUUID().equals(helper.getBlockEntity(at, SpawnerLinkSenderBlockEntity.class).owner()),
                "set by a player it is theirs");
        done(helper, player);
    }

    /**
     * Eggs in oil, on the way to tamagoyaki, are not told they need water; a chicken in water, on the way to cream stew,
     * is not told it needs oil; white rice in oil is told it cooks in water.
     */
    private static void potNamesTheLiquid(GameTestHelper helper) {
        ServerPlayer player = clicker(helper);
        IronPotBlockEntity eggs = heatedPot(helper, player, new BlockPos(1, 3, 1));
        eggs.addLiquid(PotRecipe.Liquid.OIL, OilFluids.BOTTLE);
        eggs.addIngredient(new ItemStack(Items.EGG));
        check(helper, says(eggs.stuck(), "nookworks.iron_pot.no_recipe"), "an egg in oil may become tamagoyaki: " + eggs.stuck());
        IronPotBlockEntity stew = heatedPot(helper, player, new BlockPos(3, 3, 1));
        stew.addLiquid(PotRecipe.Liquid.WATER, IronPotBlockEntity.CAPACITY);
        stew.addIngredient(new ItemStack(Items.CHICKEN));
        check(helper, says(stew.stuck(), "nookworks.iron_pot.no_recipe"), "a chicken in water may become cream stew: " + stew.stuck());
        IronPotBlockEntity rice = heatedPot(helper, player, new BlockPos(5, 3, 1));
        rice.addLiquid(PotRecipe.Liquid.OIL, OilFluids.BOTTLE);
        rice.addIngredient(new ItemStack(ModCrops.WHITE_RICE.get()));
        check(helper, says(rice.stuck(), "nookworks.iron_pot.cooks_in_water"), "white rice in oil cooks in water: " + rice.stuck());
        done(helper, player);
    }

    /**
     * An egg boiling in 200 mB keeps its water from an empty bottle (it is not tipped out mid-cook); its water taken during
     * the settle wait, the pot reads as not cooking, not "Cooking 0%".
     */
    private static void cookingPotKeepsLiquid(GameTestHelper helper) {
        ServerPlayer player = clicker(helper);
        BlockPos grill = new BlockPos(2, 3, 2);
        IronPotBlockEntity pot = heatedPot(helper, player, grill);
        pot.addLiquid(PotRecipe.Liquid.WATER, 200);
        pot.addIngredient(new ItemStack(Items.EGG));
        helper.startSequence().thenExecuteAfter(3, () -> {
            check(helper, pot.percent() >= 0, "the egg is on to boil: " + pot.percent());
            click(helper, player, new ItemStack(Items.GLASS_BOTTLE), grill, Direction.UP);
            check(helper, pot.amount() == 200 && player.getMainHandItem().is(Items.GLASS_BOTTLE),
                    "its water stays while it cooks: " + pot.amount());
            pot.drainLiquid(200);
        }).thenExecuteAfter(3, () -> {
            check(helper, pot.percent() < 0, "with no water it is not cooking, not at 0%: " + pot.percent());
            List<String> lines = new ArrayList<>();
            pot.readout(line -> lines.add(line.getString()));
            check(helper, lines.stream().noneMatch(l -> l.contains("0%")), "and its readout says so: " + lines);
        }).thenExecute(() -> done(helper, player)).thenSucceed();
    }

    /**
     * A Stirring Tank with biodiesel (from oil put in from a side) and an empty Copper Pipe on top that leads nowhere keeps
     * its biodiesel; with a Generator joined to that pipe, the biodiesel goes up and on to it.
     */
    private static void lineGoingNowhere(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at, ModBlocks.STIRRING_TANK.get());
        var tank = helper.getBlockEntity(at, StirringTankBlockEntity.class);
        tank.energy().set((int) tank.energy().getCapacityAsLong());
        tank.setItem(0, new ItemStack(ModOil.METHANOL.get()));
        tank.setItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()));
        try (Transaction tx = Transaction.openRoot()) {
            tank.fluidHandler().insert(FluidResource.of(OilFluids.SOURCE.get()), StirringTankBlockEntity.OIL_PER_BATCH, tx);
            tx.commit();
        }
        BlockPos pipe = at.above();
        placePipe(helper, pipe);
        BlockPos generatorAt = pipe.east();
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, tank.biodieselStored() == StirringTankBlockEntity.OIL_PER_BATCH,
                        "a batch is stirred: " + tank.biodieselStored()))
                .thenExecuteAfter(30, () -> {
                    check(helper, helper.getBlockEntity(pipe, FluidPipeBlockEntity.class).stored() == 0
                            && tank.biodieselStored() == StirringTankBlockEntity.OIL_PER_BATCH, "none goes up into a pipe leading nowhere");
                    helper.setBlock(generatorAt, ModBlocks.GENERATOR.get());
                    placePipe(helper, pipe);
                })
                .thenWaitUntil(() -> check(helper, helper.getBlockEntity(generatorAt, GeneratorBlockEntity.class).fuelStored() > 0,
                        "with a Generator on the line, the biodiesel goes up and on to it"))
                .thenSucceed();
    }

    /**
     * "Too Hot to Handle" comes with a Red-Hot Velarium Ingot's burn; the same burn from elsewhere (nookTechnology's red-hot
     * steel shares the damage type) gives nothing.
     */
    private static void tooHotFromVelariumOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AdvancementHolder tooHot = level.getServer().getAdvancements().get(Nookworks.id("too_hot"));
        check(helper, tooHot != null, "the advancement loads");
        ServerPlayer other = clicker(helper);
        other.hurtServer(level, level.damageSources().source(Quenching.BURN), 1.0F);
        check(helper, !other.getAdvancements().getOrStartProgress(tooHot).isDone(), "a red-hot burn from elsewhere gives nothing");
        ServerPlayer player = clicker(helper);
        player.getInventory().add(Quenching.stamp(new ItemStack(ModItems.RED_HOT_VELARIUM_INGOT.get()), level.getGameTime()));
        player.getInventory().tick();
        check(helper, player.getAdvancements().getOrStartProgress(tooHot).isDone(), "a Red-Hot Velarium Ingot taken in hand gives it");
        level.getServer().getPlayerList().remove(other);
        done(helper, player);
    }

    /**
     * Round three's smaller rules: with the Sifter off the bakery's dough cannot be had (so the bakery stands down and
     * vanilla's bread is back); a Creaking does not go in a Mob Bottle.
     */
    private static void roundThreeRules(GameTestHelper helper) {
        check(helper, Features.enabled(ModKitchen.BREAD_DOUGH.get(), part -> true)
                && !Features.enabled(ModKitchen.BREAD_DOUGH.get(), part -> !part.equals("sifter")),
                "bread dough goes with the Sifter's salt");
        check(helper, !PartGraph.reachable(part -> !part.equals("sifter"), java.util.List.of()).contains(PartGraph.BREAD_DOUGH),
                "and the part table walks it so");
        Creaking creaking = EntityTypes.CREAKING.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        check(helper, creaking != null && MobBottleItem.check(creaking) == MobBottleItem.Refusal.NOT_CAPTURABLE,
                "a Creaking does not go in a Mob Bottle");
        helper.succeed();
    }

    /** A Copper Pipe set down as a player would, joined to what is round it. */
    private static void placePipe(GameTestHelper helper, BlockPos pipe) {
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get().getStateForPlacement(new BlockPlaceContext(helper.getLevel(), null,
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.COPPER_PIPE.get()),
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pipe)), Direction.UP, helper.absolutePos(pipe), false))));
    }

    /** Our items by name, for the few rules asked of many of them. */
    private static final class BuiltInItem {
        static net.minecraft.world.item.Item of(String name) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Nookworks.id(name));
        }
    }
}
