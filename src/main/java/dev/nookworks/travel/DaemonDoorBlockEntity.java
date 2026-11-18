package dev.nookworks.travel;

import org.jspecify.annotations.Nullable;
import java.util.Optional;

import com.mojang.logging.LogUtils;

import dev.nookworks.registry.ModTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import dev.nookworks.port.storage.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.nookworks.port.storage.TagValueOutput;
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.core.PreRemoveSideEffects;
import dev.nookworks.port.storage.ValueIO;

/**
 * A Dock-a-Daemon Door's lower half, as the client needs it to draw the world beyond: whether it is the home door (which
 * opens onto wherever the player looking at it chose) or a return door, and where a return door leads. Nothing of it
 * is saved: the server works it out from {@link DaemonDoors} each time it is sent.
 */
public class DaemonDoorBlockEntity extends BlockEntity implements PreRemoveSideEffects {
    /** How long the leaf takes to slide all the way, in ticks. */
    public static final float SLIDE_TICKS = 6.0F;

    private boolean home;
    private Optional<ResourceKey<Level>> leadsTo = Optional.empty();
    /**
     * On the client, for the slide: how far open the leaf was when it last began to slide (0 shut, 1 open), and when (game
     * time). Set as the change comes - seen or not - so a door opened while out of sight is open when looked at, not
     * sliding then; and from where it was, so one turned back mid-slide slides back from there, not from the far end.
     */
    private float slideFrom = -1;
    private double slideStart = Double.NEGATIVE_INFINITY;

    public DaemonDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModTravel.DAEMON_DOOR_ENTITY.get(), pos, state);
    }

    /** Whether this is the home door (on the client, as last sent). */
    public boolean isHome() {
        return this.home;
    }

    /** Where a return door leads: the home door's dimension. Empty for the home door, or with no home door. */
    public Optional<ResourceKey<Level>> leadsTo() {
        return this.leadsTo;
    }

    /**
     * Where this door leads for {@code player} (on the client, as last sent): the home door to the dimension they chose at
     * its panel - nowhere while they have it off - and a return door home. Never the dimension it stands in.
     */
    public Optional<ResourceKey<Level>> leadsFor(Player player) {
        Optional<ResourceKey<Level>> to = this.home ? TravelLog.of(player).destination() : this.leadsTo;
        Level level = this.level;
        return level == null ? Optional.empty() : to.filter(key -> !key.equals(level.dimension()));
    }

    /** How far open the leaf is drawn at game time {@code now}: 0 shut, 1 open, eased along its slide. */
    public float openness(double now) {
        float to = this.getBlockState().hasProperty(DoorBlock.OPEN) && this.getBlockState().getValue(DoorBlock.OPEN) ? 1 : 0;
        if (this.slideFrom < 0) {
            return to; // as it was first seen: no slide
        }
        float span = Math.abs(to - this.slideFrom) * SLIDE_TICKS;
        float t = span <= 0 ? 1 : Mth.clamp((float) ((now - this.slideStart) / span), 0.0F, 1.0F);
        t = t * t * (3 - 2 * t);
        return Mth.lerp(t, this.slideFrom, to);
    }

    @Override
    public void setBlockState(BlockState state) {
        BlockState was = this.getBlockState();
        Level level = this.level;
        if (level != null && level.isClientSide() && was.hasProperty(DoorBlock.OPEN) && state.hasProperty(DoorBlock.OPEN)
                && was.getValue(DoorBlock.OPEN) != state.getValue(DoorBlock.OPEN)) {
            double now = level.getGameTime();
            this.slideFrom = this.openness(now);
            this.slideStart = now;
        }
        super.setBlockState(state);
    }

    /** The door gone: forgotten, and the doors still standing told if the home door went with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        PreRemoveSideEffects.super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            DaemonDoors doors = DaemonDoors.get(server.getServer());
            doors.forget(server.dimension(), pos);
            refreshAll(server.getServer());
        }
    }

    /**
     * Sends every loaded door to whoever sees it, as it is now: after a door is set down or taken away, which may make
     * another the home door, or leave the return doors with no home. There are a handful at most. Every player's client
     * is told where the door of their dimension stands now ({@link DaemonDoors#tell}).
     */
    static void refreshAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(DaemonDoors::tell);
        for (var entry : DaemonDoors.get(server).all().entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level != null && level.isLoaded(entry.getValue()) && level.getBlockEntity(entry.getValue()) instanceof DaemonDoorBlockEntity door) {
                level.sendBlockUpdated(entry.getValue(), door.getBlockState(), door.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(ProblemReporter.pathOf(this), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            if (this.level instanceof ServerLevel server) {
                DaemonDoors doors = DaemonDoors.get(server.getServer());
                this.home = doors.isHome(server.dimension(), this.worldPosition);
                GlobalPos homeDoor = doors.home();
                this.leadsTo = this.home || homeDoor == null ? Optional.empty() : Optional.of(homeDoor.dimension());
            }
            output.putBoolean("Home", this.home);
            output.storeNullable("LeadsTo", ResourceKey.codec(Registries.DIMENSION), this.leadsTo.orElse(null));
            return output.buildResult();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueInput input = ValueIO.input(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.loadAdditional(portTag, portLookup);
        this.home = input.getBooleanOr("Home", false);
        this.leadsTo = input.read("LeadsTo", ResourceKey.codec(Registries.DIMENSION));
    }
}
