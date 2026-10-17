package dev.nookworks.experience;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.DrawRights;
import dev.nookworks.fluid.OneTank;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.mixin.ExperienceOrbAccessor;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Absorption Pad: pulls in loose experience orbs, and drains whoever stands on it, turning both into Liquid
 * Experience which it pushes into whatever is next to it - our pipes and tanks, or another mod's. A redstone signal
 * switches it off, orbs and players alike ({@link AbsorptionPadBlock#POWERED}); what it holds still goes on out.
 * <p>
 * It holds only a little itself: it is a drain, not a reservoir, so there is somewhere for the fluid to go
 * wrong loudly (it simply stops) rather than quietly swallowing a player's levels with nothing attached.
 */
public class AbsorptionPadBlockEntity extends BlockEntity implements DrawRights.Owned {
    public static final int BUFFER = 2_000;
    public static final double ORB_RANGE = 6.0;
    /**
     * A player standing on it gives up about a level a second, whatever level it is: each drain (one every
     * {@link #INTERVAL} ticks) takes a fifth of what the level the player is at is worth. See {@link #drainRate}.
     */
    public static final int DRAINS_PER_LEVEL = 5;
    public static final int INTERVAL = 4;
    public static final int PUSH_RATE = 500;

    /**
     * Who set it down; null for one set down before owners were kept. What it gathers goes across a claim's edge only
     * where claims let it ({@link DrawRights#mayMove}).
     */
    private @Nullable UUID owner;
    /** What its owner may take from and give into across a claim's edge, as last asked. */
    private final DrawRights drawRights = new DrawRights();

    private final FluidStacksResourceHandler fluid =
            new OneTank(BUFFER) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.typeHolder().is(ExperienceFluids.EXPERIENCE);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount,
                TransactionContext transaction) {
            // A drain only ever gives. Letting anything fill it would let the pipe beside it hand the fluid
            // straight back, and the two would pass it to and fro instead of it reaching a tank.
            return 0;
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            // only saved: nothing reads the pad with a comparator, and a pipe drawing from it changes it every tick (r16 S3)
            MachineBlockEntity.savePower(AbsorptionPadBlockEntity.this);
        }
    };

    /** What the pad holds, so a pipe that pulls works as well as one that is pushed to. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fluid;
    }

    public AbsorptionPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ABSORPTION_PAD.get(), pos, state);
    }

    public int stored() {
        return this.fluid.getAmountAsInt(0);
    }

    private void add(int millibuckets) {
        if (millibuckets <= 0) {
            return;
        }
        this.fluid.set(0, FluidResource.of(ExperienceFluids.SOURCE.get()), Math.min(BUFFER, this.stored() + millibuckets));
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbsorptionPadBlockEntity pad) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        pad.push(server, pos);
        if (server.getGameTime() % INTERVAL != 0 || state.getValue(AbsorptionPadBlock.POWERED)) {
            return;
        }
        pad.swallowOrbs(server, pos);
        pad.drainPlayers(server, pos);
        pad.push(server, pos);
    }

    /** Sucks in nearby orbs. They are pulled towards the pad first, so it reads as a vacuum rather than a trap. */
    private void swallowOrbs(ServerLevel level, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        List<ExperienceOrb> orbs = level.getEntities(EntityTypes.EXPERIENCE_ORB,
                new AABB(pos).inflate(ORB_RANGE), orb -> orb.isAlive());
        for (ExperienceOrb orb : orbs) {
            if (this.space() < ExperienceFluids.MB_PER_POINT) {
                return;
            }
            Vec3 toPad = centre.subtract(orb.position());
            if (toPad.lengthSqr() < 0.8) {
                this.swallow(orb);
            } else {
                orb.setDeltaMovement(orb.getDeltaMovement().add(toPad.normalize().scale(0.12)));
            }
        }
    }

    /**
     * Takes as many of the orbs this entity stands for as there is room for. What does not fit stays an orb:
     * a full pad leaves experience lying there to be picked up, rather than quietly losing it.
     */
    private void swallow(ExperienceOrb orb) {
        var merged = (ExperienceOrbAccessor) orb;
        int each = ExperienceFluids.millibuckets(orb.getValue());
        int count = merged.nookworks$getCount();
        int taken = each <= 0 ? count : Math.min(count, this.space() / each);
        if (taken <= 0) {
            // One orb worth more than the pad has room for (a dragon's): take the points that fit off it.
            int fit = ExperienceFluids.points(this.space());
            if (fit > 0 && fit < orb.getValue()) {
                if (count > 1) {
                    merged.nookworks$setCount(count - 1);
                    ExperienceOrb rest = new ExperienceOrb(orb.level(), orb.getX(), orb.getY(), orb.getZ(), orb.getValue() - fit);
                    orb.level().addFreshEntity(rest);
                } else {
                    orb.setValue(orb.getValue() - fit);
                }
                this.add(ExperienceFluids.millibuckets(fit));
            }
            return;
        }
        this.add(taken * each);
        if (taken >= count) {
            orb.discard();
        } else {
            merged.nookworks$setCount(count - taken);
        }
    }

    /** Drains so far, for the sound of every other one. Not saved. */
    private int drains;

    /** Takes experience off anyone standing on the pad. */
    private void drainPlayers(ServerLevel level, BlockPos pos) {
        // Standing on it: from the plate up, not below it (a pad in a ceiling drains nobody walking underneath), and
        // over it - partly, as one stands on a block's edge - not beside it (widened, it drank from whoever stood next to
        // it, along a wall of pads)
        AABB onTop = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        for (Player player : level.getEntitiesOfClass(Player.class, onTop)) {
            if (player.isSpectator() || !player.isAlive() || this.space() < ExperienceFluids.MB_PER_POINT) {
                continue; // a dead player's experience is vanilla's to drop, not the pad's to drink
            }
            long before = heldPoints(player);
            int taken = (int) Math.min(Math.min(drainRate(player), before), ExperienceFluids.points(this.space()));
            if (taken > 0) {
                player.giveExperiencePoints(-taken);
                // Only what really left the player: another mod may change or cancel the change. In longs, uncapped: held
                // to an int, past level 21,863 both counts read the same and the pad stored nothing of what it took.
                int gone = (int) Math.max(0, Math.min(taken, before - heldPoints(player)));
                this.add(ExperienceFluids.millibuckets(gone));
                // heard as it drinks: an orb's pickup, low, every other drain (five a second ran together)
                if (gone > 0 && this.drains++ % 2 == 0) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.ABSORPTION_PAD_DRAIN.get(),
                            SoundSource.BLOCKS, 1.0F, 0.5F + level.getRandom().nextFloat() * 0.2F);
                }
            }
        }
    }

    /**
     * The points one drain takes off a player: a fifth of the level they are at, rounded up, so a level goes in five
     * drains or a little under - a second. A high level is worth far more points than a low one, and the fixed 10 points
     * a second it took before needed over two minutes from level 30 (the user, 2026-10-03: faster). Past about level 70
     * the pad passes it on no faster than its {@link #PUSH_RATE}, and a pipe no faster than it carries.
     */
    public static int drainRate(Player player) {
        return Math.ceilDiv(player.getXpNeededForNextLevel(), DRAINS_PER_LEVEL);
    }

    /**
     * Experience the player is actually holding right now, worked out from level and progress. This is not
     * {@code totalExperience}, which is a lifetime score and drifts from the bar after commands or an anvil.
     */
    public static long heldPoints(Player player) {
        long level = player.experienceLevel;
        long atLevel;
        if (level <= 16) {
            atLevel = level * level + 6 * level;
        } else if (level <= 31) {
            atLevel = (long) (2.5 * level * level - 40.5 * level + 360.0);
        } else {
            atLevel = (long) (4.5 * level * level - 162.5 * level + 2220.0);
        }
        // in longs: past level 21,863 (a command's) an int overflowed
        return atLevel + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    private int space() {
        return BUFFER - this.stored();
    }

    /**
     * Moves what it holds into any fluid handler around it, ours or anyone else's - not across a claim's edge where
     * claims keep it from going ({@link DrawRights#mayMove}).
     */
    private void push(ServerLevel level, BlockPos pos) {
        FluidResource resource = FluidResource.of(ExperienceFluids.SOURCE.get());
        for (Direction side : Direction.values()) {
            if (this.stored() <= 0) {
                return;
            }
            if (!level.isLoaded(pos.relative(side))) {
                continue;
            }
            var target = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());
            if (target == null || !DrawRights.mayMove(level, pos, this, pos.relative(side), level.getBlockEntity(pos.relative(side)))) {
                continue;
            }
            Move.transfer(this.fluid, target, resource, Math.min(PUSH_RATE, this.stored()));
        }
    }

    @Override
    public @Nullable UUID owner() {
        return this.owner;
    }

    @Override
    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    @Override
    public DrawRights drawRights() {
        return this.drawRights;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
        this.fluid.serialize(output.child("Fluid"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        input.child("Fluid").ifPresent(this.fluid::deserialize);
    }

    /** Broken, it pours what it held out as orbs. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            ExperienceFluids.spillAsOrbs(server, pos, this.stored());
        }
    }
}
