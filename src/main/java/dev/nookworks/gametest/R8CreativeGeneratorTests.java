package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.block.CreativeGeneratorBlock;
import dev.nookworks.item.CreativeOnlyBlockItem;
import dev.nookworks.machine.CreativeGeneratorBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Creative Generator (the user, 2026-10-06: "クリエイティブ用の無限発電機作れる？サバイバルでは使用不能のやつ"): power
 * without end and without fuel, sent along Energy Cables as a Generator's is and drawn on by any cable; set down only by
 * a player in creative mode, and in survival neither broken nor dropped, as vanilla's command block.
 */
final class R8CreativeGeneratorTests {
    private R8CreativeGeneratorTests() {}

    static void register() {
        NookworksGameTests.test("creative_generator_powers_an_electric_furnace_over_a_cable", 200,
                R8CreativeGeneratorTests::powersAFurnaceOverACable);
        NookworksGameTests.test("creative_generator_is_endless_and_stops_on_a_signal", R8CreativeGeneratorTests::endlessAndStopsOnASignal);
        NookworksGameTests.test("creative_generator_is_placed_in_creative_mode_only", R8CreativeGeneratorTests::placedInCreativeOnly);
        NookworksGameTests.test("creative_generator_is_not_broken_or_dropped_in_survival", R8CreativeGeneratorTests::notBrokenOrDroppedInSurvival);
    }

