package dev.nookworks.feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Sides of a fence, wall or pane the Wrench joined or parted by hand (the user, 2026-11-07), kept per chunk by
 * position, as {@link PositionMarks} keeps its marks.
 * <p>
 * Vanilla works a fence's, wall's or pane's joins out again whenever a neighbour changes, so a side set by hand would
 * snap back at the next block set beside it. What is set here is laid over vanilla's answer instead: by
 * {@code CrossCollisionJoinMixin} at the return of a fence's or pane's {@code updateShape}, and by
 * {@code WallBlockJoinMixin} on the connections a wall is shaped from (a wall joined so is as high as vanilla would make
 * it, low or tall, and its post rises as vanilla's rule says). Only what differs from vanilla's own answer is kept: a side
 * set back to how vanilla has it is forgotten.
 * <p>
 * What is kept goes with the block: forgotten when it is broken or replaced by anything but a fence, wall or pane, and a
 * side is forgotten when what stood on that side goes (as a bench's parting is), so a fence set there afresh joins. A
 * piston carries it along. The data syncs to clients, so a client's own guess at a block set beside one (before the
 * server answers) shapes it as the server will; where it still guessed otherwise, the server sends the block again.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class JoinOverrides {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);

    private record Entry(long pos, byte sides) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(Entry::pos), Codec.BYTE.fieldOf("sides").forGetter(Entry::sides)).apply(i, Entry::new));
    }

    private static final MapCodec<Long2ByteOpenHashMap> CODEC = Entry.CODEC.listOf().fieldOf("sides").xmap(list -> {
        Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();
        list.forEach(e -> map.put(e.pos(), e.sides()));
        return map;
    }, map -> {
        List<Entry> list = new ArrayList<>();
        map.long2ByteEntrySet().forEach(e -> list.add(new Entry(e.getLongKey(), e.getByteValue())));
        return list;
    });

    private static final StreamCodec<RegistryFriendlyByteBuf, Long2ByteOpenHashMap> STREAM_CODEC = StreamCodec.of((buf, map) -> {
        buf.writeVarInt(map.size());
        map.long2ByteEntrySet().forEach(e -> {
            buf.writeLong(e.getLongKey());
            buf.writeByte(e.getByteValue());
        });
    }, buf -> {
        int size = buf.readVarInt();
        Long2ByteOpenHashMap map = new Long2ByteOpenHashMap(Math.min(size, 4096));
        for (int i = 0; i < size; i++) {
            map.put(buf.readLong(), buf.readByte());
        }
        return map;
    });

    /**
     * Sides set by hand in a chunk, by position (BlockPos#asLong): the low four bits say which sides are set (by
     * {@link Direction#get2DDataValue}), the high four whether each is joined. Treat as immutable: replace, never change.
     */
    public static final Supplier<AttachmentType<Long2ByteOpenHashMap>> SIDES = ATTACHMENT_TYPES.register("wrench_joins",
            () -> AttachmentType.builder(() -> new Long2ByteOpenHashMap())
                    .serialize(CODEC, map -> !map.isEmpty())
                    .sync(STREAM_CODEC)
                    .build());

    /** Vanilla's own answer is being asked for ({@link #natural}): nothing is laid over it. */
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private JoinOverrides() {}

    /** A fence, wall or pane (iron bars, glass panes) - vanilla's or a subclass of another mod's. */
    public static boolean joinable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof FenceBlock || block instanceof IronBarsBlock || block instanceof WallBlock;
    }

    /**
     * A fence gate: the Wrench sets whether it sits lowered as between walls (vanilla's in_wall) or at a fence's height,
     * whatever stands beside it (the user, 2026-11-07). Kept in the same store as a fence's sides, the first side's bits
     * standing for it: bit 0 set by hand, bit 4 lowered.
     */
    public static boolean gate(BlockState state) {
        return state.getBlock() instanceof FenceGateBlock;
    }

    /** Whether the Wrench sets anything of {@code state}: a fence, wall or pane's sides, a fence gate's height. */
    public static boolean adjustable(BlockState state) {
        return joinable(state) || gate(state);
    }

    /**
     * Whether the Wrench can set {@code side} of {@code state}: it has that side (another mod's subclass may not), and its
     * class shapes itself through vanilla's own updateShape, where the kept sides are laid over (one that does not would
     * show nothing changed while the message said it had).
     */
    public static boolean settable(BlockState state, Direction side) {
        Block block = state.getBlock();
        boolean has = block instanceof WallBlock ? state.hasProperty(WallBlock.PROPERTY_BY_DIRECTION.get(side))
                : state.hasProperty(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side));
        return has && shapesAsVanilla(block);
    }

    /** Whether {@code block}'s class takes vanilla's updateShape (FenceBlock's, IronBarsBlock's, WallBlock's) as it is. */
    private static boolean shapesAsVanilla(Block block) {
        return OWN_SHAPES.computeIfAbsent(block.getClass(), JoinOverrides::shapesAsVanilla);
    }

    private static final Map<Class<?>, Boolean> OWN_SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    /** The nearest class from {@code type} up that declares updateShape (it is protected): vanilla's, or a mod's own. */
    private static boolean shapesAsVanilla(Class<?> type) {
        for (Class<?> c = type; c != null && c != Block.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod("updateShape", BlockState.class, net.minecraft.world.level.LevelReader.class,
                        net.minecraft.world.level.ScheduledTickAccess.class, BlockPos.class, Direction.class, BlockPos.class,
                        BlockState.class, net.minecraft.util.RandomSource.class);
                return c == FenceBlock.class || c == IronBarsBlock.class || c == WallBlock.class || c == FenceGateBlock.class;
            } catch (NoSuchMethodException e) {
                // not here: its parent's
            }
        }
        return false;
    }

    /** Whether {@code state} is joined on {@code side}. */
    public static boolean joined(BlockState state, Direction side) {
        if (state.getBlock() instanceof WallBlock) {
            var property = WallBlock.PROPERTY_BY_DIRECTION.get(side);
            return state.hasProperty(property) && state.getValue(property) != WallSide.NONE;
        }
        var property = CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side);
        return state.hasProperty(property) && state.getValue(property);
    }

    // ---- reading ----

    /**
     * The sides set by hand at {@code pos} (see {@link #SIDES}), or 0. Never loads a chunk. Nothing for a square that holds
     * no fence, wall, pane or gate now: one changed to something else by a way that tells no neighbours (a strict /fill,
     * another mod's block mover) leaves its bits behind, and they must not come back on a fence set there later (r1
     * review); {@link #onPlaced} forgets them too.
     */
    public static int bits(BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level) || !level.isLoaded(pos) || BYPASS.get()) {
            return 0;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        if (!chunk.hasData(SIDES)) {
            return 0;
        }
        int bits = chunk.getData(SIDES).get(pos.asLong()) & 0xFF;
        return bits != 0 && adjustable(chunk.getBlockState(pos)) ? bits : 0;
    }

    /** Whether anything is stored for {@code pos}, whatever stands there now. */
    private static boolean stored(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.hasData(SIDES) && chunk.getData(SIDES).containsKey(pos.asLong());
    }

    /**
     * A block set by a player or a mob starts fresh: whatever an earlier one here was set to is forgotten. Not for a
     * placing a claim mod refuses (heard last, and a cancelled one not at all), nor for our own asking whether one may
     * be placed ({@link Permission#asking}): a fence's sides set by hand were lost to a question (r22 CXC-007).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!Permission.asking() && event.getLevel() instanceof Level level && !level.isClientSide()) {
            if (stored(level, event.getPos())) {
                put(level, event.getPos(), 0);
            }
        }
    }

    /** {@code vanilla}'s answer for {@code side}, or the one set by hand in {@code bits}. */
    public static boolean side(int bits, Direction side, boolean vanilla) {
        int bit = 1 << side.get2DDataValue();
        return (bits & bit) == 0 ? vanilla : (bits & (bit << 4)) != 0;
    }

    /** Whether {@code side} at {@code pos} is set by hand: joined, apart, or null for vanilla's answer. */
    public static @Nullable Boolean forced(BlockGetter level, BlockPos pos, Direction side) {
        int bits = bits(level, pos);
        int bit = 1 << side.get2DDataValue();
        return (bits & bit) == 0 ? null : (bits & (bit << 4)) != 0;
    }

    // ---- laid over vanilla (the mixins) ----

    /**
     * A fence's or pane's {@code computed} shape at {@code pos} with the sides set by hand laid over it, or a fence gate's
     * with the height set by hand.
     */
    public static BlockState keepCross(BlockState computed, BlockGetter level, BlockPos pos) {
        int bits = bits(level, pos);
        if (bits == 0) {
            return computed;
        }
        if (gate(computed)) {
            BlockState kept = computed.setValue(FenceGateBlock.IN_WALL, side(bits, GATE, computed.getValue(FenceGateBlock.IN_WALL)));
            if (kept != computed) {
                resend(level, pos);
            }
            return kept;
        }
        if (!(computed.getBlock() instanceof CrossCollisionBlock)) {
            return computed;
        }
        BlockState kept = computed;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            var property = CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side);
            if (kept.hasProperty(property)) {
                kept = kept.setValue(property, side(bits, side, kept.getValue(property)));
            }
        }
        if (kept != computed) {
            resend(level, pos);
        }
        return kept;
    }

    /**
     * What vanilla worked out differed from what is kept: the server sends the block again, for a client that guessed
     * vanilla's way (setting a block beside it) and was not told otherwise, the server's block not having changed.
     */
    public static void resend(BlockGetter level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            server.getChunkSource().blockChanged(pos);
        }
    }

    // ---- the Wrench ----

    /**
     * The side of a fence, wall or pane a click at {@code hit} on {@code face} picks: an arm hit picks the side it
     * reaches to, as the Wrench picks a pipe's side; the post (or a pane's middle) the face hit, or - its top or bottom -
     * the side facing the player.
     */
    public static Direction sideFromHit(BlockPos pos, Vec3 hit, Direction face, Direction towardPlayer) {
        double x = hit.x - pos.getX() - 0.5;
        double z = hit.z - pos.getZ() - 0.5;
        double most = Math.max(Math.abs(x), Math.abs(z));
        if (most < 0.26) { // within the post (a wall's is the widest, half 0.25 across)
            return face.getAxis().isHorizontal() ? face : towardPlayer;
        }
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        }
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Joins {@code side} at {@code pos} if it is apart, or parts it if joined - this block's side only: the one beside it
     * keeps its own, so a wall and a fence side by side can each be set (the user, 2026-11-07: 「どちらかをつなげると、
     * もう片方も出てくる」). Returns whether it is now joined.
     */
    public static boolean toggle(Level level, BlockPos pos, BlockState state, Direction side) {
        boolean joined = !joined(state, side);
        keep(level, pos, state, side, joined);
        reshape(level, pos);
        return joined;
    }

    /** The side whose bits stand for a fence gate's height. */
    private static final Direction GATE = Direction.SOUTH;

    /** Lowers a fence gate as between walls, or raises it to a fence's height. Returns whether it is now lowered. */
    public static boolean toggleGate(Level level, BlockPos pos, BlockState state) {
        boolean lowered = !state.getValue(FenceGateBlock.IN_WALL);
        // kept even where vanilla would have it so: set by hand, it stays whatever comes beside it later (DECISIONS)
        store(level, pos, GATE, lowered);
        BlockState now = level.getBlockState(pos);
        BlockState shaped = now.setValue(FenceGateBlock.IN_WALL, lowered);
        if (shaped != now) {
            level.setBlock(pos, shaped, Block.UPDATE_ALL);
        }
        return lowered;
    }

    /** Keeps {@code side} at {@code pos} {@code joined}, or forgets it where vanilla has it so anyway. */
    private static void keep(Level level, BlockPos pos, BlockState state, Direction side, boolean joined) {
        boolean vanilla = joined(natural(level, pos, state), side);
        store(level, pos, side, vanilla == joined ? null : joined);
    }

    /** {@code state} at {@code pos} as vanilla shapes it among its neighbours, with nothing set by hand. */
    public static BlockState natural(Level level, BlockPos pos, BlockState state) {
        boolean was = BYPASS.get();
        BYPASS.set(true);
        try {
            return Block.updateFromNeighbourShapes(state, level, pos);
        } finally {
            BYPASS.set(was);
        }
    }

    /** Shapes the block at {@code pos} again among its neighbours, the sides set by hand laid over. */
    private static void reshape(Level level, BlockPos pos) {
        BlockState now = level.getBlockState(pos);
        if (!joinable(now)) {
            return;
        }
        BlockState shaped = Block.updateFromNeighbourShapes(now, level, pos);
        if (shaped != now && shaped.is(now.getBlock())) {
            level.setBlock(pos, shaped, Block.UPDATE_ALL);
        }
    }

    /** Sets {@code side} at {@code pos} joined, apart, or (null) vanilla's. */
    private static void store(Level level, BlockPos pos, Direction side, @Nullable Boolean joined) {
        int bit = 1 << side.get2DDataValue();
        int bits = bits(level, pos);
        int next = joined == null ? bits & ~(bit | bit << 4) : joined ? bits | bit | bit << 4 : (bits | bit) & ~(bit << 4);
        put(level, pos, next);
    }

    private static void put(Level level, BlockPos pos, int bits) {
        LevelChunk chunk = level.getChunkAt(pos);
        Long2ByteOpenHashMap current = chunk.getData(SIDES);
        int was = current.get(pos.asLong()) & 0xFF;
        if (was == bits) {
            return;
        }
        Long2ByteOpenHashMap next = new Long2ByteOpenHashMap(current);
        if (bits == 0) {
            next.remove(pos.asLong());
        } else {
            next.put(pos.asLong(), (byte) bits);
        }
        chunk.setData(SIDES, next); // marks the chunk dirty and syncs it
    }

    // ---- forgetting ----

    /**
     * Every change that tells the blocks around comes through here, before they are shaped again: a square that is no
     * longer a fence, wall or pane forgets its sides, and a square left empty (air, fire, a liquid) has the sides facing
     * it forgotten, as a bench's parting is once the other one goes - the neighbour is then shaped as vanilla has it.
     */
    @SubscribeEvent
    static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState now = event.getState();
        if (!adjustable(now) && stored(level, pos)) {
            put(level, pos, 0);
        }
        if (now.isAir() || now.is(BlockTags.FIRE) || now.liquid()) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(side);
                // a gate keeps its height whatever goes beside it: it was set for itself
                if (level.isLoaded(next) && !gate(level.getBlockState(next)) && forced(level, next, side.getOpposite()) != null) {
                    store(level, next, side.getOpposite(), null);
                    reshape(level, next);
                }
            }
        }
    }

    // ---- following blocks through pistons (as PositionMarks does) ----

    private record Carried(BlockPos from, Block block, BlockPos to, int bits) {}

    private record Move(List<Carried> carried, List<BlockPos> cleared) {}

    private record Key(ResourceKey<Level> dimension, BlockPos piston) {}

    private static final Map<Key, Move> PENDING = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void beforePiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        List<Carried> carried = new ArrayList<>();
        List<BlockPos> cleared = new ArrayList<>();
        Direction push = resolver.getPushDirection();
        for (BlockPos pos : resolver.getToPush()) {
            int bits = bits(level, pos);
            if (bits != 0) {
                carried.add(new Carried(pos.immutable(), level.getBlockState(pos).getBlock(), pos.relative(push), bits));
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (bits(level, pos) != 0) {
                cleared.add(pos.immutable());
            }
        }
        Key key = new Key(level.dimension(), event.getPos().immutable());
        if (carried.isEmpty() && cleared.isEmpty()) {
            PENDING.remove(key);
        } else {
            PENDING.put(key, new Move(carried, cleared));
        }
    }

    @SubscribeEvent
    static void afterPiston(PistonEvent.Post event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        Move move = PENDING.remove(new Key(level.dimension(), event.getPos()));
        if (move == null) {
            return;
        }
        for (BlockPos pos : move.cleared()) {
            put(level, pos, 0);
        }
        List<Carried> moved = move.carried().stream()
                .filter(c -> level.getBlockEntity(c.to()) instanceof PistonMovingBlockEntity moving
                        && moving.getMovedState().is(c.block()))
                .toList();
        for (Carried c : moved) {
            put(level, c.from(), 0);
        }
        for (Carried c : moved) {
            put(level, c.to(), c.bits()); // laid over when the block lands and is shaped among its new neighbours
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
