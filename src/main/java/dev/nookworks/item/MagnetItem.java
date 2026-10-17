package dev.nookworks.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import dev.nookworks.Nookworks;
import dev.nookworks.compat.FeatureGate;
import dev.nookworks.mixin.ItemEntityAccessor;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Magnet: right-click to switch on; while on and anywhere in the inventory it pulls dropped items and XP orbs
 * within {@link #range} blocks towards the player. Sneaking pauses it (so you can drop things), items still
 * in their pickup delay are skipped, and other mods can opt items out with the "PreventRemoteMovement" tag. It pulls
 * only what the player could see - not through a wall into the next room - and never an item meant for someone else.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public class MagnetItem extends Item {
    private static final double SPEED = 0.45;

    public final int range;

    public MagnetItem(int range, Properties properties) {
        super(properties);
        this.range = range;
    }

    public static boolean isActive(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.MAGNET_ACTIVE.get(), false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean on = !isActive(stack);
        stack.set(ModDataComponents.MAGNET_ACTIVE.get(), on);
        level.playSound(null, player.blockPosition(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.4F, on ? 1.6F : 1.2F);
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable(on ? "message.nookworks.magnet.on" : "message.nookworks.magnet.off"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isActive(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.nookworks.magnet", this.range,
                Component.translatable(isActive(stack) ? "tooltip.nookworks.magnet.on" : "tooltip.nookworks.magnet.off"))
                .withStyle(ChatFormatting.GRAY));
        // (no word of its standing down for another mod: that is in the log and on the mod's page, not in the game -
        // the user's call, and it said "another mod" when a setting had switched it off)
    }

    /** The largest range among active magnets the player carries, or 0. */
    public static int activeRange(Player player) {
        int best = 0;
        for (ItemStack stack : player.getInventory()) {
            if (stack.getItem() instanceof MagnetItem magnet && isActive(stack)) {
                best = Math.max(best, magnet.range);
            }
        }
        return best;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.isShiftKeyDown()
                || player.tickCount % 2 != 0) {
            return;
        }
        int range = FeatureGate.magnet() ? activeRange(player) : 0;
        if (range > 0) {
            pull(player, range);
        }
    }

    /** What a player was told about taking an item or an orb, and until when the answer holds. */
    private record Leave(boolean allowed, long until) {}

    private static final Map<Player, Map<Entity, Leave>> LEAVE = new WeakHashMap<>();
    /** How long an answer is kept: a second, so a pile of drops is not asked about every other tick. */
    private static final int LEAVE_TICKS = 20;

    /**
     * Whether {@code player} may take {@code entity} - an item or an orb - where it lies: whether they could pick it up by
     * walking to it. A claim mod that keeps others' drops on its land for its own people says no to that, and a magnet
     * pulling them over the claim's edge first would get round it (the user, 2026-10-01). So the magnet asks the very
     * question a pickup asks - NeoForge's item pickup and experience pickup events - which a claim mod answers without a
     * word to the player. (It asked whether the player might break a block there, which a claim mod answers by telling
     * the player they cannot break blocks here: every few seconds while the magnet was on near a claim.) Asked once a
     * second for each thing.
     */
    private static boolean mayTake(Player player, Entity entity) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        long now = level.getGameTime();
        Map<Entity, Leave> asked = LEAVE.computeIfAbsent(player, p -> new WeakHashMap<>());
        Leave leave = asked.get(entity);
        if (leave == null || leave.until() < now) {
            boolean allowed = entity instanceof ItemEntity item
                    ? NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(player, item)).canPickup() != TriState.FALSE
                    : !(entity instanceof ExperienceOrb orb) || !NeoForge.EVENT_BUS.post(new PlayerXpEvent.PickupXp(player, orb)).isCanceled();
            leave = new Leave(allowed, now + LEAVE_TICKS);
            asked.put(entity, leave);
        }
        return leave.allowed() && entity.isAlive() && !(entity instanceof ItemEntity item && item.getItem().isEmpty());
    }

    /** What a player's magnet found walled off, and until when it does not look again. */
    private static final Map<Player, Map<Entity, Long>> HIDDEN = new WeakHashMap<>();
    /**
     * Half a second: a pile of drops behind glass or a farm's wall is traced to once in that time, not every other tick,
     * while one in sight is traced every time (it moves, towards the player).
     */
    private static final int HIDDEN_TICKS = 10;

    /** {@link #sees}, a wall's answer kept a while. */
    private static boolean inSight(Player player, Entity entity) {
        long now = player.level().getGameTime();
        Map<Entity, Long> hidden = HIDDEN.computeIfAbsent(player, p -> new WeakHashMap<>());
        Long until = hidden.get(entity);
        if (until != null && until >= now) {
            return false;
        }
        if (sees(player, entity)) {
            hidden.remove(entity);
            return true;
        }
        hidden.put(entity, now + HIDDEN_TICKS);
        return false;
    }

    /**
     * Whether nothing solid stands between the player's eyes and {@code entity}, and no lava lies on the straight way it
     * would be pulled: drops across a lava pool or behind a lavafall were drawn through it and burned up (r10).
     */
    private static boolean sees(Player player, Entity entity) {
        Vec3 eyes = player.getEyePosition();
        Vec3 at = entity.position().add(0, entity.getBbHeight() / 2, 0);
        return player.level().clip(new ClipContext(eyes, at, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS
                && !BlockGetter.traverseBlocks(at, player.position().add(0, player.getBbHeight() / 2, 0), player.level(),
                        (level, pos) -> level.getFluidState(pos).is(FluidTags.LAVA) ? Boolean.TRUE : null, level -> Boolean.FALSE);
    }

    /** Another player near enough with a magnet on, and how far it reaches. */
    private record Rival(Player player, AABB reach) {}

    /** The players besides {@code player} whose magnets may reach what {@code player}'s does. */
    private static List<Rival> rivals(Player player, int range) {
        List<Rival> rivals = new ArrayList<>();
        double near = range + 16 + 2; // the farthest two magnets can be apart and still reach one thing
        for (Player other : player.level().players()) {
            if (other != player && !other.isSpectator() && !other.isShiftKeyDown() && other.distanceToSqr(player) < near * near) {
                int reach = activeRange(other);
                if (reach > 0) {
                    rivals.add(new Rival(other, other.getBoundingBox().inflate(reach)));
                }
            }
        }
        return rivals;
    }

    /** Pulls items and XP within {@code range} towards the player. Public for tests. */
    public static void pull(Player player, int range) {
        AABB box = player.getBoundingBox().inflate(range);
        Vec3 target = player.position().add(0, player.getBbHeight() / 2, 0);
        List<Entity> found = player.level().getEntities(player, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb);
        // With two magnets in reach, a thing goes to the nearer player: it went to whoever was ticked last, emeralds
        // lying at another's feet too (r7 multiplayer test). Nearer and able to take it: one behind a wall, or kept off
        // the item by a claim, left it to nobody (r7 review).
        List<Rival> rivals = found.isEmpty() ? List.of() : rivals(player, range);
        for (Entity entity : found) {
            if (!rivals.isEmpty() && rivals.stream().anyMatch(rival -> rival.reach().contains(entity.position())
                    && rival.player().distanceToSqr(entity) < player.distanceToSqr(entity)
                    && inSight(rival.player(), entity) && mayTake(rival.player(), entity))) {
                continue;
            }
            if (entity instanceof ItemEntity item && (item.hasPickUpDelay() || item.getPersistentData().getBooleanOr("PreventRemoteMovement", false)
                    || ((ItemEntityAccessor) item).nookworks$getTarget() != null
                            && !player.getUUID().equals(((ItemEntityAccessor) item).nookworks$getTarget()))) {
                continue;
            }
            Vec3 toward = target.subtract(entity.position());
            if (toward.lengthSqr() < 1.0 || !inSight(player, entity) || !mayTake(player, entity)) {
                continue; // at the player's feet already, nothing to do - and so no line of sight to trace to it
            }
            entity.setDeltaMovement(toward.normalize().scale(SPEED));
            entity.syncVelocity = true;
        }
    }
}
