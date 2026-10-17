package dev.nookworks.mercury;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Placed mercury. Anything living that touches it is poisoned, gets dizzy (nausea) and takes a little damage
 * once a second. Undead are not bothered by poison, but still take the damage.
 */
public class MercuryBlock extends LiquidBlock {
    public static final int POISON_TICKS = 100;
    public static final int NAUSEA_TICKS = 200;
    public static final float DAMAGE = 1.0F;
    /**
     * An effect is given again only once this little of it is left. Given afresh each tick, it was sent to the player
     * each tick, and poison, which bites when its time left is a multiple of 25, bit every tick it was new.
     */
    public static final int TOP_UP = 20;

    public MercuryBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    /** Its own damage, so a death in it says mercury (it said magic); armour is no help against it. */
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Nookworks.id("mercury"));

    /** In it: below its surface, not merely over a thin sheet of it spreading across a block. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
            boolean isPrecise) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living
                && living.getY() < pos.getY() + state.getFluidState().getHeight(level, pos)) {
            poison(server, living);
        }
    }

    /** The mercury exposure effect. Public for tests. */
    public static void poison(ServerLevel level, LivingEntity living) {
        if (living instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        topUp(living, MobEffects.POISON, POISON_TICKS);
        topUp(living, MobEffects.NAUSEA, NAUSEA_TICKS);
        if (living.tickCount % 20 == 0) {
            living.hurtServer(level, new DamageSource(
                    level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE)), DAMAGE);
        }
    }

    private static void topUp(LivingEntity living, Holder<MobEffect> effect, int ticks) {
        MobEffectInstance current = living.getEffect(effect);
        if (current == null || current.endsWithin(TOP_UP)) {
            living.addEffect(new MobEffectInstance(effect, ticks, 0));
        }
    }
}
