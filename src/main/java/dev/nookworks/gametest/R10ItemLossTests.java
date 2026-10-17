package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.util.List;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.TrashCanBlock;
import dev.nookworks.block.entity.TrashCanBlockEntity;
import dev.nookworks.item.MagnetItem;
import dev.nookworks.pearl.LodePearlItem;
import dev.nookworks.pearl.ThrownLodePearl;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.sorter.SorterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The user's report of 2026-10-10: in survival, things - Lode Pearls most of all - go missing now and then. An audit of
 * the barrels, sorters, warps, magnets and the pearl found these ways an item really was lost or out of sight: a pearl
 * left hanging in mid-air when its thrower went far off before it landed; a pearl that came back to a full inventory
 * thrown ahead of its thrower, over the edge they stood at; a magnet drawing drops through lava; a sorter giving what it
 * held "back up" into a Trash Can on top of it; and a sorter that drew in what its outlet would never take - a shulker
 * box for a shulker box, what the next sorter keeps out - and held it, unseen, for good.
 */
final class R10ItemLossTests {
    private R10ItemLossTests() {}

    static void register() {
        NookworksGameTests.test("lode_pearl_lands_after_its_thrower_has_gone_far_off", 400, R10ItemLossTests::pearlLandsAfterItsThrowerHasGone);
        NookworksGameTests.test("lode_pearl_comes_back_to_the_feet_of_a_full_inventory", 100, R10ItemLossTests::pearlBackAtTheFeet);
        NookworksGameTests.test("magnet_does_not_draw_drops_through_lava", R10ItemLossTests::magnetSparesDropsAcrossLava);
        NookworksGameTests.test("sorter_gives_nothing_back_into_a_trash_can", 80, R10ItemLossTests::nothingBackIntoATrashCan);
        NookworksGameTests.test("sorter_leaves_a_shulker_box_a_shulker_box_refuses", 200, R10ItemLossTests::shulkerBoxForAShulkerBox);
        NookworksGameTests.test("sorter_leaves_what_the_next_sorter_keeps_out", 200, R10ItemLossTests::whatTheNextSorterKeepsOut);
        // r11 review (2026-10-10): a pearl landing while its thrower could not go was discarded with nothing said
        NookworksGameTests.test("lode_pearl_comes_back_to_a_thrower_asleep", 100, helper -> pearlWhileTheyCannotGo(helper, true));
        NookworksGameTests.test("lode_pearl_lies_where_it_landed_for_a_thrower_dead", 100, helper -> pearlWhileTheyCannotGo(helper, false));
    }

