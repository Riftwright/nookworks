package dev.nookworks.port.core;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 26.3's {@code DeferredRegister.Blocks/Items} take the properties as a function of fresh ones ({@code p ->
 * p.strength(2.0F)}) or a supplier, and give them the registry id themselves; 1.21.1's take the properties object.
 * tx/core.py wraps that argument of {@code registerBlock}, {@code registerSimpleBlock}, {@code registerItem},
 * {@code registerSimpleItem} and {@code registerSimpleBlockItem} in {@code Props.block(...)} / {@code Props.item(...)},
 * which applies it to fresh properties as 26.3 does (1.21.1 needs no id on them).
 */
public final class Props {
    private Props() {}

    public static BlockBehaviour.Properties block(UnaryOperator<BlockBehaviour.Properties> properties) {
        return properties.apply(BlockBehaviour.Properties.of());
    }

    public static BlockBehaviour.Properties block(Supplier<BlockBehaviour.Properties> properties) {
        return properties.get();
    }

    public static BlockBehaviour.Properties block(BlockBehaviour.Properties properties) {
        return properties;
    }

    public static Item.Properties item(UnaryOperator<Item.Properties> properties) {
        return properties.apply(new Item.Properties());
    }

    public static Item.Properties item(Supplier<Item.Properties> properties) {
        return properties.get();
    }

    public static Item.Properties item(Item.Properties properties) {
        return properties;
    }
}
