package dev.nookworks.oil;

import dev.nookworks.crop.BloomingCropBlock;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Sunflower Seeds sown in farmland (the user, 2026-10-12: sunflowers are hard to find in survival, so the Sifter gives
 * their seeds, and the Oil Press takes the seeds, not the flower). It comes up as a sprout, grows a stalk and a green
 * bud, and then opens into vanilla's sunflower, both halves - as vanilla's torchflower crop becomes a torchflower. With
 * no room above for the flower's head it waits as a bud. Bone meal grows it a stage at a time.
 */
public class SunflowerCropBlock extends BloomingCropBlock {
    public SunflowerCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModOil.SUNFLOWER_SEEDS.get();
    }

    @Override
    protected boolean canBloom(LevelReader level, BlockPos pos) {
        return roomAbove(level, pos);
    }

    @Override
    protected boolean bloom(Level level, BlockPos pos) {
        if (!roomAbove(level, pos)) {
            return false;
        }
        DoublePlantBlock.placeAt(level, Blocks.SUNFLOWER.defaultBlockState(), pos, Block.UPDATE_ALL);
        return true;
    }
}
