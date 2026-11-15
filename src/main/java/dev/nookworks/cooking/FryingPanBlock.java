package dev.nookworks.cooking;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Trial (the cooking review, 2026-11-14). Frying Pan: a shallow black iron pan with a wooden grip, for what is fried or
 * stir-fried in a little oil - fried eggs, tamagoyaki, pancakes, fried rice, stir-fried vegetables. It is the Iron Pot
 * in everything but what it holds and cooks: set over heat as a pot is (on a Grill over a campfire it sits on the
 * grate), a bottle of vegetable oil at most and never water, four things at a time, and the recipes marked
 * {@code "pan": true} ({@link PotRecipe}). Hoppers and pipes feed and empty it as they do a pot.
 */
public class FryingPanBlock extends IronPotBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 3, 15);
    private static final VoxelShape ON_GRILL_SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 1, 16), Block.box(1, 1, 1, 15, 4, 15));

    public FryingPanBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean pan() {
        return true;
    }

    @Override
    protected VoxelShape shape(boolean onGrill) {
        return onGrill ? ON_GRILL_SHAPE : SHAPE;
    }
}
