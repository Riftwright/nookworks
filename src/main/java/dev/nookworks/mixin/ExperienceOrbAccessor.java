package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ExperienceOrb;

/**
 * An orb entity stands for {@code count} orbs of the same value once vanilla has merged them, which it does
 * all the time at a mob farm. Anything that swallows orbs has to take them one at a time off that count, the
 * way a player picking them up does, or discarding the entity throws all but one of them away.
 */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
    @Accessor("count")
    int nookworks$getCount();

    @Accessor("count")
    void nookworks$setCount(int count);
}
