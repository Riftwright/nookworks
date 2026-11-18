package dev.nookworks.drive;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a drive belt is made of: Rope, for a wooden workshop, or Thin Chain, longer, for an iron one (the user,
 * 2026-10-10: Thin Rope would snap on a motor). Which items make one is a tag a pack can add to.
 */
public enum BeltKind implements StringRepresentable {
    ROPE("rope", 8, "drive_belt_ropes"),
    CHAIN("chain", 16, "drive_belt_chains");

    private final String name;
    /** The longest belt, wheel to wheel, in blocks. */
    public final int maxLength;
    public final TagKey<Item> items;

    BeltKind(String name, int maxLength, String tag) {
        this.name = name;
        this.maxLength = maxLength;
        this.items = TagKey.create(Registries.ITEM, Nookworks.id(tag));
    }

    /** The kind of belt {@code stack} makes, if it makes one. */
    public static @Nullable BeltKind of(ItemStack stack) {
        for (BeltKind kind : values()) {
            if (stack.is(kind.items)) {
                return kind;
            }
        }
        return null;
    }

    /** The longest any kind of belt reaches, in blocks. */
    public static int longest() {
        int most = 0;
        for (BeltKind kind : values()) {
            most = Math.max(most, kind.maxLength);
        }
        return most;
    }

    /** How many a belt over {@code length} blocks takes: it goes there and back, one a block each way. */
    public static int itemsFor(double length) {
        return 2 * Math.max(1, Mth.ceil(length));
    }

    public SoundEvent sound() {
        return this == CHAIN ? SoundEvents.CHAIN_PLACE : SoundEvents.WOOL_PLACE;
    }

    public SoundEvent snapSound() {
        return this == CHAIN ? SoundEvents.CHAIN_BREAK : SoundEvents.WOOL_BREAK;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
