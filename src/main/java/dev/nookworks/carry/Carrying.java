package dev.nookworks.carry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;

/**
 * Taking a block with its block entity out of the world into a {@link CarriedBlock}, and putting it back (server side).
 * What can go (the user, 2026-11-10: 「縛れるのはフルブロックやそれに準じたものだけ」): a block whose collision shape is a full
 * cube, a chest or a barrel ({@code #c:chests}, {@code #c:barrels}), or one in {@link #CARRIABLE}; not one in
 * {@link #NOT_CARRIABLE}, nor one in {@code #c:relocation_not_supported} unless it is in {@link #CARRIABLE} too
 * ({@link #tagsLet}); not an unbreakable one; with a double chest's other half, and whatever an add-on gathers
 * ({@link CarryEvent.Gather}). The API: docs/compat.md, "Carried blocks and tied loads".
 */
public final class Carrying {
    /**
     * Opts in a block that is not a full cube but should carry (our machines that are not cubes), and lets one go though
     * it is in {@code #c:relocation_not_supported} - which keeps other mods' movers (Create's contraptions) off a block
     * moving would break: name one here only if a carry moves it whole (nookTechnology's Large Battery, both halves
     * gathered; its Chunk Loader, letting its tickets go in {@link CarryAware#beforeCarried}).
     */
    public static final TagKey<Block> CARRIABLE = TagKey.create(Registries.BLOCK, Nookworks.id("carriable"));
    /** Opts a block out, over everything (spawners, the If's mirror, the Warp Anchor: blocks that are their place). */
    public static final TagKey<Block> NOT_CARRIABLE = TagKey.create(Registries.BLOCK, Nookworks.id("not_carriable"));
    /** The most blocks one carry may take, an add-on's gathering included. */
    public static final int MAX_SET = 16;

    /**
     * Removing a taken block: told to clients only, no drops, and the block entity's removal side effects
     * ({@code preRemoveSideEffects}: a container's spill, a barrel's, an Experience Tank's orbs, a furnace's experience)
     * skipped - it is not broken. Neighbours are told once the whole set is gone ({@link #take}).
     */
    private static final int TAKE_QUIETLY = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    /** Setting a put-back block down: told to clients only, until its data is in; {@code onPlace} and neighbours after. */
    private static final int SET_QUIETLY = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    private Carrying() {}

    /**
     * Why a carry was refused, for a carrier that words it its own way: {@link Check#why()}, {@link Put#why()}.
     * {@link #text()} is Nookworks' own words, a carrier's of any kind ("This can't be carried", "No room to set it down
     * here"); the rope says "tied up" for the five at taking (the Tied Load's own keys), and an add-on's carrier may do
     * the same by {@link #id()}, showing any reason it has no words for as given ({@link Check#reason()}). An add-on's
     * own refusal ({@link CarryEvent.Gather#refuse}, {@link CarryEvent.Check#refuse}) has its text and no {@code Why}.
     */
    public enum Why {
        /** Not a full cube, a chest or a barrel, nor in {@link Carrying#CARRIABLE}. */
        NOT_WHOLE,
        /** Kept off by the tags ({@link Carrying#tagsLet}), unbreakable, not loaded, or its data would not save. */
        NOT_CARRIABLE,
        /** Its data as saved is over {@link CarriedBlock#MAX_BYTES}. */
        TOO_MUCH,
        /** Its set is over {@link Carrying#MAX_SET} blocks. */
        TOO_MANY,
        /** The actor may not take it there (claims). */
        NOT_YOURS,
        /** The actor may not set it down there (claims). */
        NOT_HERE,
        /** A cell is not free ({@link Put#BLOCKED}). */
        BLOCKED;

        /** As in the key: {@code not_whole}. */
        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        /** {@code message.nookworks.carry.<id>}, Nookworks' words for any carrier. */
        public String key() {
            return "message.nookworks.carry." + this.id();
        }

        public Component text() {
            return Component.translatable(this.key());
        }
    }

