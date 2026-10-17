package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * r9: Thin Rope as the user saw it go wrong (2026-10-07) - a span tied from one end of a fence to the other ran through
 * its middle post, a knot stayed on its post a second or so after its rope was taken off, a bulb hung with a block under
 * it, and a block could be set down where something hung.
 */
final class R9RopeTests {
    private R9RopeTests() {}

    static void register() {
        NookworksGameTests.test("a_rope_is_not_tied_through_what_stands_in_its_way", R9RopeTests::notThroughThings);
        NookworksGameTests.test("a_knot_left_with_no_rope_goes_at_once", 40, R9RopeTests::knotGoesAtOnce);
        NookworksGameTests.test("a_hung_thing_needs_room_for_all_of_it", R9RopeTests::roomForAllOfIt);
        NookworksGameTests.test("no_block_is_set_down_where_something_hangs", R9RopeTests::noBlockWhereItHangs);
        NookworksGameTests.test("a_knot_made_off_the_server_thread_waits_on_nothing", R9RopeTests::knotMadeOffTheServerThread);
    }

    /**
     * A knot made off the server's own thread - as one in a structure is when the structure is set down while a chunk is
     * made - does not read its post from there: that waited on the server thread, which was waiting on the chunk, for ever
     * (another mod's town of templates, one with rope stakes in it, hung its world). Made on another thread while this one
     * waits for it, it comes at once, tied as on a fence post; made here, it is tied at the foot of its rod as before.
     */
    private static void knotMadeOffTheServerThread(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos post = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(post, Blocks.END_ROD.defaultBlockState().setValue(net.minecraft.world.level.block.RodBlock.FACING, Direction.NORTH));
        RopeKnotEntity away;
        try {
            away = java.util.concurrent.CompletableFuture.supplyAsync(() -> new RopeKnotEntity(level, post)).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw helper.assertionException(net.minecraft.network.chat.Component.literal("a knot made off the server's thread waited on it: " + e));
        }
        check(helper, away.position().distanceTo(RopeSpans.tie(post)) < 1.0E-6, "made off the thread, it is tied as on a fence post: " + away.position());
        RopeKnotEntity here = new RopeKnotEntity(level, post);
        check(helper, here.position().distanceTo(RopeSpans.tie(level, post)) < 1.0E-6 && here.position().distanceTo(RopeSpans.tie(post)) > 0.1,
                "made here, at its rod's foot: " + here.position());
        helper.succeed();
    }

