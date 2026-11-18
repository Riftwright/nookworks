package dev.nookworks.port.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The items 26.3 draws (in part) by code - a {@code minecraft:special} in their item definition - drawn in 1.21.1 by one
 * {@link BlockEntityWithoutLevelRenderer}, under an item model whose parent is {@code builtin/entity} (with the display
 * transforms of the model 26.3 names as the special's {@code base}). For each such item: the plain model drawn first
 * (26.3's composite), if any, then the {@link SpecialModelRenderer} of the type it names, baked from the codec the mod
 * registered for it and the arguments its definition gives:
 * <ul>
 * <li>{@code nookworks:repair_box} - {@code nookworks:repair_box} alone;</li>
 * <li>{@code nookworks:label} - {@code nookworks:item/label}, then {@code nookworks:label} ({@code "gui": true} on a
 * screen);</li>
 * <li>{@code nookworks:plate} (its {@code nookworks:has_contents} override) - {@code nookworks:block/plate}, then
 * {@code nookworks:plate_contents};</li>
 * <li>{@code nookworks:wooden_tray} (its {@code nookworks:has_contents} override) -
 * {@code nookworks:item/wooden_tray_carried}, then {@code nookworks:tray_contents};</li>
 * <li>{@code nookworks:poster} - for a photo, {@code nookworks:photo} alone; else its design's own model,
 * {@code <namespace>:item/poster/<path>} (26.3's {@code select} on {@code nookworks:poster}), or {@code nookworks:item/poster}
 * for a design with none.</li>
 * </ul>
 * The plain models are loaded on their own ({@link #registerModels}). Another mod's items drawn so join the table
 * through {@link #add}.
 */
public final class SpecialItems {
    private SpecialItems() {}

    /** One item's drawing: its plain model (by stack and context; null for none) and its special's type and arguments. */
    record Entry(BiFunction<ItemStack, ItemDisplayContext, @Nullable ResourceLocation> base,
            BiFunction<ItemStack, ItemDisplayContext, @Nullable ResourceLocation> special,
            BiFunction<ItemStack, ItemDisplayContext, JsonObject> arguments) {}

    // the plain poster icon: the item's own model (item/poster) is the code-drawn one in 1.21.1
    private static final ResourceLocation POSTER_MODEL = PortIds.id("item/poster_plain");
    private static final Map<ResourceLocation, Entry> ENTRIES = Map.of(
            PortIds.id("repair_box"), new Entry((s, c) -> null, (s, c) -> PortIds.id("repair_box"), (s, c) -> new JsonObject()),
            PortIds.id("label"), new Entry((s, c) -> PortIds.id("item/label_base"), (s, c) -> PortIds.id("label"), SpecialItems::labelArguments),
            PortIds.id("plate"), new Entry((s, c) -> PortIds.id("block/plate"), (s, c) -> PortIds.id("plate_contents"),
                    (s, c) -> new JsonObject()),
            PortIds.id("wooden_tray"), new Entry((s, c) -> PortIds.id("item/wooden_tray_carried"), (s, c) -> PortIds.id("tray_contents"),
                    (s, c) -> new JsonObject()),
            PortIds.id("poster"), new Entry(SpecialItems::posterModel, (s, c) -> isPhoto(s) ? PortIds.id("photo") : null,
                    (s, c) -> new JsonObject()));

    /** Other mods' items drawn here ({@link #add}), by item, with the plain models each wants loaded. */
    private static final Map<ResourceLocation, Entry> ADDED = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, List<ResourceLocation>> ADDED_MODELS = new ConcurrentHashMap<>();

    /**
     * Port only (26.3 reads this from the item's definition): {@code item} is drawn by code, as a {@code minecraft:special}
     * in a composite. {@code base} names the plain model drawn first for a stack in a context (null for none), one of
     * {@code plainModels}, which are loaded on their own at each reload; {@code special} names the type of
     * {@link SpecialModelRenderer} drawn after it (registered by any mod with {@code RegisterSpecialModelRendererEvent};
     * null for none) and {@code arguments} what its codec reads. The item's model file must have
     * {@code builtin/entity} as its parent (1.21.1 draws such an item through the renderer this registers). To be called
     * before the client extensions are registered: from the mod's constructor, on the client.
     */
    public static void add(ResourceLocation item, BiFunction<ItemStack, ItemDisplayContext, @Nullable ResourceLocation> base,
            BiFunction<ItemStack, ItemDisplayContext, @Nullable ResourceLocation> special,
            BiFunction<ItemStack, ItemDisplayContext, JsonObject> arguments, List<ResourceLocation> plainModels) {
        ADDED.put(item, new Entry(base, special, arguments));
        ADDED_MODELS.put(item, List.copyOf(plainModels));
    }

    private static @Nullable Entry entry(ResourceLocation item) {
        Entry own = ENTRIES.get(item);
        return own != null ? own : ADDED.get(item);
    }

    /** The specials baked since the last reload, by type and arguments. */
    private static final Map<List<Object>, SpecialModelRenderer<?>> BAKED = new HashMap<>();
    /** The poster designs with a model of their own, found at the last reload. */
    private static volatile java.util.Set<ResourceLocation> posterModels = java.util.Set.of();
    private static @Nullable Renderer renderer;

    private static JsonObject labelArguments(ItemStack stack, ItemDisplayContext context) {
        JsonObject arguments = new JsonObject();
        if (context == ItemDisplayContext.GUI) {
            arguments.addProperty("gui", true);
        }
        return arguments;
    }

    private static boolean isPhoto(ItemStack stack) {
        // the mod's own condition, nookworks:photo (registered from PhotoIconRenderer.IsPhoto)
        MapCodec<? extends ConditionalItemModelProperty> codec = RegisterConditionalItemModelPropertyEvent.TYPES.get(PortIds.id("photo"));
        ConditionalItemModelProperty photo = codec == null ? null : ItemModelProperties.decode(codec, new JsonObject());
        return photo != null && photo.get(stack, Minecraft.getInstance().level, null, 0, ItemDisplayContext.NONE);
    }

    private static @Nullable ResourceLocation posterModel(ItemStack stack, ItemDisplayContext context) {
        if (isPhoto(stack)) {
            return null;
        }
        ResourceLocation design = stack.get(PortIds.<ResourceLocation>component("poster"));
        ResourceLocation own = design == null ? null : design.withPrefix("item/poster/");
        return own != null && posterModels.contains(own) ? own : POSTER_MODEL;
    }

    /** The plain models drawn here, loaded on their own: each entry's fixed one, and every design's poster model. */
    static void registerModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        for (ResourceLocation id : List.of(PortIds.id("item/label_base"), PortIds.id("block/plate"), PortIds.id("item/wooden_tray_carried"),
                POSTER_MODEL)) {
            event.register(ModelResourceLocation.standalone(id));
        }
        ADDED_MODELS.values().forEach(models -> models.forEach(id -> event.register(ModelResourceLocation.standalone(id))));
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        java.util.Set<ResourceLocation> found = new java.util.HashSet<>();
        for (ResourceLocation file : resources.listResources("models/item/poster", id -> id.getPath().endsWith(".json")).keySet()) {
            String path = file.getPath().substring("models/".length(), file.getPath().length() - ".json".length());
            ResourceLocation model = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            found.add(model);
            event.register(ModelResourceLocation.standalone(model));
        }
        posterModels = java.util.Set.copyOf(found);
    }

    /** A reload made new models and atlases: the specials are baked again when next drawn. */
    static synchronized void reset() {
        BAKED.clear();
    }

    static void registerExtensions(RegisterClientExtensionsEvent event) {
        java.util.Set<ResourceLocation> drawn = new java.util.LinkedHashSet<>(ENTRIES.keySet());
        drawn.addAll(ADDED.keySet());
        for (ResourceLocation id : drawn) {
            Item item = ItemModelProperties.item(id);
            if (item == null) {
                continue;
            }
            event.registerItem(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return renderer();
                }
            }, item);
        }
    }

    private static synchronized Renderer renderer() {
        if (renderer == null) {
            renderer = new Renderer();
        }
        return renderer;
    }

    @SuppressWarnings("unchecked")
    private static synchronized @Nullable SpecialModelRenderer<Object> special(ResourceLocation type, JsonObject arguments) {
        List<Object> key = List.of(type, arguments.toString());
        SpecialModelRenderer<?> baked = BAKED.get(key);
        if (baked == null && !BAKED.containsKey(key)) {
            MapCodec<? extends SpecialModelRenderer.Unbaked<?>> codec = RegisterSpecialModelRendererEvent.TYPES.get(type);
            SpecialModelRenderer.Unbaked<?> unbaked = codec == null ? null : ItemModelProperties.decode(codec, arguments);
            baked = unbaked == null ? null : unbaked.bake(SpecialModelRenderer.BakingContext.current());
            BAKED.put(key, baked);
        }
        return (SpecialModelRenderer<Object>) baked;
    }

    /** Draws the items of {@link #ENTRIES} and those added, in the block space (0 to 1) the item model's transforms leave. */
    private static final class Renderer extends BlockEntityWithoutLevelRenderer {
        Renderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void onResourceManagerReload(ResourceManager resources) {
            reset();
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light,
                int overlay) {
            Entry entry = entry(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (entry == null) {
                return;
            }
            ResourceLocation base = entry.base().apply(stack, context);
            if (base != null) {
                drawPlain(base, stack, poseStack, buffers, light, overlay);
            }
            ResourceLocation type = entry.special().apply(stack, context);
            SpecialModelRenderer<Object> special = type == null ? null : special(type, entry.arguments().apply(stack, context));
            if (special != null) {
                special.submit(special.extractArgument(stack), poseStack, new SubmitNodeCollector(buffers), light, overlay, stack.hasFoil(), 0);
            }
        }

        /** A model loaded on its own, as an item's model is drawn: its own render types, tinted by the item's colours, glint and all. */
        private static void drawPlain(ResourceLocation id, ItemStack stack, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel model = minecraft.getModelManager().getModel(ModelResourceLocation.standalone(id));
            if (model == minecraft.getModelManager().getMissingModel()) {
                return;
            }
            ItemRenderer items = minecraft.getItemRenderer();
            for (BakedModel pass : model.getRenderPasses(stack, true)) {
                for (RenderType type : pass.getRenderTypes(stack, true)) {
                    items.renderModelLists(pass, stack, light, overlay, poseStack, ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil()));
                }
            }
        }
    }
}
