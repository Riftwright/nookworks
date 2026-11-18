package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.item.WrenchItem;
import dev.nookworks.item.WrenchMode;
import dev.nookworks.registry.ModCreativeTabs;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.wood.TurnedPlanks;
import dev.nookworks.wood.TurnedPlanksBlock;
import dev.nookworks.wood.TurnedSlabBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Turned planks (the user, 2026-11-10): the Wrench turns every wood's planks and wooden slab to the turned block and
 * back, a slab's half and water kept; the turned ones are the planks to a player - they drop and pick as them, are named
 * as them, burn, sound and mine as them - and have no item: in no creative tab, with no recipe.
 */
final class TurnedPlanksTests {
    private TurnedPlanksTests() {}

    static void register() {
        test("wrench_turns_every_woods_planks_and_slab_both_ways", TurnedPlanksTests::wrenchTurns);
        test("turned_planks_drop_and_pick_as_the_planks", TurnedPlanksTests::dropsAndPick);
        test("turned_planks_have_no_item_recipe_or_tab", TurnedPlanksTests::hidden);
        test("turned_planks_broken_by_a_player_give_the_planks", 100, TurnedPlanksTests::brokenByAPlayer);
        test("a_slab_set_into_a_turned_half_makes_a_turned_double", TurnedPlanksTests::slabIntoTurnedHalf);
    }

