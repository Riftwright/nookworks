package dev.nookworks.port.core;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A player's right click on an entity as 26.3's {@code Player.interactOn(entity, hand, location)} makes it, on 1.21.1.
 *
 * <p>26.3 has one call, which asks the entity ({@code interact(player, hand, location)}) and then the item in hand.
 * 1.21.1's client sends the click twice: first {@code entity.interactAt(player, location, hand)}, and only when that
 * does not take the click, {@code player.interactOn(entity, hand)} (the entity's {@code interact}, then the item).
 * Our entities' 26.3 {@code interact} became {@code interactAt} (tx/core.py), so this does what the game does.
 * {@code location} is relative to the entity's position in both versions.
 */
public final class EntityClicks {
    private EntityClicks() {}

    public static InteractionResult interactOn(Player player, Entity entity, InteractionHand hand, Vec3 location) {
        InteractionResult at = entity.interactAt(player, location, hand);
        if (at.consumesAction()) {
            return at;
        }
        return player.interactOn(entity, hand);
    }

    /**
     * 26.3's {@code entity.interact(player, hand, location)} called on the entity itself (the entity's part of a click,
     * not the item's): 1.21.1's {@code interactAt}, then, when that does not take the click, its {@code interact} - where
     * vanilla's entities (an item frame) keep what 26.3 moved into the one call.
     */
    public static InteractionResult interact(Entity entity, Player player, InteractionHand hand, Vec3 location) {
        InteractionResult at = entity.interactAt(player, location, hand);
        if (at.consumesAction()) {
            return at;
        }
        return entity.interact(player, hand);
    }
}
