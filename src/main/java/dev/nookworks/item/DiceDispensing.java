package dev.nookworks.item;

import dev.nookworks.Nookworks;
import dev.nookworks.block.DiceBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * A dispenser throws a die: it drops to the ground in front of it, lands as a die block and tumbles to a number - coming
 * in from the dispenser's side, end over end -
 * which a comparator reads as it would any die. Knock it loose with a piston and a Magnet Chest (or a hopper) brings
 * it back round to the dispenser. With nowhere to land it is spat out as an item, as anything else would be.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class DiceDispensing {
    /** How far a thrown die falls before it is simply dropped as an item instead. */
    private static final int MAX_FALL = 8;

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DispenserBlock.registerBehavior(ModItems.DICE.get(), new Throw()));
    }

    /**
     * Where a die thrown from {@code from} towards {@code side} comes to rest, or null if it cannot: through air and
     * grass, not into water or lava (a pond would lose a source to it, and a die does not lie on water).
     */
    public static BlockPos landing(ServerLevel level, BlockPos from, Direction side) {
        BlockPos spot = from.relative(side);
        if (!level.isLoaded(spot) || !open(level.getBlockState(spot))) {
            return null;
        }
        for (int i = 0; i < MAX_FALL && level.isLoaded(spot.below()) && open(level.getBlockState(spot.below())); i++) {
            spot = spot.below();
        }
        BlockState floor = level.getBlockState(spot.below());
        return floor.canBeReplaced() ? null : spot;
    }

    /** Room for a die to pass through or lie in: what a block put there would replace, with no fluid in it. */
    private static boolean open(BlockState state) {
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    static final class Throw extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            ServerLevel level = source.level();
            Direction side = source.state().getValue(DispenserBlock.FACING);
            BlockPos spot = landing(level, source.pos(), side);
            if (spot == null) {
                return super.execute(source, stack);
            }
            BlockState die = ModBlocks.DICE.get().defaultBlockState()
                    .setValue(DiceBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()))
                    .setValue(DiceBlock.VALUE, 1 + level.getRandom().nextInt(6));
            level.setBlock(spot, die, Block.UPDATE_ALL);
            level.gameEvent(null, GameEvent.BLOCK_PLACE, spot);
            // thrown in from the side it came from; out of a dispenser facing up or down it is only tossed up where
            // it landed, as a die rolled by hand is (a throw is worked out side on: up or down has no side to come from)
            DiceBlock.roll(level, spot, die, side.getAxis().isHorizontal() ? side : null);
            stack.shrink(1);
            return stack;
        }
    }

    private DiceDispensing() {}
}
