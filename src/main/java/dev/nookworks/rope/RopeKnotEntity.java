package dev.nookworks.rope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.StructureTurns;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The knot of Thin Rope (or Thin Chain, {@link SpanMaterial}) tied round a fence post (see {@link RopeSpans}). A knot keeps
 * the spans it tied off - the posts they run to, and which of them are chain - and draws them; the knot at the far end of a
 * span is only its end, and is kept for as long as some span ends there. While a player is paying out rope or chain from
 * it, it holds that player too, and draws the line to their hand.
 * <p>
 * Hitting a knot cuts every line tied at it, and they drop; so does taking away the post it is tied to, or the post at
 * the far end of a span, and lightning striking the lightning rod it is tied to ({@link StruckLoose}). On a client, a
 * chain span of its own swings a little and rattles when something goes through it ({@link ChainSway}).
 */
public class RopeKnotEntity extends BlockAttachedEntity {
    private static final EntityDataAccessor<List<BlockPos>> DATA_SPANS = SynchedEntityData.defineId(RopeKnotEntity.class, ModRope.POSITIONS);
    /** Which of its spans are chain (the rest are rope): a knot saved before there were chains has none. */
    private static final EntityDataAccessor<List<BlockPos>> DATA_CHAINS = SynchedEntityData.defineId(RopeKnotEntity.class, ModRope.POSITIONS);
    private static final EntityDataAccessor<Integer> DATA_HOLDER = SynchedEntityData.defineId(RopeKnotEntity.class, EntityDataSerializers.INT);
    /** Whether what its holder is paying out is chain. */
    private static final EntityDataAccessor<Boolean> DATA_HELD_CHAIN = SynchedEntityData.defineId(RopeKnotEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * What other knots' spans that end here are made of ({@link SpanMaterial#bit}s): what is wound round this post besides
     * its own, which the client draws the knot by - a chain wound round it, a rope's knot, or both.
     */
    private static final EntityDataAccessor<Byte> DATA_ENDS = SynchedEntityData.defineId(RopeKnotEntity.class, EntityDataSerializers.BYTE);
    /** Half the size of the knot, round the post. */
    private static final double HALF = 3.0 / 16;
    /**
     * A rope that ended here was cut at its other end: look at the next check whether anything is still tied here,
     * rather than at the slow round an end knot is otherwise looked at in (a festival's worth of knots, each searching
     * the 35-block cube round it every second, adds up).
     */
    private boolean recheck;
    /**
     * Whether {@link #DATA_ENDS} is to be looked for again: on a knot loaded from disk (it is not saved), and after a span
     * ending here was cut. A knot tied now has nothing else ending at its post.
     */
    private boolean endsStale = true;
    /** How often it looks at its post's redstone signal, in ticks. */
    private static final int SIGNAL_EVERY = 4;
    /** Whether its post took a signal when it last looked, and whether it has looked (or loaded what it last saw). */
    private boolean powered;
    private boolean signalKnown;
    /** The bolt striking it now, which spares the rope it knocks loose; null otherwise. */
    private @Nullable UUID struckBy;
    /**
     * Read from a save that kept its spans as steps from where the knot was: that place (as a template sets the knot
     * down, the copy's), and the steps from it to the middle of each post its spans and chains run to - kept as read,
     * for a structure setting it down to turn and mirror about it ({@link #rotate}). Null for a knot tied here, or one
     * saved before there were steps.
     */
    private @Nullable Steps steps;
    /** How a structure setting it down has turned it, for the mirror it tells next ({@link StructureTurns#afterTurn}). */
    private Rotation turned = Rotation.NONE;
    /** On a client: how each chain span of its own swings, by the post it runs to, while it swings. */
    private final Map<BlockPos, ChainSway> sways = new HashMap<>();
    /** The chain spans swinging on this client, to draw what hangs on them swinging with them. */
    private static final Map<RopeKnotEntity, Boolean> SWINGING = new java.util.WeakHashMap<>();

    public RopeKnotEntity(EntityType<? extends RopeKnotEntity> type, Level level) {
        super(type, level);
    }

    public RopeKnotEntity(Level level, BlockPos post) {
        super(ModRope.ROPE_KNOT.get(), level, post);
        this.setPos(post.getX(), post.getY(), post.getZ());
        this.endsStale = false; // a post with no knot has no line ending at it
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_SPANS, List.of());
        entityData.define(DATA_CHAINS, List.of());
        entityData.define(DATA_HOLDER, -1);
        entityData.define(DATA_HELD_CHAIN, false);
        entityData.define(DATA_ENDS, (byte) 0);
    }

