package dev.nookworks.travel;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.ifworld.IfWorld;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Where a player has been, for the Dock-a-Daemon Door: the dimensions they have stood in (the door goes nowhere they
 * have not), where they last were in each (where a door first opens onto it), and the dimension they chose at the
 * door's panel. Kept through death, and sent to its own player only - the door shows them the colour of where they go.
 * <p>
 * Visits count from when the mod first saw them; the Nether and the End also count from vanilla's advancements for
 * entering them, so a world from before is not shut out of its own Nether.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public record TravelLog(Set<ResourceKey<Level>> visited, Map<ResourceKey<Level>, BlockPos> lastPos,
        Optional<ResourceKey<Level>> destination, int ifGeneration) {
    public static final TravelLog EMPTY = new TravelLog(Set.of(), Map.of(), Optional.empty(), 0);

    private static final Codec<ResourceKey<Level>> DIMENSION = ResourceKey.codec(Registries.DIMENSION);
    public static final MapCodec<TravelLog> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DIMENSION.listOf().<Set<ResourceKey<Level>>>xmap(LinkedHashSet::new, List::copyOf).optionalFieldOf("visited", Set.of())
                    .forGetter(TravelLog::visited),
            Codec.unboundedMap(DIMENSION, BlockPos.CODEC).optionalFieldOf("last_pos", Map.of()).forGetter(TravelLog::lastPos),
            DIMENSION.optionalFieldOf("destination").forGetter(TravelLog::destination),
            Codec.INT.optionalFieldOf("if_generation", 0).forGetter(TravelLog::ifGeneration)).apply(i, TravelLog::new));
    private static final StreamCodec<ByteBuf, ResourceKey<Level>> DIMENSION_STREAM = ResourceKey.streamCodec(Registries.DIMENSION);
    /**
     * The most places of each kind its player's client is sent, as many as the codec takes: past them (a pack making
     * dimensions by the thousand) the log would not go at all, and the player was disconnected. The client reads only the
     * destination; the server keeps every place.
     */
    private static final int SENT = 1024;
    public static final StreamCodec<ByteBuf, TravelLog> STREAM_CODEC = StreamCodec.composite(
            DIMENSION_STREAM.apply(ByteBufCodecs.collection(LinkedHashSet::new, SENT)), TravelLog::sentVisited,
            ByteBufCodecs.map(HashMap::new, DIMENSION_STREAM, BlockPos.STREAM_CODEC, SENT), TravelLog::sentLastPos,
            ByteBufCodecs.optional(DIMENSION_STREAM), TravelLog::destination,
            ByteBufCodecs.VAR_INT, TravelLog::ifGeneration,
            TravelLog::new);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Nookworks.MODID);
    public static final Supplier<AttachmentType<TravelLog>> TYPE = ATTACHMENT_TYPES.register("travel_log",
            () -> AttachmentType.builder(() -> EMPTY).serialize(CODEC).copyOnDeath()
                    .sync((holder, to) -> holder == to, STREAM_CODEC).build());

    /** The vanilla advancements that say a player has been to a dimension. */
    private static final Map<ResourceKey<Level>, Identifier> ENTERED = Map.of(
            Level.NETHER, Identifier.withDefaultNamespace("story/enter_the_nether"),
            Level.END, Identifier.withDefaultNamespace("story/enter_the_end"));

    public static TravelLog of(Player player) {
        return player.getData(TYPE);
    }

    /** The places been to that the client is sent: the first {@link #SENT}. */
    private Set<ResourceKey<Level>> sentVisited() {
        return this.visited.size() <= SENT ? this.visited
                : this.visited.stream().limit(SENT).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Where they last were in each, as the client is sent it: {@link #SENT} places at most. */
    private Map<ResourceKey<Level>, BlockPos> sentLastPos() {
        return this.lastPos.size() <= SENT ? this.lastPos
                : this.lastPos.entrySet().stream().limit(SENT).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public boolean hasBeenTo(ResourceKey<Level> dimension) {
        return this.visited.contains(dimension);
    }

    public TravelLog withVisit(ResourceKey<Level> dimension) {
        if (this.visited.contains(dimension)) {
            return this;
        }
        Set<ResourceKey<Level>> visited = new LinkedHashSet<>(this.visited);
        visited.add(dimension);
        return new TravelLog(Set.copyOf(visited), this.lastPos, this.destination, this.ifGeneration);
    }

    public TravelLog withLastPos(ResourceKey<Level> dimension, BlockPos pos) {
        Map<ResourceKey<Level>, BlockPos> last = new HashMap<>(this.lastPos);
        last.put(dimension, pos.immutable());
        return new TravelLog(this.visited, Map.copyOf(last), this.destination, this.ifGeneration);
    }

    public TravelLog withDestination(Optional<ResourceKey<Level>> destination) {
        return new TravelLog(this.visited, this.lastPos, destination, this.ifGeneration);
    }

    /**
     * As of the If numbered {@code generation} (counting resets): an earlier If forgotten - not visited, nowhere last
     * seen in it, not the door's destination - since the If now is another world.
     */
    public TravelLog ofIf(int generation) {
        if (generation == this.ifGeneration) {
            return this;
        }
        TravelLog forgotten = this.withoutIf();
        return new TravelLog(forgotten.visited, forgotten.lastPos, forgotten.destination, generation);
    }

    /** The If forgotten: not visited, nowhere last seen in it, not the door's destination. */
    public TravelLog withoutIf() {
        Set<ResourceKey<Level>> visited = new LinkedHashSet<>(this.visited);
        visited.remove(IfWorld.KEY);
        Map<ResourceKey<Level>, BlockPos> last = new HashMap<>(this.lastPos);
        last.remove(IfWorld.KEY);
        return new TravelLog(Set.copyOf(visited), Map.copyOf(last), this.destination.filter(d -> !d.equals(IfWorld.KEY)), this.ifGeneration);
    }

    /** Records that {@code player} is in {@code dimension} now (and, from vanilla's advancements, the Nether and End). */
    public static void visit(ServerPlayer player, ResourceKey<Level> dimension) {
        TravelLog log = of(player).ofIf(IfWorld.get(player.level().getServer()).generation()).withVisit(Level.OVERWORLD).withVisit(dimension);
        var advancements = player.level().getServer().getAdvancements();
        for (var entered : ENTERED.entrySet()) {
            var holder = advancements.get(entered.getValue());
            if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
                log = log.withVisit(entered.getKey());
            }
        }
        if (!log.equals(of(player))) {
            player.setData(TYPE, log);
        }
    }

    @SubscribeEvent
    static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            visit(player, player.level().dimension());
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            visit(player, event.getTo());
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            visit(player, player.level().dimension());
        }
    }

    /** Where a player leaving a dimension was in it: where a door first opens onto it, next time. */
    @SubscribeEvent
    static void onLeave(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDimension() != player.level().dimension()) {
            player.setData(TYPE, of(player).withLastPos(player.level().dimension(), player.blockPosition()));
        }
    }
}
