package dev.nookworks.pearl;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import dev.nookworks.registry.ModItems;
import dev.nookworks.travel.DaemonDestinations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Lode Pearl: an ender pearl bound to a lodestone (use a pearl on one; see {@link LodePearls}). Thrown, wherever it
 * lands - or once it has fallen out of the bottom of the world - it takes whoever threw it to that lodestone, in
 * whatever dimension, and sets them down gently: no fall, no damage, no endermite. One use, like the pearl it was.
 * <p>
 * The binding is vanilla's lodestone tracker, the one a lodestone compass keeps, so pearls bound to the same lodestone
 * stack and every other one does not. Soap washes the binding off in the crafting grid, back to an ender pearl. A bound
 * pearl is called after its lodestone's name ({@link LodestoneNames}), so a pocket of them says which goes where.
 */
public class LodePearlItem extends Item {
    public LodePearlItem(Properties properties) {
        super(properties);
    }

    /** A Lode Pearl bound to the lodestone at {@code lodestone}. */
    public static ItemStack bound(GlobalPos lodestone) {
        ItemStack pearl = new ItemStack(ModItems.LODE_PEARL.get());
        pearl.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(lodestone), true));
        return pearl;
    }

    /** The lodestone a pearl is bound to, if it is. */
    public static Optional<GlobalPos> lodestone(ItemStack pearl) {
        LodestoneTracker tracker = pearl.get(DataComponents.LODESTONE_TRACKER);
        return tracker == null ? Optional.empty() : tracker.target();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (lodestone(stack).isEmpty()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("item.nookworks.lode_pearl.unbound"));
            }
            return InteractionResult.FAIL;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (level instanceof ServerLevel server) {
            Projectile.spawnProjectileFromRotation(ThrownLodePearl::new, server, stack, player, 0.0F, 1.5F, 1.0F);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /** "Lode Pearl (Plains)": after the lodestone it is bound to, by that lodestone's name as it is now. */
    @Override
    public Component getName(ItemStack stack) {
        return lodestone(stack).flatMap(LodestoneNames::of).<Component>map(name -> Component.translatable("item.nookworks.lode_pearl.named", name))
                .orElseGet(() -> super.getName(stack));
    }

    /** A bound pearl shines as a lodestone compass does. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return lodestone(stack).isPresent() || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        lodestone(stack).ifPresentOrElse(target -> builder.accept(LodestoneNames.of(target)
                        .map(name -> Component.translatable("item.nookworks.lode_pearl.bound_named", target.pos().getX(), target.pos().getY(),
                                target.pos().getZ(), dimension(target), name))
                        .orElseGet(() -> Component.translatable("item.nookworks.lode_pearl.bound", target.pos().getX(), target.pos().getY(),
                                target.pos().getZ(), dimension(target))).withStyle(ChatFormatting.GRAY)),
                () -> builder.accept(Component.translatable("item.nookworks.lode_pearl.how").withStyle(ChatFormatting.GRAY)));
    }

    /** The Overworld, the Nether, the End by name; any other dimension as the door's panel names it (the If, a mod's). */
    public static Component dimension(GlobalPos target) {
        var id = target.dimension().identifier();
        if (id.getNamespace().equals("minecraft") && Set.of("overworld", "the_nether", "the_end").contains(id.getPath())) {
            return Component.translatable("item.nookworks.lode_pearl.dimension." + id.getPath());
        }
        return DaemonDestinations.name(target.dimension());
    }
}
