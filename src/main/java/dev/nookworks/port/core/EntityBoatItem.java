package dev.nookworks.port.core;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 26.3's {@code new BoatItem(entityType, properties)}: a boat item that sets down an entity of the type given (ours:
 * {@link ItemBoat}, {@link ItemChestBoat}). 1.21.1's BoatItem only makes vanilla's two boat entities, by a vanilla
 * {@code Boat.Type}. Used as 26.3's is: aimed at a block or water within reach, refused if someone stands in the way
 * or the boat would not fit, one taken from the stack (not in creative), the item-used statistic counted.
 * The type is given as a supplier: the item is registered before the entity types are.
 */
public class EntityBoatItem extends BoatItem {
    private final Supplier<? extends EntityType<? extends Boat>> entityType;

    public EntityBoatItem(Supplier<? extends EntityType<? extends Boat>> entityType, boolean hasChest, Properties properties) {
        super(hasChest, Boat.Type.OAK, properties);
        this.entityType = entityType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        Vec3 view = player.getViewVector(1.0F);
        List<Entity> near = level.getEntities(player, player.getBoundingBox().expandTowards(view.scale(5.0)).inflate(1.0),
                EntitySelector.NO_SPECTATORS.and(Entity::isPickable));
        Vec3 eyes = player.getEyePosition();
        for (Entity entity : near) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            if (box.contains(eyes)) {
                return InteractionResultHolder.pass(stack);
            }
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        Boat boat = this.entityType.get().create(level);
        if (boat == null) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 at = hit.getLocation();
        boat.setPos(at.x, at.y, at.z);
        boat.xo = at.x;
        boat.yo = at.y;
        boat.zo = at.z;
        if (level instanceof ServerLevel server) {
            EntityType.<Boat>createDefaultStackConfig(server, stack, player).accept(boat);
        }
        boat.setYRot(player.getYRot());
        if (!level.noCollision(boat, boat.getBoundingBox())) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide()) {
            level.addFreshEntity(boat);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, hit.getLocation());
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
