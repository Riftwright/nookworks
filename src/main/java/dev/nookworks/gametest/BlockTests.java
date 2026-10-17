package dev.nookworks.gametest;

import static dev.nookworks.gametest.MercuryTests.placeMercuryMachine;
import static dev.nookworks.gametest.MercuryTests.readAsset;
import static dev.nookworks.gametest.MiningTests.playerWith;
import static dev.nookworks.gametest.NookworksGameTests.CEILING;
import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.block.DiceBlock;
import dev.nookworks.block.DiceTumble;
import dev.nookworks.block.entity.DiceBlockEntity;
import dev.nookworks.block.FertileSoilBlock;
import dev.nookworks.block.HubStoneBlock;
import dev.nookworks.block.PipeSides;
import dev.nookworks.block.SconceBlock;
import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.entity.CrateBlockEntity;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.feature.Fireproofing;
import dev.nookworks.furniture.DeskBlock;
import dev.nookworks.furniture.SeatEntity;
import dev.nookworks.furniture.StoolBlock;
import dev.nookworks.furniture.WallShelfBlock;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.menu.CrateMenu;
import dev.nookworks.mercury.RetortBlock;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModMenus;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.wood.SupportBlock;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Blocks for the home and the yard: rope, crates, sconces, coating, the Hub Stone, soil, furniture, dice, Dawnwood. */
final class BlockTests {
    static void register() {
        test("rope_extends_downwards", BlockTests::ropeExtendsDownwards);
        test("rope_pulls_up_bottom_segment", BlockTests::ropePullsUp);
        test("rope_unravels_without_support", BlockTests::ropeUnravels);
        test("crate_keeps_contents_when_broken", BlockTests::crateKeepsContents);
        test("crate_rejects_nesting", BlockTests::crateRejectsNesting);
        test("crate_drives_comparator", BlockTests::crateDrivesComparator);
        test("wall_sconce_drops_sconce", BlockTests::wallSconceDropsSconce);
        test("coating_applies_once_to_flammable_blocks", BlockTests::coatingApplies);
        test("coated_planks_never_burn_away", BlockTests::coatedDoesNotBurnOut);
        test("coated_planks_do_not_spread_or_hold_fire", BlockTests::coatedDoesNotSpread);
        test("coated_planks_are_safe_from_lava", BlockTests::coatedSafeFromLava);
        test("breaking_a_coated_block_clears_the_coating", BlockTests::breakingClearsCoating);
        test("hub_stone_carries_redstone_both_ways", BlockTests::hubStoneCarries);
        test("hub_stone_set_down_powered_lights_a_lamp_past_a_stone", BlockTests::hubStoneSetDownPowered);
        test("nourished_soil_takes_saplings_and_grows_crops", BlockTests::fertileSoilGrows);
        test("nourished_soil_wears_out", BlockTests::fertileSoilWearsOut);
        test("nourished_soil_is_ruined_by_trampling", BlockTests::fertileSoilIsRuinedByTrampling);
        test("advancements_are_whole", BlockTests::advancementsAreWhole);
        test("pipe_parts_meet", BlockTests::pipePartsMeet);
        test("retort_shows_its_fire", BlockTests::retortShowsItsFire);
        test("chair_seats_and_releases", BlockTests::chairSeatsAndReleases);
        test("chair_ignores_sneaking_players", BlockTests::chairIgnoresSneakingPlayers);
        test("chair_keeps_one_rider_after_a_relog", BlockTests::chairKeepsOneRiderAfterARelog);
        test("stool_seats_lower_than_a_chair", BlockTests::stoolSeatsLowerThanAChair);
        test("wall_shelf_hangs_joins_and_holds", BlockTests::wallShelfHangsJoinsAndHolds);
        test("sorter_mirrored_keeps_its_outlets_by_their_chests", BlockTests::sorterMirroredKeepsItsOutlets);
        test("pipe_sides_switched_off_turn_with_a_structure", BlockTests::pipeSidesTurnWithAStructure);
        test("desk_is_plain_decoration", BlockTests::deskIsPlainDecoration);
        test("desks_side_by_side_join_into_one", BlockTests::desksJoin);
        test("dice_rolls_and_reports_to_a_comparator", BlockTests::diceRollsAndReportsToAComparator);
        test("dice_roll_is_worked_out_the_same_every_time", BlockTests::diceRollRepeats);
        test("dice_roll_stays_in_its_block_and_ends_level_where_it_rests", BlockTests::diceRollStaysAndSettles);
        test("dice_roll_comes_up_fairly", BlockTests::diceRollIsFair);
        test("dice_set_down_mid_roll_comes_to_rest", BlockTests::diceSetDownMidRollComesToRest);
        test("dice_model_is_the_die_its_roll_turns", BlockTests::diceModelsMatchTheRoll);
        test("dice_lies_where_it_came_to_rest", 100, BlockTests::diceLiesWhereItCameToRest);
        test("dice_settles_on_its_worked_out_number_in_time", 100, BlockTests::diceSettlesInTime);
        test("dawnwood_comes_from_sifted_dirt", BlockTests::dawnwoodComesFromSiftedDirt);
        test("stony_dirt_is_made_dug_and_kept", BlockTests::stonyDirtIsMadeDugAndKept);
        test("supports_change_shape_with_an_axe", BlockTests::supportsChangeShapeWithAnAxe);
    }

    // ---- Stony Dirt ----

