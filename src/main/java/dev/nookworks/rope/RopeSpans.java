package dev.nookworks.rope;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.wood.SupportBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Thin Rope strung from post to post, as a lead is tied to one: a railing along a rope bridge, a line to hang things
 * from, a line across a room from a {@link HookBlock} on one wall to one on the other, or from a wall bracket's brace
 * along a house front, or from a lightning rod on the roof. The posts are fence posts, bars, rods, hooks and brackets
 * ({@link #POSTS}, a tag a pack can add to). Used on
 * a post, the rope is tied there and paid out to the player's hand ({@link RopeKnotEntity} holds it); used on a second
 * post within {@link #MAX_LENGTH}, it is tied off there, a Thin Rope for every block of it. It hangs slack between the
 * two, lowest in the middle, as a rope does. Thin Chain is strung the same way, a Thin Chain a block
 * ({@link SpanMaterial}); one post can have rope and chain tied to it, but two posts are joined by one line only.
 * <p>
 * This class is the shape of a span - where it is tied, how it droops - and the tying. Where it is tied on a post
 * depends on the post ({@link #tie(BlockGetter, BlockPos)}: a hook's bend, a bracket's brace); how much rope a span
 * takes is counted from the posts' places alone, so tying and untying always come to the same.
 */
public final class RopeSpans {
    /** How high on its post a rope is tied: a little under the top. */
    public static final double TIE_Y = 13.0 / 16;
    /** The longest span, post to post in a straight line. */
    public static final double MAX_LENGTH = 16;
    /** How far a span droops at its middle, per block of its length, and at the least. */
    private static final double SAG_PER_BLOCK = 0.055;
    private static final double MIN_SAG = 0.04;
    /** How near a look has to pass a rope to be on it. */
    private static final double PICK_RADIUS = 0.2;

    private RopeSpans() {}

    /**
     * What a rope can be tied to: #minecraft:fences, iron and copper bars, lightning rods, the end rod, the Hook and the
     * supports, and what a pack adds. Not walls: too thick a post for a cord (the user, 2026-10-05).
     */
    public static final TagKey<Block> POSTS =
            TagKey.create(Registries.BLOCK, Nookworks.id("rope_posts"));

    /** A post in the tag; a support only as a wall bracket, which has a brace to tie round (a post or a beam has none). */
    public static boolean isPost(BlockState state) {
        return state.is(POSTS) && (!(state.getBlock() instanceof SupportBlock) || SupportBlock.braced(state));
    }

    /** Where on a fence post at {@code post} a rope is tied: a little under its top. */
    public static Vec3 tie(BlockPos post) {
        return new Vec3(post.getX() + 0.5, post.getY() + TIE_Y, post.getZ() + 0.5);
    }

    /**
     * Where on the post at {@code post} a rope is tied, as the post is: in a hook's bend, round a bracket's brace, round a
     * rod, or as on a fence post (iron bars too, round the middle bar).
     */
    public static Vec3 tie(BlockGetter level, BlockPos post) {
        BlockState state = level.getBlockState(post);
        if (state.getBlock() instanceof HookBlock) {
            return HookBlock.eye(state, post);
        }
        if (state.getBlock() instanceof RodBlock) {
            return rod(state, post);
        }
        return SupportBlock.braced(state) ? SupportBlock.brace(state, post) : tie(post);
    }

    /** How far along a lightning rod from its foot a rope is tied, in pixels: just under its cap (12 to 16). */
    public static final double LIGHTNING_ROD_TIE = 10.5;
    /** And along an end rod standing up: near its tip, as high as on a fence post. */
    public static final double END_ROD_TIE = 13;
    /**
     * Along an end rod out of a wall or down from a ceiling: at its foot, against its base (0 to 1), where a rope stays
     * put - near the bare tip of a rod sticking out it looked about to slip off (the user, 2026-10-05). A lightning rod's
     * cap holds a rope at its tip whichever way it points.
     */
    public static final double END_ROD_FOOT_TIE = 2;

    /** Where on the rod {@code state} at {@code pos} a rope is tied: round it, along the way it points. */
    public static Vec3 rod(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(RodBlock.FACING);
        double tie = state.is(BlockTags.LIGHTNING_RODS) ? LIGHTNING_ROD_TIE : facing == Direction.UP ? END_ROD_TIE : END_ROD_FOOT_TIE;
        double along = tie / 16 - 0.5;
        return Vec3.atCenterOf(pos).add(facing.getStepX() * along, facing.getStepY() * along, facing.getStepZ() * along);
    }

    /** How far a rope from {@code a} to {@code b} hangs below the straight line at its middle. */
    public static double sag(Vec3 a, Vec3 b) {
        return MIN_SAG + SAG_PER_BLOCK * a.distanceTo(b);
    }

    /** The point {@code t} of the way along a slack rope from {@code a} to {@code b}. */
    public static Vec3 at(Vec3 a, Vec3 b, double t) {
        return a.lerp(b, t).subtract(0, 4 * sag(a, b) * t * (1 - t), 0);
    }

    /** The point {@code t} of the way along the span from post {@code from} to post {@code to}. */
    public static Vec3 at(BlockPos from, BlockPos to, double t) {
        return at(tie(from), tie(to), t);
    }

    /** The same, tied as the posts at either end are: a hook's bend. */
    public static Vec3 at(BlockGetter level, BlockPos from, BlockPos to, double t) {
        return at(tie(level, from), tie(level, to), t);
    }

    /** Thin Rope (or Thin Chain) a span between two posts takes: one a block, as a rope let down takes one a block. */
    public static int ropesFor(BlockPos from, BlockPos to) {
        return Math.max(1, Mth.ceil(tie(from).distanceTo(tie(to))));
    }

    /** What a span's rope and droop take up, for culling. */
    public static AABB bounds(BlockPos from, BlockPos to) {
        Vec3 a = tie(from);
        Vec3 b = tie(to);
        return new AABB(a, b).expandTowards(0, -sag(a, b), 0).inflate(0.1);
    }

    /** The same, tied as the posts at either end are. */
    public static AABB bounds(BlockGetter level, BlockPos from, BlockPos to) {
        Vec3 a = tie(level, from);
        Vec3 b = tie(level, to);
        return new AABB(a, b).expandTowards(0, -sag(a, b), 0).inflate(0.1);
    }

    /** Whether someone else is paying a line out from {@code post}: it is not taken out of their hands, and the player is told. */
    private static boolean heldByAnother(ServerLevel server, Player player, BlockPos post) {
        RopeKnotEntity knot = RopeKnotEntity.getOrCreate(server, post);
        if (knot.holder() instanceof Player other && other != player && other.isAlive()) {
            player.sendOverlayMessage(Component.translatable(knot.heldMaterial().message("message.nookworks.rope.held_by"),
                    other.getDisplayName()).withStyle(ChatFormatting.RED));
            return true;
        }
        return false;
    }

    /**
     * Thin Rope (or Thin Chain) used on the fence post {@code post}. Holding no line yet, the player ties it there and
     * holds the other end; holding one, from another post, ties it off here (or, clicking the post it came from, lets it
     * go). Holding a rope and using a chain, or the other way round, lets the one held go and starts the other here.
     */
    public static InteractionResult tie(Level level, Player player, BlockPos post, ItemStack line) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.mayBuild() || !level.mayInteract(player, post)) {
            return InteractionResult.PASS;
        }
        SpanMaterial material = SpanMaterial.of(line);
        if (material == null) {
            material = SpanMaterial.ROPE;
        }
        RopeKnotEntity held = RopeKnotEntity.heldBy(server, player);
        if (held != null && held.heldMaterial() != material) {
            if (heldByAnother(server, player, post)) {
                return InteractionResult.FAIL; // refused here: the line in hand is not let go for nothing
            }
            held.letGo(); // a chain used while paying out rope: the rope is let go, and the chain tied on here
            held = null;
        }
        if (held == null) {
            if (heldByAnother(server, player, post)) {
                return InteractionResult.FAIL;
            }
            RopeKnotEntity knot = RopeKnotEntity.getOrCreate(server, post);
            knot.holdBy(player, material);
            level.playSound(null, post, material.tied(), SoundSource.BLOCKS, 0.8F, 1.3F);
            return InteractionResult.SUCCESS;
        }
        BlockPos from = held.getPos();
        if (from.equals(post)) {
            held.letGo();
            level.playSound(null, post, material.untied(), SoundSource.BLOCKS, 0.8F, 1.3F);
            return InteractionResult.SUCCESS;
        }
        if (tie(from).distanceTo(tie(post)) > MAX_LENGTH) {
            player.sendOverlayMessage(Component.translatable(material.message("message.nookworks.rope.too_far"), (int) MAX_LENGTH)
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (blocked(server, from, post)) {
            player.sendOverlayMessage(Component.translatable(material.message("message.nookworks.rope.blocked")).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        RopeKnotEntity other = RopeKnotEntity.at(server, post);
        if (held.ties(post) || other != null && other.ties(from)) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.rope.already").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        int ropes = ropesFor(from, post);
        if (!player.hasInfiniteMaterials() && count(player, material.item()) < ropes) {
            player.sendOverlayMessage(Component.translatable(material.message("message.nookworks.rope.needs"), ropes)
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        RopeKnotEntity.getOrCreate(server, post).endsHere(material);
        held.addSpan(post, material);
        held.letGo();
        if (!player.hasInfiniteMaterials()) {
            take(player, line, material.item(), ropes);
        }
        level.playSound(null, post, material.tied(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_ATTACH, post);
        return InteractionResult.SUCCESS;
    }

    /**
     * Whether something stands in the way of a span from post {@code from} to post {@code to}: a block the rope would be
     * drawn through between them - a fence's middle post, a wall, leaves, whatever a player bumps into - or, between two
     * posts side by side, the rail of the fence (or the bars) joining them, which the rope would run inside. The user,
     * 2026-10-07, of a rope tied from one end of a fence to the other through its middle post: "物理的におかしい". Its own
     * two posts are the rope's ends; what is set down in its way once it is tied is let be, as a lead lets it be.
     */
    public static boolean blocked(BlockGetter level, BlockPos from, BlockPos to) {
        return inTheWay(level, from, to) != null;
    }

    /** What stands in the way of a span from {@code from} to {@code to}, as {@link #blocked} says: its place, or null. */
    public static @Nullable BlockPos inTheWay(BlockGetter level, BlockPos from, BlockPos to) {
        if (railed(level, from, to)) {
            return from;
        }
        if (railed(level, to, from)) {
            return to;
        }
        Vec3 a = tie(level, from);
        Vec3 b = tie(level, to);
        int steps = Math.max(2, Mth.ceil(a.distanceTo(b) * 16));
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++) {
            Vec3 point = at(a, b, i / (double) steps);
            cell.set(Mth.floor(point.x), Mth.floor(point.y), Mth.floor(point.z));
            if (cell.equals(from) || cell.equals(to)) {
                continue;
            }
            VoxelShape shape = level.getBlockState(cell).getCollisionShape(level, cell);
            if (!shape.isEmpty() && inside(shape, point.subtract(cell.getX(), cell.getY(), cell.getZ()))) {
                return cell.immutable();
            }
        }
        return null;
    }

    /** Whether {@code point}, in a block's own space, lies inside {@code shape} (not just on its face). */
    private static boolean inside(VoxelShape shape, Vec3 point) {
        double e = 1.0E-4;
        for (AABB box : shape.toAabbs()) {
            if (point.x > box.minX + e && point.x < box.maxX - e && point.y > box.minY + e && point.y < box.maxY - e
                    && point.z > box.minZ + e && point.z < box.maxZ - e) {
                return true;
            }
        }
        return false;
    }

    /** Whether the post at {@code post} is joined by a rail (a fence's, bars') to the post beside it at {@code other}. */
    private static boolean railed(BlockGetter level, BlockPos post, BlockPos other) {
        BlockState state = level.getBlockState(post);
        if (!(state.getBlock() instanceof CrossCollisionBlock)) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (post.relative(side).equals(other)) {
                return state.getValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side));
            }
        }
        return false;
    }

    private static int count(Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    /** Takes {@code count} Thin Rope (or Thin Chain), from the stack in hand first and then from the rest of the inventory. */
    private static void take(Player player, ItemStack inHand, Item item, int count) {
        int fromHand = Math.min(count, inHand.getCount());
        inHand.shrink(fromHand); // no further: the stack in hand must not go below nothing
        int left = count - fromHand;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != inHand && stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    /** A point on a span the player is looking at: the span, how far along it, the point, and how far off it is. */
    public record Pick(RopeKnotEntity knot, BlockPos to, double t, Vec3 point, double distance) {}

    /** The point of a span the player is looking at, within reach and nearer than any block, or null. */
    public static @Nullable Pick pick(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double reach = player.blockInteractionRange();
        HitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double limit = block.getType() == HitResult.Type.MISS ? reach : block.getLocation().distanceTo(eye);
        Pick best = null;
        for (RopeKnotEntity knot : level.getEntitiesOfClass(RopeKnotEntity.class, player.getBoundingBox().inflate(reach + MAX_LENGTH))) {
            for (BlockPos to : knot.spans()) {
                Vec3 a = tie(level, knot.getPos());
                Vec3 b = tie(level, to);
                int steps = Math.max(8, Mth.ceil(a.distanceTo(b) * 8));
                for (int i = 1; i < steps; i++) {
                    double t = i / (double) steps;
                    Vec3 point = at(a, b, t);
                    Vec3 rel = point.subtract(eye);
                    double along = rel.dot(look);
                    if (along <= 0 || along > limit) {
                        continue;
                    }
                    if (rel.subtract(look.scale(along)).length() < PICK_RADIUS && (best == null || along < best.distance())) {
                        best = new Pick(knot, to, t, point, along);
                    }
                }
            }
        }
        return best;
    }
}
