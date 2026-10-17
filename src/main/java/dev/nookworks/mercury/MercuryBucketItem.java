package dev.nookworks.mercury;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;

/**
 * A bucket of mercury: a bucket like any other, but a class of its own. NeoForge gives every plain {@link BucketItem}
 * a fluid capability, through which any other mod's tank or machine would take the mercury in - and mercury moves
 * only through Nookworks' own iron tanks and pipes ({@link MercuryHandler}). Poured out, it is a pool of mercury as
 * before; filled and emptied at a Mercury Tank, as before.
 */
public class MercuryBucketItem extends BucketItem {
    public MercuryBucketItem(Fluid fluid, Properties properties) {
        super(fluid, properties);
    }
}
