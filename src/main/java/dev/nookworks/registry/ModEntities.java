package dev.nookworks.registry;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.furniture.SeatEntity;
import dev.nookworks.label.LabelEntity;
import dev.nookworks.pearl.ThrownLodePearl;
import dev.nookworks.poster.NookItemFrame;
import dev.nookworks.poster.PosterEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.nookworks.port.core.ItemBoat;
import dev.nookworks.port.core.ItemChestBoat;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Nookworks.MODID);

    public static final Supplier<EntityType<PosterEntity>> POSTER = ENTITY_TYPES.register("poster",
            () -> EntityType.Builder.<PosterEntity>of(PosterEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE) // hanging entities never move on their own
                    .build(Nookworks.id("poster").toString()));

    /** Item frames of dark oak and of brass, and their glowing forms, built as vanilla's item frame is: see {@link NookItemFrame}. */
    public static final Supplier<EntityType<NookItemFrame>> DARK_OAK_ITEM_FRAME = itemFrame("dark_oak_item_frame");
    public static final Supplier<EntityType<NookItemFrame>> DARK_OAK_GLOW_ITEM_FRAME = itemFrame("dark_oak_glow_item_frame");
    public static final Supplier<EntityType<NookItemFrame>> BRASS_ITEM_FRAME = itemFrame("brass_item_frame");
    public static final Supplier<EntityType<NookItemFrame>> BRASS_GLOW_ITEM_FRAME = itemFrame("brass_glow_item_frame");

    private static Supplier<EntityType<NookItemFrame>> itemFrame(String name) {
        return ENTITY_TYPES.register(name, () -> EntityType.Builder.<NookItemFrame>of(NookItemFrame::new, MobCategory.MISC)
                
                .sized(0.5F, 0.5F)
                .eyeHeight(0.0F)
                .clientTrackingRange(10)
                .updateInterval(Integer.MAX_VALUE) // 26.3's noUpdateInterval()
                .setShouldReceiveVelocityUpdates(false) // 26.3's dontTrackDeltas()
                .build(Nookworks.id(name).toString()));
    }

    public static final Supplier<EntityType<LabelEntity>> LABEL = ENTITY_TYPES.register("label",
            () -> EntityType.Builder.<LabelEntity>of(LabelEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(Nookworks.id("label").toString()));

    /** Dawnwood's boats, sized and tracked as vanilla's are; each drops its own item. */
    public static final Supplier<EntityType<Boat>> DAWNWOOD_BOAT = ENTITY_TYPES.register("dawnwood_boat",
            () -> EntityType.Builder.<Boat>of(
                            (type, level) -> new ItemBoat(type, level, () -> ModWoods.DAWNWOOD_BOAT.get()),
                            MobCategory.MISC)
                    .sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10)
                    .build(Nookworks.id("dawnwood_boat").toString()));
    public static final Supplier<EntityType<ChestBoat>> DAWNWOOD_CHEST_BOAT = ENTITY_TYPES.register(
            "dawnwood_chest_boat",
            () -> EntityType.Builder.<ChestBoat>of(
                            (type, level) -> new ItemChestBoat(type, level, () -> ModWoods.DAWNWOOD_CHEST_BOAT.get()),
                            MobCategory.MISC)
                    .sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10)
                    .build(Nookworks.id("dawnwood_chest_boat").toString()));

    /** A thrown Lode Pearl, sized and tracked as vanilla's ender pearl is. */
    public static final Supplier<EntityType<ThrownLodePearl>> LODE_PEARL = ENTITY_TYPES.register("lode_pearl",
            () -> EntityType.Builder.<ThrownLodePearl>of(ThrownLodePearl::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10)
                    .build(Nookworks.id("lode_pearl").toString()));

    /**
     * The seat a chair puts under a player.
     * <p>
     * It has to be saveable even though it is meant to be throwaway: the server refuses to mount anything whose
     * type cannot serialize. One that does come back from a save discards itself on its first tick, because by
     * then it has neither a rider nor a chair under it.
     */
    public static final Supplier<EntityType<SeatEntity>> SEAT = ENTITY_TYPES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.001F, 0.001F)
                    .noSummon()
                    .clientTrackingRange(8)
                    .build(Nookworks.id("seat").toString()));

    private ModEntities() {}
}