    /**
     * Landing while its thrower cannot go - asleep in a bed, or dead - it takes nobody anywhere and is not lost: still
     * bound, it is back in a sleeper's hands, or lies where it landed for one who died.
     */
    private static void pearlWhileTheyCannotGo(GameTestHelper helper, boolean asleep) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.LODESTONE);
        ServerPlayer player = survivor(helper, new Vec3(2.5, 1, 4.5));
        GlobalPos lodestone = global(helper, new BlockPos(1, 1, 1));
        var thrown = new ThrownLodePearl(helper.getLevel(), player, LodePearlItem.bound(lodestone));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 3, 4.5));
        thrown.setPos(at.x, at.y, at.z);
        thrown.setDeltaMovement(0, -0.5, 0);
        helper.getLevel().addFreshEntity(thrown);
        if (asleep) {
            // 26.3 lays a sleeper down in a bed only
            BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            helper.setBlock(new BlockPos(2, 1, 5), bed.setValue(BedBlock.PART, BedPart.FOOT));
            helper.setBlock(new BlockPos(2, 1, 4), bed.setValue(BedBlock.PART, BedPart.HEAD));
            player.startSleeping(helper.absolutePos(new BlockPos(2, 1, 4)));
        } else {
            player.setHealth(0.0F);
        }
        Vec3 start = player.position();
        helper.runAfterDelay(60, () -> {
            check(helper, thrown.isRemoved(), "the pearl has landed");
            check(helper, player.position().distanceTo(start) < 0.01, "nobody was taken anywhere");
            List<ItemEntity> lying = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                    item -> item.getItem().getItem() instanceof LodePearlItem);
            boolean held = player.getInventory().hasAnyMatching(s -> s.getItem() instanceof LodePearlItem);
            if (asleep) {
                check(helper, held && lying.isEmpty(), "a sleeper has it back in hand: held " + held + ", lying " + lying.size());
            } else {
                check(helper, lying.size() == 1 && !held, "for one who died it lies where it landed: " + lying.size());
            }
            helper.succeed();
        });
    }

    private static ServerPlayer survivor(GameTestHelper helper, Vec3 at) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(at));
        return player;
    }

    private static GlobalPos global(GameTestHelper helper, BlockPos pos) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos));
    }

    /**
     * Thrown far out - a long fall from high up - by a thrower who is then back here, out of reach of it (through the If
     * mirror, a door, a portal): the ground it flies over loaded only by itself, it lands all the same and takes them
     * home. It hung in mid-air out there, and took them home whenever they next came by.
     */
    private static void pearlLandsAfterItsThrowerHasGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lodestone = new BlockPos(1, 1, 1);
        helper.setBlock(lodestone, Blocks.LODESTONE);
        ServerPlayer player = survivor(helper, new Vec3(5.5, 1, 5.5));
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(4000, 0, 4000);
        int chunkX = far.getX() >> 4;
        int chunkZ = far.getZ() >> 4;
        BlockPos high = new BlockPos(far.getX(), level.getMaxY() - 20, far.getZ());
        level.setChunkForced(chunkX, chunkZ, true);
        // Entities tick in a chunk only once the two chunks all round it are loaded in full too: made here and now, in
        // time - waited for in ticks, the test server's back-to-back ticks ran out first. The forced ticket alone keeps them.
        NookworksGameTests.loadChunks(level, chunkX, chunkZ, 2);
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, level.isPositionEntityTicking(high), "the far ground loads"))
                .thenExecute(() -> {
                    var thrown = new ThrownLodePearl(level, player, LodePearlItem.bound(global(helper, lodestone)));
                    thrown.setPos(high.getX() + 0.5, high.getY(), high.getZ() + 0.5);
                    thrown.setDeltaMovement(0, 0.3, 0);
                    level.addFreshEntity(thrown);
                })
                .thenIdle(3)
                .thenExecute(() -> level.setChunkForced(chunkX, chunkZ, false)) // nothing but the pearl keeps it loaded now
                .thenWaitUntil(() -> check(helper, player.blockPosition().equals(helper.absolutePos(lodestone.above())),
                        "the thrower is taken home: at " + player.blockPosition()))
                .thenSucceed();
    }

    /**
     * Bound to a lodestone gone, thrown by someone whose pockets are full: the ender pearl it comes back as lies at their
     * feet, not thrown on ahead of them as a drop is (over the edge of the island they stood at, into the lava below).
     */
    private static void pearlBackAtTheFeet(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, new Vec3(2.5, 1, 4.5));
        player.setYRot(-90.0F); // looking east, along the floor
        player.setXRot(0.0F);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        var thrown = new ThrownLodePearl(helper.getLevel(), player, LodePearlItem.bound(global(helper, new BlockPos(1, 1, 1))));
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 3, 6.5));
        thrown.setPos(at.x, at.y, at.z);
        thrown.setDeltaMovement(0, -0.5, 0);
        helper.getLevel().addFreshEntity(thrown);
        helper.runAfterDelay(60, () -> {
            List<ItemEntity> back = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(6),
                    item -> item.getItem().is(Items.ENDER_PEARL));
            check(helper, back.size() == 1, "one ender pearl comes back: " + back.size());
            double off = back.getFirst().position().subtract(player.position()).horizontalDistance();
            check(helper, off < 0.3, "at the thrower's feet, not thrown on: " + String.format("%.2f", off) + " blocks off");
            helper.succeed();
        });
    }

    /**
     * Drops beyond a strip of lava, in plain sight: a magnet leaves them be - drawn across, they burned up in it - while
     * drops on this side of it, with clear floor between, come as before.
     */
    private static void magnetSparesDropsAcrossLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper, new Vec3(1.5, 1, 2.5));
        for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(3, 1, z), Blocks.LAVA);
            helper.setBlock(new BlockPos(4, 1, z), Blocks.LAVA);
        }
        ItemEntity across = drop(helper, new Vec3(6.5, 1, 2.5), Items.ENDER_PEARL);
        ItemEntity beside = drop(helper, new Vec3(1.5, 1, 6.5), Items.DIAMOND); // on this side of it
        MagnetItem.pull(player, 8);
        check(helper, across.getDeltaMovement().lengthSqr() == 0, "the pearl across the lava is left where it lies");
        check(helper, beside.getDeltaMovement().lengthSqr() > 0, "the diamond with clear floor between is drawn in");
        helper.succeed();
    }

    private static ItemEntity drop(GameTestHelper helper, Vec3 at, net.minecraft.world.item.Item item) {
        Vec3 where = helper.absoluteVec(at).add(0, 0.05, 0);
        ItemEntity entity = new ItemEntity(helper.getLevel(), where.x, where.y, where.z, new ItemStack(item));
        entity.setNoPickUpDelay();
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    /** An Item Sorter facing north: diamonds registered leave west, the rest east. */
    private static SorterBlockEntity sorter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        var sorter = helper.getBlockEntity(pos, SorterBlockEntity.class);
        sorter.filter().setItem(0, new ItemStack(Items.DIAMOND));
        return sorter;
    }

    private static Container chest(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, ChestBlockEntity.class);
    }

    /**
     * A switched-on Trash Can on top of a sorter, and the sorter holding cobblestone it keeps out (come in from a side,
     * before it was kept out): it holds on to it, waiting, and gives none of it "back up" - into the can, for good.
     */
    private static void nothingBackIntoATrashCan(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos);
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above().north(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(pos.above(), ModBlocks.TRASH_CAN.get().defaultBlockState().setValue(TrashCanBlock.POWERED, true));
        sorter.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        sorter.deny().setItem(0, new ItemStack(Items.COBBLESTONE));
        var can = helper.getBlockEntity(pos.above(), TrashCanBlockEntity.class);
        helper.runAfterDelay(40, () -> {
            check(helper, can.swallowed() == 0, "the can took none of it: " + can.swallowed());
            check(helper, sorter.getItem(0).is(Items.COBBLESTONE) && sorter.getItem(0).getCount() == 5,
                    "the sorter still holds all five: " + sorter.getItem(0));
            helper.succeed();
        });
    }

    /**
     * The other outlet a shulker box, which takes in no shulker box: the one in the chest above stays there, and the
     * cobblestone after it goes into the box all the same. The sorter drew it in and held it for good.
     */
    private static void shulkerBoxForAShulkerBox(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos);
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.SHULKER_BOX);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = chest(helper, pos.above());
        above.setItem(0, new ItemStack(Items.SHULKER_BOX));
        above.setItem(1, new ItemStack(Items.COBBLESTONE, 3));
        var box = helper.getBlockEntity(pos.east(), ShulkerBoxBlockEntity.class);
        helper.succeedWhen(() -> {
            check(helper, countIn(box, Items.COBBLESTONE) == 3, "the cobblestone goes into the shulker box: " + countIn(box, Items.COBBLESTONE));
            check(helper, countIn(above, Items.SHULKER_BOX) == 1 && sorter.isEmpty(), "the shulker box stays in the chest above");
        });
    }

    /**
     * Sorter feeding sorter, the second keeping cobblestone out: the first leaves the cobblestone in the chest above it
     * and sends the dirt behind it on, where it drew the cobblestone in and stopped, holding it.
     */
    private static void whatTheNextSorterKeepsOut(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 3);
        var first = sorter(helper, pos);
        var second = sorter(helper, pos.east());
        second.deny().setItem(0, new ItemStack(Items.COBBLESTONE));
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east().east(), Blocks.CHEST);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = chest(helper, pos.above());
        above.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        above.setItem(1, new ItemStack(Items.DIRT, 2));
        helper.succeedWhen(() -> {
            check(helper, countIn(chest(helper, pos.east().east()), Items.DIRT) == 2, "the dirt goes on through both");
            check(helper, countIn(above, Items.COBBLESTONE) == 4 && first.isEmpty() && second.isEmpty(),
                    "the cobblestone stays in the chest above, held by neither sorter");
        });
    }
}