    /** With no fuel in anything, an Electric Furnace two cables away smelts raw iron into the barrel under it. */
    private static void powersAFurnaceOverACable(GameTestHelper helper) {
        BlockPos gen = new BlockPos(1, 2, 3);
        BlockPos furnace = new BlockPos(4, 2, 3);
        helper.setBlock(gen, ModBlocks.CREATIVE_GENERATOR.get());
        helper.setBlock(new BlockPos(2, 2, 3), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(furnace.below(), Blocks.BARREL);
        helper.setBlock(furnace, ModBlocks.ELECTRIC_FURNACE.get());
        ElectricFurnaceBlockEntity smelter = helper.getBlockEntity(furnace, ElectricFurnaceBlockEntity.class);
        check(helper, smelter.energyStored() == 0, "the furnace starts with no power");
        smelter.setItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON));
        var level = helper.getLevel();
        helper.succeedWhen(() -> {
            check(helper, MachineIO.receivers(level, helper.absolutePos(gen)).stream()
                    .anyMatch(r -> r.pos().equals(helper.absolutePos(furnace))), "the cable leads it to the furnace");
            check(helper, helper.getBlockEntity(furnace.below(), BarrelBlockEntity.class).countItem(Items.IRON_INGOT) == 1,
                    "an iron ingot in the barrel under the furnace");
        });
    }

    /**
     * Another mod's cable may draw on it without end, and put nothing in; a Generator beside it does not count it among
     * the machines it feeds. A redstone signal stops it: nothing more is drawn, it sends nothing, and it says so.
     */
    private static void endlessAndStopsOnASignal(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.CREATIVE_GENERATOR.get());
        BlockPos generator = pos.east();
        helper.setBlock(generator, ModBlocks.GENERATOR.get());
        var level = helper.getLevel();
        var gen = helper.getBlockEntity(pos, CreativeGeneratorBlockEntity.class);
        var power = level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.UP);
        check(helper, power != null, "a cable finds its power");
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, power.extract(Integer.MAX_VALUE, tx) == Integer.MAX_VALUE && power.extract(Integer.MAX_VALUE, tx) == Integer.MAX_VALUE,
                    "drawn on as much as asked, again and again");
            check(helper, power.insert(1_000, tx) == 0, "and nothing goes in");
            tx.commit();
        }
        check(helper, power.getAmountAsLong() == Long.MAX_VALUE, "as full as ever: " + power.getAmountAsLong());
        check(helper, MachineIO.receivers(level, helper.absolutePos(generator)).stream()
                .noneMatch(r -> r.pos().equals(helper.absolutePos(pos))), "a Generator does not feed it");
        check(helper, key(gen).equals("nookworks.machine.creative_generator.running"), "it says it runs: " + key(gen));

        helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, power.extract(1_000, tx) == 0, "stopped by a signal, nothing is drawn");
        }
        check(helper, power.getAmountAsLong() == 0, "and it shows none");
        check(helper, key(gen).equals(MachineStatus.REDSTONE.translationKey()), "it says a signal stops it: " + key(gen));
        helper.succeed();
    }

    private static String key(CreativeGeneratorBlockEntity gen) {
        return gen.status().getContents() instanceof TranslatableContents t ? t.getKey() : gen.status().getString();
    }

    /**
     * A player in survival using the item on the floor sets nothing down and keeps the item; so does one in adventure,
     * and no player at all (a machine placing it). A player in creative mode sets it down, facing them.
     */
    private static void placedInCreativeOnly(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 1, 3);
        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.ADVENTURE}) {
            ServerPlayer player = MockPlayers.inLevel(helper, mode);
            ItemStack stack = new ItemStack(ModItems.CREATIVE_GENERATOR.get());
            place(helper, player, stack, at);
            check(helper, helper.getBlockState(at).isAir(), "a player in " + mode + " sets none down: " + helper.getBlockState(at));
            check(helper, player.getMainHandItem().is(ModItems.CREATIVE_GENERATOR.get()) && player.getMainHandItem().getCount() == 1,
                    "and keeps the item: " + player.getMainHandItem());
        }
        check(helper, !CreativeOnlyBlockItem.mayPlace(null), "nor does a machine placing it with no player");

        ServerPlayer creative = MockPlayers.inLevel(helper, GameType.CREATIVE);
        place(helper, creative, new ItemStack(ModItems.CREATIVE_GENERATOR.get()), at);
        BlockState placed = helper.getBlockState(at);
        check(helper, placed.is(ModBlocks.CREATIVE_GENERATOR.get()), "a player in creative mode sets it down: " + placed);
        check(helper, placed.getValue(CreativeGeneratorBlock.FACING) == Direction.SOUTH, "its front to them: "
                + placed.getValue(CreativeGeneratorBlock.FACING));
        helper.succeed();
    }

    /** Uses {@code stack} on the top of the floor under {@code at}, from a little south of it, looking north. */
    private static void place(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos at) {
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(at.south(2)));
        player.snapTo(stand.x, stand.y, stand.z, 180.0F, 30.0F); // facing north
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(at.below());
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
    }

    /**
     * Mined in survival with the best pickaxe for as long as you like, it never breaks, as bedrock or a command block; no
     * explosion, wither or dragon breaks it either. Taken away by anything else (a block breaker, a command), it drops
     * nothing, and nothing when a creative player breaks it.
     */
    private static void notBrokenOrDroppedInSurvival(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.CREATIVE_GENERATOR.get());
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(pos);
        BlockState state = helper.getBlockState(pos);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(pos.south(2)));
        player.snapTo(stand.x, stand.y, stand.z, 180.0F, 30.0F);
        check(helper, state.getDestroyProgress(player, level, at) == 0.0F, "a netherite pickaxe makes no headway: "
                + state.getDestroyProgress(player, level, at));
        player.gameMode.handleBlockBreakAction(at, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.SOUTH,
                level.getMaxY(), 0);
        for (int tick = 0; tick < 600; tick++) {
            player.gameMode.tick();
        }
        player.gameMode.handleBlockBreakAction(at, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.SOUTH,
                level.getMaxY(), 1);
        helper.assertBlockPresent(ModBlocks.CREATIVE_GENERATOR.get(), pos);
        check(helper, state.getBlock().getExplosionResistance() >= 3_600_000.0F
                && state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE), "nor explosions, the wither or the dragon");
        check(helper, Block.getDrops(state, level, at, level.getBlockEntity(at), player, player.getMainHandItem()).isEmpty(),
                "it has nothing to drop");

        level.destroyBlock(at, true); // a block breaker's way
        helper.assertBlockNotPresent(ModBlocks.CREATIVE_GENERATOR.get(), pos);
        helper.setBlock(pos, ModBlocks.CREATIVE_GENERATOR.get());
        ServerPlayer creative = MockPlayers.inLevel(helper, GameType.CREATIVE);
        creative.snapTo(stand.x, stand.y, stand.z, 180.0F, 30.0F);
        creative.gameMode.handleBlockBreakAction(at, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.SOUTH,
                level.getMaxY(), 2);
        helper.assertBlockNotPresent(ModBlocks.CREATIVE_GENERATOR.get(), pos);
        helper.runAfterDelay(2, () -> {
            check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "nothing dropped: " + helper.getEntities(EntityTypes.ITEM));
            helper.succeed();
        });
    }
}
