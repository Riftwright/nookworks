package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 26.3's {@code ItemModelResolver}: fills an {@link ItemStackRenderState} for a stack. There is one for everyone
 * ({@link #get}); a scripted edit points the mod's {@code context.itemModelResolver()} and
 * {@code Minecraft.getItemModelResolver()} at it.
 */
public final class ItemModelResolver {
    private static final ItemModelResolver INSTANCE = new ItemModelResolver();

    private ItemModelResolver() {}

    public static ItemModelResolver get() {
        return INSTANCE;
    }

    /** {@code stack} drawn where {@code displayContext} says, held by {@code owner} if anyone, its model picked by {@code seed}. */
    public void updateForTopItem(ItemStackRenderState state, ItemStack stack, ItemDisplayContext displayContext, @Nullable Level level,
            @Nullable LivingEntity owner, int seed) {
        state.clear();
        state.stack = stack;
        state.displayContext = displayContext;
        state.level = level;
        state.owner = owner;
        state.seed = seed;
    }

    /** {@code stack} shown by a thing that is not alive (a frame, a label): its world, and its id as the seed. */
    public void updateForNonLiving(ItemStackRenderState state, ItemStack stack, ItemDisplayContext displayContext, Entity entity) {
        this.updateForTopItem(state, stack, displayContext, entity.level(), null, entity.getId());
    }
}
