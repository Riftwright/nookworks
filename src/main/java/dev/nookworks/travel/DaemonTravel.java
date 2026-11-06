package dev.nookworks.travel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.feature.Permission;
import dev.nookworks.ifworld.IfGates;
import dev.nookworks.ifworld.IfMirrorBlock;
import dev.nookworks.ifworld.IfTravel;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.registry.ModTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Going through a Dock-a-Daemon Door. Through the home door a player steps out at their chosen dimension's door - set
 * down there on the first trip, near where they last were in it (or where a portal would have taken them, if they never
 * were seen there), in a frame of stone bricks - and through any other door, at the home door. One arrives in front of
 * the door, facing away from it, the door open behind. A door goes nowhere unless set into a wall ({@link
 * DaemonDoorBlock#framed}).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class DaemonTravel {
    /** Ticks after going through before a door takes the same player again: one does not bounce straight back. */
    static final int COOLDOWN = 40;
    /** What a return door's frame, and the little floor one is set on where there is no ground, are made of. */
    static final Block MASONRY = Blocks.STONE_BRICKS;
    /** How far round where a door first opens onto a dimension a place to stand it is looked for. */
    static final int SEARCH = 16;
    private static final Map<ServerPlayer, Integer> LAST = new WeakHashMap<>();
    /** Players stepping through a door this tick: the trip is made once the tick's block checks are done. */
    private static final Set<ServerPlayer> STEPPING = Collections.newSetFromMap(new WeakHashMap<>());
    /** The trip being made (on the server thread): who goes, and the gate their client is shown once it is sure. */
    private static @Nullable ServerPlayer sureFor;
    private static @Nullable ResourceKey<Level> sureGate;

    /** On the client: the gate its player last went through a door by, and when (in milliseconds), for the loading screen. */
    private static volatile @Nullable ResourceKey<Level> trippingBy;
    private static volatile long trippingSince;

    private DaemonTravel() {}

    /** On the client: its player is going through a door showing {@code gate}'s gate. */
    public static void tripping(ResourceKey<Level> gate) {
        trippingBy = gate;
        trippingSince = System.currentTimeMillis();
    }

    /** On the client: the gate its player is going through, while the far side loads (for half a minute at most). */
    public static Optional<ResourceKey<Level>> trippingBy() {
        ResourceKey<Level> gate = trippingBy;
        return gate != null && System.currentTimeMillis() - trippingSince < 30_000 ? Optional.of(gate) : Optional.empty();
    }

    /** On the client: the far side has loaded. */
    public static void arrivedThere() {
        trippingBy = null;
    }

    /**
     * {@code player} walked into the open door at {@code door}, as a block's inside is checked: the trip waits for that
     * check to finish. Made there and then, the rest of the player's blocks would be looked at in the new dimension, at
     * the old place - loading it, and taking a fire or lava there for theirs. The door's place is copied for that wait:
     * the check hands each block a position it then moves on to the next block, the door's upper half among them.
     */
    public static void walkIn(ServerPlayer player, BlockPos door) {
        MinecraftServer server = player.level().getServer();
        BlockPos at = door.immutable();
        if (STEPPING.add(player)) {
            server.schedule(new TickTask(server.getTickCount(), () -> {
                STEPPING.remove(player);
                if (!player.isRemoved()) {
                    through(player, at);
                }
            }));
        }
    }

    /** {@code player} has just arrived somewhere (through a mirror, say): no door takes them on for a moment. */
    public static void arrived(ServerPlayer player) {
        LAST.put(player, player.level().getServer().getTickCount());
    }

    /** {@code player} walking into the open door whose lower half is at {@code door}. */
    public static void through(ServerPlayer player, BlockPos door) {
        MinecraftServer server = player.level().getServer();
        int now = server.getTickCount();
        Integer last = LAST.get(player);
        if (last != null && now - last < COOLDOWN) {
            return;
        }
        LAST.put(player, now); // also when it goes nowhere: the message is said once, not every tick in the doorway
        ServerLevel from = player.level();
        if (!DaemonDoors.standsAt(from, door) || !DaemonDoorBlock.framed(from, door, from.getBlockState(door).getValue(DoorBlock.FACING))) {
            return; // a door like any other
        }
        DaemonDoors doors = DaemonDoors.get(server);
        if (doors.doorIn(from.dimension()) == null) {
            doors.record(from.dimension(), door); // a door set down by a command, or from before it was kept
        }
        TeleportTransition transition = doors.isHome(from.dimension(), door) ? out(player, from, door, doors) : home(player, doors);
        if (transition != null) {
            go(player, from, door, transition);
        }
    }

    /** Through the home door: to the dimension the player chose at the panel. */
    private static @Nullable TeleportTransition out(ServerPlayer player, ServerLevel from, BlockPos door, DaemonDoors doors) {
        TravelLog log = TravelLog.of(player);
        Optional<ResourceKey<Level>> chosen = log.destination();
        if (chosen.isEmpty() || chosen.get().equals(from.dimension())) {
            return null; // set off at the panel: a door like any other
        }
        ResourceKey<Level> to = chosen.get();
        ServerLevel target = from.getServer().getLevel(to);
        if (target == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.gone"));
            return null;
        }
        if (to.equals(IfWorld.KEY) && IfWorld.get(from.getServer()).resetPending()) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.if.reset_pending"));
            return null;
        }
        if (!log.hasBeenTo(to)) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.not_been", DaemonDestinations.name(to)));
            return null;
        }
        BlockPos there = existing(doors, target);
        if (there == null) {
            // the first door there is the trip's to set down; one taken up since is set down again from the traveller's pocket
            ItemStack carried = doors.hadDoor(to) && !player.hasInfiniteMaterials() ? carried(player, ModTravel.DAEMON_DOOR_ITEM.get()) : null;
            if (carried != null && carried.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.taken_up", DaemonDestinations.name(to)));
                return null;
            }
            BlockPos base = log.lastPos().get(to);
            if (base == null && to.equals(Level.END)) {
                there = endLanding(target, player); // in a dimension never seen: always the first door there
            } else {
                if (base == null) {
                    base = scaled(from, target, door);
                }
                // in the End never on or by its platform, made afresh each time someone comes through a portal (r15: last
                // seen standing on it, a traveller had their door set down there, and the next arrival broke it)
                there = setDown(target, base, !log.lastPos().containsKey(to), DOOR, to.equals(Level.END) ? endPlatform() : null, player);
            }
            if (there == null) {
                // refused by claims, or simply no room: said which, as a claim mod refusing is not "no room"
                player.sendOverlayMessage(Component.translatable(lastRefused() ? "message.nookworks.daemon_door.claimed"
                        : "message.nookworks.daemon_door.no_room"));
                return null;
            }
            if (carried != null) {
                carried.shrink(1);
            }
            doors.record(to, there);
            DaemonDoorBlockEntity.refreshAll(from.getServer());
        } else if (!frameIfBare(target, there, player)) {
            // out of a wall and not to be walled in (something in the way, or land the traveller may not build on):
            // arrived there, they would find an ordinary door and no way back
            player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.far_unframed", DaemonDestinations.name(to)));
            return null;
        }
        TeleportTransition arrival = arriveAt(target, there);
        if (arrival == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.blocked"));
        }
        return arrival;
    }

    /**
     * The first stack of {@code item} in {@code player}'s inventory, or an empty stack: what a door or mirror taken up is
     * set down again from.
     */
    public static ItemStack carried(ServerPlayer player, Item item) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return inventory.getItem(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The End's first door, for a player who has not stood in the End (or not since dying there): where an end portal sets
     * a player down, by its obsidian platform - beside it, not on it, as the platform is made afresh, everything on it
     * cleared away, each time someone comes through a portal. A ledge of obsidian, five by five, joined level to the
     * platform's east edge; a rim a block high round its three open sides; the door at its far end, its doorway towards
     * the platform. One comes out facing the platform and the island beyond, with the void behind a door and a rim. Of
     * stone bricks, as the door's frame. Returns where the door's lower half is.
     * <p>
     * Built only over what may be built over - air, as that side of the platform is unless someone built there - and,
     * with {@code actor}, only where claim mods and spawn protection let them build: what stands there already is not
     * cleared away (vanilla's platform clears only itself, so the void beside it is a safe place to build). With
     * something in the way, or refused, the door is set down near the platform as anywhere else, never on it (it is made
     * afresh each time someone comes through a portal); null if not even that can be.
     */
    public static @Nullable BlockPos endLanding(ServerLevel end) {
        return endLanding(end, null);
    }

    /**
     * Where a return door may not stand in the End: the platform an end portal makes afresh (its floor and the room over
     * it), and a block round it, so that no door, nor its frame beside and over it, is in what the platform clears.
     */
    public static BoundingBox endPlatform() {
        BlockPos spawn = ServerLevel.END_SPAWN_POINT;
        int y = spawn.getY() - 1;
        return new BoundingBox(spawn.getX() - 3, y - 1, spawn.getZ() - 3, spawn.getX() + 3, y + 4, spawn.getZ() + 3);
    }

    /** As {@link #endLanding(ServerLevel)}, asking claim mods and spawn protection as {@code actor}. */
    public static @Nullable BlockPos endLanding(ServerLevel end, @Nullable Player actor) {
        BlockPos spawn = ServerLevel.END_SPAWN_POINT;
        EndPlatformFeature.createEndPlatform(end, spawn.below(), true); // its floor a block under where one stands
        int y = spawn.getY() - 1; // where one stands on the platform
        int west = spawn.getX() + 3; // the ledge: the five columns east of the platform's
        int east = west + 4;
        List<BlockPos> masonry = new ArrayList<>();
        List<BlockPos> cleared = new ArrayList<>();
        BlockPos door = new BlockPos(east - 1, y, spawn.getZ());
        // the frame of the door the ledge was built for: bricks left there by an earlier one (taken up since) are its
        // frame again, and anything there is left to the door's own checks (fits, asked as placing)
        List<BlockPos> doorFrame = DaemonDoorBlock.frame(door, Direction.WEST);
        boolean clear = true;
        for (int x = west; x <= east && clear; x++) {
            for (int z = spawn.getZ() - 2; z <= spawn.getZ() + 2 && clear; z++) {
                BlockPos floor = new BlockPos(x, y - 1, z);
                if (buildable(end, floor)) {
                    masonry.add(floor); // what is solid there already is a floor as it is
                }
                boolean rim = x == east || Math.abs(z - spawn.getZ()) == 2;
                for (int up = 0; up <= 2; up++) {
                    BlockPos cell = new BlockPos(x, y + up, z);
                    if (doorFrame.contains(cell)) {
                        continue;
                    }
                    if (rim && up == 0) {
                        if (buildable(end, cell)) {
                            masonry.add(cell); // a rim: anything solid there serves
                        }
                    } else if (!end.getBlockState(cell).isAir()) {
                        if (!buildable(end, cell)) {
                            clear = false; // the ledge's room is someone's build: not taken away
                            break;
                        }
                        cleared.add(cell);
                    }
                }
            }
        }
        List<BlockPos> changes = new ArrayList<>(masonry);
        changes.addAll(cleared);
        changes.addAll(DOOR.changes(end, door, Direction.WEST));
        if (!clear || !DOOR.fits(end, door, Direction.WEST) || !mayChange(end, changes, actor)) {
            return setDown(end, spawn.east(5), false, DOOR, endPlatform(), actor);
        }
        for (BlockPos cell : masonry) {
            end.setBlockAndUpdate(cell, MASONRY.defaultBlockState());
        }
        for (BlockPos cell : cleared) {
            end.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
        }
        place(end, door, Direction.WEST);
        return door;
    }

    /** A block that may be built over: air, grass, snow, a fluid - not something built, nor bedrock and the like. */
    private static boolean buildable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() && state.getDestroySpeed(level, pos) >= 0 && level.getBlockEntity(pos) == null;
    }

    /**
     * Whether {@code actor} - the traveller whose trip sets a door or mirror down - may change every one of
     * {@code cells}, as claim mods and spawn protection answer for placing a block there; with no actor, yes. The trip
     * asks for the cells it changes only, and only for the spots it would take, nearest first.
     */
    private static boolean mayChange(ServerLevel level, List<BlockPos> cells, @Nullable Player actor) {
        if (actor == null) {
            return true;
        }
        for (BlockPos cell : cells) {
            if (!Permission.mayPlace(level, cell, Direction.UP, actor)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Through a return door: to the home door, or with none standing, to the player's respawn point - never into the If,
     * as the mirror's own fallback (r21: a bed in an If waiting for its reset took them back into it, to be wiped).
     */
    private static @Nullable TeleportTransition home(ServerPlayer player, DaemonDoors doors) {
        GlobalPos home = doors.home();
        ServerLevel target = home == null ? null : player.level().getServer().getLevel(home.dimension());
        if (home != null && target == null) {
            doors.forget(home.dimension(), home.pos()); // its dimension is gone (the mod that made it taken out)
            DaemonDoorBlockEntity.refreshAll(player.level().getServer()); // the return doors' views with it
        }
        if (target != null && target.dimension().equals(IfWorld.KEY) && IfWorld.get(player.level().getServer()).resetPending()) {
            // the home door stands in an If waiting for its reset: no way in, as the mirror and the pearl are refused
            player.sendOverlayMessage(Component.translatable("message.nookworks.if.reset_pending"));
            return IfTravel.respawn(player);
        }
        BlockPos there = target == null ? null : existing(doors, target);
        if (target == null || there == null || !doors.isHome(target.dimension(), there)) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.no_home"));
            return IfTravel.respawn(player);
        }
        TeleportTransition arrival = arriveAt(target, there);
        if (arrival == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.blocked_home"));
            return IfTravel.respawn(player);
        }
        return arrival;
    }

    /** The door recorded in {@code level}'s dimension, if it still stands there (its chunk loaded to see); else forgotten. */
    private static @Nullable BlockPos existing(DaemonDoors doors, ServerLevel level) {
        BlockPos there = doors.doorIn(level.dimension());
        if (there == null) {
            return null;
        }
        level.getChunk(there); // loads it, as the trip is about to
        if (DaemonDoors.standsAt(level, there)) {
            return there;
        }
        doors.forget(level.dimension(), there);
        return null;
    }

    /** Where a portal would come out: the door's place scaled as the two dimensions' coordinates are. */
    private static BlockPos scaled(ServerLevel from, ServerLevel to, BlockPos door) {
        double scale = DimensionType.getTeleportationScale(from.dimensionType(), to.dimensionType());
        return BlockPos.containing(door.getX() * scale, door.getY(), door.getZ() * scale);
    }

    /**
     * In front of the door at {@code door}, facing away from it, the door opened behind - or behind it, or the nearest room
     * near it when something has been built round it since; null with none.
     */
    private static @Nullable TeleportTransition arriveAt(ServerLevel level, BlockPos door) {
        BlockState state = level.getBlockState(door);
        Direction facing = state.getValue(DoorBlock.FACING);
        Direction out = facing.getOpposite(); // the leaf's side: where whoever set it down stood
        BlockPos stand = standingRoom(level, door, out);
        if (stand == null) {
            return null;
        }
        if (stand.equals(door.relative(facing))) {
            out = facing;
        }
        if (!state.getValue(DoorBlock.OPEN) && state.getBlock() instanceof DoorBlock block) {
            block.setOpen(null, level, state, door, true);
        }
        return new TeleportTransition(level, Vec3.atBottomCenterOf(stand), Vec3.ZERO, out.toYRot(), 0.0F, TeleportTransition.DO_NOTHING);
    }

    /**
     * Where to come out at the two-high door or mirror at {@code at} whose front is towards {@code out}: in front, if
     * something safe to land on is no more than {@link #SAFE_DROP} blocks under it (it is where its owner set the door,
     * a step off a pillar or onto a slab included); else behind it, the same; else the nearest standing room within two
     * blocks - something has been built round it, or taken away from in front of it (a ghast's blast through a Nether
     * door's floor, over the lava sea), since; else in front or behind with room to stand at all; null with none.
     */
    public static @Nullable BlockPos standingRoom(ServerLevel level, BlockPos at, Direction out) {
        BlockPos front = at.relative(out);
        BlockPos back = at.relative(out.getOpposite());
        if (safeLanding(level, front)) {
            return front;
        }
        if (safeLanding(level, back)) {
            return back;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cell : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
            double distance = cell.distSqr(front);
            if (distance < bestDistance && standable(level, cell)) {
                best = cell.immutable();
                bestDistance = distance;
            }
        }
        if (best != null) {
            return best;
        }
        return open(level, front) ? front : open(level, back) ? back : null;
    }

    /**
     * Through, unless another mod refuses the trip (a dimension lock): then it says so, and the player is as they were.
     * The client is shown the gate while the far side loads only once the trip is sure ({@link #onSure}), and the
     * teleport itself takes a rider off their mount: before, a refused trip left them dismounted and the gate armed for
     * their next change of dimension, half a minute on.
     */
    private static void go(ServerPlayer player, ServerLevel from, BlockPos door, TeleportTransition transition) {
        ResourceKey<Level> toward = transition.newLevel().dimension();
        sureFor = player;
        sureGate = !toward.equals(from.dimension()) && player.connection.hasChannel(NookworksNetwork.DaemonTrip.TYPE)
                ? DaemonDestinations.gate(from.dimension(), toward) : null;
        ServerPlayer moved;
        try {
            moved = IfGates.passing(() -> player.teleport(transition));
        } finally {
            sureFor = null;
            sureGate = null;
        }
        if (moved == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.daemon_door.refused"));
            return;
        }
        from.playSound(null, door, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.5F, 1.4F);
        ServerLevel to = transition.newLevel();
        BlockPos at = BlockPos.containing(transition.position());
        to.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.5F, 1.4F);
        to.sendParticles(ParticleTypes.REVERSE_PORTAL, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.02);
        LAST.put(player, to.getServer().getTickCount());
    }

    /**
     * A door's trip that no mod has refused, as last asked before the player is moved: its gate is sent to their client
     * now, ahead of the far side, for the loading screen.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onSure(EntityTravelToDimensionEvent event) {
        ResourceKey<Level> gate = sureGate;
        if (gate != null && event.getEntity() == sureFor) {
            sureGate = null;
            PacketDistributor.sendToPlayer(sureFor, new NookworksNetwork.DaemonTrip(gate));
        }
    }

    // ---- setting down a return door ----

    /** What a trip sets down where it first arrives: a two-high block at a spot, with standing room on its {@code out} side. */
    @FunctionalInterface
    public interface Placer {
        void place(ServerLevel level, BlockPos lower, Direction out);

        /** Whether it may go at {@code lower}, its front towards {@code out}, besides the standing room there and in front. */
        default boolean fits(ServerLevel level, BlockPos lower, Direction out) {
            return true;
        }

        /** The blocks setting it down at {@code lower} changes: by default its own two. */
        default List<BlockPos> changes(ServerLevel level, BlockPos lower, Direction out) {
            return List.of(lower.immutable(), lower.above().immutable());
        }
    }

    /**
     * A return door, in its frame: only where each block of the frame is a full block already or may be built over
     * (air, grass, snow), so that setting it down takes nothing away from anyone's build.
     */
    public static final Placer DOOR = new Placer() {
        @Override
        public void place(ServerLevel level, BlockPos lower, Direction out) {
            DaemonTravel.place(level, lower, out);
        }

        @Override
        public List<BlockPos> changes(ServerLevel level, BlockPos lower, Direction out) {
            List<BlockPos> cells = new ArrayList<>(List.of(lower.immutable(), lower.above().immutable()));
            cells.addAll(frameChanges(level, lower, out.getOpposite()));
            return cells;
        }

        @Override
        public boolean fits(ServerLevel level, BlockPos lower, Direction out) {
            for (BlockPos cell : DaemonDoorBlock.frame(lower, out.getOpposite())) {
                BlockState state = level.getBlockState(cell);
                if (!level.getWorldBorder().isWithinBounds(cell) || state.hasBlockEntity() && !state.isCollisionShapeFullBlock(level, cell)
                        || !state.isCollisionShapeFullBlock(level, cell) && !(state.canBeReplaced() && state.getDestroySpeed(level, cell) >= 0)) {
                    return false;
                }
            }
            return true;
        }
    };

    /** A return door near {@code base}: see {@link #setDown(ServerLevel, BlockPos, boolean, Placer, BoundingBox, Player)}. */
    public static @Nullable BlockPos setDown(ServerLevel level, BlockPos base, boolean underSky) {
        return setDown(level, base, underSky, DOOR, null, null);
    }

    /** As {@link #setDown(ServerLevel, BlockPos, boolean, Placer, BoundingBox, Player)}, nowhere kept clear but round doors and mirrors, asking nobody. */
    public static @Nullable BlockPos setDown(ServerLevel level, BlockPos base, boolean underSky, Placer placer) {
        return setDown(level, base, underSky, placer, null, null);
    }

    /** As {@link #setDown(ServerLevel, BlockPos, boolean, Placer, BoundingBox, Player)}, for {@code actor}'s trip. */
    public static @Nullable BlockPos setDown(ServerLevel level, BlockPos base, boolean underSky, Placer placer, @Nullable Player actor) {
        return setDown(level, base, underSky, placer, null, actor);
    }

    /** How many of the spots a search finds, nearest first, claim mods are asked about before it gives up. */
    private static final int ASKED = 256;

    private record Spot(BlockPos at, double distance) {}

    /**
     * Whether the last {@link #setDown} that came to nothing was refused somewhere by a claim or spawn protection - said to
     * the traveller rather than "no room", even if what stopped the last try was something else (a chest), as the land
     * round there is what they are to know of.
     */
    private static boolean refused;

    /** See {@link #refused}: for the trip that called {@link #setDown}, right after it, to say why. */
    public static boolean lastRefused() {
        return refused;
    }

    /**
     * Sets a two-high thing down near {@code base} in {@code level} (a return door, the If's mirror), as a portal is set
     * down: on the nearest dry spot with standing room in front of it, within {@link #SEARCH} blocks across and over the
     * dimension's whole height; with none, on a little stone-brick floor of its own, above any sea, its room walled off
     * from what flows. Returns where its lower half went, or null when not even that could be made (outside the world border).
     * <p>
     * With {@code underSky} - where the player has not been in that dimension, so {@code base} is only where it lies
     * from here - a spot under the open sky wins over any cave, and with none it goes on the surface: a first step into
     * a world is not into the dark under a sea. Where they have been, nearest wins, underground or not: that is where
     * they were. (A dimension with no sky, or with a roof, has no such preference.)
     * <p>
     * Never within two blocks of another door or mirror (one's doorway on the other's arrival would send whoever came
     * through it straight on), nor in {@code avoid}. The floor of its own has a low rim round it: one arrives with one's
     * back to the door, and the floor stands over the Nether's lava sea.
     * <p>
     * With {@code actor} - the traveller - claim mods and spawn protection are asked, as for {@code actor} placing a
     * block, about every block the setting down changes: the nearest spots found are tried in turn until one is let
     * be, and the floor of its own is made only if it may be. It never clears away a block holding anything (a chest),
     * claimed or not; with nowhere it may go, null.
     */
    public static @Nullable BlockPos setDown(ServerLevel level, BlockPos base, boolean underSky, Placer placer, @Nullable BoundingBox avoid,
            @Nullable Player actor) {
        boolean sky = underSky && level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
        WorldBorder border = level.getWorldBorder();
        int top = Math.min(level.getMaxY(), level.getMinY() + level.getLogicalHeight() - 1) - 2; // the room's top under a roof
        List<Spot> spots = new ArrayList<>(); // every spot found, to be taken nearest first
        // the area loaded (made, if need be) first: an unloaded chunk's heights read as the world's floor
        for (int cx = (base.getX() - SEARCH) >> 4; cx <= (base.getX() + SEARCH) >> 4; cx++) {
            for (int cz = (base.getZ() - SEARCH) >> 4; cz <= (base.getZ() + SEARCH) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(base, SEARCH, Direction.EAST, Direction.SOUTH)) {
            if (!border.isWithinBounds(column)) {
                continue;
            }
            int x = column.getX();
            int z = column.getZ();
            int height = Math.min(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z); // under a tree is under the sky
            int lowest = sky ? Math.max(level.getMinY() + 1, ground) : level.getMinY() + 1; // under the sky: not below it
            for (int y = height; y >= lowest; y--) {
                BlockPos at = new BlockPos(x, y, z);
                if (standable(level, at) && (avoid == null || !avoid.isInside(at))) {
                    spots.add(new Spot(at, at.distSqr(base)));
                }
            }
        }
        // nearest first (stable: of spots as near, the first found); the costlier checks only now, stopping at the first
        // spot taken, and claim mods asked about at most ASKED of them
        spots.sort(java.util.Comparator.comparingDouble(Spot::distance));
        int asked = 0;
        refused = false;
        for (Spot spot : spots) {
            BlockPos at = spot.at();
            if (nearGate(level, at)) {
                continue;
            }
            Direction out = null;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos front = at.relative(side);
                if (border.isWithinBounds(front) && standable(level, front) && (avoid == null || !avoid.isInside(front))
                        && placer.fits(level, at, side)) {
                    out = side;
                    break;
                }
            }
            if (out == null) {
                continue;
            }
            if (mayChange(level, placer.changes(level, at, out), actor)) {
                placer.place(level, at, out);
                return at;
            }
            refused = true;
            if (++asked >= ASKED) {
                break;
            }
        }
        BlockPos start = border.clampToBounds(base.getX(), base.getY(), base.getZ());
        if (!border.isWithinBounds(start) || !border.isWithinBounds(start.north())) {
            return null;
        }
        level.getChunk(start); // its heights are read: an unloaded chunk's read as the world's floor
        if (sky) {
            // on the surface, a sea's included: a little raft on the water
            start = start.atY(Mth.clamp(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX(), start.getZ()),
                    level.getMinY() + 1, top - 1));
        } else {
            // as a portal's own floor: at least at 70, so above a sea (the Nether's lava), and under the roof
            int low = Math.max(level.getMinY() + 1, 70);
            int high = top - 8;
            start = start.atY(high < low ? Mth.clamp(base.getY(), level.getMinY() + 1, top - 1) : Mth.clamp(base.getY(), low, high));
        }
        Direction out = Direction.NORTH;
        List<BlockPos> room = List.of(start, start.above(), start.relative(out), start.relative(out).above());
        List<BlockPos> cleared = new ArrayList<>();
        List<BlockPos> masonry = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(start.offset(-1, -1, -2), start.offset(1, 2, 1))) {
            BlockState state = level.getBlockState(cell);
            if (state.getDestroySpeed(level, cell) < 0) {
                continue; // bedrock and the like stay
            }
            if (room.contains(cell)) {
                if (level.getBlockEntity(cell) != null) {
                    return null; // a chest or the like where the room would be: not cleared away
                }
                if (!state.isAir()) {
                    cleared.add(cell.immutable());
                }
            } else if (cell.getY() == start.getY() - 1 && !state.isFaceSturdy(level, cell, Direction.UP) || !state.getFluidState().isEmpty()) {
                if (level.getBlockEntity(cell) == null) {
                    masonry.add(cell.immutable());
                }
            }
        }
        if (!sky) {
            // a rim one block high round the floor: walked to its edge one stops there, and can still step over on purpose
            for (BlockPos cell : BlockPos.betweenClosed(start.offset(-2, 0, -3), start.offset(2, 0, 2))) {
                boolean edge = Math.abs(cell.getX() - start.getX()) == 2 || cell.getZ() - start.getZ() == -3 || cell.getZ() - start.getZ() == 2;
                if (edge && border.isWithinBounds(cell) && level.getBlockState(cell).canBeReplaced() && level.getBlockEntity(cell) == null
                        && !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
                    masonry.add(cell.immutable());
                }
            }
        }
        if (placer == DOOR && DaemonDoorBlock.frame(start, out.getOpposite()).stream().anyMatch(cell -> {
            BlockState state = level.getBlockState(cell);
            return state.hasBlockEntity() && !state.isCollisionShapeFullBlock(level, cell) || state.getDestroySpeed(level, cell) < 0
                    && !state.isCollisionShapeFullBlock(level, cell);
        })) {
            // its frame would have to take the place of a chest or the like beside it, or of what cannot be broken: not
            // set down there. (Kelp, coral and the like round a raft at sea are framed over, as before.)
            return null;
        }
        List<BlockPos> changes = new ArrayList<>(cleared);
        changes.addAll(masonry);
        changes.addAll(placer.changes(level, start, out));
        if (!mayChange(level, changes, actor)) {
            refused = true;
            return null;
        }
        refused = false;
        for (BlockPos cell : cleared) {
            level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos cell : masonry) {
            level.setBlockAndUpdate(cell, MASONRY.defaultBlockState());
        }
        placer.place(level, start, out);
        return start.immutable();
    }

    /**
     * Each block round the doorway of the door at {@code lower} that is not a full block made one of stone bricks - but
     * never one holding anything (a chest): {@link #DOOR}'s fits keeps a door from where one is.
     */
    private static void frame(ServerLevel level, BlockPos lower, Direction facing) {
        for (BlockPos cell : DaemonDoorBlock.frame(lower, facing)) {
            BlockState state = level.getBlockState(cell);
            if (!state.isCollisionShapeFullBlock(level, cell) && state.getDestroySpeed(level, cell) >= 0 && !state.hasBlockEntity()) {
                level.setBlockAndUpdate(cell, MASONRY.defaultBlockState());
            }
        }
    }

    /**
     * A return door stood out of any wall - set down before doors had to be in one, or moved since - framed as a new one
     * is, where nothing but air and the like is in the way and {@code actor} may build. Whether it stands in a frame
     * after: if not, whoever came through it would find no way back, so no one is sent.
     */
    private static boolean frameIfBare(ServerLevel level, BlockPos door, @Nullable Player actor) {
        Direction facing = level.getBlockState(door).getValue(DoorBlock.FACING);
        if (DaemonDoorBlock.framed(level, door, facing)) {
            return true;
        }
        if (DOOR.fits(level, door, facing.getOpposite()) && mayChange(level, frameChanges(level, door, facing), actor)) {
            frame(level, door, facing);
        }
        return DaemonDoorBlock.framed(level, door, facing);
    }

    /** The blocks round the doorway of the door at {@code lower} that {@link #frame} makes stone bricks. */
    private static List<BlockPos> frameChanges(ServerLevel level, BlockPos lower, Direction facing) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos cell : DaemonDoorBlock.frame(lower, facing)) {
            BlockState state = level.getBlockState(cell);
            if (!state.isCollisionShapeFullBlock(level, cell) && state.getDestroySpeed(level, cell) >= 0 && !state.hasBlockEntity()) {
                out.add(cell.immutable());
            }
        }
        return out;
    }

    /** How far one may come out above what one lands on: a step off a pillar, not a fall. */
    private static final int SAFE_DROP = 3;

    /** Room to stand at {@code pos}, and under it within {@link #SAFE_DROP} blocks something to land on that is not lava. */
    private static boolean safeLanding(ServerLevel level, BlockPos pos) {
        if (!open(level, pos)) {
            return false;
        }
        for (int down = 1; down <= SAFE_DROP; down++) {
            BlockPos below = pos.below(down);
            BlockState state = level.getBlockState(below);
            if (!state.isAir()) {
                return !state.getFluidState().is(FluidTags.LAVA) && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.FIRE)
                        && !state.is(Blocks.SOUL_FIRE);
            }
        }
        return false;
    }

    /** A door or mirror within two blocks of {@code pos}, at its height or the one above. */
    private static boolean nearGate(ServerLevel level, BlockPos pos) {
        for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
            Block block = level.getBlockState(cell).getBlock();
            if (block instanceof DaemonDoorBlock || block instanceof IfMirrorBlock) {
                return true;
            }
        }
        return false;
    }

    /** Room to stand at {@code pos}: two blocks of open air (no lava, no water, no fire) over a floor that holds. */
    public static boolean standable(ServerLevel level, BlockPos pos) {
        if (!open(level, pos)) {
            return false;
        }
        BlockState floor = level.getBlockState(pos.below());
        return floor.isFaceSturdy(level, pos.below(), Direction.UP) && !floor.getFluidState().is(FluidTags.LAVA)
                && !floor.is(Blocks.MAGMA_BLOCK);
    }

    /** Two blocks of open air at {@code pos}: nothing to stand inside, no lava, no water, no fire. */
    private static boolean open(ServerLevel level, BlockPos pos) {
        for (BlockPos room : new BlockPos[] {pos, pos.above()}) {
            BlockState state = level.getBlockState(room);
            if (!state.getCollisionShape(level, room).isEmpty() || !state.getFluidState().isEmpty() || !state.canBeReplaced()
                    || state.is(BlockTags.FIRE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A return door at {@code pos}, its front towards {@code out} - where one stands coming through - open, and set into
     * a frame: each block round its doorway that is not a full block already made one of stone bricks.
     */
    private static void place(ServerLevel level, BlockPos pos, Direction out) {
        frame(level, pos, out.getOpposite());
        BlockState lower = ModTravel.DAEMON_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, out.getOpposite())
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, true).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        setTwoHigh(level, pos, lower, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * A two-high block (a return door, the If's mirror) set down whole at {@code pos} and above it, over whatever may be
     * built over there. The cell above is cleared first without a word to its neighbours, as vanilla clears it for a door
     * or a tall plant set down by hand: set over half by half, a plant two blocks high standing there (tall grass, a
     * sunflower) lost its foot to the new lower half, its top went - and took that lower half with it, whose own top was
     * not set yet: half a door or half a mirror, and the trip read the facing of the air where the rest should have been.
     */
    public static void setTwoHigh(ServerLevel level, BlockPos pos, BlockState lower, BlockState upper) {
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(pos, lower, Block.UPDATE_ALL);
        level.setBlock(pos.above(), upper, Block.UPDATE_ALL);
    }
}
