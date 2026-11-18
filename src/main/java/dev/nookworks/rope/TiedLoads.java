package dev.nookworks.rope;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.block.Clicks;
import dev.nookworks.block.RopeBlock;
import dev.nookworks.carry.CarriedBlock;
import dev.nookworks.carry.CarriedLook;
import dev.nookworks.carry.Carrying;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModRope;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.LivingEntity;

/**
 * Tying blocks with rope and hoisting them (the user, 2026-11-10; DECISIONS "Tying blocks with rope and hoisting them").
 * Sneak and use Rope on a block to tie it up ({@link Carrying#take}): a {@link TiedLoadEntity} stands in its place, one
 * rope used. Sneak with an empty hand, or use shears (a dispenser's too), to untie it ({@link Carrying#put}). A Rope
 * whose bottom end hangs right over a load holds it, and the rope's own clicks move it: an empty hand pulls the rope up
 * a length and the load with it, more rope lets both down one. Thin Rope does not: a cord for bottles and lanterns, it
 * holds no chest.
 * <p>
 * Public for add-ons (docs/compat.md, "Carried blocks and tied loads"): {@link #tie}, {@link #untie}, {@link #hoist},
 * {@link #release}, {@link #spawn}.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class TiedLoads {
    private TiedLoads() {}

    /** The sides a rope is wound round: the four and the top (under it, the floor it stands on is no matter). */
    private static final Direction[] WOUND_ROUND = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

    /**
     * Ties up the block at {@code pos} as {@code actor} (a player, a machine's fake player, or null): taken out of the world
     * and a Tied Load set in its place, at rest. Null when it may not be - the actor, a player, told why over the hotbar:
     * as {@link Carrying#check} says, or a full block against a side or the top that the rope would go round
     * ({@link #walledIn}). Uses no rope: the caller does.
     */
    public static @Nullable TiedLoadEntity tie(ServerLevel level, BlockPos pos, @Nullable Player actor) {
        Carrying.Check check = Carrying.check(level, pos, actor);
        if (check.isOk() && walledIn(level, check.set())) {
            tell(actor, Component.translatable("message.nookworks.tied_load.walled_in"));
            return null;
        }
        Optional<CarriedBlock> taken = Carrying.take(level, pos, actor);
        if (taken.isEmpty()) {
            tell(actor, inRopeWords(Carrying.check(level, pos, actor))); // asked again only to say why
            return null;
        }
        CarriedBlock carried = taken.get();
        TiedLoadEntity load = made(level, Vec3.atBottomCenterOf(pos), carried);
        if (!level.addFreshEntity(load)) {
            // its joining the world refused (an add-on cancelling it, r22 CXC-004): the block set back as it was, or -
            // that refused too - broken open where it stood; never gone with what it held
            if (!Carrying.put(level, pos, Rotation.NONE, carried, actor).placed()) {
                breakOpen(level, pos, carried);
            }
            return null;
        }
        level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F); // PORT: no lead sounds in 1.21.1
        level.gameEvent(actor, GameEvent.ENTITY_PLACE, pos);
        return load;
    }

    /**
     * Whether a full block stands against one of {@code set}'s four sides or its top (not one of the set's own: a double
     * chest's halves): with the ground packed round it, a rope cannot be wound round it (the user, 2026-11-12: 「「四方と
     * 上面にフルブロックが存在しない」という状態のときだけ縛れたほうがいい」). Safe on the client, for the click's swing.
     */
    public static boolean walledIn(Level level, Set<BlockPos> set) {
        for (BlockPos cell : set) {
            for (Direction side : WOUND_ROUND) {
                BlockPos next = cell.relative(side);
                if (!set.contains(next) && level.getBlockState(next).isCollisionShapeFullBlock(level, next)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Why a block cannot be tied up, as the rope says it ("This can't be tied up", not Carrying's "carried"): the five
     * reasons at taking in the Tied Load's own words, any other - an add-on's veto - as given.
     */
    private static Component inRopeWords(Carrying.Check check) {
        Carrying.Why why = check.why();
        if (why == null) {
            Component given = check.reason();
            return given != null ? given : Component.translatable("message.nookworks.tied_load.not_carriable");
        }
        return switch (why) {
            case NOT_WHOLE, NOT_CARRIABLE, TOO_MUCH, TOO_MANY, NOT_YOURS -> Component.translatable("message.nookworks.tied_load." + why.id());
            case NOT_HERE, BLOCKED -> why.text();
        };
    }

    /**
     * Unties {@code load} as {@code actor} (or null): its block put back where it stands, unturned, and the load gone.
     * Blocked, or refused by a claim, it stays tied and nothing changes. Gives no rope back: the caller does.
     */
    public static Carrying.Put untie(TiedLoadEntity load, @Nullable Player actor) {
        CarriedBlock carried = load.carried();
        if (carried == null || load.isRemoved() || !(load.level() instanceof ServerLevel level)) {
            return Carrying.Put.BLOCKED;
        }
        BlockPos at = load.base();
        Carrying.Put put = Carrying.put(level, at, Rotation.NONE, carried, actor, load);
        if (put.placed()) {
            load.takeCarried();
            load.discard();
            level.playSound(null, at, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F); // PORT: no lead sounds in 1.21.1
            level.gameEvent(actor, GameEvent.BLOCK_PLACE, at);
        }
        return put;
    }

    /** Whether {@link #untie} would put it back now. */
    public static boolean canUntie(TiedLoadEntity load, @Nullable Player actor) {
        CarriedBlock carried = load.carried();
        return carried != null && load.level() instanceof ServerLevel level
                && Carrying.canPut(level, load.base(), Rotation.NONE, carried, actor, load);
    }

    /**
     * Moves {@code load} up ({@code blocks} &gt; 0) or down whole blocks, if every cell on the way is free (nothing solid,
     * nobody standing there): true if it moved. It does not touch ropes, nor ask claims - the caller's pulley does what
     * its rope needs. A load moved off its rope falls as soon as nothing holds it.
     */
    public static boolean hoist(TiedLoadEntity load, int blocks) {
        if (blocks == 0 || !canMove(load, blocks, null)) {
            return blocks == 0;
        }
        load.moveRows(blocks);
        return true;
    }

    /**
     * Takes the value out of {@code load} and removes it, for a holder of the add-on's to carry on (a gondola). The block
     * does not go through the world.
     */
    public static CarriedBlock release(TiedLoadEntity load) {
        CarriedBlock carried = load.takeCarried();
        if (carried == null) {
            throw new IllegalStateException("This load holds nothing (released already?)");
        }
        load.discard();
        return carried;
    }

    /**
     * A load killed: its block set back where it stands and broken there, as by an explosion - drops, a container's
     * spill, a Storage Barrel left whole when too full - or, with no room or leave to set it back, broken open where it
     * stands as though it were there ({@link #breakOpen(ServerLevel, BlockPos, CarriedBlock)}). Never just gone with
     * what it held.
     */
    static void breakOpen(ServerLevel level, TiedLoadEntity load, @Nullable CarriedBlock carried) {
        if (carried == null) {
            return;
        }
        BlockPos at = load.base();
        if (Carrying.put(level, at, Rotation.NONE, carried, null, load).placed()) {
            for (CarriedBlock.Part part : carried.parts()) {
                level.destroyBlock(at.offset(part.offset()), true);
            }
            return;
        }
        breakOpen(level, at, carried);
    }

    /**
     * {@code carried} broken open at {@code at} with no block set there: each part's drops worked out as an explosion's
     * would be, with its block entity made from what it held, and then that block entity's own removal side effects -
     * a chest's spill, a Storage Barrel's (or the whole barrel, too full to break), a furnace's experience - as when the
     * block is broken. Not a container's contents besides, which a Crate's or a shulker box's drop already holds: a
     * Storage Barrel's were lost so, a Crate's doubled (r22 CXC-001, CXM-001).
     */
    private static void breakOpen(ServerLevel level, BlockPos at, CarriedBlock carried) {
        for (CarriedBlock.Part part : carried.parts()) {
            BlockPos cell = at.offset(part.offset());
            net.minecraft.world.level.block.entity.BlockEntity entity = null;
            CompoundTag data = part.data();
            if (data != null) {
                entity = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(cell, part.state(), data, level.registryAccess());
                if (entity != null) {
                    entity.setLevel(level);
                }
            }
            net.minecraft.world.level.block.Block.dropResources(part.state(), level, cell, entity);
            if (entity != null) {
                dev.nookworks.port.core.BlockRemoval.preRemoveSideEffects(entity, level, cell, part.state());
            }
        }
    }

    /**
     * {@code player}'s hit on {@code load}: a hit's worth of digging at its block with what the hand holds
     * ({@link TiedLoadEntity#HIT_TICKS} ticks), cracks shown as on a block, so the harder the block, the more hits - one in
     * creative. Broken, its block is set back where it stands and broken there by the player, as they would break the
     * block: drops by their tool, the tool worn, a chest's spill, the rope dropped. Not where they may not break it,
     * nor with what may not break a block (a sword in creative). True when the hit landed.
     */
    static boolean hit(ServerLevel level, TiedLoadEntity load, ServerPlayer player) {
        CarriedBlock carried = load.carried();
        if (carried == null || !player.mayBuild()) {
            return false;
        }
        BlockPos at = load.base();
        BlockState state = carried.state();
        if (!Permission.mayBreak(level, at, player)) {
            return false; // a claim's own words say so, as for its blocks
        }
        if (!player.getMainHandItem().getItem().canAttackBlock(state, level, at, player)) { // (1.21.1's name for it)
            return false;
        }
        float step = player.hasInfiniteMaterials() ? 1.0F : state.getDestroyProgress(player, level, at) * TiedLoadEntity.HIT_TICKS;
        if (step <= 0) {
            return false;
        }
        level.playSound(null, load.getX(), load.getY() + 0.5, load.getZ(), state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.5F, 0.75F);
        if (load.hit(step, level.getGameTime())) {
            breakBy(level, load, player);
        }
        return true;
    }

    /**
     * Broken by {@code player}: set back and broken as they would break the block. With no room or leave to set it back it
     * stays tied; a block that will not break by hand when set back (a Storage Barrel too full) stays there, untied.
     */
    private static void breakBy(ServerLevel level, TiedLoadEntity load, ServerPlayer player) {
        CarriedBlock carried = load.takeCarried();
        if (carried == null) {
            return;
        }
        BlockPos at = load.base();
        if (!Carrying.put(level, at, Rotation.NONE, carried, player, load).placed()) {
            load.setCarried(carried);
            return;
        }
        load.discard();
        for (CarriedBlock.Part part : carried.parts()) {
            player.gameMode.destroyBlock(at.offset(part.offset()));
        }
        if (!player.hasInfiniteMaterials()) {
            Block.popResource(level, at, new ItemStack(ModItems.ROPE.get()));
        }
    }

    /**
     * Whether {@code explosion} is strong enough where {@code load} stands to break its block, as it would break the block
     * standing there: its strength at that distance (vanilla's ray loses about 0.75 a block) over what the block's blast
     * resistance takes off it. TNT breaks a tied chest or machine close by, not one of obsidian; an explosion that breaks
     * no blocks (mobGriefing off, a wind charge) breaks none.
     */
    static boolean blownApartBy(TiedLoadEntity load, Explosion explosion) {
        CarriedBlock carried = load.carried();
        if (carried == null || !dev.nookworks.port.core.Explosions.shouldAffectBlocklikeEntities(explosion.getBlockInteraction())) { // one that breaks blocks
            return false;
        }
        float resistance = 0;
        for (CarriedBlock.Part part : carried.parts()) {
            resistance = Math.max(resistance, part.state().getBlock().getExplosionResistance());
        }
        double distance = Math.sqrt(load.getBoundingBox().getCenter().distanceToSqr(explosion.center()));
        return explosion.radius() - 0.75 * distance > (resistance + 0.3F) * 0.3F;
    }

    /** Broken by an explosion that reached it ({@link #blownApartBy}): broken open as a killed load is, the rope dropped. */
    static void blownUp(ServerLevel level, TiedLoadEntity load) {
        BlockPos at = load.base();
        breakOpen(level, load, load.takeCarried());
        load.discard();
        Block.popResource(level, at, new ItemStack(ModItems.ROPE.get()));
    }

    /**
     * Sets {@code carried} down tied at {@code at} (its first part's bottom middle), as a new Tied Load. Should the world
     * refuse it (an add-on cancelling its joining), what it holds is broken open there instead, as a killed load's is,
     * and the load returned is in no world and holds nothing ({@link TiedLoadEntity#carried()} null): nothing is
     * lost either way.
     */
    public static TiedLoadEntity spawn(ServerLevel level, Vec3 at, CarriedBlock carried) {
        TiedLoadEntity load = made(level, at, carried);
        if (!level.addFreshEntity(load)) {
            breakOpen(level, BlockPos.containing(at), carried);
            load.takeCarried();
        }
        return load;
    }

    private static TiedLoadEntity made(ServerLevel level, Vec3 at, CarriedBlock carried) {
        TiedLoadEntity load = new TiedLoadEntity(ModRope.TIED_LOAD.get(), level);
        load.setCarried(carried);
        load.setPos(at);
        return load;
    }

    // ---- clicks ----

    /**
     * Sneak and use Rope on a block's top or side: tie it up. Not its underside where a rope can hang (that hangs the
     * rope, as it always did), nor a rope (that lets it down).
     */
    @SubscribeEvent
    static void clickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.ROPE.get()) || !player.isSecondaryUseActive() || !player.mayBuild()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RopeBlock) {
            return;
        }
        if (event.getFace() == Direction.DOWN && ModBlocks.ROPE.get().tie(level, pos.below()) >= 0
                && level.getBlockState(pos.below()).canBeReplaced()) {
            return; // clicked from below: the rope is hung under it
        }
        InteractionResult result;
        if (level instanceof ServerLevel server) {
            if (tie(server, pos, player) != null) {
                stack.consume(1, player); // none in creative
                result = InteractionResult.SUCCESS;
            } else {
                result = Clicks.REFUSED;
            }
        } else {
            result = Carrying.couldCarry(state) && !walledIn(level, Set.of(pos)) ? InteractionResult.SUCCESS : Clicks.REFUSED;
        }
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    /** A click on a load: untie it (sneaking with an empty hand, or shears), or be told it is tied. */
    static InteractionResult click(TiedLoadEntity load, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean shears = stack.is(Tags.Items.TOOLS_SHEAR);
        boolean bare = hand == InteractionHand.MAIN_HAND && stack.isEmpty() && player.isSecondaryUseActive();
        if (shears || bare) {
            if (!player.mayBuild()) {
                return InteractionResult.PASS;
            }
            if (load.level() instanceof ServerLevel level) {
                Carrying.Put put = untie(load, player);
                if (!put.placed()) {
                    tell(player, put.reason());
                    return Clicks.REFUSED;
                }
                ItemStack rope = new ItemStack(ModItems.ROPE.get());
                if (shears) {
                    level.playSound(null, load.getX(), load.getY() + 0.5, load.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F); // PORT: no shears snip in 1.21.1
                    load.spawnAtLocation(rope, 0.5F); // cut: the rope falls off
                    stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                } else if (!player.hasInfiniteMaterials() && !player.getInventory().add(rope)) {
                    player.drop(rope, false);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND || stack.isEmpty() && player.getOffhandItem().is(Tags.Items.TOOLS_SHEAR)) {
            return InteractionResult.PASS; // shears in the other hand get their turn
        }
        if (!load.level().isClientSide()) {
            tell(player, Component.translatable("message.nookworks.tied_load.tied"));
        }
        return Clicks.REFUSED;
    }

    private static void tell(@Nullable Player player, @Nullable Component reason) {
        if (player != null && reason != null && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    // ---- hoisting by the rope's own clicks (RopeBlock) ----

    /** The load standing in {@code cell}, if any. */
    public static @Nullable TiedLoadEntity at(Level level, BlockPos cell) {
        List<TiedLoadEntity> loads = level.getEntitiesOfClass(TiedLoadEntity.class, new AABB(cell).deflate(0.25),
                load -> !load.isRemoved() && load.cells().contains(cell));
        return loads.isEmpty() ? null : loads.get(0);
    }

    /** The load a Rope whose bottom end is {@code ropeBottom} holds: one right under it, standing on whole blocks. */
    public static @Nullable TiedLoadEntity hangingFrom(Level level, BlockPos ropeBottom) {
        TiedLoadEntity load = at(level, ropeBottom.below());
        return load != null && load.aligned() ? load : null;
    }

    /**
     * Whether the rope ending at {@code bottom} may be pulled up with {@code load} on it: not its last length (the load
     * would be against what the rope hangs from, held by nothing), and room above the load's other parts. Says why not.
     */
    public static boolean mayPullUp(Level level, Player player, BlockPos bottom, TiedLoadEntity load, RopeBlock rope) {
        if (!level.getBlockState(bottom.above()).is(rope)) {
            if (!level.isClientSide()) {
                tell(player, Component.translatable("message.nookworks.tied_load.at_top"));
            }
            return false;
        }
        if (!canMove(load, 1, bottom)) {
            if (!level.isClientSide()) {
                tell(player, Component.translatable("message.nookworks.tied_load.no_room"));
            }
            return false;
        }
        return true;
    }

    /** Whether {@code player} may have {@code load} moved {@code rows} rows: asked where it goes, as a thing hung is. */
    public static boolean mayMoveInto(ServerLevel level, TiedLoadEntity load, int rows, Player player) {
        List<BlockPos> cells = load.cells();
        for (BlockPos cell : cells) {
            BlockPos to = cell.above(rows);
            if (!cells.contains(to) && !Permission.mayPlace(level, to, Direction.UP, player)) {
                return false;
            }
        }
        return true;
    }

    /** Moves a load the rope's click pulled up or let down (checked already). */
    public static void moveWithRope(TiedLoadEntity load, int rows) {
        load.moveRows(rows);
    }

    /**
     * Whether {@code load} can move {@code rows} whole rows up or down: every cell it comes into on the way free -
     * nothing solid or set there, nobody standing there - but {@code alsoFree} (the rope length pulled up as it rises).
     */
    public static boolean canMove(TiedLoadEntity load, int rows, @Nullable BlockPos alsoFree) {
        if (!load.aligned() || rows == 0) {
            return rows == 0;
        }
        Level level = load.level();
        List<BlockPos> cells = load.cells();
        int step = rows > 0 ? 1 : -1;
        Predicate<Entity> inTheWay = e -> e != load && !e.isRemoved() && e.blocksBuilding;
        for (int moved = step; moved != rows + step; moved += step) {
            for (BlockPos cell : cells) {
                if (cells.contains(cell.above(step))) {
                    continue; // only the face it moves into: a part's own cells come free as it goes
                }
                BlockPos to = cell.above(moved);
                if (!to.equals(alsoFree) && !freeCell(level, to)) {
                    return false;
                }
                if (!level.getEntities(load, new AABB(to), inTheWay).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a falling load can come down into row {@code baseRow} (its first part's row there): each cell under its
     * bottom free of anything solid or set there, and of another load. Below the world counts as free (it falls out).
     */
    static boolean freeAt(Level level, TiedLoadEntity load, int baseRow) {
        BlockPos base = BlockPos.containing(load.getX(), baseRow, load.getZ());
        List<CarriedLook.LookPart> parts = load.look().parts();
        List<BlockPos> offsets = parts.isEmpty() ? List.of(BlockPos.ZERO) : parts.stream().map(CarriedLook.LookPart::offset).toList();
        for (BlockPos offset : offsets) {
            if (offsets.contains(offset.below())) {
                continue;
            }
            BlockPos cell = base.offset(offset);
            if (cell.getY() < level.getMinBuildHeight()) {
                continue;
            }
            if (!freeCell(level, cell)
                    || !level.getEntities(load, new AABB(cell), e -> e instanceof TiedLoadEntity && !e.isRemoved()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Nothing solid there, nor anything set there that a block would not replace (a flower, a torch); loaded. */
    private static boolean freeCell(Level level, BlockPos cell) {
        if (level.isOutsideBuildHeight(cell) || !level.isLoaded(cell)) {
            return false;
        }
        BlockState state = level.getBlockState(cell);
        return state.canBeReplaced() && state.getCollisionShape(level, cell).isEmpty();
    }
}
