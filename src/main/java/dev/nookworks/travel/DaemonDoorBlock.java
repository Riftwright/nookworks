package dev.nookworks.travel;

import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Dock-a-Daemon Door: one door to every dimension, so a base in a pack of many mods is not walled with portals. It
 * opens and shuts by hand as a wooden door does; open, whoever walks through it steps out in the dimension its panel is
 * set to for them ({@link DaemonPanelBlock}) - or, through a return door, back at the home door ({@link DaemonTravel}).
 * <p>
 * One stands in a dimension at a time ({@link DaemonDoors}); setting down a second says where the first is. The first in
 * the world is the home door; the others are the return doors its trips set down, one in each dimension it has opened
 * onto, which may be picked up and set elsewhere in theirs.
 * <p>
 * It is a gate only when set into a wall - the ten blocks round its doorway full blocks ({@link #framed}) - and only from
 * its front, the side whoever set it down stood on; from behind, unframed, or with its panel set off, it is a door like
 * any other. As a gate it shows the gate of the world beyond, shut in its porthole and open filling its doorway: the
 * Nether's swirl, the End's stars, the If's mirror, another mod's own portal (drawn by the client).
 */
public class DaemonDoorBlock extends DoorBlock implements EntityBlock {
    /** Opened by hand and by a wind charge; a wooden leaf rolling along its rail, as it sounds. */
    public static final BlockSetType TYPE = BlockSetType.register(new BlockSetType(Nookworks.id("daemon").toString(), true, true, true,
            BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.WOOD,
            SoundEvents.BARREL_CLOSE, SoundEvents.BARREL_OPEN, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundEvents.WOODEN_TRAPDOOR_OPEN,
            SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundEvents.WOODEN_BUTTON_CLICK_ON));
    /**
     * The leaf, hung in front of the wall over the doorway: two pixels out from the block's front (shaped here for a door
     * whose front is north). Shut, it is in the way; open, it has slid off along the wall and only a click on the doorway
     * finds it, to shut it again.
     */
    private static final Map<Direction, VoxelShape> LEAF = Shapes.rotateHorizontal(Block.box(0, 0, -2, 16, 16, 0));
    /** The doorway one steps into to go through: the middle of the block, not its edges (walking past is not going in). */
    private static final VoxelShape DOORWAY = Block.box(3, 0, 3, 13, 16, 13);

    public DaemonDoorBlock(Properties properties) {
        super(TYPE, properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LEAF.get(front(state));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : LEAF.get(front(state));
    }

    /**
     * The side it slides to as it opens, along the wall: where a hinged door's hinge would be (vanilla's hinge, chosen as
     * a door's is as it is set down) - its pull bar on the other edge, as on a sliding door.
     */
    public static Direction slideSide(BlockState state) {
        Direction facing = state.getValue(FACING);
        return (state.getValue(HINGE) == DoorHingeSide.RIGHT ? facing.getCounterClockWise() : facing.getClockWise()).getOpposite();
    }

    /**
     * A Destination Panel on the wall beside {@code lower}'s door, on the side it slides to: it would slide over it. Turned
     * to slide the other way, both halves (the upper follows the lower), unless a panel is on that side too.
     */
    public static void slideAwayFromPanels(Level level, BlockPos lower) {
        BlockState state = level.getBlockState(lower);
        if (!(state.getBlock() instanceof DaemonDoorBlock)) {
            return;
        }
        Direction side = slideSide(state);
        if (panelOn(level, lower, state, side) && !panelOn(level, lower, state, side.getOpposite())) {
            level.setBlock(lower, state.setValue(HINGE, state.getValue(HINGE) == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT),
                    Block.UPDATE_ALL);
        }
    }

    /** A panel on the wall beside the door, a block or two to {@code side}, at any height from its foot to over its top. */
    private static boolean panelOn(Level level, BlockPos lower, BlockState state, Direction side) {
        Direction front = front(state);
        for (int along = 1; along <= 2; along++) {
            for (int up = -1; up <= 2; up++) {
                for (int out = 0; out <= 1; out++) {
                    if (level.getBlockState(lower.relative(side, along).above(up).relative(front, out)).getBlock() instanceof DaemonPanelBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * As a door is placed, but not where its dimension has a door already: it says where that one is. The client, which
     * cannot know, has set it down and used it up already: its inventory is sent again, or the door left its hand.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            BlockPos there = DaemonDoors.get(level.getServer()).otherThan(level, context.getClickedPos());
            if (there != null) {
                if (context.getPlayer() != null) {
                    context.getPlayer().sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.one_here",
                            there.getX(), there.getY(), there.getZ()));
                    context.getPlayer().containerMenu.sendAllDataToRemote();
                }
                return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    /**
     * Not broken by a mob: a zombie at a door on Hard, a vindicator in a raid. It is a wooden door to them, and broken it
     * took the home door's record with it, sending everyone coming home to their respawn point until it was set down again.
     */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return !(entity instanceof Mob) && super.canEntityDestroy(state, level, pos, entity);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel server) {
            slideAwayFromPanels(level, pos); // a panel already on the wall: it slides the other way
            DaemonDoors doors = DaemonDoors.get(server.getServer());
            doors.record(server.dimension(), pos);
            DaemonDoorBlockEntity.refreshAll(server.getServer());
            if (by instanceof Player player) {
                player.sendOverlayMessage(Component.translatable(doors.isHome(server.dimension(), pos)
                        ? "message.nookworks.daemon_door.home" : "message.nookworks.daemon_door.return"));
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new DaemonDoorBlockEntity(pos, state) : null;
    }

    /** Opened where it would lead somewhere but stands in no wall, it says it must be set into one. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
        if (level instanceof ServerLevel server && !state.getValue(OPEN)) {
            BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            DaemonDoors doors = DaemonDoors.get(server.getServer());
            boolean leads = doors.isHome(server.dimension(), lower)
                    ? TravelLog.of(player).destination().filter(d -> !d.equals(server.dimension())).isPresent()
                    : doors.home() != null;
            if (leads && !framed(level, lower, state.getValue(FACING))) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.unframed"));
            }
        }
        return result;
    }

    /**
     * The ten blocks round the doorway of the door whose lower half is at {@code lower}, in its wall: beside both halves,
     * under the lower, over the upper, and the four corners.
     */
    public static List<BlockPos> frame(BlockPos lower, Direction facing) {
        Direction across = facing.getClockWise();
        List<BlockPos> out = new ArrayList<>(10);
        for (int up = -1; up <= 2; up++) {
            for (int side = -1; side <= 1; side++) {
                if (side != 0 || up == -1 || up == 2) {
                    out.add(lower.relative(across, side).above(up));
                }
            }
        }
        return out;
    }

    /** Set into a wall: each of the ten blocks round its doorway ({@link #frame}) a full block. Only so is it a gate. */
    public static boolean framed(BlockGetter level, BlockPos lower, Direction facing) {
        for (BlockPos pos : frame(lower, facing)) {
            if (!level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** Its front: the side whoever set it down stood on, where one comes out and from where one goes in. */
    public static Direction front(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return state.getValue(OPEN) ? DOORWAY : Shapes.empty();
    }

    /**
     * Open onto the Nether, it does what a nether portal does: purple motes drawn about its doorway, now and then its hum.
     * The other gates are quiet, as theirs are. Where it leads is the client's own player's choice at a home door.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!state.getValue(OPEN) || !(level.getBlockEntity(lower) instanceof DaemonDoorBlockEntity door)) {
            return;
        }
        Optional<ResourceKey<Level>> to = level.players().stream().filter(Player::isLocalPlayer).findFirst().flatMap(door::leadsFor);
        if (to.isEmpty() || !DaemonDestinations.gate(level.dimension(), to.get()).equals(Level.NETHER)
                || !framed(level, lower, state.getValue(FACING))) {
            return;
        }
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F,
                    random.nextFloat() * 0.4F + 0.8F, false);
        }
        // as a nether portal's: each starts a little off the doorway and is drawn back to where it was spawned
        Direction front = front(state);
        Direction across = front.getClockWise();
        double plane = 0.5 - 1.5 / 16;
        for (int i = 0; i < 4; i++) {
            double side = (random.nextDouble() - 0.5) * 0.7;
            double x = pos.getX() + 0.5 - front.getStepX() * plane + across.getStepX() * side;
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + 0.5 - front.getStepZ() * plane + across.getStepZ() * side;
            double out = random.nextFloat() * (random.nextBoolean() ? 1 : -1);
            level.addParticle(ParticleTypes.PORTAL, x, y, z, front.getStepX() * out, (random.nextFloat() - 0.5) * 0.5, front.getStepZ() * out);
        }
    }

    /**
     * Walked into while open, from its front: through it, if it leads anywhere for this player. Walked through from
     * behind it is a doorway like any other.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        if (state.getValue(OPEN) && entity instanceof ServerPlayer player && !player.isSpectator() && goingIn(player, state)) {
            // pos is the game's one cursor over every block the player touches this tick: a copy is kept, not it
            DaemonTravel.walkIn(player, state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.immutable() : pos.below());
        }
    }

    /** {@code player} stepping into the doorway from the front: moving towards its back, as the client last said. */
    static boolean goingIn(ServerPlayer player, BlockState door) {
        Direction front = front(door);
        Vec3 moving = player.getKnownMovement();
        return moving.x * front.getStepX() + moving.z * front.getStepZ() < 0;
    }
}