    @Override
    protected void recalculateBoundingBox() {
        // The game calls this from the entity's constructor too, with pos still the world's origin: read the post only
        // where it is loaded, or that chunk was loaded (made, in a dimension never visited) for each knot. Read again,
        // as it should be, once the knot's own position is set. Nor off the server's own thread: a structure with a knot
        // in it set down as a chunk is made reads it from the world-making thread, which waited for ever on the chunk
        // being made (another mod's town of templates hung its world so). Tied as on a fence post until then; it goes
        // to its post within a second ({@link #followPost}).
        boolean here = !(this.level() instanceof ServerLevel server) || server.getServer().isSameThread();
        Vec3 tie = here && this.level().isLoaded(this.pos) ? RopeSpans.tie(this.level(), this.pos) : RopeSpans.tie(this.pos);
        this.setPosRaw(tie.x, tie.y, tie.z);
        this.setBoundingBox(new AABB(tie.x - HALF, tie.y - HALF, tie.z - HALF, tie.x + HALF, tie.y + HALF, tie.z + HALF));
    }

    /**
     * Lit as the place it is tied is, not a third of a block over it (an entity's eyes): under a ceiling - a hook there,
     * an end rod down from it - that point was in the ceiling, and the knot was drawn black.
     */
    @Override
    public Vec3 getLightProbePosition(float partialTick) {
        return this.position();
    }

    /** The posts this knot's spans run to. */
    public List<BlockPos> spans() {
        return this.entityData.get(DATA_SPANS);
    }

    /** The posts this knot's chain spans run to. */
    public List<BlockPos> chains() {
        return this.entityData.get(DATA_CHAINS);
    }

    public boolean ties(BlockPos to) {
        return this.spans().contains(to);
    }

    /** What the span from here to {@code to} is made of. */
    public SpanMaterial material(BlockPos to) {
        return this.chains().contains(to) ? SpanMaterial.CHAIN : SpanMaterial.ROPE;
    }

    void addSpan(BlockPos to, SpanMaterial material) {
        List<BlockPos> spans = new ArrayList<>(this.spans());
        spans.add(to.immutable());
        this.entityData.set(DATA_SPANS, List.copyOf(spans));
        if (material == SpanMaterial.CHAIN) {
            List<BlockPos> chains = new ArrayList<>(this.chains());
            chains.add(to.immutable());
            this.entityData.set(DATA_CHAINS, List.copyOf(chains));
        }
    }

    void removeSpan(BlockPos to) {
        List<BlockPos> spans = new ArrayList<>(this.spans());
        spans.remove(to);
        this.entityData.set(DATA_SPANS, List.copyOf(spans));
        if (this.chains().contains(to)) {
            List<BlockPos> chains = new ArrayList<>(this.chains());
            chains.remove(to);
            this.entityData.set(DATA_CHAINS, List.copyOf(chains));
        }
        this.recheck = true; // that may have been the last thing tied here
    }

    /** A span of {@code material} was tied off here, from another post. */
    void endsHere(SpanMaterial material) {
        this.entityData.set(DATA_ENDS, (byte) (this.entityData.get(DATA_ENDS) | material.bit));
    }

    /**
     * Whether a line of {@code material} is wound round this post: a span of its own, the line its holder is paying out,
     * or a span from another post ending here.
     */
    public boolean wound(SpanMaterial material) {
        if ((this.entityData.get(DATA_ENDS) & material.bit) != 0) {
            return true;
        }
        if (this.holder() != null && this.heldMaterial() == material) {
            return true;
        }
        List<BlockPos> chains = this.chains();
        return material == SpanMaterial.CHAIN ? !chains.isEmpty() : this.spans().size() > chains.size();
    }

