package dev.nookworks.spawner;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Spawner Link Sender: place it touching one or more spawners. Right-click it with Spawner Link Receivers to
 * pair them; a Spawner Controller set on such a receiver then controls every spawner here. Its lines light
 * ({@link #LIT}) while some controller runs through it: each run keeps it lit a little longer ({@link #keepLit}), and
 * when none has for a while it goes dark again of itself. Only whoever set it down pairs with it, and set down again it
 * starts afresh: see {@link SpawnerLinkSenderBlockEntity}.
 */
public class SpawnerLinkSenderBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Lit, it glows a little: well under a torch. */
    public static final int LIGHT_LEVEL = 4;
    /** Till when each lit sender is kept lit, by level (the server's alone; a restart lets them go dark, and the running
     * controllers light them again at once). */
    private static final Map<Level, Map<BlockPos, Long>> LEASES = new WeakHashMap<>();

    public SpawnerLinkSenderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /** A controller running through the sender at {@code pos}: it is lit, and stays so until {@code until} at least. */
    public static void keepLit(ServerLevel level, BlockPos pos, long until) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SpawnerLinkSenderBlock sender)) {
            return;
        }
        LEASES.computeIfAbsent(level, l -> new HashMap<>()).merge(pos.immutable(), until, Math::max);
        if (!state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, sender, (int) Math.max(1, until - level.getGameTime()));
    }

    /** Its lease run out, it goes dark; still leased, it looks again when the lease ends. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        // after a restart there are no leases at all (they are not saved), and the sender goes dark until a
        // controller runs through it again
        Map<BlockPos, Long> leases = LEASES.get(level);
        long until = leases == null ? 0L : leases.getOrDefault(pos, 0L);
        if (level.getGameTime() < until) {
            level.scheduleTick(pos, this, (int) (until - level.getGameTime()));
        } else {
            if (leases != null) {
                leases.remove(pos);
            }
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
        }
    }

    /**
     * Broken, blown up or replaced: its lease goes with it. Only its own tick let a lease go, and that tick never comes
     * for a sender no longer there, so each one taken away while lit was kept for the rest of the run.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Map<BlockPos, Long> leases = LEASES.get(level);
        if (leases != null) {
            leases.remove(pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /** Till when the sender at {@code pos} is kept lit, in game time; 0 with no lease. Public for the tests. */
    public static long leasedUntil(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Long> leases = LEASES.get(level);
        return leases == null ? 0L : leases.getOrDefault(pos, 0L);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerLinkSenderBlockEntity(pos, state);
    }

    /** Its owner, and a stamp no receiver carries yet: links made before it was moved reach nothing now. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpawnerLinkSenderBlockEntity sender) {
            long stamp;
            do {
                stamp = level.getRandom().nextLong();
            } while (stamp == 0); // 0 is the stamp of a sender from before stamps
            sender.placed(by instanceof Player player ? player.getUUID() : null, stamp);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.SPAWNER_LINK_RECEIVER.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            SpawnerLinkSenderBlockEntity sender = level.getBlockEntity(pos) instanceof SpawnerLinkSenderBlockEntity s ? s : null;
            if (sender != null && sender.owner() != null && !sender.owner().equals(player.getUUID())
                    && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.spawner_link.not_yours"));
                return Clicks.REFUSED;
            }
            if (sender != null) {
                sender.adopt(player.getUUID());
            }
            stack.set(ModDataComponents.SPAWNER_LINK.get(), GlobalPos.of(level.dimension(), pos));
            stack.set(ModDataComponents.SPAWNER_LINK_STAMP.get(), sender == null ? 0L : sender.stamp());
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
            player.sendOverlayMessage(Component.translatable("message.nookworks.spawner_link.paired", pos.getX(), pos.getY(), pos.getZ()));
        }
        return InteractionResult.SUCCESS;
    }
}
