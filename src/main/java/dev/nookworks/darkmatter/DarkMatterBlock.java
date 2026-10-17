package dev.nookworks.darkmatter;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Dark Matter: charred food and an amethyst block. It puts out the light in the disc round it ({@link DarkMatter}),
 * until something in the disc gives light of its own. DARKENING says which: the block shows its drifting motes while it
 * works, and goes dull while a light has beaten it.
 */
public class DarkMatterBlock extends BaseEntityBlock {
    public static final BooleanProperty DARKENING = BooleanProperty.create("darkening");

    public DarkMatterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DARKENING, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DARKENING);
    }

    /** Placed beside a lamp, it starts out beaten rather than putting the dark down for a moment. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(DARKENING, !DarkMatter.lit(context.getLevel(), context.getClickedPos()));
    }

    /** Set down (not loaded): tells the clients to spread the dark from nothing. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        super.onPlace(state, level, pos, old, movedByPiston);
        if (!level.isClientSide() && !old.is(this)) {
            level.blockEvent(pos, this, DarkMatterBlockEntity.EVENT_PLACED, 0);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DarkMatterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, ModBlockEntities.DARK_MATTER.get(), DarkMatterBlockEntity::clientTick);
        }
        return createTickerHelper(type, ModBlockEntities.DARK_MATTER.get(), DarkMatterBlockEntity::serverTick);
    }
}