    /**
     * A bulb hangs fourteen pixels below the rope: over a block whose top is within that, it is refused, though a bottle,
     * shorter, still hangs there. Its box is what is drawn - its glass five pixels across, a bottle's picture 0.45 along
     * the rope and flat across it.
     */
    private static void roomForAllOfIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos[] posts = RopeTests.posts(helper, 2, 2);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        Vec3 point = RopeSpans.at(level, a, b, 0.5);
        ItemStack bulbItem = new ItemStack(ModRope.FILAMENT_BULB_ITEM.get());
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.5, bulbItem);
        check(helper, Math.abs(bulb.getBoundingBox().maxY - (point.y - 1.0 / 16)) < 1.0E-6
                && Math.abs(bulb.getBoundingBox().minY - (point.y - 14.0 / 16)) < 1.0E-6
                && Math.abs(bulb.getBoundingBox().getXsize() - 5.0 / 16) < 1.0E-6, "a bulb's box is its glass: " + bulb.getBoundingBox());
        HungItemEntity bottle = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, Math.abs(bottle.getBoundingBox().getXsize() - 0.45) < 0.07 && bottle.getBoundingBox().getZsize() < 0.2,
                "a bottle's is a picture along the rope (here along x): " + bottle.getBoundingBox());
        check(helper, bulb.misfit(level) == null, "with nothing under the rope, a bulb hangs");
        // a block under the middle, its top below where the old box ended but within the bulb's glass
        BlockPos under = helper.absolutePos(new BlockPos(1, 1, 2));
        level.setBlockAndUpdate(under, Blocks.STONE.defaultBlockState());
        double top = under.getY() + 1.0;
        check(helper, top > point.y - 14.0 / 16 && top < point.y - HungItemEntity.STRING - 0.4,
                "the block's top is within the bulb's glass, below the old box (0.4 under its string)");
        check(helper, HungItemEntity.onSpan(level, a, b, 0.5, bulbItem).misfit(level) != null, "a bulb is refused over it");
        check(helper, HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(Items.GLASS_BOTTLE)).misfit(level) == null,
                "a bottle, shorter, still hangs there");
        helper.succeed();
    }

    /** A bulb hangs on a rope: a block used on the floor under it, or on the post beside it, is not set down in its way. */
    private static void noBlockWhereItHangs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 0);
        BlockPos[] posts = RopeTests.posts(helper, 2, 4);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        level.addFreshEntity(bulb);
        BlockPos cell = BlockPos.containing(bulb.getBoundingBox().getCenter());
        BlockPos floor = cell.below();
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 4));
        var onFloor = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
        player.getMainHandItem().useOn(onFloor);
        check(helper, level.getBlockState(cell).isAir() && player.getMainHandItem().getCount() == 4,
                "the planks are not set down where the bulb hangs: " + level.getBlockState(cell));
        // the control: the same click beside it, where nothing hangs
        BlockPos clear = helper.absolutePos(new BlockPos(2, 1, 5));
        level.setBlockAndUpdate(clear, Blocks.STONE.defaultBlockState());
        var beside = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clear).add(0, 0.5, 0), Direction.UP, clear, false));
        player.getMainHandItem().useOn(beside);
        check(helper, level.getBlockState(clear.above()).is(Blocks.OAK_PLANKS), "where nothing hangs they are");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A fence of three posts: a rope from one end to the other would run through the middle post, and one between two
     * posts side by side inside the rail joining them - both refused, nothing spent. Two posts with nothing between are
     * tied, and so are two with a block under the rope's line; with the block at the rope's height, refused.
     */
    private static void notThroughThings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 32);
        ItemStack rope = player.getMainHandItem();
        for (int x = 0; x < 3; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.OAK_FENCE);
        }
        BlockPos first = helper.absolutePos(new BlockPos(0, 2, 1));
        BlockPos middle = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos last = helper.absolutePos(new BlockPos(2, 2, 1));
        RopeSpans.tie(level, player, first, rope);
        check(helper, RopeSpans.tie(level, player, last, rope) == InteractionResult.FAIL, "not over the fence, through its middle post");
        check(helper, RopeSpans.tie(level, player, middle, rope) == InteractionResult.FAIL, "nor along the rail to the post beside");
        check(helper, rope.getCount() == 32, "nothing spent: " + rope.getCount());
        RopeKnotEntity knot = RopeKnotEntity.at(level, first);
        check(helper, knot != null && knot.spans().isEmpty() && knot.holder() == player, "still held, nothing strung");
        RopeSpans.tie(level, player, first, rope); // let go

        // posts four apart: clear, then a block under the rope's line, then one in it
        helper.setBlock(new BlockPos(0, 2, 4), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_FENCE);
        BlockPos a = helper.absolutePos(new BlockPos(0, 2, 4));
        BlockPos b = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.STONE);
        check(helper, !blocked(level, a, b), "a block under the rope is out of its way");
        helper.setBlock(new BlockPos(2, 2, 4), Blocks.STONE);
        check(helper, blocked(level, a, b), "a block at the rope's height is in it");
        RopeSpans.tie(level, player, a, rope);
        check(helper, RopeSpans.tie(level, player, b, rope) == InteractionResult.FAIL, "and the rope is not tied through it");
        RopeSpans.tie(level, player, a, rope); // let go
        helper.setBlock(new BlockPos(2, 2, 4), Blocks.AIR);
        RopeSpans.tie(level, player, a, rope);
        check(helper, RopeSpans.tie(level, player, b, rope) == InteractionResult.SUCCESS, "with it gone, tied");
        check(helper, rope.getCount() == 32 - RopeSpans.ropesFor(a, b), "and spent: " + rope.getCount());
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static boolean blocked(ServerLevel level, BlockPos a, BlockPos b) {
        return RopeSpans.blocked(level, a, b);
    }

    /**
     * Cut either end of a span and the knot at the other goes on the next tick or two, whichever of them held the span;
     * a knot let go of with nothing tied goes as soon. (They used to wait for their next look round: up to five seconds.)
     * Cut once the knots round about have loaded ({@link #onceLoaded}): until they have, a knot cannot tell "no rope
     * here" from "its rope not loaded yet", and waits.
     */
    private static void knotGoesAtOnce(GameTestHelper helper) {
        BlockPos[] posts = RopeTests.posts(helper, 1, 4);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos e = helper.absolutePos(new BlockPos(6, 2, 6));
        onceLoaded(helper, a, e, () -> knotGoesAtOnceLoaded(helper, posts));
    }

    /**
     * Runs {@code then} once the entities round both {@code from} and {@code to} have loaded - the chunks round a test's
     * own are loaded only as it starts, and their entities come in a few ticks after, read from disk off the server thread
     * (so waited for {@link NookworksGameTests#inTime in time}) - then lets it end the test.
     */
    static void onceLoaded(GameTestHelper helper, BlockPos from, BlockPos to, Runnable then) {
        ServerLevel level = helper.getLevel();
        helper.startSequence().thenWaitUntil(() -> check(helper, NookworksGameTests.inTime(() -> RopeKnotEntity.entitiesLoadedRound(level, from)
                && RopeKnotEntity.entitiesLoadedRound(level, to)), "the area's entities have loaded")).thenExecute(then);
    }

    private static void knotGoesAtOnceLoaded(GameTestHelper helper, BlockPos[] posts) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 32);
        ItemStack rope = player.getMainHandItem();
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        helper.setBlock(new BlockPos(0, 2, 4), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_FENCE);
        BlockPos c = helper.absolutePos(new BlockPos(0, 2, 4));
        BlockPos d = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos e = helper.absolutePos(new BlockPos(6, 2, 6));
        helper.setBlock(new BlockPos(6, 2, 6), Blocks.OAK_FENCE);
        RopeSpans.tie(level, player, a, rope);
        RopeSpans.tie(level, player, b, rope); // a holds the span
        RopeSpans.tie(level, player, c, rope);
        RopeSpans.tie(level, player, d, rope); // c holds the span
        RopeKnotEntity heldEnd = RopeKnotEntity.at(level, b);
        RopeKnotEntity holder = RopeKnotEntity.at(level, c);
        check(helper, heldEnd != null && holder != null && RopeKnotEntity.at(level, a).ties(b) && holder.ties(d), "two spans strung");
        // cut the end a span runs to: the knot that held it is left with nothing; cut the knot that holds one: its end
        heldEnd.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        holder.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
        // and a knot started, then let go
        RopeSpans.tie(level, player, e, rope);
        RopeSpans.tie(level, player, e, rope);
        helper.runAfterDelay(3, () -> {
            check(helper, RopeKnotEntity.at(level, a) == null, "the knot that held the cut span is gone at once");
            check(helper, RopeKnotEntity.at(level, d) == null, "the knot the cut knot's span ran to is gone at once");
            check(helper, RopeKnotEntity.at(level, e) == null, "a knot let go of with nothing tied is gone at once");
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }
}
