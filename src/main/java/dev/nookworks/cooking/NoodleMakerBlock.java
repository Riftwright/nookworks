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
 * Noodle Maker: a hand-cranked machine clamped to a board, as noodle shops use - a sheet of udon dough goes in at
 * the top, is rolled thin between its rollers and cut into noodles by the cutters under them. What it makes is the
 * {@code nookworks:noodle_making} data map ({@link HandWork}).
 */
public class NoodleMakerBlock extends HandMachineBlock {
    // the board and the machine on it, facing north as the model is drawn; turned with it for the other ways
    private static final Map<Direction, VoxelShape> SHAPES =
            Shapes.rotateHorizontal(Shapes.or(Block.box(1, 0, 2, 15, 2, 14), Block.box(3, 2, 4, 13, 12, 12)));

    public NoodleMakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape shape(Direction facing) {
        return SHAPES.get(facing);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.NOODLE_MAKER.get(), HandMachineBlockEntity::serverTick);
    }

    public static class Entity extends HandMachineBlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ModBlockEntities.NOODLE_MAKER.get(), pos, state, HandWork.NOODLE_MAKING);
        }

        @Override
        protected SoundEvent turnSound() {
            return SoundEvents.WOOD_HIT;
        }

        @Override
        protected String statusKey() {
            return "nookworks.machine.noodle_maker";
        }
    }
}
