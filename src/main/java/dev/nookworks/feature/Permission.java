package dev.nookworks.feature;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Asking before changing the world on someone's behalf. Vanilla only checks the block a player clicks; anything we
 * break or place further off (a rope's far end, a laser's target, leaves falling after a felled tree) asks here, so
 * spawn protection, the world border and claim mods that listen to NeoForge's events get their say.
 * <p>
 * A machine asks as a fake player carrying its owner's id, so a claim mod lets it work inside its owner's claim and
 * nowhere else; a machine placed before owners were recorded asks as an anonymous one.
 */
public final class Permission {
    /** Server thread only. */
    private static boolean asking;
    private static final GameProfile ANONYMOUS = new GameProfile(UUID.fromString("6e6f6f6b-776f-726b-7300-6d6163686e65"), "[Nookworks]");

    /** Someone to ask as for a machine owned by {@code owner} (or by nobody we know). */
    public static FakePlayer actor(ServerLevel level, @Nullable UUID owner) {
        return FakePlayerFactory.get(level, owner == null ? ANONYMOUS : new GameProfile(owner, "[Nookworks]"));
    }

    /**
     * Whether {@code actor} may break the block at {@code pos}. This posts the break event, as mining it would. It is
     * asked right before breaking a block (a rope's end, a laser's tree, leaves falling), and also as a question of
     * whose a block is, with nothing broken: the Barter Terminal's room, the Spawner Controller's spawners, what our
     * blocks take from or give into ({@link DrawRights}). So every break listener of ours checks {@link #asking()} and
     * does nothing then (a lodestone asked about lost its name), and a question is asked only when its answer is needed -
     * something there to take - and the answer kept a while: other mods' listeners (a logging mod) hear each one.
     * <p>
     * It may be asked inside another mod's transaction, as that mod puts power into a cable: a break listener that opened
     * a root transaction of its own then would throw into that mod's insert. None of ours does (r15 fix check 4, N4).
     */
    public static boolean mayBreak(ServerLevel level, BlockPos pos, Player actor) {
        if (!level.mayInteract(actor, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        asking = true;
        try {
            return !NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, actor)).isCanceled();
        } finally {
            asking = false;
        }
    }

    /**
     * Whether the break or place event now being posted is only this class asking. Our own handlers (timber, hammers,
     * the coating and oil marks, lodestone names, a Storage Barrel's word to whoever hits it) must not treat it as a
     * block really mined or put down: a leaf falling after a felled tree would swing a hammer, a post a rope was asked
     * about lost its fireproof coat, and a lodestone beside a Repair Box its name.
     */
    public static boolean asking() {
        return asking;
    }

    /** How the cell asked about is changed and put back: no neighbour or shape updates, no drops, nothing told to clients. */
    private static final int QUIET = Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

    /**
     * Whether {@code actor} may place something at {@code pos} (a rope let down, a door's frame, a mob let out) - asked
     * with a block of stone standing for it. See {@link #mayPlace(ServerLevel, BlockPos, Direction, Player, BlockState)}.
     */
    public static boolean mayPlace(ServerLevel level, BlockPos pos, Direction against, Player actor) {
        return mayPlace(level, pos, against, actor, Blocks.STONE.defaultBlockState());
    }

    /**
     * Whether {@code actor} may put {@code placing} at {@code pos}, asked as vanilla asks: the place event is posted with
     * the block standing in an empty cell, as a player's placing posts it (the block set, the cell's state before it
     * kept) - and the cell is put back as it was at once, block entity and all, quietly. Claim mods read the block placed
     * from the world: asked before anything was there, they saw air, and some let air through. Open Parties and Claims
     * does: a Planter set by one player outside another's claim planted that player's claimed field (found in play,
     * 2026-10-05); asked this way, it keeps to its own side. Our own place handlers see {@link #asking()} and leave
     * their marks as they are.
     * <p>
     * The cell is changed in its chunk, not through the level, which also drops a point of interest and makes it again
     * when its block comes back - free: a bed or a workstation a villager had claimed was anyone's after the asking (a
     * bed washed from its foot, a Trading Counter's villager standing in a composter). {@code against} is the face
     * clicked, as a hand's placing passes it: the event's block placed against is the one {@code pos} stands against.
     */
    public static boolean mayPlace(ServerLevel level, BlockPos pos, Direction against, Player actor, BlockState placing) {
        if (!level.mayInteract(actor, pos)) {
            return false;
        }
        asking = true;
        if (!level.isLoaded(pos)) {
            try { // asked as it stands, not changed for the asking (the snapshot reads the block, which loads the chunk: callers load it first)
                return !EventHooks.onBlockPlace(actor, BlockSnapshot.create(level.dimension(), level, pos), against);
            } finally {
                asking = false;
            }
        }
        BlockPos cell = pos.immutable();
        LevelChunk chunk = level.getChunkAt(cell);
        BlockSnapshot was = BlockSnapshot.create(level.dimension(), level, cell, QUIET);
        try {
            chunk.setBlockState(cell, Blocks.AIR.defaultBlockState(), QUIET);
            BlockSnapshot empty = BlockSnapshot.create(level.dimension(), level, cell);
            chunk.setBlockState(cell, placing, QUIET);
            return !EventHooks.onBlockPlace(actor, empty, against);
        } finally {
            BlockState before = was.getState();
            if (!level.getBlockState(cell).equals(before) || was.getTag() != null) {
                chunk.setBlockState(cell, Blocks.AIR.defaultBlockState(), QUIET); // (so the state below is always set,
                chunk.setBlockState(cell, before, QUIET);                         // and its block entity made again)
                was.restoreBlockEntity(level, cell);
                if (!before.isAir()) {
                    level.sendBlockUpdated(cell, before, before, QUIET); // as a snapshot's restore tells clients
                }
            }
            asking = false;
        }
    }

    private Permission() {}
}
