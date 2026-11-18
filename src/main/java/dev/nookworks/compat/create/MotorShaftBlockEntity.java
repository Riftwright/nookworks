package dev.nookworks.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The block entity of the retired {@code nookworks:motor_shaft} ({@link MotorShaftBlock}): at its first tick the block
 * is Create's plain shaft again, axis and water kept. Whatever a Motor meets there it then drives where it stands
 * ({@link CreateEnds}); what the old block saved (its direction, its capacity, a relay's source) is not read.
 */
public class MotorShaftBlockEntity extends KineticBlockEntity {
    public MotorShaftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            super.tick();
            return;
        }
        BlockState state = this.getBlockState();
        this.level.setBlock(this.worldPosition, MotorShaftBlock.plainShaft().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, state.getValue(RotatedPillarKineticBlock.AXIS))
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED)), Block.UPDATE_ALL);
    }
}
