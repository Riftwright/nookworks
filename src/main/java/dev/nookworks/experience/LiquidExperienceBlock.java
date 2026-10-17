package dev.nookworks.experience;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Placed Liquid Experience. Raw XP is not something to swim in: anything living in it burns a little every
 * second.
 * <p>
 * A mob killed this way counts as killed by the nearest player, because the damage names that player as the
 * entity behind it - which is exactly what vanilla looks at. So the mob drops what it only drops for a player
 * (wither skeleton skulls, blaze rods from a blaze farm) and gives up its XP, and no special-casing of loot
 * tables is needed. With nobody in range it is an ordinary environmental death.
 */
public class LiquidExperienceBlock extends LiquidBlock {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, Nookworks.id("liquid_experience"));
    public static final float DAMAGE = 2.0F;
    public static final int DAMAGE_INTERVAL = 20;
    /** How far away a player may be and still be credited with the kill. */
    public static final double CREDIT_RANGE = 32.0;

    public LiquidExperienceBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
            boolean isPrecise) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living) {
            burn(server, living);
        }
    }

    /** The damage a soak in raw experience does. Public for tests. */
    public static void burn(ServerLevel level, LivingEntity living) {
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        if (living.tickCount % DAMAGE_INTERVAL != 0) {
            return;
        }
        living.hurtServer(level, source(level, living), DAMAGE);
    }

    /** Names the nearest player as the cause, so a kill here is credited to them. */
    public static DamageSource source(ServerLevel level, LivingEntity victim) {
        var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE);
        @Nullable ServerPlayer credited = nearestPlayer(level, victim);
        return new DamageSource(type, null, credited);
    }

    /**
     * The nearest other player, for what a farm's pool is for: monsters and livestock. What no farm drowns is burnt by
     * the liquid itself, credited to no one:
     * <ul>
     * <li>a player - naming the victim would be refused as PvP (no damage at all on a no-PvP server), and naming a
     * bystander would be a PvP kill;
     * <li>a villager or a trader - credited, its death made the nearest player its killer to the whole village, the
     * golems after them;
     * <li>a pet, or a mob someone has named - credited, its death message named whoever happened to be near.
     * </ul>
     */
    private static @Nullable ServerPlayer nearestPlayer(ServerLevel level, LivingEntity victim) {
        if (victim instanceof Player || victim instanceof AbstractVillager || victim.hasCustomName()
                || victim instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
            return null;
        }
        Player player = level.getNearestPlayer(victim.getX(), victim.getY(), victim.getZ(), CREDIT_RANGE,
                p -> p != victim && !p.isSpectator());
        return player instanceof ServerPlayer server ? server : null;
    }
}
