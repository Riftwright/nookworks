package dev.nookworks.ifworld;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.decor.ChevalMirrorBlock;
import dev.nookworks.registry.ModItems;
import dev.nookworks.travel.DaemonDestinations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The If Mirror: a standing glass in a Dawnwood frame, two blocks high. Thrown at six times with a Die (a click with one
 * in hand), the six faces are the number of the If it opens onto, and the If is made; from then on, a click with an
 * empty hand steps through it ({@link IfTravel}). One stands at home - anywhere but the If - and one in the If, its way
 * home; a second in either place is refused, saying where the first is. Its glass shows the faces as they are thrown,
 * and once all six are, the world beyond (the client draws both, from {@link IfMirrorBlockEntity}).
 */
public class IfMirrorBlock extends ChevalMirrorBlock implements EntityBlock {
    public IfMirrorBlock(Properties properties) {
        super(properties);
    }

    /**
     * Its glass to whoever sets it down; not where the world already has its mirror on this side. Refused so, the client,
     * which cannot know, has set it down and used it up already: its inventory is sent again, or the mirror left its hand.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (super.getStateForPlacement(context) == null) {
            return null;
        }
        if (level instanceof ServerLevel server) {
            GlobalPos there = IfMirrors.otherThan(server, pos);
            if (there != null) {
                if (context.getPlayer() != null) {
                    context.getPlayer().sendOverlayMessage(Component.translatable(server.dimension().equals(IfWorld.KEY)
                            ? "message.nookworks.if_mirror.one_in_if" : "message.nookworks.if_mirror.one_at_home",
                            there.pos().getX(), there.pos().getY(), there.pos().getZ(), DaemonDestinations.name(there.dimension())));
                    context.getPlayer().containerMenu.sendAllDataToRemote();
                }
                return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel server) {
            IfMirrors.placed(server, pos);
            if (by instanceof Player player) {
                IfWorld world = IfWorld.get(server.getServer());
                player.sendOverlayMessage(server.dimension().equals(IfWorld.KEY) ? Component.translatable("message.nookworks.if_mirror.way_home")
                        : world.rolled() ? Component.translatable("message.nookworks.if_mirror.number", world.number())
                        : Component.translatable("message.nookworks.if_mirror.throw"));
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new IfMirrorBlockEntity(pos, state) : null;
    }

    /** A Die thrown at it, while the If is not rolled: one more face. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(ModItems.DICE.get())) {
            // through the glass with an empty hand only: a block held is set down against it, not a trip
            return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            IfWorld world = IfWorld.get(server.getServer());
            BlockPos lower = lower(state, pos);
            if (world.rolled()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.number", world.number()));
            } else if (server.dimension().equals(IfWorld.KEY) || !IfMirrors.isHome(server, lower)) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.not_home"));
            } else {
                int face = 1 + server.getRandom().nextInt(6);
                world.addThrow(face);
                server.playSound(null, lower.above(), SoundEvents.BONE_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 1.6F);
                if (server.getBlockEntity(lower) instanceof IfMirrorBlockEntity mirror) {
                    mirror.thrown(server.getGameTime());
                }
                if (world.rolled()) {
                    player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.rolled", world.number()));
                    // made between ticks, not inside one (execute would run it here and now, on the server thread)
                    server.getServer().schedule(new TickTask(server.getServer().getTickCount(), () -> IfLevel.open(server.getServer())));
                    server.playSound(null, lower.above(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.7F);
                } else {
                    player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.thrown", face, world.faces().size(),
                            IfWorld.THROWS));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** With an empty hand: through the glass, once the If is rolled. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer traveller) {
            BlockPos lower = lower(state, pos);
            if (!IfWorld.get(server.getServer()).rolled()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.throw"));
            } else if (hit.getDirection() == state.getValue(FACING).getOpposite()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.back")); // its back is wood
            } else {
                IfTravel.through(traveller, server, lower);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
