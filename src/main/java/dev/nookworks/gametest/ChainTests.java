package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import com.mojang.logging.LogUtils;

import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.ChainSway;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeHanging;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import dev.nookworks.rope.SpanMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Thin Chain (the user, 2026-10-10): strung between posts as Thin Rope is, sagging the same and carrying the same things,
 * a chain a block; cut, it drops as chain; a knot saved before there were chains loads as rope. On a client it rattles
 * once a touch and swings a little, then settles - looks only, nothing about the span changes on the server.
 */
final class ChainTests {
    static void register() {
        NookworksGameTests.test("thin_chain_strings_and_sags_as_rope_does", 60, ChainTests::stringsAsRope);
        NookworksGameTests.test("thin_chain_carries_a_bulb_and_a_bottle", 60, ChainTests::carries);
        NookworksGameTests.test("a_knot_saved_before_chains_loads_as_rope", ChainTests::oldKnotLoadsAsRope);
        NookworksGameTests.test("a_cut_chain_drops_thin_chain", 120, ChainTests::cutDropsChain);
        NookworksGameTests.test("thin_chain_is_made_of_three_iron_nuggets", ChainTests::recipe);
        NookworksGameTests.test("thin_chain_rattles_once_a_touch_and_settles", 60, ChainTests::rattlesAndSettles);
    }

