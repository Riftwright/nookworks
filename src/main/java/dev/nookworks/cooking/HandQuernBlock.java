package dev.nookworks.cooking;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hand Quern: two millstones in a spruce frame, the upper one turned by the bar on its spindle, grind wheat to flour
 * between them - flour for bread, noodles and frying before there is a powered Crusher. It also crumbles bread. What it
 * makes is the {@code nookworks:milling} data map ({@link HandWork}). A Motor turns it by its spindle, from above or
 * below; from a side it meets nothing that turns, and does not (the user, 2026-10-10).
 */
public class HandQuernBlock extends HandMachineBlock {
    // the stones, the spout, the frame (its posts and beam), the spindle up through the beam - facing north, the spout
    // to the north and the frame across x, as the model is drawn; turned with it for the other ways
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(Block.box(2, 0, 2, 14, 8, 14),
            Block.box(6, 1, 0, 10, 3, 2), Block.box(0.5, 0, 6.5, 15.5, 14, 9.5), Block.box(7, 8, 7, 9, 16, 9)));

    public HandQuernBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape shape(Direction facing) {
        return SHAPES.get(facing);
    }

    @Override
    protected boolean drivenVertically() {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.HAND_QUERN.get(), HandMachineBlockEntity::serverTick);
    }

    public static class Entity extends HandMachineBlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.HAND_QUERN.get(), pos, state, HandWork.MILLING);
        }

        /** By its spindle: from above or below, never from a side. */
        @Override
        public boolean drivenFrom(Direction side) {
            return side.getAxis().isVertical();
        }

        @Override
        protected SoundEvent turnSound() {
            return SoundEvents.GRINDSTONE_USE;
        }

        @Override
        protected String statusKey() {
            return "nookworks.machine.hand_quern";
        }
    }
}