    private static ServerPlayer player(GameTestHelper helper, boolean sneaking) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.moveTo(aside.x, aside.y, aside.z);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setShiftKeyDown(sneaking);
        return player;
    }

    /** {@code stack} used on the top of {@code pos} (a horizontal face's upper half, for a slab, when {@code side}). */
    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos, Direction face, double y) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atLowerCornerOf(helper.absolutePos(pos)).add(0.5, y, 0.5).add(face.getStepX() * 0.5, 0, face.getStepZ() * 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, helper.absolutePos(pos), false));
    }

    private static Block plainPlanks(String wood) {
        return TurnedPlanks.PLANKS.get(wood).get().plain();
    }

    private static Block plainSlab(String wood) {
        return TurnedPlanks.SLABS.get(wood).get().plain();
    }

    /** Every wood: planks turned and back, a bottom, a top and a waterlogged double slab turned and back, the half kept. */
    private static void wrenchTurns(GameTestHelper helper) {
        check(helper, TurnedPlanks.PLANKS.keySet().equals(new java.util.LinkedHashSet<>(ModWoods.SUPPORT_WOODS)),
                "every wood should have turned planks");
        List<String> wrong = new ArrayList<>();
        BlockPos planks = new BlockPos(1, 1, 1);
        BlockPos slab = new BlockPos(3, 1, 1);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        WrenchItem.setMode(wrench, WrenchMode.ADJUST);
        ServerPlayer standing = player(helper, false);
        ServerPlayer sneaking = player(helper, true);
        for (String wood : ModWoods.SUPPORT_WOODS) {
            // sneaking turns it the same: there are only the two ways
            ServerPlayer player = wood.equals("birch") ? sneaking : standing;
            Block turned = TurnedPlanks.PLANKS.get(wood).get();
            helper.setBlock(planks, plainPlanks(wood));
            use(helper, player, wrench, planks, Direction.UP, 1.0);
            if (!helper.getBlockState(planks).is(turned)) {
                wrong.add(wood + " planks not turned: " + helper.getBlockState(planks));
            }
            use(helper, player, wrench, planks, Direction.UP, 1.0);
            if (!helper.getBlockState(planks).is(plainPlanks(wood))) {
                wrong.add(wood + " planks not turned back: " + helper.getBlockState(planks));
            }
            for (SlabType type : SlabType.values()) {
                boolean water = type != SlabType.DOUBLE && wood.equals("oak");
                helper.setBlock(slab, plainSlab(wood).defaultBlockState().setValue(SlabBlock.TYPE, type).setValue(SlabBlock.WATERLOGGED, water));
                use(helper, player, wrench, slab, Direction.UP, type == SlabType.BOTTOM ? 0.5 : 1.0);
                BlockState now = helper.getBlockState(slab);
                if (!now.is(TurnedPlanks.SLABS.get(wood).get()) || now.getValue(SlabBlock.TYPE) != type
                        || now.getValue(SlabBlock.WATERLOGGED) != water) {
                    wrong.add(wood + " " + type + " slab turned to " + now);
                }
                use(helper, player, wrench, slab, Direction.UP, type == SlabType.BOTTOM ? 0.5 : 1.0);
                now = helper.getBlockState(slab);
                if (!now.is(plainSlab(wood)) || now.getValue(SlabBlock.TYPE) != type || now.getValue(SlabBlock.WATERLOGGED) != water) {
                    wrong.add(wood + " " + type + " slab turned back to " + now);
                }
            }
        }
        check(helper, wrong.isEmpty(), String.join(" | ", wrong));
        check(helper, wrench.getDamageValue() > 0, "turning wears the Wrench, as every setting does");
        helper.succeed();
    }

    /** Their loot gives the planks and the slab (two from a double), picking gives them, and they are named and made as them. */
    private static void dropsAndPick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(2, 1, 2);
        BlockPos abs = helper.absolutePos(at);
        List<String> wrong = new ArrayList<>();
        for (String wood : ModWoods.SUPPORT_WOODS) {
            TurnedPlanksBlock turned = TurnedPlanks.PLANKS.get(wood).get();
            TurnedSlabBlock slab = TurnedPlanks.SLABS.get(wood).get();
            Block plain = plainPlanks(wood);
            Block plainSlab = plainSlab(wood);
            helper.setBlock(at, turned);
            BlockState state = helper.getBlockState(at);
            List<ItemStack> drops = Block.getDrops(state, level, abs, null);
            if (drops.size() != 1 || !drops.get(0).is(plain.asItem()) || drops.get(0).getCount() != 1) {
                wrong.add(wood + " planks drop " + drops);
            }
            if (!state.getBlock().getCloneItemStack(level, abs, state).is(plain.asItem())) {
                wrong.add(wood + " planks pick " + state.getBlock().getCloneItemStack(level, abs, state));
            }
            if (!turned.getName().getString().equals(plain.getName().getString())) {
                wrong.add(wood + " planks named " + turned.getName().getString());
            }
            BlockState plainState = plain.defaultBlockState();
            if (state.getDestroySpeed(level, abs) != plainState.getDestroySpeed(level, abs) || turned.getExplosionResistance() != plain.getExplosionResistance()
                    || state.getSoundType() != plainState.getSoundType() || state.getMapColor(level, abs) != plainState.getMapColor(level, abs)
                    || state.ignitedByLava() != plainState.ignitedByLava()
                    || state.isFlammable(level, abs, Direction.UP) != plainState.isFlammable(level, abs, Direction.UP)
                    || state.getFlammability(level, abs, Direction.UP) != plainState.getFlammability(level, abs, Direction.UP)) {
                wrong.add(wood + " planks not made as the planks");
            }
            if (!state.is(BlockTags.PLANKS) || !state.is(BlockTags.MINEABLE_WITH_AXE) || !state.blocksMotion()) { // PORT: 26.3 asks its tag blocks_motion; 1.21.1 has the state's own
                wrong.add(wood + " planks missing a planks tag");
            }
            for (SlabType type : SlabType.values()) {
                helper.setBlock(at, slab.defaultBlockState().setValue(SlabBlock.TYPE, type));
                BlockState s = helper.getBlockState(at);
                List<ItemStack> slabDrops = Block.getDrops(s, level, abs, null);
                int count = slabDrops.stream().filter(d -> d.is(plainSlab.asItem())).mapToInt(ItemStack::getCount).sum();
                if (slabDrops.size() != 1 || count != (type == SlabType.DOUBLE ? 2 : 1)) {
                    wrong.add(wood + " " + type + " slab drops " + slabDrops);
                }
                if (!s.getBlock().getCloneItemStack(level, abs, s).is(plainSlab.asItem())) {
                    wrong.add(wood + " slab picks " + s.getBlock().getCloneItemStack(level, abs, s));
                }
                if (!s.is(BlockTags.WOODEN_SLABS) || !s.is(BlockTags.SLABS) || !s.is(BlockTags.MINEABLE_WITH_AXE)) {
                    wrong.add(wood + " slab missing a slab tag");
                }
            }
            if (!slab.getName().getString().equals(plainSlab.getName().getString())) {
                wrong.add(wood + " slab named " + slab.getName().getString());
            }
        }
        check(helper, wrong.isEmpty(), String.join(" | ", wrong));
        helper.succeed();
    }

    /** No item: so in no creative tab, no item tag and no recipe's result - a player never holds one. */
    private static void hidden(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        var level = helper.getLevel();
        var parameters = new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess());
        List<ItemStack> shown = new ArrayList<>();
        for (var tab : ModCreativeTabs.CREATIVE_MODE_TABS.getEntries()) {
            tab.get().buildContents(parameters);
            shown.addAll(tab.get().getDisplayItems());
        }
        List<Block> turned = new ArrayList<>();
        TurnedPlanks.PLANKS.values().forEach(b -> turned.add(b.get()));
        TurnedPlanks.SLABS.values().forEach(b -> turned.add(b.get()));
        for (Block block : turned) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (block.asItem() != Items.AIR || BuiltInRegistries.ITEM.getOptional(id).isPresent()) {
                wrong.add(id + " has an item");
            }
            if (shown.stream().anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id))) {
                wrong.add(id + " is in a creative tab");
            }
        }
        level.getServer().getRecipeManager().getRecipes().forEach(holder -> {
            if (holder.id().getPath().contains("turned_")) {
                wrong.add("a recipe " + holder.id());
            }
        });
        check(helper, wrong.isEmpty(), String.join(" | ", wrong));
        helper.succeed();
    }

    /** Broken by a player with an axe, turned planks give oak planks, a turned double slab two oak slabs. */
    private static void brokenByAPlayer(GameTestHelper helper) {
        BlockPos planks = new BlockPos(1, 1, 2);
        BlockPos slab = new BlockPos(3, 1, 2);
        helper.setBlock(planks, TurnedPlanks.PLANKS.get("oak").get());
        helper.setBlock(slab, TurnedPlanks.SLABS.get("oak").get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        ServerPlayer player = player(helper, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        player.gameMode.destroyBlock(helper.absolutePos(planks));
        player.gameMode.destroyBlock(helper.absolutePos(slab));
        helper.runAfterDelay(5, () -> {
            int oakPlanks = 0;
            int oakSlabs = 0;
            int other = 0;
            for (var item : helper.getEntities(EntityType.ITEM)) {
                ItemStack stack = item.getItem();
                if (stack.is(Items.OAK_PLANKS)) {
                    oakPlanks += stack.getCount();
                } else if (stack.is(Items.OAK_SLAB)) {
                    oakSlabs += stack.getCount();
                } else {
                    other++;
                }
            }
            check(helper, oakPlanks == 1 && oakSlabs == 2 && other == 0,
                    "broken, they should give 1 oak planks and 2 oak slabs, gave " + oakPlanks + " and " + oakSlabs + " (+" + other + ")");
            helper.succeed();
        });
    }

    /** The slab's item set into a turned half: a turned double, one slab used; set beside it, a slab of its own. */
    private static void slabIntoTurnedHalf(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, TurnedPlanks.SLABS.get("spruce").get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        ServerPlayer player = player(helper, false);
        ItemStack slabs = new ItemStack(Items.SPRUCE_SLAB, 3);
        use(helper, player, slabs, at, Direction.UP, 0.5);
        BlockState now = helper.getBlockState(at);
        check(helper, now.is(TurnedPlanks.SLABS.get("spruce").get()) && now.getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
                "a spruce slab on a turned bottom half should make a turned double, made " + now);
        check(helper, player.getMainHandItem().getCount() == 2, "one slab used, left " + player.getMainHandItem().getCount());
        // another wood's slab is not its half: set above, as vanilla sets an oak slab on a spruce one
        helper.setBlock(at, TurnedPlanks.SLABS.get("spruce").get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        ItemStack oak = new ItemStack(Items.OAK_SLAB, 1);
        use(helper, player, oak, at, Direction.UP, 0.5);
        check(helper, helper.getBlockState(at).getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "an oak slab should not join a turned spruce half");
        helper.succeed();
    }
}