    static ServerPlayer chainer(GameTestHelper helper, int chains) {
        ServerPlayer player = RopeTests.player(helper, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRope.THIN_CHAIN.get(), chains));
        return player;
    }

    /**
     * Chain on one post, then another: tied off there, slack between them as a rope hangs, a Thin Chain a block, the
     * span chain and the knots at both ends wound in chain. Not tied through a post in its way. Paying out chain and
     * putting it away lets it go; using rope instead lets the chain go and starts a rope there.
     */
    private static void stringsAsRope(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = chainer(helper, 10);
        BlockPos[] posts = RopeTests.posts(helper, 1, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        ItemStack chain = player.getMainHandItem();
        check(helper, ModRope.THIN_CHAIN.get().onItemUseFirst(chain, new net.minecraft.world.item.context.UseOnContext(player,
                InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(a), net.minecraft.core.Direction.DOWN,
                        a, false))) == InteractionResult.SUCCESS, "tied on the first post, from below too");
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        check(helper, knot != null && knot.holder() == player && knot.heldMaterial() == SpanMaterial.CHAIN, "the knot holds the player's chain");
        check(helper, RopeSpans.tie(level, player, b, chain) == InteractionResult.SUCCESS, "tied off on the second");
        RopeKnotEntity far = RopeKnotEntity.at(level, b);
        check(helper, knot.ties(b) && knot.material(b) == SpanMaterial.CHAIN && far != null, "strung in chain from the first to the second");
        check(helper, knot.wound(SpanMaterial.CHAIN) && !knot.wound(SpanMaterial.ROPE) && far.wound(SpanMaterial.CHAIN)
                && !far.wound(SpanMaterial.ROPE), "both knots are chain wound round the post");
        check(helper, chain.getCount() == 10 - RopeSpans.ropesFor(a, b), "a Thin Chain a block: " + chain.getCount() + " left");
        check(helper, knot.getPickResult().is(ModRope.THIN_CHAIN.get()), "picked, it is Thin Chain");
        Vec3 ta = RopeSpans.tie(a);
        Vec3 tb = RopeSpans.tie(b);
        check(helper, RopeSpans.at(ta, tb, 0.5).y < ta.y - 0.3, "and it hangs slack, lowest in the middle, as rope does");

        // a fence's middle post in the way, as for rope
        helper.setBlock(new BlockPos(0, 2, 4), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 2, 4), Blocks.OAK_FENCE);
        BlockPos c = helper.absolutePos(new BlockPos(0, 2, 4));
        BlockPos d = helper.absolutePos(new BlockPos(6, 2, 4));
        RopeSpans.tie(level, player, c, chain);
        int before = chain.getCount();
        check(helper, RopeSpans.tie(level, player, d, chain) == InteractionResult.FAIL && chain.getCount() == before,
                "not tied through what stands in its way, and nothing spent");

        // the chain put away, the knot lets it go; rope used while paying out chain starts a rope instead
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.runAfterDelay(21, () -> {
            check(helper, RopeKnotEntity.heldBy(level, player) == null, "the chain put away is let go");
            player.setItemInHand(InteractionHand.MAIN_HAND, chain);
            RopeSpans.tie(level, player, c, chain);
            ItemStack rope = new ItemStack(ModItems.THIN_ROPE.get(), 8);
            player.setItemInHand(InteractionHand.MAIN_HAND, rope);
            RopeSpans.tie(level, player, a, rope);
            RopeKnotEntity held = RopeKnotEntity.heldBy(level, player);
            check(helper, held != null && held.getPos().equals(a) && held.heldMaterial() == SpanMaterial.ROPE,
                    "using rope while paying out chain starts a rope where it is used");
            RopeSpans.tie(level, player, c, rope);
            check(helper, knot.material(c) == SpanMaterial.ROPE && knot.material(b) == SpanMaterial.CHAIN
                    && knot.wound(SpanMaterial.ROPE) && knot.wound(SpanMaterial.CHAIN), "one post holds a chain and a rope");
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /** What hangs on rope hangs on chain: a bulb lights where it hangs, a bottle goes on it where the player looks. */
    private static void carries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = chainer(helper, 20);
        BlockPos[] posts = RopeTests.posts(helper, 2, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.7, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        check(helper, bulb.misfit(level) == null, "a bulb has room under the chain");
        level.addFreshEntity(bulb);
        Vec3 target = RopeSpans.at(a, b, 0.3);
        player.snapTo(target.x, target.y - 1.62, target.z - 2.0, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, RopeHanging.onSpan(level, player, player.getMainHandItem()) == InteractionResult.SUCCESS
                && player.getMainHandItem().isEmpty(), "a bottle goes on the chain where the player looks");
        level.getServer().getPlayerList().remove(player);
        helper.runAfterDelay(25, () -> {
            check(helper, !bulb.isRemoved() && bulb.litAt() != null && level.getBlockState(bulb.litAt()).is(ModRope.HUNG_LIGHT.get()),
                    "the bulb still hangs, and lights where it hangs");
            check(helper, level.getEntitiesOfClass(HungItemEntity.class, new AABB(a).minmax(new AABB(b)).inflate(1),
                    h -> h.item().is(Items.GLASS_BOTTLE)).size() == 1, "and the bottle still hangs");
            helper.succeed();
        });
    }

    /**
     * A knot of rope alone saves just as it did before there were chains (no "chains"); one saved so - an old world's -
     * loads as all rope; one with a chain keeps it, saved and loaded. Knots are entities, not in the save fixtures of
     * {@link SaveCompat} (blocks only): this is their save compatibility.
     */
    private static void oldKnotLoadsAsRope(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 20);
        BlockPos[] posts = RopeTests.posts(helper, 1, 3);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        helper.setBlock(new BlockPos(0, 2, 5), Blocks.OAK_FENCE);
        BlockPos c = helper.absolutePos(new BlockPos(0, 2, 5));
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        CompoundTag rope = save(knot);
        check(helper, rope.contains("spans") && !rope.contains("chains"), "a knot of rope alone is saved as before: " + rope);
        RopeKnotEntity loaded = load(level, rope);
        check(helper, loaded.ties(b) && loaded.material(b) == SpanMaterial.ROPE && loaded.chains().isEmpty(), "and loads as rope");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRope.THIN_CHAIN.get(), 10));
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, c, player.getMainHandItem());
        CompoundTag mixed = save(knot);
        RopeKnotEntity kept = load(level, mixed);
        check(helper, mixed.contains("chains") && kept.material(c) == SpanMaterial.CHAIN && kept.material(b) == SpanMaterial.ROPE,
                "a chain beside the rope is kept: " + mixed);
        CompoundTag old = mixed.copy();
        old.remove("chains"); // as an old world has it: nor were the spans' steps from the post kept then
        old.remove("chain_offsets");
        old.remove("span_offsets");
        RopeKnotEntity before = load(level, old);
        check(helper, before.ties(b) && before.ties(c) && before.material(c) == SpanMaterial.ROPE && before.chains().isEmpty(),
                "a knot saved before chains, its every span rope");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static CompoundTag save(RopeKnotEntity knot) {
        try (var reporter = new ProblemReporter.ScopedCollector(knot.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, knot.level().registryAccess());
            knot.saveWithoutId(output);
            return output.buildResult();
        }
    }

    private static RopeKnotEntity load(ServerLevel level, CompoundTag tag) {
        RopeKnotEntity copy = new RopeKnotEntity(ModRope.ROPE_KNOT.get(), level);
        try (var reporter = new ProblemReporter.ScopedCollector(copy.problemPath(), LogUtils.getLogger())) {
            copy.load(TagValueInput.create(reporter, level.registryAccess(), tag));
        }
        return copy;
    }

    /**
     * Cut at a post holding a chain and a rope, each drops as it is: Thin Chain for the chain, Thin Rope for the rope.
     * Its far post gone, a chain span drops its chain.
     */
    private static void cutDropsChain(GameTestHelper helper) {
        BlockPos[] posts = RopeTests.posts(helper, 1, 5);
        R9RopeTests.onceLoaded(helper, helper.absolutePos(posts[0]), helper.absolutePos(posts[1]), () -> cutDropsChainLoaded(helper, posts));
    }

    private static void cutDropsChainLoaded(GameTestHelper helper, BlockPos[] posts) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = chainer(helper, 20);
        player.getInventory().setItem(9, new ItemStack(ModItems.THIN_ROPE.get(), 20));
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        helper.setBlock(new BlockPos(0, 2, 5), Blocks.OAK_FENCE);
        BlockPos c = helper.absolutePos(new BlockPos(0, 2, 5));
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        ItemStack rope = player.getInventory().getItem(9);
        RopeSpans.tie(level, player, c, rope);
        RopeSpans.tie(level, player, a, rope); // a rope from c, ending at a
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        check(helper, knot != null && knot.material(b) == SpanMaterial.CHAIN && RopeKnotEntity.at(level, c).material(a) == SpanMaterial.ROPE,
                "a chain from the first post, a rope ending there");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.attack(knot);
        check(helper, knot.isRemoved(), "a hit cuts the knot");
        int chains = dropped(level, a, ModRope.THIN_CHAIN.get());
        int ropes = dropped(level, a, ModItems.THIN_ROPE.get());
        check(helper, chains == RopeSpans.ropesFor(a, b) && ropes == RopeSpans.ropesFor(c, a),
                "the chain drops as Thin Chain, the rope as Thin Rope: " + chains + " / " + ropes);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(8)).forEach(ItemEntity::discard);

        // strung again, and its far post taken away
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRope.THIN_CHAIN.get(), 20));
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        RopeKnotEntity again = RopeKnotEntity.at(level, a);
        check(helper, again != null && again.material(b) == SpanMaterial.CHAIN, "strung again in chain");
        helper.setBlock(posts[1], Blocks.AIR);
        helper.runAfterDelay(45, () -> {
            check(helper, !again.ties(b), "its far post gone, the span is gone");
            int fell = dropped(level, a, ModRope.THIN_CHAIN.get());
            check(helper, fell == RopeSpans.ropesFor(a, b) && dropped(level, a, ModItems.THIN_ROPE.get()) == 0,
                    "and its chain dropped, as chain: " + fell);
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static int dropped(ServerLevel level, BlockPos near, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(near).inflate(8)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    /** Three iron nuggets one over another make two Thin Chain, in any column of the grid. */
    private static void recipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack n = new ItemStack(Items.IRON_NUGGET);
        CraftingInput column = CraftingInput.of(1, 3, List.of(n, n, n));
        ItemStack made = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, column, level)
                .map(recipe -> recipe.value().assemble(column)).orElse(ItemStack.EMPTY);
        check(helper, made.is(ModRope.THIN_CHAIN.get()) && made.getCount() == 2, "three nuggets in a column make two: " + made);
        ItemStack e = ItemStack.EMPTY;
        CraftingInput grid = CraftingInput.of(3, 3, List.of(e, e, n, e, e, n, e, e, n));
        check(helper, level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(recipe -> recipe.value().assemble(grid)).orElse(ItemStack.EMPTY).is(ModRope.THIN_CHAIN.get()), "in the right-hand column too");
        CraftingInput row = CraftingInput.of(3, 1, List.of(n, n, n));
        check(helper, level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, row, level)
                .map(recipe -> !recipe.value().assemble(row).is(ModRope.THIN_CHAIN.get())).orElse(true), "but not in a row");
        helper.succeed();
    }

    /**
     * A mob walking into a chain touches it, one beside it does not; an arrow shot through it does. A touch sets it
     * swinging with a rattle, the same touch again does nothing, another just after sways it without a second rattle;
     * swung, it settles in a few seconds. On the server none of that happens: a zombie walking through leaves the span
     * and what hangs on it as they were.
     */
    private static void rattlesAndSettles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = chainer(helper, 20);
        BlockPos[] posts = RopeTests.posts(helper, 2, 6);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        level.getServer().getPlayerList().remove(player);
        Vec3 ta = RopeSpans.tie(level, a);
        Vec3 tb = RopeSpans.tie(level, b);
        Vec3 middle = RopeSpans.at(ta, tb, 0.5);

        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 2));
        zombie.setNoAi(true);
        zombie.snapTo(middle.x, middle.y - 1.0, middle.z);
        zombie.setOldPosAndRot(zombie.position().add(0, 0, -0.2), 0.0F, 0.0F); // it walked south into it
        check(helper, ChainSway.crosses(ta, tb, zombie.getBoundingBox()) && ChainSway.touches(ta, tb, zombie),
                "a zombie walking into the chain touches it");
        check(helper, ChainSway.push(ta, tb, ChainSway.motion(zombie)) != 0, "and pushes it across");
        check(helper, !ChainSway.crosses(ta, tb, zombie.getBoundingBox().move(0, 0, 2)), "one two blocks off does not");
        zombie.setOldPosAndRot(zombie.position(), 0.0F, 0.0F);
        check(helper, !ChainSway.touches(ta, tb, zombie), "nor one standing still in it");
        check(helper, ChainSway.passes(ta, tb, middle.add(0, 0.2, -3), middle.add(0, -0.2, 3), 0.1)
                && !ChainSway.passes(ta, tb, middle.add(0, 1, -3), middle.add(0, 1, 3), 0.1), "an arrow's path through it touches it, one over it not");

        ChainSway sway = new ChainSway();
        check(helper, sway.feel(1, 0, 1.0, ChainSway.KICK) == ChainSway.Felt.RATTLED, "a touch rattles it");
        check(helper, sway.feel(1, 1, 1.0, ChainSway.KICK) == ChainSway.Felt.NOTHING, "the same touch, a tick on, does nothing more");
        check(helper, sway.feel(2, 3, 0.5, ChainSway.KICK) == ChainSway.Felt.SWAYED, "another just after sways it, without a rattle");
        double most = 0;
        for (int t = 1; t <= 150; t++) {
            sway.tick(t);
            most = Math.max(most, Math.abs(sway.angle(1.0F)));
            if (t == 30) {
                check(helper, sway.feel(3, t, 1.0, ChainSway.KICK) == ChainSway.Felt.RATTLED, "a touch after the cooldown rattles again");
            }
        }
        check(helper, most > 0.2 && most < 0.8, "it swung a little: " + most);
        check(helper, sway.settled(), "and has settled within five seconds of the last touch: " + sway.angle(1.0F));

        HungItemEntity bottle = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.GLASS_BOTTLE));
        level.addFreshEntity(bottle);
        Vec3 hung = bottle.position();
        zombie.snapTo(middle.x, middle.y - 1.0, middle.z - 0.6);
        zombie.setDeltaMovement(0, 0, 0.3);
        zombie.setNoAi(false);
        RopeKnotEntity knot = RopeKnotEntity.at(level, a);
        helper.runAfterDelay(20, () -> {
            check(helper, knot.ties(b) && knot.swing(b, 1.0F) == 0.0F, "on the server the span stays as it was, unswung");
            check(helper, !bottle.isRemoved() && bottle.position().distanceTo(hung) < 1.0E-6, "and what hangs on it stays put");
            helper.succeed();
        });
    }
}
