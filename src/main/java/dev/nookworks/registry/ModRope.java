package dev.nookworks.registry;

import java.util.List;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.rope.FilamentBulbBlock;
import dev.nookworks.rope.HookBlock;
import dev.nookworks.rope.HungBannerEntity;
import dev.nookworks.rope.HungLightBlock;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.ThinChainItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * What Thin Rope does beyond hanging a planter: ropes strung from post to post - fence posts, or Hooks on a wall or
 * ceiling - things hung on them or from a rope's end - bottles, fish and kelp to dry, banners, a Filament Bulb - and
 * the Dried Fish that comes of it; and Thin Chain, strung as the rope is.
 */
public final class ModRope {
    public static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, Nookworks.MODID);
    /** A knot's spans, sent to clients to draw. */
    public static final EntityDataSerializer<List<BlockPos>> POSITIONS =
            EntityDataSerializer.forValueType(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()));

    static {
        SERIALIZERS.register("positions", () -> POSITIONS);
    }

    public static final Supplier<EntityType<RopeKnotEntity>> ROPE_KNOT = ModEntities.ENTITY_TYPES.register("rope_knot",
            () -> EntityType.Builder.<RopeKnotEntity>of(RopeKnotEntity::new, MobCategory.MISC)
                    .noLootTable().sized(0.375F, 0.375F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Nookworks.id("rope_knot"))));
    public static final Supplier<EntityType<HungItemEntity>> HUNG_ITEM = ModEntities.ENTITY_TYPES.register("hung_item",
            () -> EntityType.Builder.<HungItemEntity>of(HungItemEntity::new, MobCategory.MISC)
                    .noLootTable().sized(0.3F, 0.4F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Nookworks.id("hung_item"))));
    /** A banner hung on a rope, its bar under it: big enough for a box of its own (turned and leaning as it hangs). */
    public static final Supplier<EntityType<HungBannerEntity>> HUNG_BANNER = ModEntities.ENTITY_TYPES.register("hung_banner",
            () -> EntityType.Builder.<HungBannerEntity>of(HungBannerEntity::new, MobCategory.MISC)
                    .noLootTable().sized((float) HungBannerEntity.WIDTH, (float) HungBannerEntity.HEIGHT).clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Nookworks.id("hung_banner"))));

    public static final DeferredBlock<FilamentBulbBlock> FILAMENT_BULB = ModBlocks.BLOCKS.registerBlock("filament_bulb", FilamentBulbBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(0.3F).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.POPPED)
                    .lightLevel(FilamentBulbBlock::light));
    public static final DeferredItem<BlockItem> FILAMENT_BULB_ITEM = ModItems.ITEMS.registerSimpleBlockItem(FILAMENT_BULB);

    /** The unseen light a bulb or glow berries give where they hang on a rope ({@link HungLightBlock}). No item. */
    public static final DeferredBlock<HungLightBlock> HUNG_LIGHT = ModBlocks.BLOCKS.registerBlock("hung_light", HungLightBlock::new,
            p -> p.replaceable().noCollision().noLootTable().noOcclusion().strength(-1.0F, 3600000.8F)
                    .pushReaction(PushReaction.POPPED).lightLevel(state -> state.getValue(HungLightBlock.LEVEL)));

    /**
     * A small iron hook for a wall or a ceiling: Thin Rope ties to it, berries hang on it - glow berries a lamp. Nothing
     * to walk into.
     */
    public static final DeferredBlock<HookBlock> HOOK = ModBlocks.BLOCKS.registerBlock("hook", HookBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(0.5F).sound(SoundType.CHAIN).noCollision().noOcclusion()
                    .lightLevel(HookBlock::light).pushReaction(PushReaction.POPPED));
    public static final DeferredItem<BlockItem> HOOK_ITEM = ModItems.ITEMS.registerSimpleBlockItem(HOOK);

    /**
     * Thin Chain: strung from post to post as Thin Rope is, the same sag and the same things hung on it, in iron links -
     * and it rattles and sways a little when something goes through it ({@link dev.nookworks.rope.ChainSway}).
     */
    public static final DeferredItem<ThinChainItem> THIN_CHAIN = ModItems.ITEMS.registerItem("thin_chain", ThinChainItem::new);

    /**
     * Fish opened out and dried in the sun (干物): a day of sun makes it keep, and a little better eating than a fish
     * cooked in a furnace - and no parasite lives through it.
     */
    public static final DeferredItem<Item> DRIED_FISH = ModItems.ITEMS.registerSimpleItem("dried_fish",
            p -> p.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7F).build()));

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    /** Loads this class, so its blocks, items and entities are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("rope: {}", FILAMENT_BULB.getId());
    }

    private ModRope() {}
}