    /**
     * Stony Dirt: four from two dirt and two cobblestone set crosswise; dug with a shovel or broken with a pickaxe, either
     * quicker than by hand; it gives itself back, and stays put with nothing under it.
     */
    static void stonyDirtIsMadeDugAndKept(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        var grid = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, List.of(dirt, cobble, cobble, dirt));
        ItemStack made = level.getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level)
                .map(recipe -> recipe.value().assemble(grid)).orElse(ItemStack.EMPTY);
        check(helper, made.is(ModItems.STONY_DIRT.get()) && made.getCount() == 4, "two dirt and two cobblestone make four: " + made);

        BlockState state = ModBlocks.STONY_DIRT.get().defaultBlockState();
        check(helper, state.is(BlockTags.MINEABLE_WITH_SHOVEL) && state.is(BlockTags.MINEABLE_WITH_PICKAXE), "a shovel or a pickaxe");
        check(helper, new ItemStack(Items.STONE_SHOVEL).getDestroySpeed(state) > 1 && new ItemStack(Items.STONE_PICKAXE).getDestroySpeed(state) > 1,
                "either tool is quicker than a hand");
        check(helper, !state.requiresCorrectToolForDrops(), "and a hand still gets it");

        BlockPos at = new BlockPos(2, 3, 2);
        helper.setBlock(at, state);
        BlockPos abs = helper.absolutePos(at);
        var drops = Block.getDrops(level.getBlockState(abs), level, abs, null);
        check(helper, drops.size() == 1 && drops.get(0).is(ModItems.STONY_DIRT.get()), "it gives itself back: " + drops);
        helper.runAfterDelay(5, () -> {
            check(helper, level.getBlockState(abs).is(ModBlocks.STONY_DIRT.get()), "it does not fall, as gravel would");
            helper.succeed();
        });
    }

    // ---- Rope ----

    static void ropeExtendsDownwards(GameTestHelper helper) {
        helper.setBlock(CEILING, Blocks.STONE);
        helper.setBlock(CEILING.below(), ModBlocks.ROPE.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get(), 2));

        helper.useBlock(CEILING.below(), player);
        helper.useBlock(CEILING.below(), player);

        helper.assertBlockPresent(ModBlocks.ROPE.get(), CEILING.below(2));
        helper.assertBlockPresent(ModBlocks.ROPE.get(), CEILING.below(3));
        check(helper, player.getMainHandItem().isEmpty(), "both ropes should be consumed");
        helper.succeed();
    }

    static void ropePullsUp(GameTestHelper helper) {
        helper.setBlock(CEILING, Blocks.STONE);
        for (int i = 1; i <= 3; i++) {
            helper.setBlock(CEILING.below(i), ModBlocks.ROPE.get());
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.useBlock(CEILING.below(), player);

        helper.assertBlockPresent(ModBlocks.ROPE.get(), CEILING.below(2));
        helper.assertBlockNotPresent(ModBlocks.ROPE.get(), CEILING.below(3));
        check(helper, player.getInventory().countItem(ModItems.ROPE.get()) == 1, "pulled rope should go to the inventory");

        // With something else in hand the click is for that item: a torch or a block goes against the rope.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
        BlockState rope = helper.getBlockState(CEILING.below());
        InteractionResult result = rope.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(helper.absoluteVec(Vec3.atCenterOf(CEILING.below())), Direction.NORTH,
                        helper.absolutePos(CEILING.below()), false));
        check(helper, result == InteractionResult.PASS, "a held block is left to the block, got " + result);
        helper.assertBlockPresent(ModBlocks.ROPE.get(), CEILING.below(2));
        helper.succeed();
    }

    static void ropeUnravels(GameTestHelper helper) {
        helper.setBlock(CEILING, Blocks.STONE);
        for (int i = 1; i <= 3; i++) {
            helper.setBlock(CEILING.below(i), ModBlocks.ROPE.get());
        }
        BlockState rope = ModBlocks.ROPE.get().defaultBlockState();
        check(helper, !rope.canSurvive(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1))),
                "rope must not float in mid-air");

        helper.setBlock(CEILING, Blocks.AIR);
        helper.succeedWhen(() -> {
            for (int i = 1; i <= 3; i++) {
                helper.assertBlockNotPresent(ModBlocks.ROPE.get(), CEILING.below(i));
            }
            helper.assertItemEntityCountIs(ModItems.ROPE.get(), CEILING.below(2), 3.0, 3);
        });
    }

    // ---- Crate ----

    static void crateKeepsContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.CRATE.get());
        CrateBlockEntity crate = helper.getBlockEntity(pos, CrateBlockEntity.class);
        crate.setItem(4, new ItemStack(Items.DIAMOND, 7));

        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

        helper.succeedWhen(() -> {
            List<ItemEntity> drops = helper.getLevel().getEntities(EntityTypes.ITEM,
                    new AABB(helper.absolutePos(pos)).inflate(2.0), e -> e.getItem().is(ModItems.CRATE.get()));
            check(helper, drops.size() == 1, "exactly one crate item should drop, got " + drops.size());
            ItemContainerContents contents = drops.get(0).getItem().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            ItemStack inside = contents.itemCopies().skip(4).findFirst().orElse(ItemStack.EMPTY);
            check(helper, inside.is(Items.DIAMOND) && inside.getCount() == 7, "crate item should hold 7 diamonds in slot 4");
            helper.assertItemEntityNotPresent(Items.DIAMOND, pos, 3.0);
        });
    }

    static void crateRejectsNesting(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.CRATE.get());
        CrateBlockEntity crate = helper.getBlockEntity(pos, CrateBlockEntity.class);
        ItemStack crateItem = new ItemStack(ModItems.CRATE.get());
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        ItemStack diamond = new ItemStack(Items.DIAMOND);

        check(helper, !crate.canPlaceItem(0, crateItem), "hoppers must not insert a crate");
        check(helper, !crate.canPlaceItem(0, shulker), "hoppers must not insert a shulker box");
        check(helper, crate.canPlaceItem(0, diamond), "normal items must be accepted");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CrateMenu menu = new CrateMenu(0, player.getInventory(), crate);
        check(helper, !menu.getSlot(0).mayPlace(crateItem), "crate slot must refuse a crate");
        check(helper, !menu.getSlot(8).mayPlace(shulker), "crate slot must refuse a shulker box");
        check(helper, menu.getType() == ModMenus.CRATE.get() && crate.getContainerSize() == 9,
                "the crate has a menu type of its own, so the client predicts the same rules");
        check(helper, menu.getSlot(0).mayPlace(diamond), "crate slot must take normal items");
        check(helper, menu.getSlot(9).mayPlace(crateItem), "player inventory slots stay unrestricted");
        helper.succeed();
    }

    static void crateDrivesComparator(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.CRATE.get());
        BlockState state = helper.getBlockState(pos);
        BlockPos abs = helper.absolutePos(pos);
        check(helper, state.getAnalogOutputSignal(helper.getLevel(), abs, Direction.NORTH) == 0, "empty crate should output 0");

        helper.getBlockEntity(pos, CrateBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        int signal = state.getAnalogOutputSignal(helper.getLevel(), abs, Direction.NORTH);
        check(helper, signal > 0, "crate with items should output a signal, got " + signal);
        helper.succeed();
    }

    // ---- Sconce ----

    static void wallSconceDropsSconce(GameTestHelper helper) {
        BlockPos wall = new BlockPos(2, 2, 3);
        BlockPos pos = wall.east();
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(pos, ModBlocks.WALL_SCONCE.get().defaultBlockState()
                .setValue(WallTorchBlock.FACING, Direction.EAST).setValue(SconceBlock.LIT, true));
        check(helper, helper.getBlockState(pos).getLightEmission() == SconceBlock.LIGHT_LEVEL, "lit wall sconce should give light");

        helper.setBlock(wall, Blocks.AIR);
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(ModBlocks.WALL_SCONCE.get(), pos);
            helper.assertItemEntityPresent(ModItems.SCONCE.get(), pos, 2.0);
        });
    }

    // ---- Fireproof coating ----
    // Fire is random, so these call the fire code's decision points directly with the odds forced to 100%.

    static final BlockPos COATED = new BlockPos(2, 1, 3);
    static final BlockPos PLAIN = new BlockPos(4, 1, 3);

    static void setUpPlanks(GameTestHelper helper) {
        helper.setBlock(COATED, Blocks.OAK_PLANKS);
        helper.setBlock(PLAIN, Blocks.OAK_PLANKS);
        Fireproofing.coat(helper.getLevel(), helper.absolutePos(COATED));
    }

    static Object callPrivate(Object target, String name, Class<?>[] types, Object... args) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m.invoke(target, args);
            } catch (NoSuchMethodException e) {
                // keep looking in the superclass
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot call " + name, e);
            }
        }
        throw new IllegalStateException("no method " + name + " on " + target.getClass());
    }

    static void coatingApplies(GameTestHelper helper) {
        helper.setBlock(PLAIN, Blocks.OAK_PLANKS);
        helper.setBlock(COATED, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FIREPROOF_COATING.get()));

        useFirst(helper, PLAIN, player);
        useFirst(helper, PLAIN, player); // second coat: refused, no durability used
        useFirst(helper, COATED, player); // stone is not flammable: nothing happens

        check(helper, Fireproofing.isCoated(helper.getLevel(), helper.absolutePos(PLAIN)), "planks should be coated");
        check(helper, !Fireproofing.isCoated(helper.getLevel(), helper.absolutePos(COATED)), "stone must not be coated");
        int used = player.getMainHandItem().getDamageValue();
        check(helper, used == 1, "exactly one use should be spent, spent " + used);
        helper.succeed();
    }

    /**
     * A right-click on {@code pos} with a tool that acts before the block does ({@code onItemUseFirst}), as the game
     * asks it first. {@code GameTestHelper.useBlock} starts at the block and never asks.
     */
    static void useFirst(GameTestHelper helper, BlockPos pos, Player player) {
        BlockPos at = helper.absolutePos(pos);
        player.getMainHandItem().onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false)));
    }

    static void coatedDoesNotBurnOut(GameTestHelper helper) {
        setUpPlanks(helper);
        Object fire = Blocks.FIRE;
        Class<?>[] types = {Level.class, BlockPos.class, int.class,
                RandomSource.class, int.class, Direction.class};
        for (BlockPos p : List.of(COATED, PLAIN)) {
            callPrivate(fire, "checkBurnOut", types, helper.getLevel(), helper.absolutePos(p), 1,
                    helper.getLevel().getRandom(), 0, Direction.UP);
        }
        helper.assertBlockPresent(Blocks.OAK_PLANKS, COATED);
        helper.assertBlockNotPresent(Blocks.OAK_PLANKS, PLAIN); // control: uncoated planks burn
        helper.succeed();
    }

    static void coatedDoesNotSpread(GameTestHelper helper) {
        setUpPlanks(helper);
        FireBlock fire = (FireBlock) Blocks.FIRE;
        Class<?>[] types = {LevelReader.class, BlockPos.class};
        int oddsNextToCoated = (int) callPrivate(fire, "getIgniteOdds", types, helper.getLevel(), helper.absolutePos(COATED.above()));
        int oddsNextToPlain = (int) callPrivate(fire, "getIgniteOdds", types, helper.getLevel(), helper.absolutePos(PLAIN.above()));
        check(helper, oddsNextToCoated == 0, "coated planks must not feed fire spread, odds " + oddsNextToCoated);
        check(helper, oddsNextToPlain > 0, "control: plain planks feed fire spread");
        check(helper, !fire.canCatchFire(helper.getLevel(), helper.absolutePos(COATED), Direction.UP), "coated planks cannot catch fire");
        check(helper, fire.canCatchFire(helper.getLevel(), helper.absolutePos(PLAIN), Direction.UP), "control: plain planks can");
        helper.succeed();
    }

    static void coatedSafeFromLava(GameTestHelper helper) {
        setUpPlanks(helper);
        Object lava = Fluids.LAVA;
        Class<?>[] types = {LevelReader.class, BlockPos.class, Direction.class};
        boolean coated = (boolean) callPrivate(lava, "isFlammable", types, helper.getLevel(), helper.absolutePos(COATED), Direction.UP);
        boolean plain = (boolean) callPrivate(lava, "isFlammable", types, helper.getLevel(), helper.absolutePos(PLAIN), Direction.UP);
        check(helper, !coated && plain, "lava should ignite plain planks only (coated=" + coated + ", plain=" + plain + ")");
        helper.succeed();
    }

    static void breakingClearsCoating(GameTestHelper helper) {
        setUpPlanks(helper);
        ServerPlayer player = playerWith(helper, ItemStack.EMPTY);

        player.gameMode.destroyBlock(helper.absolutePos(COATED));
        helper.setBlock(COATED, Blocks.OAK_PLANKS); // new planks in the same spot

        check(helper, !Fireproofing.isCoated(helper.getLevel(), helper.absolutePos(COATED)), "new planks must not inherit the coating");
        helper.succeed();
    }

    // ---- Hub Stone ----
    static void hubStoneCarries(GameTestHelper helper) {
        // It was the Redstone Cogstone up to 0.2.0: a world saved then finds its blocks and items under the new id.
        check(helper, BuiltInRegistries.BLOCK.getValue(Nookworks.id("cogstone")) == ModBlocks.HUB_STONE.get()
                && BuiltInRegistries.ITEM.getValue(Nookworks.id("cogstone")) == ModItems.HUB_STONE.get(),
                "the old id is an alias of the new one");
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), ModBlocks.HUB_STONE.get());
        }
        helper.setBlock(new BlockPos(4, 2, 3), ModBlocks.HUB_STONE.get()); // a vertical branch
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.REDSTONE_LAMP);
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.REDSTONE_BLOCK);
        var power = HubStoneBlock.POWER;
        helper.runAfterDelay(2, () -> {
            check(helper, helper.getBlockState(new BlockPos(2, 1, 3)).getValue(power) == 15, "next to the source: 15");
            check(helper, helper.getBlockState(new BlockPos(4, 1, 3)).getValue(power) == 13, "two further: 13");
            check(helper, helper.getBlockState(new BlockPos(4, 2, 3)).getValue(power) == 12, "carries upwards too: 12");
            check(helper, helper.getBlockState(new BlockPos(5, 1, 3)).getValue(RedstoneLampBlock.LIT), "the lamp lights");
            helper.setBlock(new BlockPos(1, 1, 3), Blocks.AIR);
            helper.runAfterDelay(10, () -> {
                for (int x = 2; x <= 4; x++) {
                    check(helper, helper.getBlockState(new BlockPos(x, 1, 3)).getValue(power) == 0, "source gone: all off at x=" + x);
                }
                check(helper, !helper.getBlockState(new BlockPos(5, 1, 3)).getValue(RedstoneLampBlock.LIT), "the lamp goes out");
                helper.succeed();
            });
        });
    }

    // ---- Fertile Soil ----
    static void fertileSoilGrows(GameTestHelper helper) {
        var soil = ModBlocks.FERTILE_SOIL.get();
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, soil);

        // Untilled it is ground, not a seed bed: a sapling is happy, wheat is not.
        check(helper, Blocks.OAK_SAPLING.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(pos.above())),
                "a sapling goes on untilled soil");
        check(helper, !helper.getBlockState(pos).getValue(FertileSoilBlock.TILLED), "and it starts untilled");

        // A hoe turns it over, and then it is a bed that never needs water.
        Player farmer = helper.makeMockPlayer(GameType.SURVIVAL);
        farmer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        helper.useBlock(pos, farmer);
        BlockState tilled = helper.getBlockState(pos);
        check(helper, tilled.is(soil) && tilled.getValue(FertileSoilBlock.TILLED), "a hoe tills it");
        check(helper, Blocks.WHEAT.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(pos.above())),
                "wheat goes on it once tilled");
        // Feeding the earth is no substitute for watering it: dry, it is worth no more than bare farmland.
        check(helper, !tilled.isFertile(helper.getLevel(), helper.absolutePos(pos)), "and it starts dry");
        helper.setBlock(pos, tilled.setValue(FertileSoilBlock.MOISTURE,
                FertileSoilBlock.MAX_MOISTURE));
        check(helper, helper.getBlockState(pos).isFertile(helper.getLevel(), helper.absolutePos(pos)),
                "watered, it counts as a fed bed");

        // Fresh soil grows what is on it faster than bare farmland would.
        helper.setBlock(pos.above(), Blocks.WHEAT);
        var random = RandomSource.create(42);
        for (int i = 0; i < 64; i++) {
            FertileSoilBlock.boost(helper.getLevel(), helper.absolutePos(pos), random);
        }
        check(helper, helper.getBlockState(pos.above()).getValue(CropBlock.AGE) > 0,
                "the wheat grew");
        helper.succeed();
    }

    /** The whole point of it: it is spent, and what it leaves behind is ordinary ground. */
    static void fertileSoilWearsOut(GameTestHelper helper) {
        var soil = ModBlocks.FERTILE_SOIL.get();
        BlockPos tilled = new BlockPos(1, 1, 1);
        BlockPos bare = new BlockPos(3, 1, 1);
        helper.setBlock(tilled, soil.defaultBlockState().setValue(FertileSoilBlock.TILLED, true)
                .setValue(FertileSoilBlock.MOISTURE, FertileSoilBlock.MAX_MOISTURE));
        helper.setBlock(bare, soil);
        check(helper, helper.getBlockState(tilled).getValue(FertileSoilBlock.RICHNESS)
                == FertileSoilBlock.MAX_RICHNESS, "soil starts full");

        for (int step = FertileSoilBlock.MAX_RICHNESS; step > 0; step--) {
            FertileSoilBlock.spend(helper.getLevel(), helper.absolutePos(tilled), helper.getBlockState(tilled));
            check(helper, helper.getBlockState(tilled).getValue(FertileSoilBlock.RICHNESS) == step - 1,
                    "each step takes one off, expected " + (step - 1));
        }
        // Spent tilled soil is still a field; you have only lost the boost.
        FertileSoilBlock.spend(helper.getLevel(), helper.absolutePos(tilled), helper.getBlockState(tilled));
        helper.assertBlockPresent(Blocks.FARMLAND, tilled);

        for (int step = 0; step <= FertileSoilBlock.MAX_RICHNESS; step++) {
            FertileSoilBlock.spend(helper.getLevel(), helper.absolutePos(bare), helper.getBlockState(bare));
        }
        helper.assertBlockPresent(Blocks.DIRT, bare);
        helper.succeed();
    }

    /** Walking over a bed ruins it, the same as walking over farmland. */
    static void fertileSoilIsRuinedByTrampling(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FERTILE_SOIL.get().defaultBlockState()
                .setValue(FertileSoilBlock.TILLED, true)
                .setValue(FertileSoilBlock.MOISTURE, FertileSoilBlock.MAX_MOISTURE));
        var cow = helper.spawnWithNoFreeWill(EntityTypes.COW, pos.above(3));
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.DIRT, pos));
        check(helper, cow != null, "the cow that trod on it");
    }

    /** Is there a log anywhere a grown oak could have put one, given the soil it grew from? */
    static boolean logNear(GameTestHelper helper, BlockPos soil) {
        for (int y = 1; y <= 8; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (helper.getBlockState(soil.offset(x, y, z)).is(Blocks.OAK_LOG)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- Furniture ----

    /**
     * Every advancement of ours has to load, hang off a parent that exists, and point at real items.
     * A broken one is dropped on world load with a line in the log nobody reads, so it simply never appears.
     */
    static void advancementsAreWhole(GameTestHelper helper) {
        var advancements = helper.getLevel().getServer().getAdvancements().getAllAdvancements().stream()
                .filter(holder -> holder.id().getNamespace().equals(Nookworks.MODID))
                // recipe unlocks (recipes/...) are vanilla's hidden kind, with nothing to show; hidden/ ones only keep count
                // of something for a feature (the Wandering Trader's village map: been in a village yet?)
                .filter(holder -> !holder.id().getPath().startsWith("recipes/") && !holder.id().getPath().startsWith("hidden/"))
                .toList();
        check(helper, advancements.size() >= 20, "our advancements should have loaded, got " + advancements.size());

        int roots = 0;
        for (var holder : advancements) {
            var advancement = holder.value();
            String id = holder.id().toString();
            check(helper, advancement.display().isPresent(), id + " has nothing to display");
            var display = advancement.display().orElseThrow();
            check(helper, display.icon().count() > 0, id + " has an empty icon");
            check(helper, !advancement.criteria().isEmpty(), id + " has no criteria");
            // Several item predicates in one "have these" criterion must all be met at once: almost never what is meant
            // ("make something out of Velarium" once asked for all ten pieces together). Any-of goes in one predicate.
            for (var criterion : advancement.criteria().entrySet()) {
                if (criterion.getValue().triggerInstance() instanceof InventoryChangeTrigger.TriggerInstance items) {
                    check(helper, items.items().size() <= 1, id + " / " + criterion.getKey() + " asks for " + items.items().size()
                            + " items at once; list them in one predicate for any of them");
                }
            }
            if (advancement.parent().isEmpty()) {
                roots++;
                check(helper, display.background().isPresent(), id + " is a root and needs a background");
            }
            // A name that never got into the language file shows as the raw key in game.
            for (var text : List.of(display.title(), display.description())) {
                var contents = text.getContents();
                if (contents instanceof TranslatableContents translatable) {
                    check(helper, Language.getInstance().has(translatable.getKey()),
                            id + " is missing the translation " + translatable.getKey());
                }
            }
        }
        check(helper, roots == 1, "there should be exactly one root, found " + roots);
        helper.succeed();
    }

    /**
     * The pipe is built from three models that have to meet: a core in the middle, an arm reaching out to each
     * neighbour, and a cap where a run stops. Slimming the barrel once moved the core inwards and left the arm
     * two pixels short, which opened a hole at every joint that you could see the floor through. Nothing about
     * a block catches that, so the numbers are checked here.
     */
    static void pipePartsMeet(GameTestHelper helper) {
        int face = modelEdge(helper, "mercury_pipe_core", "barrel", "from", 2);
        check(helper, face > 0, "the core has a front face at " + face);
        check(helper, modelEdge(helper, "mercury_pipe_side", "shaft", "to", 2) == face,
                "the arm must reach the core, not stop short of it");
        check(helper, modelEdge(helper, "mercury_pipe_end", "bore", "to", 2) == face,
                "and so must the cap on an open end");
        // The ring is meant to sit proud of the barrel it wraps, or it reads as a gap rather than a collar.
        check(helper, modelEdge(helper, "mercury_pipe_side", "collar", "from", 0)
                < modelEdge(helper, "mercury_pipe_side", "shaft", "from", 0),
                "the collar should be wider than the shaft");
        helper.succeed();
    }

    /** One corner co-ordinate of a named element of one of our block models. */
    static int modelEdge(GameTestHelper helper, String model, String element, String corner, int axis) {
        var json = JsonParser
                .parseString(readAsset(helper, "/assets/nookworks/models/block/" + model + ".json"))
                .getAsJsonObject();
        for (var raw : json.getAsJsonArray("elements")) {
            var part = raw.getAsJsonObject();
            if (part.has("name") && part.get("name").getAsString().equals(element)) {
                return part.getAsJsonArray(corner).get(axis).getAsInt();
            }
        }
        throw helper.assertionException(Component.literal(model + " has no element called " + element));
    }

    /** A machine you cannot tell is working is no use across a room: the retort burns only while it cooks. */
    static void retortShowsItsFire(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var retort = placeMercuryMachine(helper, ModBlocks.RETORT.get(), pos, RetortBlockEntity.class);
        check(helper, !helper.getBlockState(pos).getValue(RetortBlock.LIT), "an idle retort is dark");

        retort.setItem(RetortBlockEntity.INPUT, new ItemStack(Items.CINNABAR, 4));
        RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                helper.getBlockState(pos), retort);
        check(helper, helper.getBlockState(pos).getValue(RetortBlock.LIT),
                "a retort with a charge and power lights up");
        check(helper, helper.getBlockState(pos).getLightEmission(helper.getLevel(), helper.absolutePos(pos)) > 0,
                "and gives off some light");

        // Out of power is not working, whatever is still in the input slot: the fire goes out, a moment after (so that
        // one short of power, cooking every other tick, does not flicker).
        retort.energy().set(0);
        for (int tick = 0; tick <= MachineBlockEntity.LAMP_HOLD; tick++) {
            RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                    helper.getBlockState(pos), retort);
        }
        check(helper, !helper.getBlockState(pos).getValue(RetortBlock.LIT),
                "and goes out when the power does");
        check(helper, helper.getBlockState(pos).getLightEmission(helper.getLevel(), helper.absolutePos(pos)) == 0,
                "taking its light with it");
        helper.succeed();
    }

    /** A chair is only a chair if you can get on it, get off it, and not be stranded when it is broken. */
    static void chairSeatsAndReleases(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModWoods.CHAIRS.get("oak").get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.useBlock(pos, player);
        check(helper, player.isPassenger(), "the player should be sitting on the chair");
        var seat = player.getVehicle();
        check(helper, seat instanceof SeatEntity, "and riding the chair's own seat");

        // A second player must not be able to sit on a chair somebody is already on.
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(pos, other);
        check(helper, !other.isPassenger(), "a taken chair should refuse a second sitter");

        // Breaking the chair drops the rider rather than leaving them riding thin air. (In the test's own ticks: a
        // test that ticks the whole level runs every other test of its batch faster.)
        helper.destroyBlock(pos);
        helper.succeedWhen(() -> {
            check(helper, !player.isPassenger(), "breaking the chair should stand the player up");
            check(helper, seat.isRemoved(), "and the seat should not linger");
        });
    }

    /**
     * A stool (the user, 2026-10-11: a low seat with no back) is sat on as a chair is, its rider as far under its seat's
     * top as a chair's, which is two pixels lower: so lower by as much. It stands no higher than its seat, every wood has
     * one, and an axe takes it.
     */
    static void stoolSeatsLowerThanAChair(GameTestHelper helper) {
        BlockPos chairPos = new BlockPos(1, 2, 2);
        BlockPos stoolPos = new BlockPos(3, 2, 2);
        helper.setBlock(chairPos, ModWoods.CHAIRS.get("oak").get());
        helper.setBlock(stoolPos, ModWoods.STOOLS.get("oak").get());
        Player onChair = helper.makeMockPlayer(GameType.SURVIVAL);
        Player onStool = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(chairPos, onChair);
        helper.useBlock(stoolPos, onStool);
        check(helper, onStool.getVehicle() instanceof SeatEntity, "a stool is sat on");
        double chairSeat = onChair.getVehicle().getY() - helper.absolutePos(chairPos).getY();
        double stoolSeat = onStool.getVehicle().getY() - helper.absolutePos(stoolPos).getY();
        check(helper, Math.abs(chairSeat - SeatEntity.SEAT_HEIGHT) < 1.0E-6 && Math.abs(stoolSeat - StoolBlock.SEAT_HEIGHT) < 1.0E-6
                && Math.abs(chairSeat - stoolSeat - 2 / 16.0) < 1.0E-6, "two pixels lower than on a chair: " + chairSeat + ", " + stoolSeat);
        var shape = helper.getBlockState(stoolPos).getShape(helper.getLevel(), helper.absolutePos(stoolPos));
        check(helper, Math.abs(shape.max(Direction.Axis.Y) - StoolBlock.SEAT_TOP / 16.0) < 1.0E-6, "no higher than its seat: " + shape.bounds());
        check(helper, ModWoods.STOOLS.keySet().equals(ModWoods.CHAIRS.keySet()), "a stool for every wood with a chair");
        check(helper, helper.getBlockState(stoolPos).is(BlockTags.MINEABLE_WITH_AXE), "an axe takes it");
        onStool.stopRiding();
        check(helper, !onStool.isPassenger(), "and its rider gets up");
        helper.succeed();
    }

    /**
     * A wall shelf (the user, 2026-10-11) hangs only on a wall, and comes down with it; two side by side facing the same
     * way join, each losing the bracket at the join, and one facing another way does not; a lantern stands on its top.
     */
    static void wallShelfHangsJoinsAndHolds(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockState shelf = ModWoods.WALL_SHELVES.get("oak").get().defaultBlockState().setValue(WallShelfBlock.FACING, Direction.NORTH);
        BlockPos left = new BlockPos(2, 2, 2);
        BlockPos right = new BlockPos(1, 2, 2); // facing north, its left is to the east
        check(helper, !shelf.canSurvive(level, helper.absolutePos(left)), "no shelf on thin air");
        for (BlockPos wall : List.of(left.south(), right.south(), left.east().south())) {
            helper.setBlock(wall, Blocks.STONE);
        }
        check(helper, shelf.canSurvive(level, helper.absolutePos(left)), "a shelf hangs on a wall");
        helper.setBlock(left, shelf);
        helper.setBlock(right, Block.updateFromNeighbourShapes(shelf, level, helper.absolutePos(right)));
        check(helper, helper.getBlockState(left).getValue(WallShelfBlock.RIGHT) && !helper.getBlockState(left).getValue(WallShelfBlock.LEFT)
                && helper.getBlockState(right).getValue(WallShelfBlock.LEFT), "two side by side join, each at the other's side");
        helper.setBlock(left.east(), Block.updateFromNeighbourShapes(shelf.setValue(WallShelfBlock.FACING, Direction.EAST), level,
                helper.absolutePos(left.east())));
        check(helper, !helper.getBlockState(left).getValue(WallShelfBlock.LEFT), "one facing another way does not join");
        BlockPos top = left.above();
        check(helper, helper.getBlockState(left).isFaceSturdy(level, helper.absolutePos(left), Direction.UP)
                && Blocks.LANTERN.defaultBlockState().canSurvive(level, helper.absolutePos(top)), "a lantern stands on it");
        check(helper, ModWoods.WALL_SHELVES.keySet().equals(ModWoods.CHAIRS.keySet()), "a shelf for every wood with a chair");
        check(helper, helper.getBlockState(left).is(BlockTags.MINEABLE_WITH_AXE), "an axe takes it");
        helper.setBlock(left.south(), Blocks.AIR);
        check(helper, helper.getBlockState(left).isAir(), "it comes down with its wall");
        check(helper, !helper.getBlockState(right).getValue(WallShelfBlock.LEFT), "and its neighbour has its bracket back");
        helper.succeed();
    }

    /**
     * A player who logged out sitting comes back in their seat (saved with them), though another has sat in the chair
     * meanwhile: the one sat there keeps it, the one come back gets up (r7 multiplayer test: both in one chair).
     */
    static void chairKeepsOneRiderAfterARelog(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModWoods.CHAIRS.get("oak").get());
        Player sitting = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(pos, sitting);
        check(helper, sitting.getVehicle() instanceof SeatEntity, "the first player sits");
        Player back = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(5, () -> {
            // the seat a relog brings back with its rider, as vanilla's login does it
            SeatEntity restored = new SeatEntity(helper.getLevel(), helper.absolutePos(pos), SeatEntity.SEAT_HEIGHT);
            helper.getLevel().addFreshEntity(restored);
            back.startRiding(restored, true, false);
            check(helper, back.getVehicle() == restored, "the player come back is in their old seat at first");
            helper.succeedWhen(() -> {
                check(helper, !back.isPassenger() && restored.isRemoved(), "the one come back gets up, their seat gone");
                check(helper, sitting.getVehicle() instanceof SeatEntity seat && !seat.isRemoved(), "the one sitting keeps the chair");
            });
        });
    }

    /** A Sorter in a structure placed mirrored sends registered items to the chest they went to before (r7 review). */
    static void sorterMirroredKeepsItsOutlets(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean swapped : new boolean[] {false, true}) {
                BlockState state = ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, facing)
                        .setValue(SorterBlock.SWAPPED, swapped);
                for (Mirror mirror : Mirror.values()) {
                    BlockState mirrored = state.mirror(mirror);
                    check(helper, SorterBlock.registeredSide(mirrored) == mirror.mirror(SorterBlock.registeredSide(state)),
                            facing + (swapped ? " swapped" : "") + " mirrored " + mirror + ": registered goes "
                                    + SorterBlock.registeredSide(mirrored) + ", not " + mirror.mirror(SorterBlock.registeredSide(state)));
                }
            }
        }
        helper.succeed();
    }

    /**
     * A cable's or pipe's side switched off with the Wrench turns with a structure placed rotated or mirrored, as its
     * joins do (r7 review: it stayed off towards the old direction). Every pipe turns without a fault, a fitting too.
     */
    static void pipeSidesTurnWithAStructure(GameTestHelper helper) {
        BlockState cable = ModBlocks.ENERGY_CABLE.get().defaultBlockState().setValue(PipeSides.OFF.get(Direction.EAST), true)
                .setValue(PipeBlock.EAST, true);
        BlockState turned = cable.rotate(Rotation.CLOCKWISE_90);
        check(helper, turned.getValue(PipeSides.OFF.get(Direction.SOUTH)) && !turned.getValue(PipeSides.OFF.get(Direction.EAST))
                && turned.getValue(PipeBlock.SOUTH) && !turned.getValue(PipeBlock.EAST), "turned a quarter: east's off goes south: " + turned);
        BlockState mirrored = cable.mirror(Mirror.FRONT_BACK);
        check(helper, mirrored.getValue(PipeSides.OFF.get(Direction.WEST)) && !mirrored.getValue(PipeSides.OFF.get(Direction.EAST)),
                "mirrored: east's off goes west: " + mirrored);
        for (Block pipe : BuiltInRegistries.BLOCK) {
            if (pipe instanceof PipeBlock && BuiltInRegistries.BLOCK.getKey(pipe).getNamespace().equals(Nookworks.MODID)) {
                for (Rotation rotation : Rotation.values()) {
                    pipe.defaultBlockState().rotate(rotation);
                }
                for (Mirror mirror : Mirror.values()) {
                    pipe.defaultBlockState().mirror(mirror);
                }
            }
        }
        helper.succeed();
    }

    /**
     * A Hub Stone set down where it is powered already (its placement reads its inputs) strongly powers the stone beside
     * it, and the lamp past that stone lights at once - not only at the next change (r7 review).
     */
    static void hubStoneSetDownPowered(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.REDSTONE_LAMP);
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.HUB_STONE.get().defaultBlockState().setValue(HubStoneBlock.POWER, 15));
        helper.succeedWhen(() -> check(helper, helper.getBlockState(new BlockPos(4, 1, 2)).getValue(RedstoneLampBlock.LIT),
                "the lamp past the stone is lit"));
    }

    /**
     * A die set down rolling, with no tick to stop it - a structure saved mid-roll, a copy without its ticks - comes to
     * rest, and a comparator reads it again (r7 review: it rolled for ever).
     */
    static void diceSetDownMidRollComesToRest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.DICE.get().defaultBlockState().setValue(DiceBlock.ROLL, 1));
        helper.succeedWhen(() -> check(helper, helper.getBlockState(pos).getValue(DiceBlock.ROLL) == 0, "the die comes to rest"));
    }

    /** Sneaking is how you get up, so a sneaking player must not be grabbed by the chair instead. */
    static void chairIgnoresSneakingPlayers(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModWoods.CHAIRS.get("oak").get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        helper.useBlock(pos, player);

        check(helper, !player.isPassenger(), "a sneaking player should not sit down");
        check(helper, helper.getLevel()
                .getEntitiesOfClass(SeatEntity.class,
                        new AABB(helper.absolutePos(pos)).inflate(2))
                .isEmpty(), "and no seat should have been spawned");
        helper.succeed();
    }

    /**
     * Desks side by side join into one long desk (the user, 2026-10-05), any two woods: the legs at the join go, so the
     * row stands on its corners; one round a corner makes an L; taken away, the legs come back. A stone beside a desk is
     * not a desk. Turned with a structure, the joined sides turn with it.
     */
    static void desksJoin(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = a.east();
        BlockPos c = b.south();
        helper.setBlock(a, ModWoods.DESKS.get("oak").get());
        helper.setBlock(b, ModWoods.DESKS.get("spruce").get());
        helper.setBlock(a.west(), Blocks.STONE);
        check(helper, helper.getBlockState(a).getValue(DeskBlock.EAST) && helper.getBlockState(b).getValue(DeskBlock.WEST),
                "two desks side by side are joined, oak to spruce");
        check(helper, !helper.getBlockState(a).getValue(DeskBlock.WEST) && !helper.getBlockState(a).getValue(DeskBlock.NORTH),
                "not to a stone, nor to nothing");
        java.util.function.BiPredicate<BlockPos, double[]> leg = (pos, px) -> helper.getBlockState(pos)
                .getShape(level, helper.absolutePos(pos)).toAabbs().stream()
                .anyMatch(box -> box.contains(px[0] / 16, px[1] / 16, px[2] / 16));
        double[] nw = {2.5, 6, 2.5};
        double[] ne = {13.5, 6, 2.5};
        double[] sw = {2.5, 6, 13.5};
        double[] se = {13.5, 6, 13.5};
        check(helper, leg.test(a, nw) && leg.test(a, sw) && !leg.test(a, ne) && !leg.test(a, se),
                "the west desk keeps the legs at its west end only");
        check(helper, !leg.test(b, nw) && !leg.test(b, sw) && leg.test(b, ne) && leg.test(b, se),
                "the east desk at its east end only: one desk on four legs");
        check(helper, leg.test(a, new double[] {8, 14.5, 8}), "the top is whole");

        helper.setBlock(c, ModWoods.DESKS.get("oak").get());
        BlockState corner = helper.getBlockState(b);
        check(helper, corner.getValue(DeskBlock.WEST) && corner.getValue(DeskBlock.SOUTH) && !corner.getValue(DeskBlock.EAST),
                "one round the corner joins too: an L");
        check(helper, leg.test(b, ne) && !leg.test(b, se) && !leg.test(c, nw) && !leg.test(c, ne) && leg.test(c, sw) && leg.test(c, se),
                "legs only at the L's outer corners");

        BlockState turned = corner.rotate(Rotation.CLOCKWISE_90);
        check(helper, turned.getValue(DeskBlock.NORTH) && turned.getValue(DeskBlock.WEST) && !turned.getValue(DeskBlock.SOUTH),
                "turned a quarter clockwise, joined west and south become north and west");
        check(helper, corner.mirror(Mirror.FRONT_BACK).getValue(DeskBlock.EAST) && !corner.mirror(Mirror.FRONT_BACK).getValue(DeskBlock.WEST),
                "and mirrored, west becomes east");

        helper.setBlock(b, Blocks.AIR);
        check(helper, !helper.getBlockState(a).getValue(DeskBlock.EAST) && leg.test(a, ne) && leg.test(a, se),
                "the middle desk taken away, the others stand on their own legs again");
        check(helper, !helper.getBlockState(c).getValue(DeskBlock.NORTH) && leg.test(c, nw) && leg.test(c, ne), "both of them");
        helper.succeed();
    }

    /** The desk is decoration: it must not swallow a use, and you must be able to stand under its top. */
    static void deskIsPlainDecoration(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModWoods.DESKS.get("oak").get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.useBlock(pos, player);
        check(helper, !player.isPassenger(), "you cannot sit on a desk");
        check(helper, helper.getBlockState(pos).is(ModWoods.DESKS.get("oak").get()), "and using it changes nothing");

        var shape = helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos));
        check(helper, !shape.isEmpty(), "the desk has a shape");
        // The gap between the legs is what makes it read as a table rather than a block.
        check(helper, shape.bounds().maxY > 0.8 && !Shapes.joinIsNotEmpty(
                shape, Shapes.box(0.4, 0.1, 0.4, 0.6, 0.7, 0.6),
                BooleanOp.AND), "with open space under the middle of its top");
        helper.succeed();
    }

    /**
     * Runs {@code body} as though the land were claimed by {@code owner}: anyone else putting a block or a thing down,
     * breaking one, or teleporting is refused, as FTB Chunks and Open Parties and Claims refuse them, through NeoForge's
     * place, break and teleport events. (It stood in for placing only, so nothing could test the rest.) A placing is
     * refused only as Open Parties and Claims refuses one: a real block put into an empty cell. It lets anything else
     * through, a place event carrying air among them, and a claimed field was planted from outside that way (2026-10-05).
     */
    static void underClaim(UUID owner, Runnable body) {
        Consumer<BlockEvent.EntityPlaceEvent> place = event -> {
            if ((event.getEntity() == null || !owner.equals(event.getEntity().getUUID()))
                    && !event.getPlacedBlock().isAir() && event.getBlockSnapshot().getState().isAir()) {
                event.setCanceled(true);
            }
        };
        Consumer<BreakBlockEvent> breaking = event -> {
            if (event.getPlayer() == null || !owner.equals(event.getPlayer().getUUID())) {
                event.setCanceled(true);
            }
        };
        Consumer<EntityTeleportEvent> teleport = event -> {
            if (event.getEntity() instanceof Player player && !owner.equals(player.getUUID())) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, place);
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, breaking);
        NeoForge.EVENT_BUS.addListener(EntityTeleportEvent.class, teleport);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(place);
            NeoForge.EVENT_BUS.unregister(breaking);
            NeoForge.EVENT_BUS.unregister(teleport);
        }
    }

    // ---- Dice ----

    /** A roll tumbles for a moment (the comparator reads 0), then settles on 1-6 and the comparator reads that. */
    static void diceRollsAndReportsToAComparator(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.DICE.get());
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        Set<Integer> seen = new HashSet<>();
        for (int roll = 0; roll < 60; roll++) {
            DiceBlock.roll(level, abs, level.getBlockState(abs));
            check(helper, level.getBlockState(abs).getAnalogOutputSignal(level, abs, Direction.NORTH) == 0,
                    "a tumbling die reads 0");
            for (int i = 0; i < 4; i++) {
                BlockState state = level.getBlockState(abs);
                state.tick(level, abs, level.getRandom());
            }
            BlockState settled = level.getBlockState(abs);
            check(helper, settled.getValue(DiceBlock.ROLL) == 0, "the die settles after its tumbles");
            int signal = settled.getAnalogOutputSignal(level, abs, Direction.NORTH);
            check(helper, signal >= 1 && signal <= 6 && signal == settled.getValue(DiceBlock.VALUE),
                    "the comparator reads the number on top, got " + signal);
            seen.add(signal);
        }
        check(helper, seen.size() == 6, "sixty rolls turn up every face, saw " + seen);

        helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        check(helper, level.getBlockState(abs).getValue(DiceBlock.ROLL) > 0, "a redstone pulse rolls it");
        helper.succeed();
    }

    /** A roll is worked out from its seed and the die's pose alone: the server's and every client's are the same roll. */
    static void diceRollRepeats(GameTestHelper helper) {
        for (long seed = 0; seed < 50; seed++) {
            DiceTumble.Result a = DiceTumble.roll(seed * 7919, 3, Direction.EAST, seed % 2 == 0 ? null : Direction.SOUTH);
            DiceTumble.Result b = DiceTumble.roll(seed * 7919, 3, Direction.EAST, seed % 2 == 0 ? null : Direction.SOUTH);
            check(helper, a.value() == b.value() && a.facing() == b.facing() && a.ticks() == b.ticks()
                    && java.util.Arrays.equals(a.frames(), b.frames()) && java.util.Arrays.equals(a.impacts(), b.impacts()), "seed " + seed);
        }
        helper.succeed();
    }

    /**
     * Every pose of a roll keeps the die in its own block, out of the ground; it starts where the die lay, and ends lying
     * level on its number where it came to rest - not eased back to the middle of its block - turned as it came to rest,
     * its facing the quarter turn nearest that.
     */
    static void diceRollStaysAndSettles(GameTestHelper helper) {
        int offMiddle = 0;
        for (long seed = 0; seed < 300; seed++) {
            int value = 1 + (int) (seed % 6);
            Direction facing = Direction.from2DDataValue((int) (seed / 6 % 4));
            Direction thrown = seed % 5 == 0 ? Direction.from2DDataValue((int) (seed % 4)) : null;
            DiceTumble.Result roll = DiceTumble.roll(seed, value, facing, thrown);
            for (int t = 0; t <= roll.ticks(); t++) {
                double[] box = DiceTumble.extent(roll.at(t));
                check(helper, box[1] > -0.02 && box[0] > -0.02 && box[2] > -0.02 && box[3] < 1.02 && box[5] < 1.02,
                        "seed " + seed + " tick " + t + ": in its block, " + java.util.Arrays.toString(box));
            }
            double[] end = roll.rest();
            double[] box = DiceTumble.extent(end);
            check(helper, Math.abs(end[1] - DiceTumble.HALF) < 1e-6 && Math.abs(box[1]) < 1e-6 && Math.abs(box[4] - 2 * DiceTumble.HALF) < 1e-6
                    && DiceTumble.footCorners(end) != null, "seed " + seed + ": ends lying level on a face, " + java.util.Arrays.toString(box));
            check(helper, box[0] > -1e-9 && box[2] > -1e-9 && box[3] < 1 + 1e-9 && box[5] < 1 + 1e-9, "seed " + seed + ": ends inside its block");
            check(helper, topFace(end) == roll.value(), "seed " + seed + ": shows its number on top, " + topFace(end) + " " + roll.value());
            double[] still = roll.at(roll.restTick());
            check(helper, Math.hypot(end[0] - still[0], end[2] - still[2]) < 0.06,
                    "seed " + seed + ": levelled where it came to rest, not moved off it");
            if (Math.hypot(end[0] - 0.5, end[2] - 0.5) > 0.02) {
                offMiddle++;
            }
            double best = -1;
            Direction nearest = null;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                double[] q = DiceTumble.quaternionOf(DiceTumble.pose(roll.value(), side));
                double dot = Math.abs(end[3] * q[0] + end[4] * q[1] + end[5] * q[2] + end[6] * q[3]);
                if (dot > best) {
                    best = dot;
                    nearest = side;
                }
            }
            check(helper, nearest == roll.facing(), "seed " + seed + ": faces the quarter turn nearest the way it lies");
            if (thrown == null) {
                double[] start = roll.at(0);
                double[] from = DiceTumble.quaternionOf(DiceTumble.pose(value, facing));
                check(helper, Math.abs(start[3] * from[0] + start[4] * from[1] + start[5] * from[2] + start[6] * from[3]) > 0.9999
                        && Math.abs(start[0] - 0.5) < 1e-9 && Math.abs(start[2] - 0.5) < 1e-9, "seed " + seed + ": starts as the die lay");
            }
        }
        check(helper, offMiddle > 100, "many rolls end off the middle of the block, as they come to rest: " + offMiddle + " of 300");
        helper.succeed();
    }

    private static boolean close(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1e-9) {
                return false;
            }
        }
        return a.length == b.length;
    }

    /** {@code v} turned by the pose's quaternion. */
    private static double[] turnVector(double[] pose, double[] v) {
        org.joml.Vector3d out = new org.joml.Quaterniond(pose[3], pose[4], pose[5], pose[6]).transform(new org.joml.Vector3d(v[0], v[1], v[2]));
        return new double[] {out.x, out.y, out.z};
    }

    /** The number on top of a die in {@code pose}: its face whose way out is up, by its model's faces. */
    private static int topFace(double[] pose) {
        int[][] faces = {null, {0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, -1, 0}};
        int best = 0;
        double most = -2;
        for (int n = 1; n <= 6; n++) {
            double up = turnVector(pose, new double[] {faces[n][0], faces[n][1], faces[n][2]})[1];
            if (up > most) {
                most = up;
                best = n;
            }
        }
        return best;
    }

    /** Thousands of rolls from one pose come up each number about as often, and nearly all come to rest by themselves. */
    static void diceRollIsFair(GameTestHelper helper) {
        int[] counts = new int[7];
        int forced = 0;
        int rolls = 3000;
        for (long seed = 0; seed < rolls; seed++) {
            DiceTumble.Result roll = DiceTumble.roll(seed * 2654435761L, 1, Direction.NORTH, seed % 3 == 0 ? Direction.WEST : null);
            counts[roll.value()]++;
            forced += roll.restTick() >= DiceTumble.MAX_TICKS ? 1 : 0;
        }
        for (int n = 1; n <= 6; n++) {
            check(helper, counts[n] > 420 && counts[n] < 580, "each number about a sixth of " + rolls + ": " + java.util.Arrays.toString(counts));
        }
        check(helper, forced < rolls / 200, "rolls made to rest at the limit: " + forced);
        helper.succeed();
    }

    /**
     * The die's model is the die unturned, its faces numbered as DiceTumble numbers them (1 up, 2 north, 3 east, 4 west,
     * 5 south, 6 down), so the die its renderer turns shows what the roll worked out; each number's pose puts that
     * number on top; and the block draws nothing of itself, whatever its state.
     */
    static void diceModelsMatchTheRoll(GameTestHelper helper) {
        com.google.gson.JsonObject model = json(helper, "assets/nookworks/models/block/dice_1.json");
        com.google.gson.JsonObject element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
        check(helper, !element.has("rotation"), "dice_1 is the die unturned");
        com.google.gson.JsonObject textures = model.getAsJsonObject("textures");
        String[][] numbered = {{"up", "1"}, {"north", "2"}, {"east", "3"}, {"west", "4"}, {"south", "5"}, {"down", "6"}};
        int[][] out = {{0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, -1, 0}};
        for (int k = 0; k < 6; k++) {
            String side = numbered[k][0];
            check(helper, textures.get(side).getAsString().equals("nookworks:block/dice_" + numbered[k][1]),
                    "dice_1's " + side + " face is " + numbered[k][1]);
            check(helper, !element.getAsJsonObject("faces").getAsJsonObject(side).has("cullface"), side + " is always drawn");
            int n = Integer.parseInt(numbered[k][1]);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                int[] m = DiceTumble.pose(n, facing);
                int upY = m[3] * out[k][0] + m[4] * out[k][1] + m[5] * out[k][2];
                check(helper, upY == 1, "the pose for " + n + " facing " + facing + " puts " + n + " on top");
            }
        }
        com.google.gson.JsonObject variants = json(helper, "assets/nookworks/blockstates/dice.json").getAsJsonObject("variants");
        check(helper, variants.size() == 1 && variants.has("") && variants.getAsJsonObject("").get("model").getAsString().equals("nookworks:block/dice_drawn"),
                "one variant for every state, drawing nothing: " + variants);
        com.google.gson.JsonObject drawn = json(helper, "assets/nookworks/models/block/dice_drawn.json");
        check(helper, !drawn.has("elements") && drawn.has("textures"), "dice_drawn is the particle alone");
        helper.succeed();
    }

    /**
     * A die rolled on its block lies, once the roll is over, where it came to rest: drawn, outlined and bumped into
     * there (its shape is its foot there, inside its block), kept so through a save and a load, and its next roll
     * starts from there.
     */
    static void diceLiesWhereItCameToRest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.DICE.get());
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        DiceBlockEntity die = (DiceBlockEntity) level.getBlockEntity(abs);
        double[] before = die.lying(level.getBlockState(abs));
        check(helper, Math.abs(before[0] - 0.5) < 1e-9 && Math.abs(before[2] - 0.5) < 1e-9, "a die never rolled lies in the middle");
        DiceBlock.roll(level, abs, level.getBlockState(abs));
        DiceTumble.Result first = die.tumble();
        check(helper, close(first.at(0), before), "the roll starts as it lay");
        helper.runAfterDelay(first.ticks() + 2, () -> {
            BlockState settled = level.getBlockState(abs);
            check(helper, settled.getValue(DiceBlock.ROLL) == 0, "settled");
            double[] lying = die.lying(settled);
            check(helper, java.util.Arrays.equals(lying, first.rest()), "it lies as the roll left it");
            var shape = settled.getShape(level, abs);
            var bounds = shape.bounds();
            check(helper, bounds.minX >= 0 && bounds.minZ >= 0 && bounds.maxX <= 1 && bounds.maxZ <= 1 && bounds.minY == 0
                    && Math.abs(bounds.maxY - 2 * DiceTumble.HALF) < 1e-6, "its shape is inside its block, as tall as the die: " + bounds);
            check(helper, Math.abs((bounds.minX + bounds.maxX) / 2 - lying[0]) < 1e-6 && Math.abs((bounds.minZ + bounds.maxZ) / 2 - lying[2]) < 1e-6,
                    "its shape is round the die where it lies: " + bounds + " at " + lying[0] + ", " + lying[2]);
            double[][] foot = DiceTumble.footCorners(lying);
            for (double[] corner : foot) {
                double x = lying[0] + (corner[0] - lying[0]) * 0.9;
                double z = lying[2] + (corner[1] - lying[2]) * 0.9;
                check(helper, shape.toAabbs().stream().anyMatch(b -> b.minX <= x && x <= b.maxX && b.minZ <= z && z <= b.maxZ),
                        "its shape takes in its foot near each corner");
            }
            check(helper, settled.getCollisionShape(level, abs).bounds().equals(bounds), "and it is bumped into there");
            try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(die.problemPath(), com.mojang.logging.LogUtils.getLogger())) {
                var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, level.registryAccess());
                die.saveWithoutMetadata(output);
                var copy = new DiceBlockEntity(abs, settled);
                copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
                check(helper, java.util.Arrays.equals(copy.lying(settled), lying), "saved and loaded, it lies where it was");
            }
            DiceBlock.roll(level, abs, settled);
            DiceTumble.Result second = die.tumble();
            check(helper, second != first && close(second.at(0), lying), "the next roll starts where it lay");
            check(helper, java.util.Arrays.equals(die.lying(level.getBlockState(abs)), lying), "while it rolls, its shape stays where it lay");
            helper.succeed();
        });
    }

    private static com.google.gson.JsonObject json(GameTestHelper helper, String path) {
        try (var stream = BlockTests.class.getResourceAsStream("/" + path)) {
            check(helper, stream != null, "missing " + path);
            return com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Rolled by a click, the die reads 0 while it rolls, and when its roll is over - on its own scheduled tick - lies on
     * the number and facing worked out for it, and the comparator reads that.
     */
    static void diceSettlesInTime(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.DICE.get());
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        DiceBlock.roll(level, abs, level.getBlockState(abs));
        DiceBlockEntity die = (DiceBlockEntity) level.getBlockEntity(abs);
        DiceTumble.Result roll = die.tumble();
        check(helper, roll != null && level.getBlockState(abs).getValue(DiceBlock.ROLL) > 0, "rolling");
        check(helper, roll.ticks() <= DiceTumble.MAX_TICKS + DiceTumble.SETTLE_TICKS, "within the limit: " + roll.ticks());
        helper.runAfterDelay(roll.ticks() - 1, () -> check(helper, level.getBlockState(abs).getValue(DiceBlock.ROLL) > 0
                && level.getBlockState(abs).getAnalogOutputSignal(level, abs, Direction.NORTH) == 0, "still rolling a tick before the end"));
        helper.runAfterDelay(roll.ticks() + 2, () -> {
            BlockState settled = level.getBlockState(abs);
            check(helper, settled.getValue(DiceBlock.ROLL) == 0 && settled.getValue(DiceBlock.VALUE) == roll.value()
                    && settled.getValue(DiceBlock.FACING) == roll.facing(), "settled as worked out: " + settled);
            check(helper, settled.getAnalogOutputSignal(level, abs, Direction.NORTH) == roll.value(), "and the comparator reads it");
            helper.succeed();
        });
    }

    // ---- Dawnwood and supports ----

    /**
     * Dawnwood is reachable (dirt sifts into its seed, rarely: about one block in five hundred, some eight stacks) and
     * whole (the seed plants a sapling that grows its tree).
     */
    static void dawnwoodComesFromSiftedDirt(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack dirt = new ItemStack(Items.DIRT);
        check(helper, SifterBlockEntity.canSift(level, dirt), "dirt can be sifted");
        int found = 0;
        for (int i = 0; i < 20_000; i++) {
            found += SifterBlockEntity.sift(level, helper.absolutePos(new BlockPos(3, 1, 3)), dirt).stream()
                    .filter(stack -> stack.is(ModWoods.RARE_SEED.get())).mapToInt(ItemStack::getCount).sum();
        }
        check(helper, found >= 10 && found <= 90, "20,000 blocks of dirt should give about 40 Rare Seeds, gave " + found);

        var seed = ModWoods.RARE_SEED.get();
        check(helper, seed.getBlock() == ModWoods.DAWNWOOD_SAPLING.get(), "the seed plants the sapling");
        var tree = level.registryAccess().lookupOrThrow(Registries.FEATURE).get(ModWoods.DAWNWOOD_TREE);
        check(helper, tree.isPresent(), "the tree the sapling grows is loaded");
        var config = (TreeFeature) tree.orElseThrow().value();
        check(helper, config.trunkProvider().value().getState(level, level.getRandom(), BlockPos.ZERO)
                .is(ModWoods.DAWNWOOD_LOG.get()), "and it is a Dawnwood tree");
        check(helper, level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).listElements()
                .noneMatch(placed -> placed.value().feature().is(ModWoods.DAWNWOOD_TREE)),
                "nothing places it in the world: the seed is the only way to get one");

        // the axe's own turn (26.3: the minecraft:axe block transformer, our log's added by neoforge:transformables)
        helper.setBlock(new BlockPos(3, 1, 3), ModWoods.DAWNWOOD_LOG.get().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))), Direction.UP,
                        helper.absolutePos(new BlockPos(3, 1, 3)), false));
        player.getMainHandItem().useOn(context);
        BlockState stripped = helper.getBlockState(new BlockPos(3, 1, 3));
        check(helper, stripped.is(ModWoods.STRIPPED_DAWNWOOD_LOG.get())
                && stripped.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X,
                "an axe strips the log and keeps the way it lies");

        var fire = (FireBlock) Blocks.FIRE;
        check(helper, fire.getBurnOdds(ModWoods.DAWNWOOD_PLANKS.get().defaultBlockState()) > 0,
                "the planks burn like any planks");
        check(helper, ModWoods.DAWNWOOD_PLANKS.get().defaultBlockState()
                .is(BlockTags.PLANKS), "and are planks to every recipe that wants planks");
        helper.succeed();
    }

    /** Against a wall a support is a bracket standing out from it; an axe goes through the shapes. */
    static void supportsChangeShapeWithAnAxe(GameTestHelper helper) {
        var fire = (FireBlock) Blocks.FIRE;
        ModWoods.SUPPORTS.forEach((wood, support) -> {
            boolean nether = wood.equals("crimson") || wood.equals("warped");
            check(helper, (fire.getBurnOdds(support.get().defaultBlockState()) > 0) != nether,
                    wood + " support burns only if its wood does");
        });
        BlockPos pos = new BlockPos(3, 1, 3);
        var support = ModWoods.SUPPORTS.get("oak").get();
        helper.setBlock(pos, support.defaultBlockState()
                .setValue(SupportBlock.FACING, Direction.EAST)
                .setValue(SupportBlock.SHAPE, SupportBlock.Shape.BRACKET));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack axe = new ItemStack(Items.STONE_AXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        BlockPos abs = helper.absolutePos(pos);
        var hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        var seen = new ArrayList<SupportBlock.Shape>();
        for (int i = 0; i < 3; i++) {
            helper.getBlockState(pos).useItemOn(axe, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            seen.add(helper.getBlockState(pos).getValue(SupportBlock.SHAPE));
        }
        check(helper, seen.equals(List.of(SupportBlock.Shape.POST, SupportBlock.Shape.BEAM,
                SupportBlock.Shape.BRACKET)), "an axe goes post, beam, bracket, got " + seen);
        check(helper, helper.getBlockState(pos).getValue(SupportBlock.FACING) == Direction.EAST,
                "without turning it");
        var shape = helper.getBlockState(pos).getShape(helper.getLevel(), abs);
        check(helper, shape.min(Direction.Axis.X) == 0.0 && shape.max(Direction.Axis.X) == 1.0,
                "an east-facing bracket's beam runs west to east across the block");
        helper.succeed();
    }

    private BlockTests() {}
}