    /** The player paying out rope from this knot, or null. */
    public @Nullable Entity holder() {
        int id = this.entityData.get(DATA_HOLDER);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** What its holder is paying out. */
    public SpanMaterial heldMaterial() {
        return this.entityData.get(DATA_HELD_CHAIN) ? SpanMaterial.CHAIN : SpanMaterial.ROPE;
    }

    void holdBy(Player player, SpanMaterial material) {
        this.entityData.set(DATA_HOLDER, player.getId());
        this.entityData.set(DATA_HELD_CHAIN, material == SpanMaterial.CHAIN);
    }

    void letGo() {
        this.entityData.set(DATA_HOLDER, -1);
        this.entityData.set(DATA_HELD_CHAIN, false);
        this.recheck = true; // a knot let go of with nothing tied to it goes
    }

    @Override
    public boolean survives() {
        return RopeSpans.isPost(this.level().getBlockState(this.pos));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount % 20 == 0) {
            this.followPost();
        }
        if (this.level().isClientSide()) {
            this.feelChains();
            return;
        }
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        if (this.tickCount % 10 == 0) {
            this.checkHolder();
        }
        if (this.tickCount % SIGNAL_EVERY == 0 && level.isLoaded(this.pos)) {
            this.checkSignal(level);
        }
        // at once when a rope came off it or was let go, so the knot does not stay on its post a second or more after
        // its rope is gone (the user, 2026-10-07); the rest of the time once a second
        if (this.recheck || this.tickCount % 20 == 0) {
            this.check(level);
        }
    }

    /**
     * Its post turned (a Wrench on a rod, a hook, a bracket): the rope is drawn from where it is tied now, and the knot
     * was left where it was, in the air (r7 review). It goes round with the post - on the client too, which works its
     * knots out from the posts it has, as when they come into sight.
     */
    private void followPost() {
        if (!this.isRemoved() && this.level().isLoaded(this.pos) && this.survives()
                && this.position().distanceToSqr(RopeSpans.tie(this.level(), this.pos)) > 1.0E-6) {
            this.recalculateBoundingBox();
        }
    }

    /**
     * Its place on the network is its post's, as the packet that brings it sends it ({@link #getAddEntityPacket}), not
     * its tie, so a knot that goes round with its post is never sent a move away from it: the client would take the move
     * from the post's corner, and might have set the knot on the block beside it. (Vanilla's paintings do the same.)
     */
    @Override
    public Vec3 trackingPosition() {
        return this.pos != null ? Vec3.atLowerCornerOf(this.pos) : super.trackingPosition();
    }

    /**
     * Looks whether its post takes a redstone signal ({@link RopeSignals}): as one comes, the lights on its lines go on;
     * as it goes, off - only as it changes, so a hand switches them between. A fence takes no word of a signal beside it,
     * so the knot looks, a few times a second: a handful of reads of the blocks round its post. First looked at after
     * loading with nothing saved (a knot tied before, or new), the signal there is taken as it stands, nothing switched.
     */
    private void checkSignal(ServerLevel level) {
        boolean now = RopeSignals.powered(level, this.pos);
        if (!this.signalKnown) {
            this.signalKnown = true;
            this.powered = now;
            return;
        }
        if (now != this.powered) {
            this.powered = now;
            RopeSignals.switchLines(level, this.pos, now);
        }
    }

    /** Whether its post took a signal when it last looked. Public for tests. */
    public boolean signalled() {
        return this.powered;
    }

    /** The player lets go by going too far, or putting the rope (or chain) away. */
    private void checkHolder() {
        Entity holder = this.holder();
        var item = this.heldMaterial().item();
        if (holder != null && (!(holder instanceof Player player) || !player.isAlive() || player.level() != this.level()
                || player.distanceToSqr(this.position()) > (RopeSpans.MAX_LENGTH + 4) * (RopeSpans.MAX_LENGTH + 4)
                || !player.getMainHandItem().is(item) && !player.getOffhandItem().is(item))) {
            this.letGo();
        }
    }

