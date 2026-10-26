package dev.nookworks.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.block.LaserBlock;
import dev.nookworks.feature.ChainBreaking;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.feature.FastLeafDecay;
import dev.nookworks.feature.Fireproofing;
import dev.nookworks.feature.Permission;
import dev.nookworks.feature.PlacedLogs;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Laser: while it has power and its redstone mode allows (by default: no signal), fires straight ahead up to
 * {@link #RANGE} blocks, through
 * air and anything without a collision box (grass, saplings, crops). What the beam hits:
 * <ul>
 * <li>a log of a natural tree: the whole tree is felled (drops on the ground) and its leaves decay fast - the stem of
 * a huge crimson or warped fungus too, cut as a log is (stems do not burn), its wart blocks left as they never decay
 * ({@link ChainBreaking#looksLikeTree});</li>
 * <li>any other flammable block: it may catch fire, unless it has Fireproof Coating;</li>
 * <li>living things anywhere along the beam: damage ({@code nookworks:laser}).</li>
 * </ul>
 * It fires only while there is something in the beam's way to cut, burn or hurt; with nothing there it stands by on
 * {@link #FE_STANDBY}, the beam off, and fires the moment something comes into it. The beam length is mirrored in the
 * block state so the client can draw it.
 */
public class LaserBlockEntity extends BlockEntity implements MachineInfo, DrawRights.Owned {
    public static final int RANGE = 8;
    /** How far down a trunk the laser will look for the bottom of the tree. */
    public static final int MAX_TRUNK_SCAN = 64;
    public static final int CAPACITY = 120_000;
    /**
     * Holding the beam. Ten generators' worth, and an order above anything else in the mod: this is a beam
     * that cuts through a tree trunk, and it should cost like one to run.
     */
    public static final int FE_PER_TICK = 400;
    /**
     * Standing by, watching the way ahead with the beam off. A trickle: at 10 FE/t a full buffer lasted ten minutes, less
     * than a birch sapling takes to grow on average, and a log farm started on a few logs' power starved before its first
     * tree (r8 automation test). Set to run with a signal and pulsed by a Nested Timer it draws nothing between pulses.
     */
    public static final int FE_STANDBY = 2;
    /** On top of that, the surge that actually takes the tree down. */
    public static final int FE_PER_FELL = 4_000;
    public static final int DAMAGE_INTERVAL = 10;
    public static final float DAMAGE = 4.0F;
    private static final int FIRE_CHANCE = 30; // percent, checked once a second
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, Nookworks.id("laser"));
    /** The glow where the beam lands; the beam itself is drawn as geometry, not particles. */
    public static final DustParticleOptions SPARK_PARTICLE = new DustParticleOptions(0xFFFFB199, 1.4F);

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, 4_000, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            MachineBlockEntity.savePower(LaserBlockEntity.this);
        }
    };
    private int age;
    /** The log the beam last judged, and what it made of it: judged again once a second, or at once for a new log. */
    private @Nullable BlockPos judged;
    private Fell verdict = Fell.NOT_A_TREE;
    /** Who placed it: the laser breaks and burns as them, so it works inside their claim and nobody else's. */
    private @Nullable UUID owner;
    /** What its owner may take power from across a claim's edge, as last asked ({@link DrawRights}). */
    private final DrawRights drawRights = new DrawRights();

    /** What a log in the beam turned out to be. */
    private enum Fell {
        FELLED,
        /** A fallen log, a bare trunk, a wall: it burns like any other wood. */
        NOT_A_TREE,
        /** A tree, but the surge to fell it is not stored yet: it waits, the beam off, and is not set alight meanwhile. */
        WAITING,
        /** A tree whose every log a claim keeps from its owner: left alone, beam and fire. */
        REFUSED
    }
    /** Ticks the beam stays drawn after it last fired: on power that comes in fits it does not flicker. */
    public static final int BEAM_HOLD = 10;
    private int lastFired = Integer.MIN_VALUE / 2;
    private int lastLength;
    private RedstoneMode redstone = RedstoneMode.DEFAULT;
    private MachineStatus status = MachineStatus.IDLE;

    public LaserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER.get(), pos, state);
    }

    public SimpleEnergyHandler energy() {
        return this.energy;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserBlockEntity laser) {
        if (level instanceof ServerLevel server) {
            laser.tick(server, pos, state);
        }
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        this.age++;
        boolean allowed = this.redstone.allows(level.hasNeighborSignal(pos));
        Direction facing = state.getValue(LaserBlock.FACING);
        int length = 0;
        if (!allowed) {
            this.status = MachineStatus.REDSTONE;
        } else {
            // where the beam would end, and whether there is anything along it to work on
            BlockPos hit = null;
            int reach = RANGE;
            for (int d = 1; d <= RANGE; d++) {
                BlockPos p = pos.relative(facing, d);
                if (!level.isLoaded(p)) {
                    reach = d - 1;
                    break;
                }
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                    hit = p;
                    reach = d; // the beam reaches the face of the hit block
                    break;
                }
            }
            List<LivingEntity> targets = this.targets(level, pos, facing, reach, hit);
            boolean work = !targets.isEmpty() || hit != null && this.worthFiringAt(level, hit, facing);
            int cost = work ? MachineTuning.energy(FE_PER_TICK) : MachineTuning.energy(FE_STANDBY);
            if (this.energy.getAmountAsLong() < cost) {
                this.status = MachineStatus.NO_POWER;
            } else {
                this.energy.set((int) (this.energy.getAmountAsLong() - cost));
                this.status = work ? MachineStatus.RUNNING
                        : hit != null && hit.equals(this.judged) && this.verdict == Fell.WAITING ? MachineStatus.CHARGING
                        : MachineStatus.STANDBY;
                if (work) {
                    length = reach;
                    this.lastFired = this.age;
                    this.lastLength = reach;
                    if (this.age % DAMAGE_INTERVAL == 0) {
                        this.hurt(level, targets);
                    }
                    if (hit != null) {
                        this.hitBlock(level, hit, facing);
                    }
                }
            }
        }
        if (length == 0 && this.status == MachineStatus.NO_POWER && this.age - this.lastFired < BEAM_HOLD) {
            length = this.lastLength; // a tick short of power between paid ones: drawn on, not flickering
        }
        if (state.getValue(LaserBlock.BEAM) != length) {
            level.setBlock(pos, state.setValue(LaserBlock.BEAM, length), 2);
        }
    }

    /**
     * What the beam would hurt: the living things along it, but a creative or spectating player - up to the near face of
     * the block that stops it, as it is drawn: not whoever stands behind a pane, bars or a fence in the same block. Not
     * what the beam cannot hurt either - an armour stand, a mob made invulnerable: one in the beam kept it firing at full
     * draw, and on a generator's power the surge to fell a tree never built (r7 review).
     */
    private List<LivingEntity> targets(ServerLevel level, BlockPos pos, Direction facing, int length, @Nullable BlockPos hit) {
        if (length <= 0) {
            return List.of();
        }
        Direction.Axis axis = facing.getAxis();
        Vec3 from = Vec3.atCenterOf(pos.relative(facing)).relative(facing, -0.4);
        Vec3 to;
        if (hit != null) {
            var shape = level.getBlockState(hit).getCollisionShape(level, hit);
            double face = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? shape.min(axis) : shape.max(axis);
            to = Vec3.atCenterOf(hit).with(axis, hit.get(axis) + face);
        } else {
            to = Vec3.atCenterOf(pos.relative(facing, length)).relative(facing, 0.4);
        }
        AABB beam = new AABB(from, to).inflate(axis == Direction.Axis.X ? 0 : 0.4, axis == Direction.Axis.Y ? 0 : 0.4,
                axis == Direction.Axis.Z ? 0 : 0.4);
        DamageSource source = source(level);
        return level.getEntitiesOfClass(LivingEntity.class, beam, target -> target.isAlive() && !(target instanceof ArmorStand)
                && !target.isInvulnerableTo(level, source)
                && !(target instanceof Player player && (player.isCreative() || player.isSpectator())));
    }

    private static DamageSource source(ServerLevel level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE));
    }

    /**
     * Whether the block the beam stops at gives it anything to do: a log (a tree to fell, or wood to burn) or anything
     * else that burns and has no Fireproof Coating. Stone, glass, a coated wall: the beam would only warm it.
     */
    private boolean worthFiringAt(ServerLevel level, BlockPos hit, Direction facing) {
        BlockState state = level.getBlockState(hit);
        boolean burns = state.isFlammable(level, hit, facing.getOpposite()) && !Fireproofing.isCoated(level, hit);
        if (state.is(BlockTags.LOGS)) {
            // A new log is fired at to be judged, and each one is looked at again now and then. Judged no tree, it is
            // worth the beam only if it burns - a built wall of nether stems, or of coated logs, would otherwise draw the
            // full beam for ever, doing nothing (a huge fungus's stem is a tree, and is felled). A tree waits with the beam off until the surge to fell it is stored (on a
            // generator or two, burning 400 FE/t while it waited, it never would be), and one a claim keeps is let be.
            // (A tree waiting for its surge is not: it is judged again the moment the surge is stored.)
            if (!hit.equals(this.judged) || this.age % 100 == 0 && this.verdict != Fell.WAITING) {
                return true;
            }
            return switch (this.verdict) {
                case NOT_A_TREE -> burns;
                case WAITING -> this.energy.getAmountAsLong() >= MachineTuning.energy(FE_PER_FELL) + MachineTuning.energy(FE_PER_TICK);
                case REFUSED -> false;
                case FELLED -> true;
            };
        }
        return burns;
    }

    private void hurt(ServerLevel level, List<LivingEntity> targets) {
        DamageSource source = source(level);
        for (LivingEntity target : targets) {
            if (target.isAlive()) {
                target.hurtServer(level, source, DAMAGE);
            }
        }
    }

    private void hitBlock(ServerLevel level, BlockPos hit, Direction facing) {
        BlockState state = level.getBlockState(hit);
        if (state.is(BlockTags.LOGS)) {
            // A log is judged once a second, not searched twenty times a second; a new log in the beam at once, and a
            // tree that was waiting as soon as its surge is stored - the beam is on for that one tick only, and judged
            // on the next whole second, it went off again unfelled, a flash for nothing, as often as not
            boolean surged = this.verdict == Fell.WAITING && this.energy.getAmountAsLong() >= MachineTuning.energy(FE_PER_FELL);
            if (!hit.equals(this.judged) || this.age % 20 == 0 || surged) {
                this.verdict = this.fellTree(level, hit, state);
                this.judged = this.verdict == Fell.FELLED ? null : hit.immutable();
            }
            if (this.verdict != Fell.NOT_A_TREE) {
                return; // felled, a tree waiting for its surge, or kept by a claim: it never sets the forest alight
            }
        }
        if (this.age % 20 == 0 && level.getRandom().nextInt(100) < FIRE_CHANCE
                && state.isFlammable(level, hit, facing.getOpposite()) && !Fireproofing.isCoated(level, hit)) {
            BlockPos front = hit.relative(facing.getOpposite());
            if (level.getBlockState(front).isAir() && BaseFireBlock.canBePlacedAt(level, front, facing)
                    && Permission.mayPlace(level, front, facing, Permission.actor(level, this.owner))) {
                level.setBlock(front, BaseFireBlock.getState(level, front), 11);
            }
        }
    }

    /** Fells the tree the log belongs to, from the bottom of its trunk, if it is a natural tree and the surge is stored. */
    private Fell fellTree(ServerLevel level, BlockPos hit, BlockState state) {
        if (PlacedLogs.isBuiltLog(level, hit, state.getBlock())) {
            return Fell.NOT_A_TREE; // a wall
        }
        BlockPos base = hit;
        // Bounded, and never off the loaded chunks: a built log pillar would otherwise walk the laser down it
        // every tick, loading chunks as it went.
        for (int step = 0; step < MAX_TRUNK_SCAN; step++) {
            BlockPos below = base.below();
            if (level.isOutsideBuildHeight(below) || !level.isLoaded(below) || !level.getBlockState(below).is(state.getBlock())
                    || PlacedLogs.isBuiltLog(level, below, state.getBlock())) {
                break;
            }
            base = below;
        }
        List<BlockPos> logs = ChainBreaking.collect(level, base, state.getBlock(), NookworksConfig.TIMBER_MAX_BLOCKS.getAsInt(), true);
        if (!ChainBreaking.looksLikeTree(level, base, logs)) {
            return Fell.NOT_A_TREE;
        }
        if (this.energy.getAmountAsLong() < MachineTuning.energy(FE_PER_FELL)) {
            return Fell.WAITING;
        }
        List<BlockPos> tree = new ArrayList<>(logs);
        tree.add(0, base);
        var actor = Permission.actor(level, this.owner);
        tree.removeIf(log -> !Permission.mayBreak(level, log, actor)); // a claim's edge through the tree keeps its half
        if (tree.isEmpty()) {
            return Fell.REFUSED;
        }
        this.energy.set((int) (this.energy.getAmountAsLong() - MachineTuning.energy(FE_PER_FELL)));
        FastLeafDecay.schedule(level, tree, actor, net.minecraft.world.item.ItemStack.EMPTY);
        for (BlockPos log : tree) {
            level.destroyBlock(log, true);
        }
        level.playSound(null, hit, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.6F);
        return Fell.FELLED;
    }

    @Override
    public @Nullable UUID owner() {
        return this.owner;
    }

    /** Records who placed it. */
    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    @Override
    public DrawRights drawRights() {
        return this.drawRights;
    }

    // ---- the screen ----

    @Override
    public MachineLayout layout() {
        return MachineLayout.LASER;
    }

    @Override
    public Container slots() {
        return new SimpleContainer(0);
    }

    @Override
    public int guiEnergy() {
        return (int) this.energy.getAmountAsLong();
    }

    @Override
    public int guiEnergyCapacity() {
        return CAPACITY;
    }

    /** What it draws now: the beam's full cost while it fires, a trickle standing by. */
    @Override
    public int guiEnergyRate() {
        return this.status == MachineStatus.STANDBY || this.status == MachineStatus.CHARGING ? MachineTuning.energy(FE_STANDBY)
                : MachineTuning.energy(FE_PER_TICK);
    }

    /** The beam's length, so the screen can draw it as long as it is. */
    @Override
    public int guiProgress() {
        return this.getBlockState().hasProperty(LaserBlock.BEAM) ? this.getBlockState().getValue(LaserBlock.BEAM) : 0;
    }

    @Override
    public int guiProgressMax() {
        return RANGE;
    }

    @Override
    public MachineStatus guiStatus() {
        return this.status;
    }

    @Override
    public RedstoneMode redstoneMode() {
        return this.redstone;
    }

    @Override
    public void setRedstoneMode(RedstoneMode mode) {
        this.redstone = mode;
        this.setChanged();
    }

    /** Its power goes with its item when it is broken, as a machine's does (see {@link MachineBlockEntity#keepEnergy}). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        MachineBlockEntity.keepEnergy(this.energy, components);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        MachineBlockEntity.restoreEnergy(this.energy, components);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.energy.serialize(output.child("Energy"));
        this.redstone.save(output);
        if (this.owner != null) {
            output.store("Owner", UUIDUtil.CODEC, this.owner);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        MachineBlockEntity.readEnergy(input, this.energy);
        this.redstone = RedstoneMode.load(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
    }
}
