package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * How we ask claim mods before changing the world for a player: as vanilla's placing asks them. A claim mod reads the
 * block placed from the world, and some let air through (Open Parties and Claims does): asked with nothing set down
 * yet, they let everything through. Here a claim that holds only against a real block put into an empty cell, as Open
 * Parties and Claims' place check does, and what we do near it.
 */
final class PermissionTests {
    private PermissionTests() {}

    static void register() {
        NookworksGameTests.test("permission_asks_as_placing_does_and_puts_the_cell_back", PermissionTests::asksAsPlacingDoes);
        NookworksGameTests.test("building_wand_keeps_out_of_a_strict_claim", PermissionTests::wandKeepsOut);
    }

    private static volatile BoundingBox claimed;
    private static volatile ServerPlayer claimant;
    private static boolean heard;

    /** While set, a player's placing in {@code box} is refused - only a real block into an empty cell, as some claim mods read it. */
    private static void claim(BoundingBox box, ServerPlayer player) {
        if (!heard) {
            heard = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (BlockEvent.EntityPlaceEvent event) -> {
                BoundingBox box2 = claimed;
                if (box2 != null && event.getEntity() == claimant && box2.isInside(event.getPos())
                        && !event.getPlacedBlock().isAir() && event.getBlockSnapshot().getState().isAir()) {
                    event.setCanceled(true);
                }
            });
        }
        claimant = player;
        claimed = box;
    }

    private static void unclaim() {
        claimed = null;
        claimant = null;
    }

    /** Refused in the claim, let be outside; an empty cell left empty, and a chest asked about left as it was, its things in it. */
    private static void asksAsPlacingDoes(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BlockPos inside = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockPos outside = helper.absolutePos(new BlockPos(1, 2, 2));
        BlockPos chest = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(chest)).setItem(3, new ItemStack(Items.DIAMOND, 5));
        claim(new BoundingBox(inside.getX(), inside.getY() - 1, inside.getZ() - 1, inside.getX() + 2, inside.getY() + 2, inside.getZ() + 3), player);
        try {
            check(helper, !Permission.mayPlace(level, inside, Direction.UP, player), "refused in the claim");
            check(helper, Permission.mayPlace(level, outside, Direction.UP, player), "let be outside it");
            check(helper, level.getBlockState(inside).isAir() && level.getBlockState(outside).isAir(), "the cells left empty");
            check(helper, !Permission.mayPlace(level, chest, Direction.UP, player), "a chest's cell in the claim: refused too");
            check(helper, level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockEntity(chest) instanceof ChestBlockEntity box
                    && box.getItem(3).is(Items.DIAMOND) && box.getItem(3).getCount() == 5, "and the chest put back, its diamonds in it");
        } finally {
            unclaim();
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The Building Wand spreading a row over a claim's edge builds up to the edge and no further. */
    private static void wandKeepsOut(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
        }
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 2, 3.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BUILDING_WAND.get()));
        player.getInventory().setItem(5, new ItemStack(Items.STONE, 64));
        BlockPos edge = helper.absolutePos(new BlockPos(3, 2, 2));
        claim(new BoundingBox(edge.getX(), edge.getY() - 1, edge.getZ() - 2, edge.getX() + 5, edge.getY() + 3, edge.getZ() + 2), player);
        try {
            BlockPos clicked = helper.absolutePos(new BlockPos(1, 1, 2));
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false)));
            check(helper, helper.getBlockState(new BlockPos(1, 2, 2)).is(Blocks.STONE), "built outside the claim");
            for (int x = 3; x <= 5; x++) {
                check(helper, helper.getBlockState(new BlockPos(x, 2, 2)).isAir(), "nothing built in the claim, at x " + x);
            }
        } finally {
            unclaim();
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