    /** Its post gone, it comes undone; a span whose far post is gone drops; a knot nothing is tied to goes. */
    private void check(ServerLevel level) {
        if (!this.survives()) {
            this.kill(level);
            this.dropItem(level, null);
            return;
        }
        for (BlockPos to : this.spans()) {
            if (level.isLoaded(to) && !RopeSpans.isPost(level.getBlockState(to))) {
                SpanMaterial material = this.material(to);
                this.removeSpan(to);
                Vec3 middle = RopeSpans.at(level, this.pos, to, 0.5);
                dropLine(level, middle, material, RopeSpans.ropesFor(this.pos, to), null);
                level.playSound(null, BlockPos.containing(middle), material.snapped(), this.getSoundSource(), 1.0F, 1.0F);
            }
        }
        boolean due = this.recheck || (this.tickCount / 20 + this.getId()) % 5 == 0;
        boolean waiting = false;
        if (due && this.spans().isEmpty() && this.holder() == null) {
            if (!entitiesLoadedRound(level, this.pos)) {
                // the knots round about still loading (they come in a few ticks after the chunks' blocks): looked at
                // again next tick, not left for the next look round five seconds on - the GameTests that cut a rope as
                // their area loaded saw the far knot linger, now and then
                waiting = true;
            } else {
                int ends = endsAt(level, this.pos);
                if (ends == 0) {
                    this.discard();
                } else {
                    this.entityData.set(DATA_ENDS, (byte) ends);
                    this.endsStale = false;
                }
            }
        } else if (this.endsStale && entitiesLoadedRound(level, this.pos)) {
            // what else is tied here, for the look of the knot: once as it loads, and again when a line ending here was
            // cut - not every round
            this.entityData.set(DATA_ENDS, (byte) endsAt(level, this.pos));
            this.endsStale = false;
        }
        this.recheck = waiting;
    }

