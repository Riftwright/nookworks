package dev.nookworks.registry;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import dev.nookworks.Nookworks;
import dev.nookworks.label.LabelText;
import dev.nookworks.photo.Photos;
import dev.nookworks.storage.StorageContents;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Nookworks.MODID);

    /** Storage Chest contents carried by the item when the chest is broken. */
    public static final Supplier<DataComponentType<StorageContents>> STORAGE = COMPONENTS.registerComponentType("storage",
            b -> b.persistent(StorageContents.CODEC).networkSynchronized(StorageContents.STREAM_CODEC));

    /** The mob type of a spawner picked up with a Silk Touch hammer. */
    public static final Supplier<DataComponentType<EntityType<?>>> SPAWNER_ENTITY = COMPONENTS.registerComponentType("spawner_entity",
            b -> b.persistent(EntityType.CODEC).networkSynchronized(ByteBufCodecs.registry(Registries.ENTITY_TYPE)));

    /** Mercury (mB) in a Mercury Tank item. */
    public static final Supplier<DataComponentType<Integer>> MERCURY = COMPONENTS.registerComponentType("mercury",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** What a Copper Tank item holds, kept when the tank is broken. */
    public static final Supplier<DataComponentType<SimpleFluidContent>> FLUID_CONTENTS =
            COMPONENTS.registerComponentType("fluid_contents", b -> b.persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    /**
     * The liquids a machine held when it was broken - mercury, oil, fuel, water - kept in its item as a Copper Tank
     * keeps its contents, and poured back in when it is set down again.
     */
    public static final Supplier<DataComponentType<List<SimpleFluidContent>>> HELD_FLUIDS =
            COMPONENTS.registerComponentType("held_fluids", b -> b.persistent(SimpleFluidContent.CODEC.listOf(1, 8))
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC.apply(ByteBufCodecs.list(8))));

    /**
     * The power a machine held when it was broken, kept in its item as its liquids are and put back when it is set
     * down again (see {@link dev.nookworks.machine.MachineBlockEntity#keepEnergy}).
     */
    public static final Supplier<DataComponentType<Integer>> HELD_ENERGY =
            COMPONENTS.registerComponentType("held_energy", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The batches of biodiesel the caustic soda a Stirring Tank already took in still serves, kept in its item when it
     * is broken as its power and liquids are (see {@link dev.nookworks.mercury.StirringTankBlockEntity#catalystLeft}).
     */
    public static final Supplier<DataComponentType<Integer>> CATALYST_LEFT =
            COMPONENTS.registerComponentType("catalyst_left", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The brews the blaze powder a Spawn Egg Brewer already took in still serves, kept in its item when it is broken, as
     * the Stirring Tank's soda is (the dupe and loss audit, 2026-10-19: up to nine brews were lost on every move).
     */
    public static final Supplier<DataComponentType<Integer>> BREWS_LEFT =
            COMPONENTS.registerComponentType("brews_left", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The tag a Precision Sorter's tag entry stands for (see {@link dev.nookworks.sorter.SorterTags}). */
    public static final Supplier<DataComponentType<TagKey<Item>>> SORTER_TAG =
            COMPONENTS.registerComponentType("sorter_tag", b -> b.persistent(TagKey.codec(Registries.ITEM))
                    .networkSynchronized(TagKey.streamCodec(Registries.ITEM)));

    /**
     * The mod a Precision Sorter's mod entry stands for, by the namespace of its items' ids ("minecraft" for vanilla's):
     * the entry catches every item of it (see {@link dev.nookworks.sorter.SorterTags#modEntry}).
     */
    public static final Supplier<DataComponentType<String>> SORTER_MOD =
            COMPONENTS.registerComponentType("sorter_mod", b -> b.persistent(Codec.STRING.validate(ModDataComponents::namespace))
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Which design a Poster item carries; absent means a blank sheet. */
    public static final Supplier<DataComponentType<Identifier>> POSTER = COMPONENTS.registerComponentType("poster",
            b -> b.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

    /** What a Label is printed with: the item whose picture it shows. */
    public static final Supplier<DataComponentType<Holder<Item>>> LABEL =
            COMPONENTS.registerComponentType("label", b -> b.persistent(BuiltInRegistries.ITEM.holderByNameCodec())
                    .networkSynchronized(ByteBufCodecs.holderRegistry(Registries.ITEM)));

    /** What a letter label says; see {@link dev.nookworks.label.LabelText}. */
    /** The game time a red-hot ingot came out of the fire (dev.nookworks.quench.Quenching): it cools from then. */
    public static final Supplier<DataComponentType<Long>> HEATED_AT = COMPONENTS.registerComponentType("heated_at",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    public static final Supplier<DataComponentType<String>> LABEL_TEXT = COMPONENTS.registerComponentType("label_text",
            b -> b.persistent(Codec.string(1, LabelText.MAX))
                    .networkSynchronized(ByteBufCodecs.stringUtf8(LabelText.MAX)));

    /** Whether a Magnet is switched on. */
    public static final Supplier<DataComponentType<Boolean>> MAGNET_ACTIVE = COMPONENTS.registerComponentType("magnet_active",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** A Silk Touch axe's leaf gathering switched off (LeafGathering); absent, it is on. */
    public static final Supplier<DataComponentType<Boolean>> GATHERS_LEAVES = COMPONENTS.registerComponentType("gathers_leaves",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** The mob inside a filled Mob Bottle. */
    public static final Supplier<DataComponentType<EntityType<?>>> CAPTURED_ENTITY = COMPONENTS.registerComponentType("captured_entity",
            b -> b.persistent(EntityType.CODEC).networkSynchronized(ByteBufCodecs.registry(Registries.ENTITY_TYPE)));

    /**
     * Everything about the mob in a filled Mob Bottle - its name, health, age, trades, what it carries - as the game
     * saves an entity. Released, the same mob comes back out. Absent on bottles filled before it existed (the type
     * alone, released as a fresh mob of that kind) and on the ones JEI shows.
     */
    public static final Supplier<DataComponentType<CustomData>> CAPTURED_DATA =
            COMPONENTS.registerComponentType("captured_data", b -> b.persistent(CustomData.CODEC)
                    .networkSynchronized(CustomData.STREAM_CODEC));

    /** The Spawner Link Sender a Spawner Link Receiver is paired with. */
    public static final Supplier<DataComponentType<GlobalPos>> SPAWNER_LINK = COMPONENTS.registerComponentType("spawner_link",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    /** Which setting-down of the paired sender the link was made with (see {@link dev.nookworks.spawner.SpawnerLinkSenderBlockEntity}). */
    public static final Supplier<DataComponentType<Long>> SPAWNER_LINK_STAMP = COMPONENTS.registerComponentType("spawner_link_stamp",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** The Horizontal Item Warp Sender a receiver is paired with (see {@link dev.nookworks.block.entity.HorizontalItemWarpReceiverBlockEntity}). */
    public static final Supplier<DataComponentType<GlobalPos>> WARP_LINK = COMPONENTS.registerComponentType("warp_link",
            b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    /** Which setting-down of the paired warp sender the link was made with, as {@link #SPAWNER_LINK_STAMP} for spawners. */
    public static final Supplier<DataComponentType<Long>> WARP_LINK_STAMP = COMPONENTS.registerComponentType("warp_link_stamp",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** The photos a Camera keeps, by number, oldest first (see {@link dev.nookworks.photo.Photos}). */
    public static final Supplier<DataComponentType<List<Integer>>> CAMERA_PHOTOS = COMPONENTS.registerComponentType("camera_photos",
            b -> b.persistent(ExtraCodecs.POSITIVE_INT.listOf(0, Photos.KEPT))
                    .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(Photos.KEPT))));

    /** An id's namespace as the game allows one (lower case, digits, {@code _ . -}), or an error. */
    private static DataResult<String> namespace(String namespace) {
        return !namespace.isEmpty() && Identifier.isValidNamespace(namespace) ? DataResult.success(namespace)
                : DataResult.error(() -> "Not a namespace: " + namespace);
    }

    private ModDataComponents() {}
}
