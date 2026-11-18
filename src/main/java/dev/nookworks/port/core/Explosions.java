package dev.nookworks.port.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;

/**
 * 26.3's {@code Explosion.shouldAffectBlocklikeEntities()} and {@code BlockInteraction.shouldAffectBlocklikeEntities()}
 * - whether an explosion breaks what stands for a block (item frames, paintings, things hung up) - which 1.21.1 lacks,
 * written as 26.3 has them: the interaction does so when it destroys blocks (DESTROY, DESTROY_WITH_DECAY, not KEEP or
 * TRIGGER_BLOCK); the explosion does with mob griefing on, else as its interaction says, and a wind charge's never.
 */
public final class Explosions {
    private Explosions() {}

    public static boolean shouldAffectBlocklikeEntities(Explosion.BlockInteraction interaction) {
        return interaction == Explosion.BlockInteraction.DESTROY || interaction == Explosion.BlockInteraction.DESTROY_WITH_DECAY;
    }

    public static boolean shouldAffectBlocklikeEntities(Explosion explosion, net.minecraft.world.level.Level level) {
        Entity source = explosion.getDirectSourceEntity();
        boolean notWindCharge = source == null
                || source.getType() != EntityType.BREEZE_WIND_CHARGE && source.getType() != EntityType.WIND_CHARGE;
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? notWindCharge
                : shouldAffectBlocklikeEntities(explosion.getBlockInteraction()) && notWindCharge;
    }
}