    /**
     * In stacks no bigger than a stack of Thin Rope (or Thin Chain): a hub's hundred ropes in one item were lost if its
     * chunk was saved before anyone picked it up (an item of more than 99 is not saved). With entity drops off there is
     * none, as a lead that breaks gives none.
     */
    private static void dropLine(ServerLevel level, Vec3 at, SpanMaterial material, int count, @Nullable UUID bolt) {
        if (!level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            return;
        }
        int max = material.item().getDefaultMaxStackSize();
        while (count > 0) {
            int n = Math.min(max, count);
            var item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(material.item(), n));
            item.setDefaultPickUpDelay();
            StruckLoose.fellFrom(item, bolt);
            level.addFreshEntity(item);
            count -= n;
        }
    }

    /**
     * Tied to a lightning rod, it comes undone when lightning strikes, and the rope falls - spared by that bolt
     * ({@link StruckLoose}). Tied anywhere else it is let be, as vanilla's lead knots are.
     */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        if (this.isRemoved() || !level.getBlockState(this.pos).is(BlockTags.LIGHTNING_RODS)) {
            return;
        }
        this.struckBy = bolt.getUUID();
        try {
            this.kill(level);
            this.dropItem(level, null);
        } finally {
            this.struckBy = null;
        }
    }

    /** Cut: every line tied here comes away, this knot's own and those that end here, and drops at the post. */
    @Override
    public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
        int ropes = 0;
        int chains = 0;
        for (BlockPos to : this.spans()) {
            int n = RopeSpans.ropesFor(this.pos, to);
            if (this.material(to) == SpanMaterial.CHAIN) {
                chains += n;
            } else {
                ropes += n;
            }
            RopeKnotEntity far = at(level, to);
            if (far != null) {
                far.recheck = true; // its rope is gone: it may be the last that held it
                far.endsStale = true;
            }
        }
        this.entityData.set(DATA_SPANS, List.of());
        this.entityData.set(DATA_CHAINS, List.of());
        for (RopeKnotEntity other : near(level, this.pos)) {
            if (other != this && other.ties(this.pos)) {
                int n = RopeSpans.ropesFor(other.getPos(), this.pos);
                if (other.material(this.pos) == SpanMaterial.CHAIN) {
                    chains += n;
                } else {
                    ropes += n;
                }
                other.removeSpan(this.pos);
            }
        }
        if (chains > 0) {
            this.playSound(SpanMaterial.CHAIN.untied(), 1.0F, 1.0F);
        }
        if (ropes > 0) {
            this.playSound(SpanMaterial.ROPE.untied(), 1.0F, 1.0F);
        }
        if (ropes == 0 && chains == 0) {
            this.playSound(this.heldMaterial().untied(), 1.0F, 1.0F); // only the line being paid out: its own sound
        }
        if (!(causedBy instanceof Player player && player.hasInfiniteMaterials())) {
            dropLine(level, this.position(), SpanMaterial.ROPE, ropes, this.struckBy);
            dropLine(level, this.position(), SpanMaterial.CHAIN, chains, this.struckBy);
        }
    }

    /**
     * A hit cuts it only where a player may break things: not in adventure mode, where tying a rope and taking down what
     * hangs on one are refused too.
     */
    @Override
    public boolean skipAttackInteraction(Entity source) {
        if (source instanceof Player player && !player.mayBuild()) {
            return true;
        }
        return super.skipAttackInteraction(source);
    }

    /** Nor what such a player shoots or throws: an arrow, a snowball, a trident cut it as a hit would. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player player && !player.mayBuild()) {
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack stack = player.getItemInHand(hand);
        return SpanMaterial.of(stack) != null ? RopeSpans.tie(this.level(), player, this.pos, stack) : InteractionResult.PASS;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    /** Thin Chain where only chain is tied here, else Thin Rope. */
    @Override
    public ItemStack getPickResult() {
        boolean chain = this.wound(SpanMaterial.CHAIN) && !this.wound(SpanMaterial.ROPE);
        return new ItemStack((chain ? SpanMaterial.CHAIN : SpanMaterial.ROPE).item());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, 0, this.getPos());
    }

    /**
     * Its spans, and which are chain - written only where some are, so a knot of rope alone is saved just as it was
     * before there were chains, and one saved then (no "chains") loads as all rope. Each is written twice: where it
     * runs, as before (what an older Nookworks reads), and as a step from where the knot is to the middle of the post
     * it runs to ("span_offsets", "chain_offsets"), which is what a structure copying the knot keeps right: a template
     * sets the copy where the copy goes, and leaves every other place in it as it was - a rope in a copied build ran
     * back to the first build's posts (r15 fix check 4, S3). From the knot's place, not its post's: a template sets the
     * place down anew in every version, its post only in some (26.2 left the first one's).
     */
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        Vec3 here = this.position();
        output.store("spans", BlockPos.CODEC.listOf(), this.spans());
        output.store("span_offsets", Vec3.CODEC.listOf(), this.spans().stream().map(to -> Vec3.atCenterOf(to).subtract(here)).toList());
        if (!this.chains().isEmpty()) {
            output.store("chains", BlockPos.CODEC.listOf(), this.chains());
            output.store("chain_offsets", Vec3.CODEC.listOf(), this.chains().stream().map(to -> Vec3.atCenterOf(to).subtract(here)).toList());
        }
        if (this.signalKnown) {
            output.putInt("signal", this.powered ? 1 : 0);
        }
    }

    /**
     * Read from its steps from where it was saved ("Pos" as written - as a template sets the knot down, the copy's place;
     * read from the save, as the knot is moved onto its post's tie before this), else from where they ran (saved before
     * there were steps: as it was).
     */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int signal = input.getIntOr("signal", -1); // what its post's signal was, so one changed while away still switches
        this.signalKnown = signal >= 0;
        this.powered = signal == 1;
        Vec3 at = input.read("Pos", Vec3.CODEC).orElse(null);
        Optional<List<Vec3>> spanSteps = at == null ? Optional.empty() : input.read("span_offsets", Vec3.CODEC.listOf());
        if (spanSteps.isPresent()) {
            this.steps = new Steps(at, List.copyOf(spanSteps.get()), List.copyOf(input.read("chain_offsets", Vec3.CODEC.listOf()).orElse(List.of())));
            this.laySpans();
        } else {
            this.steps = null;
            List<BlockPos> spans = List.copyOf(input.read("spans", BlockPos.CODEC.listOf()).orElse(List.of()));
            this.entityData.set(DATA_SPANS, spans);
            this.entityData.set(DATA_CHAINS, input.read("chains", BlockPos.CODEC.listOf()).orElse(List.<BlockPos>of())
                    .stream().filter(spans::contains).toList());
        }
        this.endsStale = true;
        if (this.pos != null) {
            this.recalculateBoundingBox();
        }
    }

    /** Where it was read from, and the steps from there to the middle of each post its spans and its chains run to. */
    private record Steps(Vec3 from, List<Vec3> spans, List<Vec3> chains) {
        Steps change(UnaryOperator<Vec3> change) {
            return new Steps(this.from, this.spans.stream().map(change).toList(), this.chains.stream().map(change).toList());
        }

        List<BlockPos> posts(List<Vec3> steps) {
            return steps.stream().map(step -> BlockPos.containing(this.from.add(step))).toList();
        }
    }

    /** Its spans, and which are chain, where its steps run from where it was read. */
    private void laySpans() {
        List<BlockPos> spans = this.steps.posts(this.steps.spans());
        this.entityData.set(DATA_SPANS, spans);
        this.entityData.set(DATA_CHAINS, this.steps.posts(this.steps.chains()).stream().filter(spans::contains).toList());
    }

    /**
     * A structure setting it down turns it: its spans turn with it about where it is, as the posts they run to were
     * turned (called on the world-making thread too, before the knot is in any level: nothing is read but its own
     * steps). Spans read as they were from an older save are left as they are, as there is no knowing where they were
     * from.
     */
    @Override
    public float rotate(Rotation rotation) {
        this.turned = rotation;
        if (this.steps != null && rotation != Rotation.NONE) {
            this.steps = this.steps.change(step -> StructureTurns.rotate(step, rotation));
            this.laySpans();
        }
        return super.rotate(rotation);
    }

    /** And mirrors it, after turning it: the same about where it is. */
    @Override
    public float mirror(Mirror mirror) {
        Mirror after = StructureTurns.afterTurn(mirror, this.turned);
        if (this.steps != null && after != Mirror.NONE) {
            this.steps = this.steps.change(step -> StructureTurns.mirror(step, after));
            this.laySpans();
        }
        return super.mirror(mirror);
    }

    // ---- a chain swinging, on a client ----

    /**
     * Looks whether anything went through one of its chain spans this tick - someone walking into it, a mob, a cart, an
     * arrow - and sets that span swinging, with a rattle once a touch; and swings those swinging on, a tick.
     */
    private void feelChains() {
        List<BlockPos> chains = this.chains();
        if (chains.isEmpty() && this.sways.isEmpty()) {
            return;
        }
        Level level = this.level();
        int now = this.tickCount;
        for (BlockPos to : chains) {
            Vec3 a = RopeSpans.tie(level, this.pos);
            Vec3 b = RopeSpans.tie(level, to);
            AABB around = new AABB(a, b).expandTowards(0, -RopeSpans.sag(a, b), 0).inflate(0.5);
            // an arrow goes up to three blocks a tick: one that crosses the chain between two ticks is felt too
            AABB reach = around.inflate(3.0);
            for (Entity entity : level.getEntities(this, reach,
                    e -> ChainSway.moves(e) && (e instanceof Projectile || e.getBoundingBox().intersects(around)))) {
                if (!ChainSway.touches(a, b, entity)) {
                    continue;
                }
                ChainSway sway = this.sways.computeIfAbsent(to.immutable(), k -> new ChainSway());
                boolean shot = entity instanceof Projectile;
                double push = ChainSway.push(a, b, ChainSway.motion(entity));
                if (sway.feel(entity.getId(), now, push, shot ? ChainSway.PROJECTILE_KICK : ChainSway.KICK) == ChainSway.Felt.RATTLED) {
                    Vec3 at = nearest(a, b, entity.getBoundingBox().getCenter());
                    level.playLocalSound(at.x, at.y, at.z, ModSounds.THIN_CHAIN_RATTLE.get(), SoundSource.BLOCKS, shot ? 0.6F : 0.9F,
                            0.9F + this.random.nextFloat() * 0.25F, false);
                }
            }
        }
        for (Iterator<Map.Entry<BlockPos, ChainSway>> it = this.sways.entrySet().iterator(); it.hasNext();) {
            Map.Entry<BlockPos, ChainSway> entry = it.next();
            entry.getValue().tick(now);
            if (entry.getValue().settled() || !chains.contains(entry.getKey())) {
                it.remove();
            }
        }
        if (this.sways.isEmpty()) {
            SWINGING.remove(this);
        } else {
            SWINGING.put(this, Boolean.TRUE);
        }
    }

    /** The point of the chain from {@code a} to {@code b} nearest {@code point}, looked for a sixteenth of the way apart. */
    private static Vec3 nearest(Vec3 a, Vec3 b, Vec3 point) {
        Vec3 best = a;
        for (int i = 0; i <= 16; i++) {
            Vec3 p = RopeSpans.at(a, b, i / 16.0);
            if (p.distanceToSqr(point) < best.distanceToSqr(point)) {
                best = p;
            }
        }
        return best;
    }

    /** How far round from hanging straight the span to {@code to} is swung, in radians (0 hanging still). */
    public float swing(BlockPos to, float partialTick) {
        ChainSway sway = this.sways.get(to);
        return sway == null ? 0.0F : sway.angle(partialTick);
    }

    /**
     * Where something hanging at {@code point} on the span from {@code from} to {@code to} is drawn now, if that span is
     * a chain swinging on this client: the way the chain there has moved, and the angle it swings at - or null. Only its
     * own span: a chain running close by (out of the same post, say) does not swing what hangs on another line.
     */
    public static @Nullable Swung swungAt(Level level, @Nullable BlockPos from, @Nullable BlockPos to, Vec3 point, float partialTick) {
        if (SWINGING.isEmpty() || from == null || to == null) {
            return null;
        }
        for (RopeKnotEntity knot : List.copyOf(SWINGING.keySet())) {
            if (knot.isRemoved() || knot.level() != level) {
                continue;
            }
            BlockPos other = knot.pos.equals(from) ? to : knot.pos.equals(to) ? from : null;
            ChainSway sway = other == null ? null : knot.sways.get(other);
            if (sway == null) {
                continue;
            }
            Vec3 a = RopeSpans.tie(level, knot.pos);
            Vec3 b = RopeSpans.tie(level, other);
            Vec3 chord = b.subtract(a);
            double flat = chord.x * chord.x + chord.z * chord.z;
            double t = flat > 1.0E-6 ? ((point.x - a.x) * chord.x + (point.z - a.z) * chord.z) / flat
                    : chord.lengthSqr() > 1.0E-9 ? point.subtract(a).dot(chord) / chord.lengthSqr() : 0;
            t = Math.max(0, Math.min(1, t));
            float angle = sway.angle(partialTick);
            return new Swung(ChainSway.swung(a, b, t, angle).subtract(RopeSpans.at(a, b, t)), angle, chord);
        }
        return null;
    }

    /** A thing on a swinging chain: how far it is moved, the chain's angle there, and the way the chain runs. */
    public record Swung(Vec3 shift, float angle, Vec3 along) {}

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        SWINGING.remove(this);
    }

    /** Knots near enough to {@code post} for a span to reach it. */
    static List<RopeKnotEntity> near(Level level, BlockPos post) {
        return level.getEntitiesOfClass(RopeKnotEntity.class, new AABB(post).inflate(RopeSpans.MAX_LENGTH + 1));
    }

    /**
     * Whether the entities of every chunk a span to {@code post} could come from are loaded: a chunk's blocks come in
     * before its entities, so a knot looked for too soon is not there yet - and an end knot that took that for "nothing
     * is tied here" went for good.
     */
    public static boolean entitiesLoadedRound(ServerLevel level, BlockPos post) {
        int reach = (int) Math.ceil(RopeSpans.MAX_LENGTH) + 1;
        for (int x = (post.getX() - reach) >> 4; x <= (post.getX() + reach) >> 4; x++) {
            for (int z = (post.getZ() - reach) >> 4; z <= (post.getZ() + reach) >> 4; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.pack(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** What the spans of other knots ending at {@code post} are made of, as {@link SpanMaterial#bit}s. */
    private static int endsAt(Level level, BlockPos post) {
        int ends = 0;
        for (RopeKnotEntity knot : near(level, post)) {
            if (!knot.isRemoved() && !knot.getPos().equals(post) && knot.ties(post)) {
                ends |= knot.material(post).bit;
            }
        }
        return ends;
    }

    /** The knot on {@code post}, or null. */
    public static @Nullable RopeKnotEntity at(Level level, BlockPos post) {
        for (RopeKnotEntity knot : level.getEntitiesOfClass(RopeKnotEntity.class, new AABB(post).inflate(0.5))) {
            if (knot.getPos().equals(post) && !knot.isRemoved()) {
                return knot;
            }
        }
        return null;
    }

    static RopeKnotEntity getOrCreate(ServerLevel level, BlockPos post) {
        RopeKnotEntity knot = at(level, post);
        if (knot == null) {
            knot = new RopeKnotEntity(level, post.immutable());
            level.addFreshEntity(knot);
        }
        return knot;
    }

    /** The knot {@code player} is paying out rope from, or null. */
    public static @Nullable RopeKnotEntity heldBy(Level level, Player player) {
        for (RopeKnotEntity knot : level.getEntitiesOfClass(RopeKnotEntity.class, player.getBoundingBox().inflate(RopeSpans.MAX_LENGTH + 8))) {
            if (knot.holder() == player) {
                return knot;
            }
        }
        return null;
    }
}
