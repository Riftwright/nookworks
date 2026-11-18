package dev.nookworks.cooking;

import net.minecraft.world.item.Item;

/**
 * A dish served on its own plate: Fried Rice on a Plate, Stir-Fried Vegetables on a Plate ({@link Plating}). Eaten, the
 * Plate is left in the hand, as a bowl of it leaves its bowl. Sneak and right-click the top of a block and it is set
 * down, plate and all - a {@link PlateBlock} with the dish on it - and an empty hand takes it back up, plate and all.
 * There is always one plate to a dish: none is made and none lost, whichever way the dish goes on and off the table.
 */
public class PlatedDishItem extends Item {
    public PlatedDishItem(Properties properties) {
        super(properties);
    }
}