    /** The answer to {@link #check}: the set that would be carried, or why not. */
    public sealed interface Check permits Check.Ok, Check.Refused {
        record Ok(Set<BlockPos> set) implements Check {
            public Ok {
                set = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(set));
            }
        }

        /** Refused: the reason shown, and which of Nookworks' it is ({@code why}), or null when an add-on refused it. */
        record Refused(Component reason, @Nullable Why why) implements Check {
            public Refused(Component reason) {
                this(reason, null);
            }
        }

        static Check ok(Set<BlockPos> set) {
            return new Ok(set);
        }

        static Check refused(Component reason) {
            return new Refused(reason);
        }

        static Check refused(Why why) {
            return new Refused(why.text(), why);
        }

        default boolean isOk() {
            return this instanceof Ok;
        }

        /** The set, the block asked about first; empty when refused. */
        default Set<BlockPos> set() {
            return this instanceof Ok ok ? ok.set() : Set.of();
        }

        /** Why not, or null when it may go. */
        default @Nullable Component reason() {
            return this instanceof Refused refused ? refused.reason() : null;
        }

        /** Which of Nookworks' reasons, or null when it may go or an add-on refused it in its own words. */
        default @Nullable Why why() {
            return this instanceof Refused refused ? refused.why() : null;
        }
    }

    /** The answer to {@link #put}: placed, a cell not free, or refused (claims) with a reason. */
    public sealed interface Put permits Put.Placed, Put.Blocked, Put.Refused {
        Put PLACED = new Placed();
        Put BLOCKED = new Blocked();

        record Placed() implements Put {}

        record Blocked() implements Put {}

        /** Refused (claims): the reason shown, and which of Nookworks' it is. */
        record Refused(Component reason, @Nullable Why why) implements Put {
            public Refused(Component reason) {
                this(reason, null);
            }
        }

        static Put refused(Component reason) {
            return new Refused(reason);
        }

        static Put refused(Why why) {
            return new Refused(why.text(), why);
        }

        default boolean placed() {
            return this instanceof Placed;
        }

        /** Why not, in Nookworks' words - {@link Why#BLOCKED}'s for {@link #BLOCKED} - or null when placed. */
        default @Nullable Component reason() {
            return this instanceof Refused refused ? refused.reason() : this instanceof Blocked ? Why.BLOCKED.text() : null;
        }

        /** Which reason: {@link Why#BLOCKED}, {@link Why#NOT_HERE}, or null when placed. */
        default @Nullable Why why() {
            return this instanceof Refused refused ? refused.why() : this instanceof Blocked ? Why.BLOCKED : null;
        }
    }

    /**
     * Whether a block in {@code state} could be carried by its shape and tags alone - safe on the client, for showing a
     * cart's frame only on blocks that may go. The server's {@link #check} still decides.
     */
    public static boolean couldCarry(BlockState state) {
        if (state.isAir() || !tagsLet(state::is) || state.getBlock().defaultDestroyTime() < 0) {
            return false;
        }
        return whole(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /**
     * Whether the tags let a block go, {@code in} saying which tags it is in: never one in {@link #NOT_CARRIABLE}; one
     * in {@code #c:relocation_not_supported} only if it is in {@link #CARRIABLE} too - an add-on's block that a carry
     * moves whole though other mods' movers must leave it (nookTechnology, 2026-11-10). Public for tests.
     */
    public static boolean tagsLet(Predicate<TagKey<Block>> in) {
        return !in.test(NOT_CARRIABLE) && (!in.test(Tags.Blocks.RELOCATION_NOT_SUPPORTED) || in.test(CARRIABLE));
    }

    /** Whole enough to carry by its shape: a full cube, a chest, a barrel, or named in {@link #CARRIABLE}. */
    private static boolean whole(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(CARRIABLE) || state.is(Tags.Blocks.CHESTS) || state.is(Tags.Blocks.BARRELS)
                || state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Whether the block at {@code pos} may be carried by {@code actor} (a player, a machine's fake player -
     * {@link Permission#actor} - or null for nobody known): its shape and tags, the set it goes with
     * ({@link CarryEvent.Gather}), the add-ons' vetoes ({@link CarryEvent.Check}), claims at every block of the set
     * (asked as whose the block is: {@link Permission#mayTake}), and its size as saved ({@link CarriedBlock#MAX_BYTES}).
     */
    public static Check check(ServerLevel level, BlockPos pos, @Nullable Player actor) {
        return examine(level, pos, actor).check();
    }

    private record Examined(Check check, @Nullable CarriedBlock snapshot) {
        static Examined no(Why why) {
            return new Examined(Check.refused(why), null);
        }
    }

    private static Examined examine(ServerLevel level, BlockPos pos, @Nullable Player actor) {
        if (!level.isLoaded(pos)) {
            return Examined.no(Why.NOT_CARRIABLE);
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !tagsLet(state::is) || state.getDestroySpeed(level, pos) < 0) {
            return Examined.no(Why.NOT_CARRIABLE);
        }
        if (!whole(state, level, pos)) {
            return Examined.no(Why.NOT_WHOLE);
        }
        Set<BlockPos> set = new LinkedHashSet<>();
        set.add(pos.immutable());
        BlockPos half = otherHalf(level, pos, state);
        if (half != null) {
            set.add(half);
        }
        CarryEvent.Gather gather = NeoForge.EVENT_BUS.post(new CarryEvent.Gather(level, pos.immutable(), state, set));
        if (gather.refusal() != null) {
            return new Examined(Check.refused(gather.refusal()), null);
        }
        if (set.size() > MAX_SET) {
            return Examined.no(Why.TOO_MANY);
        }
        for (BlockPos part : set) {
            BlockState there = level.getBlockState(part);
            if (!level.isLoaded(part) || there.isAir() || !tagsLet(there::is) || there.getDestroySpeed(level, part) < 0) {
                return Examined.no(Why.NOT_CARRIABLE);
            }
        }
        CarryEvent.Check veto = NeoForge.EVENT_BUS.post(new CarryEvent.Check(level, set, actor));
        if (veto.isCanceled()) {
            Component reason = veto.reason();
            return new Examined(reason != null ? Check.refused(reason) : Check.refused(Why.NOT_CARRIABLE), null);
        }
        Player asker = actor != null ? actor : Permission.actor(level, null);
        for (BlockPos part : set) {
            // asked as whose it is, not whether it may come down: a Storage Barrel too full to break is still its owner's
            // to carry (Permission.mayTake; the same break event, which a claim mod answers)
            if (!Permission.mayTake(level, part, asker)) {
                return Examined.no(Why.NOT_YOURS);
            }
        }
        CarriedBlock snapshot;
        try {
            snapshot = snapshot(level, pos, set);
        } catch (RuntimeException e) {
            Nookworks.LOGGER.warn("Could not save the block at {} to carry it", pos, e);
            return Examined.no(Why.NOT_CARRIABLE);
        }
        if (snapshot.bytes() > CarriedBlock.MAX_BYTES) {
            return Examined.no(Why.TOO_MUCH);
        }
        return new Examined(Check.ok(set), snapshot);
    }

    /**
     * A double chest's other half, if it is one and the half is there - any block paired as chests are (a chest type and
     * a facing, the halves side by side: the Repair Box too).
     */
    private static @Nullable BlockPos otherHalf(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.CHEST_TYPE) || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
            return null;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
        BlockPos other = pos.relative(type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
        BlockState there = level.getBlockState(other);
        return there.is(state.getBlock()) && there.getValue(BlockStateProperties.CHEST_TYPE) == type.getOpposite()
                && there.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing ? other.immutable() : null;
    }

    private static CarriedBlock snapshot(ServerLevel level, BlockPos pos, Set<BlockPos> set) {
        List<CarriedBlock.Part> parts = new ArrayList<>(set.size());
        for (BlockPos at : set) {
            BlockEntity entity = level.getBlockEntity(at);
            parts.add(new CarriedBlock.Part(at.subtract(pos), level.getBlockState(at),
                    entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
        }
        return new CarriedBlock(parts);
    }

    /**
     * Takes the block at {@code pos} (and its set) out of the world: checks again ({@link #check}), saves every part,
     * tells each block entity ({@link CarryAware#beforeCarried}), then removes the set quietly - no drops, no spill, no
     * experience, no break sound or particles (the carrier plays its own), the block entities' removal side effects
     * skipped. Neighbours are told once the whole set is gone, as for any block removed, so no part of the set hears
     * another go: a two-high block's other half would break as a door's does, dropping a copy of what is carried
     * (nookTechnology's Large Battery, 2026-11-10). Empty, with the world untouched, when it may not go or anything fails
     * before the removal.
     */
    public static Optional<CarriedBlock> take(ServerLevel level, BlockPos pos, @Nullable Player actor) {
        Examined examined = examine(level, pos, actor);
        if (!(examined.check() instanceof Check.Ok ok) || examined.snapshot() == null) {
            return Optional.empty();
        }
        for (BlockPos at : ok.set()) {
            if (level.getBlockEntity(at) instanceof CarryAware aware) {
                aware.beforeCarried(level);
            }
        }
        List<BlockState> was = new ArrayList<>(ok.set().size());
        for (BlockPos at : ok.set()) {
            was.add(level.getBlockState(at));
            level.setBlock(at, level.getFluidState(at).createLegacyBlock(), TAKE_QUIETLY);
        }
        // what Level.setBlock does after a removal told to neighbours (Block.UPDATE_ALL), the set gone first; not by
        // markAndNotifyBlock again, which would have a barrel's or a composter's point of interest removed twice
        int i = 0;
        for (BlockPos at : ok.set()) {
            BlockState old = was.get(i++);
            BlockState now = level.getBlockState(at);
            old.affectNeighborsAfterRemoval(level, at, false);
            level.updateNeighborsAt(at, old.getBlock());
            old.updateIndirectNeighbourShapes(level, at, Block.UPDATE_CLIENTS, Block.UPDATE_LIMIT - 1);
            now.updateNeighbourShapes(level, at, Block.UPDATE_CLIENTS, Block.UPDATE_LIMIT - 1);
            now.updateIndirectNeighbourShapes(level, at, Block.UPDATE_CLIENTS, Block.UPDATE_LIMIT - 1);
        }
        return Optional.of(examined.snapshot());
    }

    /** One part as it would be put: where, and in what state (turned, and waterlogged as the cell is). */
    private record Placing(BlockPos cell, BlockState state, CarriedBlock.Part part) {}

    /**
     * Puts {@code carried} back with its first part at {@code at}, turned by {@code rotation}, as {@code actor} (null
     * for nobody known). Every cell must be free (replaceable, nothing standing in it) - else {@link Put#BLOCKED} - and
     * the actor may place there ({@link Permission#mayPlace}) - else refused, with a reason. On anything but
     * {@link Put#PLACED} nothing changes and the holder keeps the value. Placed: each state turned, its block entity
     * loaded with the data, {@link CarryAware#afterPut}, then {@code onPlace} and neighbour and shape updates as if it
     * were placed; the block entity's {@code onLoad} comes as any new block entity's does, at the next tick.
     */
    public static Put put(ServerLevel level, BlockPos at, Rotation rotation, CarriedBlock carried, @Nullable Player actor) {
        return put(level, at, rotation, carried, actor, null);
    }

    /** {@link #put(ServerLevel, BlockPos, Rotation, CarriedBlock, Player)}, not counting {@code holder} as in the way. */
    public static Put put(ServerLevel level, BlockPos at, Rotation rotation, CarriedBlock carried, @Nullable Player actor,
            @Nullable Entity holder) {
        Put asked = ask(level, at, rotation, carried, actor, holder);
        if (asked != Put.PLACED) {
            return asked;
        }
        List<Placing> placings = plan(level, at, rotation, carried);
        List<BlockState> before = new ArrayList<>(placings.size());
        for (Placing placing : placings) {
            before.add(level.getBlockState(placing.cell()));
            level.setBlock(placing.cell(), placing.state(), SET_QUIETLY);
        }
        for (Placing placing : placings) {
            CompoundTag data = placing.part().dataView();
            BlockEntity entity = level.getBlockEntity(placing.cell());
            if (data != null && entity != null) {
                try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LogUtils.getLogger())) {
                    entity.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), data));
                }
                entity.setChanged();
            }
        }
        for (Placing placing : placings) {
            if (level.getBlockEntity(placing.cell()) instanceof CarryAware aware) {
                aware.afterPut(level, rotation);
            }
        }
        for (int i = 0; i < placings.size(); i++) {
            Placing placing = placings.get(i);
            BlockState now = level.getBlockState(placing.cell());
            now.onPlace(level, placing.cell(), before.get(i), false);
            level.markAndNotifyBlock(placing.cell(), level.getChunkAt(placing.cell()), before.get(i), level.getBlockState(placing.cell()),
                    Block.UPDATE_ALL, Block.UPDATE_LIMIT);
        }
        return Put.PLACED;
    }

    /** Whether {@link #put} would place it: the same questions, nothing done. */
    public static boolean canPut(ServerLevel level, BlockPos at, Rotation rotation, CarriedBlock carried, @Nullable Player actor) {
        return ask(level, at, rotation, carried, actor, null) == Put.PLACED;
    }

    /** {@link #canPut(ServerLevel, BlockPos, Rotation, CarriedBlock, Player)}, not counting {@code holder} as in the way. */
    public static boolean canPut(ServerLevel level, BlockPos at, Rotation rotation, CarriedBlock carried, @Nullable Player actor,
            @Nullable Entity holder) {
        return ask(level, at, rotation, carried, actor, holder) == Put.PLACED;
    }

    private static List<Placing> plan(ServerLevel level, BlockPos at, Rotation rotation, CarriedBlock carried) {
        List<Placing> placings = new ArrayList<>(carried.parts().size());
        for (CarriedBlock.Part part : carried.parts()) {
            BlockPos cell = at.offset(part.offset().rotate(rotation)).immutable();
            BlockState state = part.state().rotate(rotation);
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(cell).is(Fluids.WATER) && level.getFluidState(cell).isSource());
            }
            placings.add(new Placing(cell, state, part));
        }
        return placings;
    }

    private static Put ask(ServerLevel level, BlockPos at, Rotation rotation, CarriedBlock carried, @Nullable Player actor,
            @Nullable Entity holder) {
        List<Placing> placings = plan(level, at, rotation, carried);
        for (Placing placing : placings) {
            BlockPos cell = placing.cell();
            if (level.isOutsideBuildHeight(cell) || !level.isLoaded(cell) || !level.getWorldBorder().isWithinBounds(cell)
                    || !level.getBlockState(cell).canBeReplaced()) {
                return Put.BLOCKED;
            }
            VoxelShape shape = placing.state().getCollisionShape(level, cell);
            if (!shape.isEmpty()) {
                AABB box = shape.bounds().move(cell);
                if (!level.getEntities(holder, box, e -> !e.isRemoved() && e.blocksBuilding && (holder == null || !e.isPassengerOfSameVehicle(holder)))
                        .isEmpty()) {
                    return Put.BLOCKED; // someone standing there, or another load
                }
            }
        }
        Player asker = actor != null ? actor : Permission.actor(level, null);
        for (Placing placing : placings) {
            if (!Permission.mayPlace(level, placing.cell(), Direction.UP, asker, placing.state())) {
                return Put.refused(Why.NOT_HERE);
            }
        }
        return Put.PLACED;
    }
}
