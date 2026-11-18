package dev.nookworks.carry;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted on {@code NeoForge.EVENT_BUS} while {@link Carrying#check} asks whether a block may be carried, server side.
 */
public abstract class CarryEvent extends Event {
    private final ServerLevel level;

    protected CarryEvent(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    /**
     * The blocks that must move with the one asked about: add to {@link #set()} (the add-on's two-high battery's other
     * half), or {@link #refuse} when it is part of something that cannot move whole. Nookworks has already put a double
     * chest's other half in. Every block added is asked about in turn (not carriable, unbreakable, claims), but not for
     * its shape: an add-on may gather a part that is not a full block.
     */
    public static final class Gather extends CarryEvent {
        private final BlockPos pos;
        private final BlockState state;
        private final Set<BlockPos> set;
        private @Nullable Component refusal;

        public Gather(ServerLevel level, BlockPos pos, BlockState state, Set<BlockPos> set) {
            super(level);
            this.pos = pos;
            this.state = state;
            this.set = set;
        }

        /** The block asked about (the one clicked). */
        public BlockPos getPos() {
            return this.pos;
        }

        public BlockState getState() {
            return this.state;
        }

        /** The set so far, to add to: the block asked about first. */
        public Set<BlockPos> set() {
            return this.set;
        }

        /** It cannot be carried; {@code reason} is shown to the player. */
        public void refuse(Component reason) {
            this.refusal = reason;
        }

        public @Nullable Component refusal() {
            return this.refusal;
        }
    }

    /**
     * The last word before claims are asked: cancel it, with a reason for the player ({@link #refuse}), to keep a block
     * where it is (the add-on's working Quarry). Or let it go and stop it in {@link CarryAware#beforeCarried}.
     */
    public static final class Check extends CarryEvent implements ICancellableEvent {
        private final Set<BlockPos> set;
        private final @Nullable Player actor;
        private @Nullable Component reason;

        public Check(ServerLevel level, Set<BlockPos> set, @Nullable Player actor) {
            super(level);
            this.set = Set.copyOf(set);
            this.actor = actor;
        }

        /** Every block that would be carried, unmodifiable. */
        public Set<BlockPos> set() {
            return this.set;
        }

        /** Who is carrying it: a player, a machine's fake player, or null for nobody known. */
        public @Nullable Player getActor() {
            return this.actor;
        }

        /** Cancels it, saying why. */
        public void refuse(Component reason) {
            this.reason = reason;
            this.setCanceled(true);
        }

        public @Nullable Component reason() {
            return this.reason;
        }
    }
}
