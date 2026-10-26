package dev.nookworks.drive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Tying a drive belt from one Drive Wheel to another: used on the first, it is started there; used on the second, it is
 * tied off taut between them, straight - a slack belt would turn nothing (the user, 2026-10-10). It is tied only
 * where it could run true:
 * <ul>
 * <li>the two wheels turn about the same line's direction (both on north-south axles, say);</li>
 * <li>and stand in the same plane across it, so the belt does not run askew off them;</li>
 * <li>no further apart than the belt's kind allows;</li>
 * <li>with nothing in the way between them, along the belt's two runs or its middle;</li>
 * <li>where the player tying it may build, all along it (claims asked);</li>
 * <li>and with room on both wheels, and the rope or chain it takes - two a block, there and back - to hand.</li>
 * </ul>
 * Each refusal says why. Using it on the first wheel again lets the started belt go; left half a minute, it lapses.
 * <p>
 * Once tied, nothing solid is put down in its way: a block set there is refused, as one is where something hangs on a
 * Thin Rope. What comes there otherwise - a piston's block, say - snaps the belt (DriveWheelBlockEntity's check).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Belts {
    private Belts() {}

    /** How far out from a wheel's middle the belt runs, in blocks: over its teeth, which sit a pixel and a half in it. */
    public static final double RADIUS = 5.5 / 16;

    /** How long a started belt waits to be tied off, in ticks: then it lapses, and the next wheel used starts one. */
    private static final int LAPSES_AFTER = 600;

    /** A belt started on a wheel, waiting to be tied off: where, in which dimension, and when. */
    private record Started(ResourceKey<Level> level, BlockPos pos, long tick) {}

    private static final Map<Player, Started> STARTED = new WeakHashMap<>();

    /**
     * The Drive Wheels in the loaded world, on the server: a block put down is looked at against their belts alone. It
     * walked every block entity within a belt's length of it instead - 3x3 chunks' worth, for every solid block put down
     * anywhere (about 10 us a block among 2,000 chests, 2026-10-14) - which only mass placement by another mod would
     * feel, but a world has few wheels (r15 late audit, N5). A wheel counts from when it is told it is loaded, before
     * its first tick: a block put down in the tick its chunk loads, before that, is not refused but snaps the belt, as a
     * piston's does. Weak, should a wheel go without word.
     */
    private static final Set<DriveWheelBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    /** A wheel is in the loaded world (set down, loaded with its chunk, or belted): its belts are kept clear. */
    static void loaded(DriveWheelBlockEntity wheel) {
        if (wheel.getLevel() != null && !wheel.getLevel().isClientSide()) {
            LOADED.add(wheel);
        }
    }

    /** A wheel has gone from the loaded world (broken, or unloaded with its chunk). */
    static void gone(DriveWheelBlockEntity wheel) {
        // the server's alone: a client's wheels go on the client's thread, and the set is not to be touched from two
        // (r15 fix check 6)
        if (wheel.getLevel() != null && !wheel.getLevel().isClientSide()) {
            LOADED.remove(wheel);
        }
    }

    /** {@code player} uses {@code stack}, a belt, on the wheel at {@code pos} (server side). */
    static void use(Level level, BlockPos pos, Player player, ItemStack stack) {
        BeltKind kind = BeltKind.of(stack);
        if (kind == null || !(level.getBlockEntity(pos) instanceof DriveWheelBlockEntity wheel)) {
            return;
        }
        Started started = STARTED.get(player);
        if (started != null && level.getGameTime() - started.tick() > LAPSES_AFTER) {
            started = null; // started long ago and forgotten: not tied off from there by surprise
        }
        if (started != null && started.level().equals(level.dimension()) && started.pos().equals(pos)) {
            STARTED.remove(player);
            player.sendOverlayMessage(Component.translatable("block.nookworks.drive_wheel.let_go"));
            return;
        }
        // a first wheel gone, or away (its chunk unloaded: not read from disk for a click far off, r16 N1), and the
        // started belt is forgotten: this wheel starts one
        if (started == null || !started.level().equals(level.dimension()) || !level.isLoaded(started.pos())
                || !(level.getBlockEntity(started.pos()) instanceof DriveWheelBlockEntity first)) {
            if (wheel.full()) {
                player.sendOverlayMessage(Component.translatable("block.nookworks.drive_wheel.full"));
                return;
            }
            STARTED.put(player, new Started(level.dimension(), pos.immutable(), level.getGameTime()));
            player.sendOverlayMessage(Component.translatable("block.nookworks.drive_wheel.started"));
            return;
        }
        STARTED.remove(player);
        Component refused = tie(level, first, wheel, kind, player, stack);
        player.sendOverlayMessage(refused != null ? refused : Component.translatable("block.nookworks.drive_wheel.tied"));
    }

    /** Ties a belt of {@code kind} from {@code a} to {@code b}, paid from the player's rope or chain; why not, if not. */
    public static @Nullable Component tie(Level level, DriveWheelBlockEntity a, DriveWheelBlockEntity b, BeltKind kind,
            @Nullable Player player, ItemStack stack) {
        BlockPos from = a.getBlockPos();
        BlockPos to = b.getBlockPos();
        String key = "block.nookworks.drive_wheel.";
        if (a.full() || b.full()) {
            return Component.translatable(key + "full");
        }
        if (a.has(to) || b.has(from)) {
            // either end: a belt is let go of by where its far end stands, so a second one to the same place (from a
            // wheel set down where a belted one was broken, before the owner looked) would go with the stale one
            return Component.translatable(key + "already");
        }
        Direction.Axis axis = a.axis();
        if (axis != b.axis()) {
            return Component.translatable(key + "not_parallel");
        }
        if (from.get(axis) != to.get(axis)) {
            return Component.translatable(key + "not_in_line");
        }
        double length = Vec3.atCenterOf(from).distanceTo(Vec3.atCenterOf(to));
        if (length > kind.maxLength) {
            return Component.translatable(key + "too_long", kind.maxLength);
        }
        if (blocker(level, from, to, axis) != null) {
            return Component.translatable(key + "in_the_way");
        }
        if (player != null && (!player.mayUseItemAt(from, Direction.UP, stack) || !player.mayUseItemAt(to, Direction.UP, stack))) {
            return Component.translatable(key + "not_here");
        }
        int count = BeltKind.itemsFor(length);
        boolean free = player != null && player.getAbilities().instabuild;
        // read before paying: a stack in hand paid down to nothing reads as air
        Item item = stack.getItem();
        if (player != null && !free && player.getInventory().countItem(item) < count) {
            return Component.translatable(key + "not_enough", count, stack.getHoverName());
        }
        if (player != null && level instanceof ServerLevel server && claimed(server, from, to, axis, player)) {
            return Component.translatable(key + "not_here");
        }
        List<ItemStack> madeOf = player != null && !free ? take(player, stack, item, count) : List.of();
        int paid = free ? 0 : count;
        UUID tiedBy = player == null ? null : DrawRights.ownerOf(player);
        // both ends know what it took: whichever lets it go (broken, taken off, snapped) gives it back, and the other end
        // only lets go of it
        a.add(new DriveWheelBlockEntity.Belt(to.immutable(), kind, item, paid, b.wheelId(), madeOf, tiedBy));
        b.add(new DriveWheelBlockEntity.Belt(from.immutable(), kind, item, paid, a.wheelId(), madeOf, tiedBy));
        Vec3 middle = Vec3.atCenterOf(from).lerp(Vec3.atCenterOf(to), 0.5);
        level.playSound(null, middle.x, middle.y, middle.z, kind.sound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return null;
    }

    /**
     * Whether a belt from {@code from} to {@code to} would pass through a block {@code player} may not build in: once
     * tied, nothing is set down in its way, by anyone - the claim's owner too - so it is tied only where the player tying
     * it could set a block down all along it, each block it runs through asked as a placing there, as a thing hung on a
     * rope is asked where it hangs (r16 S1: a stranger's belt tied
     * across the corner of a claim from wheels outside it kept the claim's owner from building on its line). A block the
     * walk reaches that is not loaded cannot be asked, and counts as refused.
     */
    private static boolean claimed(ServerLevel level, BlockPos from, BlockPos to, Direction.Axis axis, Player player) {
        Set<BlockPos> asked = new HashSet<>();
        return along(from, to, axis, (pos, inside) -> asked.add(pos)
                && (!level.isLoaded(pos) || !Permission.mayPlace(level, pos, Direction.UP, player))) != null;
    }

    /**
     * Takes {@code count} of {@code item}, from the stack in hand first and then from the rest of the inventory; gives
     * what of it carried more than the item (a name, another mod's data), as it was, for the belt to give back so.
     */
    private static List<ItemStack> take(Player player, ItemStack inHand, Item item, int count) {
        List<ItemStack> marked = new ArrayList<>();
        int fromHand = Math.min(count, inHand.getCount());
        keep(marked, inHand, fromHand);
        inHand.shrink(fromHand);
        int left = count - fromHand;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != inHand && stack.is(item)) {
                int n = Math.min(left, stack.getCount());
                keep(marked, stack, n);
                stack.shrink(n);
                left -= n;
            }
        }
        return marked;
    }

    /** {@code n} of {@code stack} into {@code marked}, if it is more than its item (with those like it already there). */
    private static void keep(List<ItemStack> marked, ItemStack stack, int n) {
        if (n <= 0 || stack.getComponentsPatch().isEmpty()) {
            return;
        }
        for (ItemStack kept : marked) {
            if (ItemStack.isSameItemSameComponents(kept, stack)) {
                kept.grow(n);
                return;
            }
        }
        marked.add(stack.copyWithCount(n));
    }

    /**
     * A block put down in a belt's way is taken back up, and its placer told why. (The event comes once the block is
     * there; cancelled, it is undone and the item given back.) A claim mod's question, asked by us, is no placing.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof Level level) || level.isClientSide() || Permission.asking()) {
            return;
        }
        List<BlockPos> placed = event instanceof BlockEvent.EntityMultiPlaceEvent several
                ? several.getReplacedBlockSnapshots().stream().map(snapshot -> snapshot.getPos()).toList()
                : List.of(event.getPos());
        List<Crossing> lapsed = new ArrayList<>();
        for (BlockPos pos : placed) {
            for (Crossing crossing : crossings(level, pos, level.getBlockState(pos))) {
                // whoever may build here sets it down; a belt tied before its tier lost the leave there (a claim made
                // over its line since) gives way to it - it kept the claim's owner from building in their own claim
                // (the 1.0 review's deferred note). One that has not lapsed keeps the block out, as before
                if (event.getEntity() instanceof Player && level instanceof ServerLevel server && lapsed(server, pos, crossing.belt())) {
                    lapsed.add(crossing);
                    continue;
                }
                event.setCanceled(true);
                if (event.getEntity() instanceof Player player) {
                    player.sendOverlayMessage(Component.translatable("block.nookworks.drive_wheel.belt_here"));
                }
                return;
            }
        }
        // not snapped here: the block may yet be taken back (another listener, a door's other half), and a belt snapped
        // stays snapped. Its wheel looks again next tick and snaps it then if the block stands in its way, as for a
        // block a piston pushes in
        lapsed.forEach(crossing -> crossing.wheel().recheck());
    }

    /**
     * Whether whoever tied {@code belt} may no longer build at {@code pos} - asked as them, as a break there (nothing is
     * changed); for a belt tied before belts kept who tied them, or by no one known, as a stranger: then it gives way
     * only in land a stranger may not build in, a claim, and stays in the way in open land as before.
     */
    private static boolean lapsed(ServerLevel level, BlockPos pos, DriveWheelBlockEntity.Belt belt) {
        return !Permission.mayBreak(level, pos, Permission.actor(level, belt.tiedBy()));
    }

    /** A belt that passes through a block, from the wheel that owns it. */
    private record Crossing(DriveWheelBlockEntity wheel, DriveWheelBlockEntity.Belt belt) {}

    /** Whether {@code state}, set at {@code pos}, would stand in the way of a belt tied there. */
    public static boolean inABeltsWay(Level level, BlockPos pos, BlockState state) {
        return !crossings(level, pos, state).isEmpty();
    }

    /** Every belt {@code state}, set at {@code pos}, would stand in the way of. */
    private static List<Crossing> crossings(Level level, BlockPos pos, BlockState state) {
        List<Crossing> crossings = new ArrayList<>();
        if (LOADED.isEmpty()) {
            return crossings;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return crossings;
        }
        AABB around = new AABB(pos).inflate(1);
        for (DriveWheelBlockEntity wheel : LOADED.toArray(DriveWheelBlockEntity[]::new)) {
            if (wheel.getLevel() != level || wheel.isRemoved()) {
                continue;
            }
            BlockPos from = wheel.getBlockPos();
            for (DriveWheelBlockEntity.Belt belt : wheel.belts()) {
                // each belt once, from its owning end; only one that passes by here is walked along
                if (wheel.owns(belt.other()) && new AABB(from).minmax(new AABB(belt.other())).intersects(around)
                        && along(from, belt.other(), wheel.axis(), (at, inside) -> at.equals(pos) && hits(shape, inside)) != null) {
                    crossings.add(new Crossing(wheel, belt));
                }
            }
        }
        return crossings;
    }

    /**
     * The first block in the way of a belt between the wheels at {@code from} and {@code to}, if any: anything solid
     * on its middle line or on either of its two runs, a wheel's radius either side of it in the wheels' plane.
     */
    public static @Nullable BlockPos blocker(Level level, BlockPos from, BlockPos to, Direction.Axis axis) {
        return along(from, to, axis, (pos, inside) -> level.isLoaded(pos) && hits(level.getBlockState(pos).getCollisionShape(level, pos), inside));
    }

    private static boolean hits(VoxelShape shape, Vec3 inside) {
        if (shape.isEmpty()) {
            return false;
        }
        for (AABB box : shape.toAabbs()) {
            if (box.inflate(1.0E-4).contains(inside)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walks a belt from {@code from} to {@code to} - its middle line and its two runs, a sixteenth of a block at a time
     * - and gives the first block, between the wheels' own, where {@code solid} says the point there (relative to that
     * block) is taken.
     */
    private static @Nullable BlockPos along(BlockPos from, BlockPos to, Direction.Axis axis, BiPredicate<BlockPos, Vec3> solid) {
        Vec3 a = Vec3.atCenterOf(from);
        Vec3 b = Vec3.atCenterOf(to);
        Vec3 along = b.subtract(a);
        double length = along.length();
        if (length < 1.0E-6) {
            return null;
        }
        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        Vec3 across = new Vec3(positive.getStepX(), positive.getStepY(), positive.getStepZ()).cross(along.scale(1 / length)).scale(RADIUS);
        int steps = (int) Math.ceil(length * 16);
        for (Vec3 offset : new Vec3[] {Vec3.ZERO, across, across.reverse()}) {
            for (int i = 1; i < steps; i++) {
                Vec3 point = a.add(along.scale(i / (double) steps)).add(offset);
                BlockPos pos = BlockPos.containing(point);
                if (!pos.equals(from) && !pos.equals(to) && solid.test(pos, point.subtract(pos.getX(), pos.getY(), pos.getZ()))) {
                    return pos;
                }
            }
        }
        return null;
    }
}
