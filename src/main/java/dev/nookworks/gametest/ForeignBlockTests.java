package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Wrench on another mod's block (the user, 2026-10-23: レンチはあなたの案で実装していいです): sneak-used, the block's
 * own use with a wrench comes first, as with that mod's own wrench - a machine that picks itself up does - and one that
 * takes none is turned back a step, its screen not opened. Two stand-in blocks of a namespace of no mod (registered only
 * in GameTest runs) play the other mod's.
 */
final class ForeignBlockTests {
    private ForeignBlockTests() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("nookworks_test");
    static final DeferredBlock<Machine> PICKED_UP = BLOCKS.registerBlock("picked_up_by_a_wrench", p -> new Machine(p, true),
            p -> p);
    static final DeferredBlock<Machine> SCREEN = BLOCKS.registerBlock("opens_a_screen", p -> new Machine(p, false),
            p -> p);
    /** Times a stand-in's screen was opened. */
    static int screens;

    static void listen(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    static void register() {
        test("wrench_sneaking_lets_another_mods_block_go_first", ForeignBlockTests::sneaking);
    }

    /** Another mod's machine, facing some way: one takes a sneak-used wrench to pick itself up; both have a screen. */
    static final class Machine extends HorizontalDirectionalBlock {
        private final boolean pickedUp;

        Machine(BlockBehaviour.Properties properties, boolean pickedUp) {
            super(properties);
            this.pickedUp = pickedUp;
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (this.pickedUp && stack.is(Tags.Items.TOOLS_WRENCH) && player.isSecondaryUseActive()) {
                level.removeBlock(pos, false);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            screens++;
            return InteractionResult.SUCCESS;
        }
    }

    private static void sneaking(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 1, 1);
        BlockPos other = new BlockPos(3, 1, 1);
        helper.setBlock(machine, PICKED_UP.get().defaultBlockState());
        helper.setBlock(other, SCREEN.get().defaultBlockState());
        ServerPlayer player = MockPlayers.inLevel(helper);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        player.getAbilities().flying = true; // as FurnitureApartTests' wrencher: one falling to the void hangs the batch
        player.onUpdateAbilities();
        player.setShiftKeyDown(true);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        int before = screens;
        use(helper, player, wrench, machine);
        check(helper, helper.getBlockState(machine).isAir(), "the machine that takes a wrench picked itself up: " + helper.getBlockState(machine));
        use(helper, player, wrench, other);
        check(helper, helper.getBlockState(other).getValue(HorizontalDirectionalBlock.FACING) != Direction.NORTH,
                "the one that takes none is turned: " + helper.getBlockState(other));
        check(helper, screens == before, "and no screen was opened");
        player.setShiftKeyDown(false);
        BlockState turned = helper.getBlockState(other);
        use(helper, player, wrench, other);
        check(helper, screens == before + 1 && helper.getBlockState(other) == turned, "not sneaking, its own use (its screen) still comes first");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos block) {
        BlockPos at = helper.absolutePos(block);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false));
    }
}
