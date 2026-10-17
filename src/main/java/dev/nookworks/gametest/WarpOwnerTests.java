package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.TransportTests.countIn;
import static dev.nookworks.gametest.TransportTests.placeLineWarp;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import dev.nookworks.block.LineWarpBlock;
import dev.nookworks.block.entity.ItemWarpReceiverBlockEntity;
import dev.nookworks.block.entity.ItemWarpSenderBlockEntity;
import dev.nookworks.block.entity.LineWarpReceiverBlockEntity;
import dev.nookworks.block.entity.LineWarpSenderBlockEntity;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The Item Warps send only to receivers of the sender's own owner - whoever set it down - and pass over anyone else's
 * (the user, 2026-10-14, in place of pairing: "ワープはbで…横ワープも区画を記録する方式で"; r15 fix check 8, S2: a stranger's
 * receiver under a claim's floor took all 8 diamonds a claimed sender sent). Warps from before keep working as they did.
 */
final class WarpOwnerTests {
    static void register() {
        test("item_warps_are_whoever_set_them_down", WarpOwnerTests::placing);
        test("item_warp_sends_past_a_strangers_receiver_to_its_owners", WarpOwnerTests::vertical);
        test("horizontal_item_warp_sends_past_a_strangers_receiver_to_its_owners", WarpOwnerTests::horizontal);
        test("item_warps_from_before_owners_work_as_they_did", WarpOwnerTests::oldVertical);
        test("horizontal_item_warps_from_before_owners_work_as_they_did", WarpOwnerTests::oldHorizontal);
    }

    /**
     * A player's placing makes each of the four warp blocks theirs (a horizontal sender with a stamp of its own); a
     * known player's machine sets them down as that player's; a machine of no known player owns none, and its horizontal
     * sender works as one from before owners (stamp 0).
     */
    private static void placing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
        player.snapTo(near.x, near.y, near.z);
        List<Block> warps = List.of(ModBlocks.ITEM_WARP_SENDER.get(), ModBlocks.ITEM_WARP_RECEIVER.get(), ModBlocks.LINE_WARP_SENDER.get(),
                ModBlocks.LINE_WARP_RECEIVER.get());
        int x = 0;
        for (Block block : warps) {
            BlockPos floor = helper.absolutePos(new BlockPos(x, 1, 1));
            level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            ItemStack stack = new ItemStack(block.asItem());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
            BlockEntity placed = level.getBlockEntity(floor.above());
            check(helper, placed instanceof DrawRights.Owned owned && player.getUUID().equals(owned.owner()),
                    block.getDescriptionId() + " set down by a player is theirs: " + placed);
            if (placed instanceof LineWarpSenderBlockEntity sender) {
                check(helper, sender.stamp() != 0, "a horizontal sender set down has a stamp of its own");
            }
            x += 2;
        }
        UUID known = UUID.randomUUID();
        level.getServer().services().nameToIdCache().add(new NameAndId(known, "warp-owner"));
        UUID machine = UUID.randomUUID();
        x = 0;
        for (Block block : warps) {
            BlockPos theirs = placeAsMachine(level, helper.absolutePos(new BlockPos(x, 1, 3)), block, known);
            check(helper, level.getBlockEntity(theirs) instanceof DrawRights.Owned owned && known.equals(owned.owner()),
                    block.getDescriptionId() + " a known player's machine sets down is theirs: " + level.getBlockEntity(theirs));
            BlockPos nobodys = placeAsMachine(level, helper.absolutePos(new BlockPos(x, 1, 5)), block, machine);
            BlockEntity placed = level.getBlockEntity(nobodys);
            check(helper, placed instanceof DrawRights.Owned owned && owned.owner() == null,
                    block.getDescriptionId() + " set down by an unknown machine keeps no owner: " + placed);
            if (placed instanceof LineWarpSenderBlockEntity sender) {
                check(helper, sender.stamp() == 0, "and its horizontal sender works as one from before: stamp " + sender.stamp());
            }
            x += 2;
        }
        helper.succeed();
    }

    /**
     * A's sender, A's receiver four above and a stranger's two below - nearer, where a stranger under a claim's floor
     * would set one: all 8 diamonds go up to A's chest, none to the stranger's.
     */
    private static void vertical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        BlockPos sender = new BlockPos(2, 4, 2);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        helper.getBlockEntity(sender, ItemWarpSenderBlockEntity.class).setOwner(owner);
        BlockPos mine = sender.above(4);
        verticalReceiver(helper, mine, owner);
        helper.setBlock(mine.below(), Blocks.CHEST);
        BlockPos theirs = sender.below(2);
        verticalReceiver(helper, theirs, stranger);
        helper.setBlock(theirs.below(), Blocks.CHEST);
        check(helper, helper.absolutePos(mine).equals(ItemWarpSenderBlockEntity.findReceiver(level, helper.absolutePos(sender), 64, owner)),
                "the sender passes over the stranger's receiver, to its owner's");
        helper.getBlockEntity(sender, ItemWarpSenderBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 8));
        helper.succeedWhen(() -> {
            check(helper, countIn(helper.getBlockEntity(mine.below(), ChestBlockEntity.class), Items.DIAMOND) == 8,
                    "all 8 diamonds reach the owner's chest");
            check(helper, countIn(helper.getBlockEntity(theirs.below(), ChestBlockEntity.class), Items.DIAMOND) == 0,
                    "none reach the stranger's");
        });
    }

    /**
     * A's horizontal sender, a stranger's receiver nearer on its line and A's farther: the 5 iron go to A's chest, none
     * to the stranger's; no pairing asked. One of the stranger's own senders sends to their receiver, not past it to A's.
     */
    private static void horizontal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        UUID owner = player.getUUID();
        UUID stranger = UUID.randomUUID();
        BlockPos senderPos = new BlockPos(0, 1, 3);
        placeLineWarp(helper, ModBlocks.LINE_WARP_SENDER.get(), senderPos, Direction.EAST);
        var sender = helper.getBlockEntity(senderPos, LineWarpSenderBlockEntity.class);
        sender.setOwner(owner);
        check(helper, sender.stamp() != 0, "given an owner, a sender has a stamp of its own");
        BlockPos theirs = new BlockPos(2, 1, 3);
        lineReceiver(helper, theirs, stranger);
        helper.setBlock(theirs.east(), Blocks.CHEST);
        BlockPos mine = new BlockPos(5, 1, 3);
        lineReceiver(helper, mine, owner);
        helper.setBlock(mine.east(), Blocks.CHEST);
        BlockPos at = helper.absolutePos(senderPos);
        check(helper, helper.absolutePos(mine).equals(LineWarpSenderBlockEntity.findLineReceiver(level, at, Direction.EAST, 64,
                sender.stamp(), owner)), "the sender passes over the stranger's receiver, to its owner's");
        check(helper, helper.absolutePos(theirs).equals(LineWarpSenderBlockEntity.findLineReceiver(level, at, Direction.EAST, 64,
                12345L, stranger)), "a stranger's sender there would send to their own, not on to the owner's");
        // what each receiver says to an empty hand
        check(helper, says(level, helper.getBlockEntity(mine, LineWarpReceiverBlockEntity.class), player).equals("message.nookworks.line_warp.yours"),
                "a receiver of one's own says so");
        check(helper, says(level, helper.getBlockEntity(theirs, LineWarpReceiverBlockEntity.class), player).equals("message.nookworks.line_warp.theirs"),
                "someone else's says it takes from their senders only");
        sender.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        helper.succeedWhen(() -> {
            check(helper, countIn(helper.getBlockEntity(mine.east(), ChestBlockEntity.class), Items.IRON_INGOT) == 5,
                    "the owner's chest gets all 5");
            check(helper, countIn(helper.getBlockEntity(theirs.east(), ChestBlockEntity.class), Items.IRON_INGOT) == 0,
                    "the stranger's gets none");
        });
    }

    /**
     * A sender from before owners (none) sends to the nearest receiver in its column, whoever's, as before; a receiver
     * from before (no block entity in its save: given one of no owner) takes from it - and from a sender with an owner,
     * past a stranger's receiver nearer still: an old column whose sender alone is set down again goes on working.
     */
    private static void oldVertical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sender = new BlockPos(2, 4, 2);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        check(helper, helper.getBlockEntity(sender, ItemWarpSenderBlockEntity.class).owner() == null, "a sender from before has no owner");
        BlockPos far = sender.above(4);
        verticalReceiver(helper, far, UUID.randomUUID());
        BlockPos near = sender.below(2);
        helper.setBlock(near, ModBlocks.ITEM_WARP_RECEIVER.get());
        check(helper, helper.getBlockEntity(near, ItemWarpReceiverBlockEntity.class).owner() == null, "a receiver from before has no owner");
        helper.setBlock(near.below(), Blocks.CHEST);
        check(helper, helper.absolutePos(near).equals(ItemWarpSenderBlockEntity.findReceiver(level, helper.absolutePos(sender), 64, null)),
                "an old sender sends to the nearest receiver");
        helper.setBlock(near, Blocks.AIR);
        check(helper, helper.absolutePos(far).equals(ItemWarpSenderBlockEntity.findReceiver(level, helper.absolutePos(sender), 64, null)),
                "whoever's it is");
        helper.setBlock(near, ModBlocks.ITEM_WARP_RECEIVER.get());
        // the sender set down again, now someone's; a stranger's receiver nearer than the old one
        UUID replacer = UUID.randomUUID();
        helper.getBlockEntity(sender, ItemWarpSenderBlockEntity.class).setOwner(replacer);
        verticalReceiver(helper, sender.below(), UUID.randomUUID());
        check(helper, helper.absolutePos(near).equals(ItemWarpSenderBlockEntity.findReceiver(level, helper.absolutePos(sender), 64, replacer)),
                "a sender with an owner sends to the old receiver, past a stranger's");
        helper.getBlockEntity(sender, ItemWarpSenderBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 8));
        helper.succeedWhen(() -> check(helper, countIn(helper.getBlockEntity(near.below(), ChestBlockEntity.class), Items.DIAMOND) == 8,
                "all 8 reach the old receiver's chest"));
    }

    /**
     * Horizontal warps from before owners keep working as they did. A sender from before pairing (no owner, stamp 0)
     * sends to the old receivers on its line, past a new one of somebody's and past one set down unpaired while
     * receivers paired; set down again, now someone's, it still reaches the old receiver, past the same two. One from the
     * pairing days (an owner, a stamp) sends to the receiver paired with it - set down by someone else from the item its
     * owner paired - past a stranger's; set down again, it reaches none of its old pairings (the receiver, set down again
     * too, is its placer's). A receiver set down from an item still carrying a pairing takes from that sender too.
     */
    private static void oldHorizontal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        // before pairing
        BlockPos oldSender = new BlockPos(0, 1, 0);
        placeLineWarp(helper, ModBlocks.LINE_WARP_SENDER.get(), oldSender, Direction.EAST);
        lineReceiver(helper, new BlockPos(1, 1, 0), owner);
        oldReceiver(helper, new BlockPos(2, 1, 0), null, true); // set down unpaired while receivers paired
        BlockPos oldOne = new BlockPos(4, 1, 0);
        oldReceiver(helper, oldOne, null, false);
        var oldEntity = helper.getBlockEntity(oldOne, LineWarpReceiverBlockEntity.class);
        check(helper, helper.absolutePos(oldOne).equals(LineWarpSenderBlockEntity.findLineReceiver(level, helper.absolutePos(oldSender),
                Direction.EAST, 64, 0L, null)), "an old sender sends to an old receiver, past a new one and an unpaired one");
        check(helper, helper.absolutePos(oldOne).equals(LineWarpSenderBlockEntity.findLineReceiver(level, helper.absolutePos(oldSender),
                Direction.EAST, 64, 99L, UUID.randomUUID())), "set down again, someone's, it still does, past the same two");
        check(helper, says(level, oldEntity, player).equals("message.nookworks.line_warp.old"), "an old receiver says it is one");
        check(helper, says(level, helper.getBlockEntity(new BlockPos(2, 1, 0), LineWarpReceiverBlockEntity.class), player)
                .equals("message.nookworks.line_warp.unpaired"), "one set down unpaired says it takes nothing");
        // the pairing days
        BlockPos senderPos = new BlockPos(0, 1, 3);
        BlockPos at = helper.absolutePos(senderPos);
        placeLineWarp(helper, ModBlocks.LINE_WARP_SENDER.get(), senderPos, Direction.EAST);
        var sender = helper.getBlockEntity(senderPos, LineWarpSenderBlockEntity.class);
        sender.placed(owner, 0x5EED_CAFEL);
        ItemStack paired = new ItemStack(ModItems.LINE_WARP_RECEIVER.get());
        paired.set(ModDataComponents.WARP_LINK.get(), GlobalPos.of(level.dimension(), at));
        paired.set(ModDataComponents.WARP_LINK_STAMP.get(), sender.stamp());
        lineReceiver(helper, new BlockPos(2, 1, 3), stranger);
        BlockPos pairedPos = new BlockPos(4, 1, 3);
        oldReceiver(helper, pairedPos, paired, true);
        var pairedEntity = helper.getBlockEntity(pairedPos, LineWarpReceiverBlockEntity.class);
        check(helper, helper.absolutePos(pairedPos).equals(LineWarpSenderBlockEntity.findLineReceiver(level, at, Direction.EAST, 64,
                sender.stamp(), owner)), "a sender of the pairing days sends to its paired receiver, past a stranger's");
        check(helper, says(level, pairedEntity, player).equals("message.nookworks.line_warp.target"), "the paired one names its sender");
        // a receiver set down now from an item still carrying that pairing, by the stranger: it takes from that sender too
        BlockPos fromItem = new BlockPos(3, 1, 6);
        placeLineWarp(helper, ModBlocks.LINE_WARP_RECEIVER.get(), fromItem, Direction.NORTH);
        var fromItemEntity = helper.getBlockEntity(fromItem, LineWarpReceiverBlockEntity.class);
        fromItemEntity.applyComponentsFromItemStack(paired);
        fromItemEntity.setOwner(stranger);
        check(helper, fromItemEntity.takesFrom(level, at, sender.stamp(), owner), "a paired item set down now keeps its pairing");
        check(helper, !fromItemEntity.takesFrom(level, at, sender.stamp() + 1, owner) && fromItemEntity.takesFrom(level, at.north(9), 7L, stranger),
                "and otherwise takes from its own owner's senders");
        var drops = Block.getDrops(helper.getBlockState(pairedPos), level, helper.absolutePos(pairedPos), pairedEntity);
        check(helper, drops.size() == 1 && drops.get(0).has(ModDataComponents.WARP_LINK.get()), "broken, a paired receiver keeps its pairing in its item");
        helper.setBlock(pairedPos.east(), Blocks.CHEST);
        sender.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        helper.succeedWhen(() -> {
            check(helper, countIn(helper.getBlockEntity(pairedPos.east(), ChestBlockEntity.class), Items.IRON_INGOT) == 5,
                    "the paired receiver's chest gets all 5");
            helper.setBlock(senderPos, Blocks.AIR);
            placeLineWarp(helper, ModBlocks.LINE_WARP_SENDER.get(), senderPos, Direction.EAST);
            ModBlocks.LINE_WARP_SENDER.get().setPlacedBy(level, at, level.getBlockState(at), player, ItemStack.EMPTY);
            var moved = helper.getBlockEntity(senderPos, LineWarpSenderBlockEntity.class);
            check(helper, LineWarpSenderBlockEntity.findLineReceiver(level, at, Direction.EAST, 64, moved.stamp(), moved.owner()) == null
                    && Boolean.TRUE.equals(pairedEntity.senderMoved(level)), "set down again, the sender reaches none of its old pairings");
            check(helper, says(level, pairedEntity, player).equals("message.nookworks.line_warp.moved"), "and the receiver says so");
        });
    }

    // ---- helpers ----

    /** An Item Warp Receiver at {@code pos}, of {@code owner} (null: one from before owners). */
    private static void verticalReceiver(GameTestHelper helper, BlockPos pos, @Nullable UUID owner) {
        helper.setBlock(pos, ModBlocks.ITEM_WARP_RECEIVER.get());
        helper.getBlockEntity(pos, ItemWarpReceiverBlockEntity.class).setOwner(owner);
    }

    /** A Horizontal Item Warp Receiver at {@code pos} facing east, of {@code owner}. */
    private static void lineReceiver(GameTestHelper helper, BlockPos pos, UUID owner) {
        placeLineWarp(helper, ModBlocks.LINE_WARP_RECEIVER.get(), pos, Direction.EAST);
        helper.getBlockEntity(pos, LineWarpReceiverBlockEntity.class).setOwner(owner);
    }

    /**
     * A Horizontal Item Warp Receiver at {@code pos} facing east, as one saved before owners: of no owner, with the
     * pairing {@code paired} carries (if any), set down while receivers paired or not - read back from its save, as a
     * world opened from disk reads it.
     */
    private static void oldReceiver(GameTestHelper helper, BlockPos pos, @Nullable ItemStack paired, boolean pairs) {
        placeLineWarp(helper, ModBlocks.LINE_WARP_RECEIVER.get(), pos, Direction.EAST);
        var receiver = helper.getBlockEntity(pos, LineWarpReceiverBlockEntity.class);
        if (paired != null) {
            receiver.applyComponentsFromItemStack(paired);
        }
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        receiver.saveCustomOnly(saved);
        CompoundTag tag = saved.buildResult();
        tag.putBoolean("Pairs", pairs);
        tag.remove("Owner");
        receiver.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
    }

    /** The key of what {@code receiver} says to {@code player}'s empty hand. */
    private static String says(ServerLevel level, LineWarpReceiverBlockEntity receiver, ServerPlayer player) {
        return LineWarpBlock.says(level, receiver, player).getContents() instanceof TranslatableContents key ? key.getKey() : "";
    }

    /** Sets {@code block} down on stone at {@code floor} as another mod's machine would: a fake player carrying {@code id}. */
    private static BlockPos placeAsMachine(ServerLevel level, BlockPos floor, Block block, UUID id) {
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        var fake = FakePlayerFactory.get(level, new GameProfile(id, "[Test deployer]"));
        ItemStack stack = new ItemStack(block.asItem());
        fake.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(fake, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return floor.above();
    }

    private WarpOwnerTests() {}
}
