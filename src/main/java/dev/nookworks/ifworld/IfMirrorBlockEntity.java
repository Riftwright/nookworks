package dev.nookworks.ifworld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

/**
 * An If Mirror's lower half, as the client needs it to draw the glass: the faces thrown so far (shown in the glass as
 * they come), when the last was thrown (the newest tumbles a moment before it settles), and where the glass looks onto
 * once all six are: from home the If, from the If the home mirror's dimension. Nothing of it is saved: the server works
 * it out from {@link IfWorld} each time it is sent.
 */
public class IfMirrorBlockEntity extends BlockEntity {
    private List<Integer> faces = List.of();
    private long thrownAt = Long.MIN_VALUE / 2;
    private Optional<ResourceKey<Level>> looksOnto = Optional.empty();

    public IfMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModTravel.IF_MIRROR_ENTITY.get(), pos, state);
    }

    /** The faces thrown so far, in order (on the client, as last sent). */
    public List<Integer> faces() {
        return this.faces;
    }

    /** The game time the newest face was thrown. */
    public long thrownAt() {
        return this.thrownAt;
    }

    /** What the glass opens onto once rolled: empty before, or where it leads nowhere. */
    public Optional<ResourceKey<Level>> looksOnto() {
        return this.looksOnto;
    }

    /** A face thrown at this mirror now: sent, with when, so the client tumbles it. */
    void thrown(long gameTime) {
        this.thrownAt = gameTime;
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            IfMirrors.removed(server, pos);
        }
    }

    /** Sends every loaded mirror as it is now: after one is set down or taken away, or the If is rolled. */
    static void refreshAll(MinecraftServer server) {
        IfWorld world = IfWorld.get(server);
        for (GlobalPos at : new GlobalPos[] {world.homeMirror(), world.mirrorFor(IfWorld.KEY)}) {
            ServerLevel level = at == null ? null : server.getLevel(at.dimension());
            if (level != null && level.isLoaded(at.pos()) && level.getBlockEntity(at.pos()) instanceof IfMirrorBlockEntity mirror) {
                level.sendBlockUpdated(at.pos(), mirror.getBlockState(), mirror.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            if (this.level instanceof ServerLevel server) {
                IfWorld world = IfWorld.get(server.getServer());
                this.faces = world.faces();
                GlobalPos home = world.homeMirror();
                this.looksOnto = !world.rolled() ? Optional.empty()
                        : !server.dimension().equals(IfWorld.KEY) ? Optional.of(IfWorld.KEY)
                        : Optional.ofNullable(home == null ? null : home.dimension());
            }
            output.store("Faces", Codec.INT.listOf(), this.faces);
            output.putLong("ThrownAt", this.thrownAt);
            output.storeNullable("LooksOnto", ResourceKey.codec(Registries.DIMENSION), this.looksOnto.orElse(null));
            return output.buildResult();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.faces = new ArrayList<>(input.read("Faces", Codec.INT.listOf()).orElse(List.of()));
        this.thrownAt = input.getLongOr("ThrownAt", Long.MIN_VALUE / 2);
        this.looksOnto = input.read("LooksOnto", ResourceKey.codec(Registries.DIMENSION));
    }
}
