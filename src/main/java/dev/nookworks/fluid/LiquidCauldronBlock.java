package dev.nookworks.fluid;

import dev.nookworks.experience.LiquidExperienceBlock;
import dev.nookworks.mercury.MercuryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cauldron of one of our liquids other than vegetable oil (the user, 2026-11-10: 「メタノールとか液体経験値とか、水銀とか
 * も、大釜に入るようにできませんか？」): methanol, wood vinegar, biodiesel, liquid experience, mercury. As the Vegetable Oil
 * Cauldron is: a bucket's worth in three levels, filled by its bucket and taken back by an empty one, a comparator reading
 * its level as a water cauldron's; rain fills none of them and none puts out a fire. Pipes and tanks fill and drain them
 * as a water cauldron, but the mercury one, as mercury goes through no fluid pipe ({@link LiquidCauldrons}). What it
 * holds does to a body in it what the liquid does in the world: mercury poisons and makes dizzy, liquid experience
 * burns.
 */
public class LiquidCauldronBlock extends LayeredCauldronBlock {
    /** What standing in it does. */
    public enum Touch {
        NONE, MERCURY, EXPERIENCE
    }

    private final Touch touch;

    public LiquidCauldronBlock(CauldronInteraction.InteractionMap interactions, Touch touch, BlockBehaviour.Properties properties) {
        super(Biome.Precipitation.NONE, interactions, properties);
        this.touch = touch;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (this.touch == Touch.NONE || !(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)
                || living.getY() >= pos.getY() + this.getContentHeight(state)) {
            return;
        }
        if (this.touch == Touch.MERCURY) {
            MercuryBlock.poison(server, living);
        } else {
            LiquidExperienceBlock.burn(server, living);
        }
    }
}
