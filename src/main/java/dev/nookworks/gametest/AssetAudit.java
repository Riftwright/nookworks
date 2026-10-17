package dev.nookworks.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.fluid.FluidPipeBlock;
import dev.nookworks.rope.HungItemEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Walks every one of our blockstates and item models down to the PNGs they end at, and reports anything that
 * does not exist.
 * <p>
 * This exists because a model naming a texture that is not there renders as the black-and-magenta missing
 * texture and nothing else notices: no server-side test touches rendering, and a blockstate is only read when
 * a client loads resources. The Garden Loam shipped pointing at {@code minecraft:block/podzol}, which vanilla
 * does not have - it has {@code podzol_top} and {@code podzol_side} - and the only way that turned up was
 * launching the game and looking at the block. It is a cheap thing to check without a window.
 * <p>
 * Our own assets come off the classpath. Vanilla's do not: a dedicated server's classloader does not serve
 * client resources, so they are read straight out of the patched game jar the server is running from.
 */
public final class AssetAudit {
    /** Everything wrong, as human-readable lines. Empty means the assets all resolve. */
    public static List<String> run() {
        AssetAudit audit = new AssetAudit();
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            Identifier id = entry.getKey().identifier();
            if (id.getNamespace().equals(Nookworks.MODID)) {
                audit.blockstate(id);
                audit.occlusion(id, entry.getValue());
                if (entry.getValue() instanceof FluidPipeBlock pipe && pipe.pullFlange() != null) {
                    // drawn by a block entity renderer, so no blockstate names it
                    audit.model(pipe.pullFlange().toString(), "pulling arm of " + id.getPath());
                }
            }
        }
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Identifier id = entry.getKey().identifier();
            if (id.getNamespace().equals(Nookworks.MODID)) {
                audit.itemModel(id);
            }
        }
        for (Identifier drawn : HungItemEntity.SPRIGS.values()) { // client items with no item of their own
            audit.itemModel(drawn);
        }
        return audit.problems;
    }

    private final List<String> problems = new ArrayList<>();
    private final Set<String> visitedModels = new HashSet<>();

    private void blockstate(Identifier block) {
        String path = "assets/" + block.getNamespace() + "/blockstates/" + block.getPath() + ".json";
        JsonObject json = readJson(path, "blockstate for " + block);
        if (json == null) {
            return;
        }
        Set<String> models = new HashSet<>();
        if (json.has("variants")) {
            for (var variant : json.getAsJsonObject("variants").entrySet()) {
                collectModels(variant.getValue(), models);
            }
        }
        if (json.has("multipart")) {
            for (JsonElement part : json.getAsJsonArray("multipart")) {
                collectModels(part.getAsJsonObject().get("apply"), models);
            }
        }
        if (models.isEmpty()) {
            problems.add(path + " names no model at all");
        }
        for (String model : models) {
            model(model, "blockstate " + block.getPath());
        }
    }

    /**
     * A block drawn as a plain solid cube has to say it occludes, or the game treats it as something you can
     * see past: light leaks through it and its neighbours keep drawing the faces it covers, which looks for
     * all the world like the block has gone half transparent. The Mercury Tank and the Experience Tank both
     * had this after they were redrawn from an overlay into a solid cube and kept their old noOcclusion.
     */
    private void occlusion(Identifier id, Block block) {
        Set<String> models = new HashSet<>();
        JsonObject json = readJson("assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json",
                "blockstate for " + id);
        if (json == null) {
            return;
        }
        if (json.has("variants")) {
            for (var variant : json.getAsJsonObject("variants").entrySet()) {
                collectModels(variant.getValue(), models);
            }
        }
        if (json.has("multipart")) {
            for (JsonElement part : json.getAsJsonArray("multipart")) {
                collectModels(part.getAsJsonObject().get("apply"), models);
            }
        }
        if (models.isEmpty() || !models.stream().allMatch(this::isSolidCube)) {
            return;
        }
        for (var state : block.getStateDefinition().getPossibleStates()) {
            if (!state.canOcclude()) {
                problems.add(id + " is drawn as a solid cube but does not occlude; drop noOcclusion from it");
                return;
            }
        }
    }

    /** A model that is exactly vanilla's opaque cube, with nothing see-through about it. */
    private boolean isSolidCube(String modelId) {
        JsonObject json = readJson(modelPath(modelId), "model " + modelId);
        if (json == null || !json.has("parent") || json.has("render_type")) {
            return false;
        }
        String parent = json.get("parent").getAsString();
        return Set.of("minecraft:block/cube", "minecraft:block/cube_all", "minecraft:block/cube_column",
                "minecraft:block/cube_bottom_top").contains(parent);
    }

    private void itemModel(Identifier item) {
        String path = "assets/" + item.getNamespace() + "/items/" + item.getPath() + ".json";
        JsonObject json = readJson(path, "item model for " + item);
        if (json == null) {
            return;
        }
        Set<String> models = new HashSet<>();
        collectModels(json, models);
        for (String model : models) {
            model(model, "item " + item.getPath());
            this.fitsItsSlot(model, "item " + item.getPath());
            this.heldAsItShouldBe(model, "item " + item.getPath());
        }
    }

    /**
     * A small or low block model standing on its floor has to carry held, ground and frame transforms of its own:
     * vanilla's block/block ones are made for a full cube, and held in first person with them the Iron Pot and the Dice
     * showed a sliver at the edge of the screen. tools/gen_item_displays.py writes them; a generator that rewrote the
     * model without them makes this fail.
     */
    private void heldAsItShouldBe(String modelId, String usedBy) {
        JsonArray elements = null;
        boolean firstPerson = false;
        String current = modelId;
        Set<String> seen = new HashSet<>();
        while (current != null && !isBuiltin(current) && current.startsWith(Nookworks.MODID + ":") && seen.add(current)) {
            JsonObject json;
            try (InputStream stream = open(modelPath(current))) {
                if (stream == null) {
                    return;
                }
                json = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                return;
            }
            if (elements == null && json.has("elements")) {
                elements = json.getAsJsonArray("elements");
            }
            firstPerson |= json.has("display") && json.getAsJsonObject("display").has("firstperson_righthand");
            current = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        if (elements == null || elements.isEmpty() || firstPerson) {
            return;
        }
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (JsonElement e : elements) {
            float[] from = triple(e.getAsJsonObject(), "from", 0);
            float[] to = triple(e.getAsJsonObject(), "to", 0);
            for (int i = 0; i < 3; i++) {
                lo[i] = Math.min(lo[i], from[i]);
                hi[i] = Math.max(hi[i], to[i]);
            }
        }
        float height = hi[1] - lo[1];
        float across = Math.max(hi[0] - lo[0], hi[2] - lo[2]);
        if (lo[1] <= 1 && (height < 12 || across < 10)) {
            problems.add(String.format(Locale.ROOT, "%s -> %s: %.0f px high and %.0f across on the floor, held with a full "
                    + "block's transforms (run tools/gen_item_displays.py)", usedBy, modelId, height, across));
        }
    }

    /**
     * An item drawn from a block-style model stays inside its inventory slot: its elements, turned and scaled by the
     * model's "gui" transform as the game does it, cover no more than the slot's 16 px (half a pixel of slack). The
     * Grill's was lifted 4 px and stood out of the top of its slot. Flat item icons have no elements and are skipped.
     */
    private void fitsItsSlot(String modelId, String usedBy) {
        JsonArray elements = null;
        JsonObject gui = null;
        String current = modelId;
        Set<String> seen = new HashSet<>();
        while (current != null && !isBuiltin(current) && seen.add(current)) {
            JsonObject json;
            try (InputStream stream = open(modelPath(current))) {
                if (stream == null) {
                    return; // reported by model()
                }
                json = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                return;
            }
            if (elements == null && json.has("elements")) {
                elements = json.getAsJsonArray("elements");
            }
            if (gui == null && json.has("display") && json.getAsJsonObject("display").has("gui")) {
                gui = json.getAsJsonObject("display").getAsJsonObject("gui");
            }
            current = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        if (elements == null || elements.isEmpty()) {
            return;
        }
        float[] rotation = triple(gui, "rotation", 0);
        float[] translation = triple(gui, "translation", 0);
        float[] scale = triple(gui, "scale", 1);
        var turn = new Quaternionf().rotationXYZ((float) Math.toRadians(rotation[0]),
                (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[2]));
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (JsonElement e : elements) {
            JsonObject element = e.getAsJsonObject();
            float[] from = triple(element, "from", 0);
            float[] to = triple(element, "to", 0);
            for (int corner = 0; corner < 8; corner++) {
                var v = new Vector3f((corner & 1) == 0 ? from[0] : to[0], (corner & 2) == 0 ? from[1] : to[1],
                        (corner & 4) == 0 ? from[2] : to[2]);
                if (element.has("rotation")) {
                    JsonObject r = element.getAsJsonObject("rotation");
                    float[] origin = triple(r, "origin", 8);
                    v.sub(origin[0], origin[1], origin[2]);
                    if (r.has("axis")) {
                        float angle = (float) Math.toRadians(r.get("angle").getAsFloat());
                        switch (r.get("axis").getAsString()) {
                            case "x" -> v.rotateX(angle);
                            case "y" -> v.rotateY(angle);
                            default -> v.rotateZ(angle);
                        }
                    } else {
                        v.rotateX((float) Math.toRadians(r.has("x") ? r.get("x").getAsFloat() : 0))
                                .rotateY((float) Math.toRadians(r.has("y") ? r.get("y").getAsFloat() : 0))
                                .rotateZ((float) Math.toRadians(r.has("z") ? r.get("z").getAsFloat() : 0));
                    }
                    v.add(origin[0], origin[1], origin[2]);
                }
                // Model pixels to a block centred on the origin, then the display transform: translate, turn, scale.
                v.div(16).sub(0.5F, 0.5F, 0.5F).mul(scale[0], scale[1], scale[2]).rotate(turn)
                        .add(translation[0] / 16, translation[1] / 16, translation[2] / 16);
                float x = 8 + 16 * v.x;
                float y = 8 - 16 * v.y;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (minX < -0.5F || maxX > 16.5F || minY < -0.5F || maxY > 16.5F) {
            problems.add(String.format(Locale.ROOT, "%s -> %s: in a slot it spans x %.1f..%.1f, y %.1f..%.1f px (a slot is 0..16)",
                    usedBy, modelId, minX, maxX, minY, maxY));
        }
    }

    private static float[] triple(@Nullable JsonObject json, String key, float fallback) {
        if (json == null || !json.has(key)) {
            return new float[] {fallback, fallback, fallback};
        }
        var array = json.getAsJsonArray(key);
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    /** Pulls every "model": "id" out of a variant, an apply, or a whole item definition, arrays included. */
    private void collectModels(@Nullable JsonElement element, Set<String> into) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectModels(child, into);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (var entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (entry.getKey().equals("model") && value.isJsonPrimitive()) {
                into.add(value.getAsString());
            } else {
                collectModels(value, into);
            }
        }
    }

    /** Resolves a model and its whole parent chain, then checks every texture it ends up using. */
    private void model(String modelId, String usedBy) {
        if (!visitedModels.add(modelId + " <- " + usedBy)) {
            return;
        }
        Map<String, String> textures = new LinkedHashMap<>();
        Set<String> faceRefs = new HashSet<>();
        // The elements the model is drawn with: the nearest in the chain (a child's replace its parent's), if ours.
        JsonArray elements = null;
        boolean elementsOurs = false;
        String current = modelId;
        Set<String> seen = new HashSet<>();
        while (current != null && !isBuiltin(current)) {
            if (!seen.add(current)) {
                problems.add(usedBy + ": model parents loop at " + current);
                return;
            }
            JsonObject json = readJson(modelPath(current), usedBy + " -> model " + current);
            if (json == null) {
                return;
            }
            if (json.has("textures")) {
                for (var entry : json.getAsJsonObject("textures").entrySet()) {
                    // A child's own textures win, so only fill in what is still missing as we walk upwards.
                    textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                }
            }
            if (json.has("elements") && elements == null) {
                elements = json.getAsJsonArray("elements");
                elementsOurs = current.startsWith(Nookworks.MODID + ":");
            }
            if (json.has("elements")) {
                for (JsonElement element : json.getAsJsonArray("elements")) {
                    JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                    for (var face : faces.entrySet()) {
                        String texture = face.getValue().getAsJsonObject().get("texture").getAsString();
                        if (texture.startsWith("#")) {
                            faceRefs.add(texture.substring(1));
                        } else {
                            // the game reads a face's texture as a slot's name, with or without the #: a texture named
                            // here directly is a slot nothing defines, drawn as the missing texture
                            problems.add(usedBy + " -> " + current + ": a face names the texture " + texture
                                    + " directly; it must name a slot (#name) that the textures define");
                        }
                    }
                }
            }
            current = json.has("parent") ? json.get("parent").getAsString() : null;
        }
        for (String ref : faceRefs) {
            if (resolve(ref, textures, new HashSet<>()) == null) {
                problems.add(usedBy + " -> " + modelId + ": a face uses #" + ref + ", which nothing defines");
            }
        }
        if (elements != null && elementsOurs) {
            pixelForPixel(modelId, usedBy, elements, textures);
            noFacesFight(modelId, usedBy, elements, textures);
        }
        for (String key : textures.keySet()) {
            String texture = resolve(key, textures, new HashSet<>());
            if (texture == null) {
                problems.add(usedBy + " -> " + modelId + ": texture \"" + key + "\" points at a #ref that nothing defines");
                continue;
            }
            Identifier id = Identifier.tryParse(texture);
            if (id == null) {
                problems.add(usedBy + " -> " + modelId + ": \"" + texture + "\" is not a texture id");
                continue;
            }
            String png = "assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png";
            if (!has(png)) {
                problems.add(usedBy + " -> " + modelId + ": texture " + texture + " does not exist (" + png + ")");
            }
        }
    }

    /**
     * Every face of our own elements that names its uv shows its texture at the face's own size, one texel to a pixel
     * of the model (a texture drawn at 32 px counts its texels as half pixels). A uv the wrong way round, or taken from
     * the wrong axis, or the whole 16 px squeezed onto a small face, draws the texture stretched one way and squashed
     * the other beside faces that are sharp - it does not look missing, only wrong, so nothing else notices.
     * Parts under a pixel thick (cords, the edges of thin fittings) are left out: a texel is the least they can show.
     */
    private void pixelForPixel(String modelId, String usedBy, JsonArray elements, Map<String, String> textures) {
        for (JsonElement e : elements) {
            JsonObject element = e.getAsJsonObject();
            if (element.has("rotation") && element.getAsJsonObject("rotation").has("rescale")
                    && element.getAsJsonObject("rotation").get("rescale").getAsBoolean()) {
                continue;
            }
            var from = element.getAsJsonArray("from");
            var to = element.getAsJsonArray("to");
            float dx = Math.abs(to.get(0).getAsFloat() - from.get(0).getAsFloat());
            float dy = Math.abs(to.get(1).getAsFloat() - from.get(1).getAsFloat());
            float dz = Math.abs(to.get(2).getAsFloat() - from.get(2).getAsFloat());
            for (var entry : element.getAsJsonObject("faces").entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                if (!face.has("uv")) {
                    // The cube's own projection: pixel for pixel already - but taken from the element's bounds, so an
                    // element standing outside the block reaches past the texture's edge, and the model fails to bake.
                    if (Math.min(Math.min(from.get(0).getAsFloat(), from.get(1).getAsFloat()), from.get(2).getAsFloat()) < 0
                            || Math.max(Math.max(to.get(0).getAsFloat(), to.get(1).getAsFloat()), to.get(2).getAsFloat()) > 16) {
                        problems.add(usedBy + " -> " + modelId + ": a " + entry.getKey()
                                + " face of an element outside the block has no uv, so its own would fall off the texture");
                    }
                    continue;
                }
                float w = switch (entry.getKey()) { case "east", "west" -> dz; default -> dx; };
                float h = switch (entry.getKey()) { case "up", "down" -> dz; default -> dy; };
                int rotation = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
                if (rotation == 90 || rotation == 270) {
                    float t = w;
                    w = h;
                    h = t;
                }
                if (w < 1 || h < 1) {
                    continue;
                }
                var uv = face.getAsJsonArray("uv");
                float uw = Math.abs(uv.get(2).getAsFloat() - uv.get(0).getAsFloat());
                float vh = Math.abs(uv.get(3).getAsFloat() - uv.get(1).getAsFloat());
                String ref = face.get("texture").getAsString();
                String texture = ref.startsWith("#") ? resolve(ref.substring(1), textures, new HashSet<>()) : ref;
                Identifier id = texture == null ? null : Identifier.tryParse(texture);
                int size = id == null ? 16 : pngWidth("assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png");
                float scale = size / 16F;
                float across = uw * scale / w;
                float down = vh * scale / h;
                if (Math.abs(across - 1) > 0.34F || Math.abs(down - 1) > 0.34F) {
                    problems.add(String.format(Locale.ROOT, "%s -> %s: a %s face %.1fx%.1f px shows %.1fx%.1f texels of %s",
                            usedBy, modelId, entry.getKey(), w, h, uw * scale, vh * scale, texture));
                }
            }
        }
    }

    /**
     * No two of our elements lay faces in the same plane, facing the same way, partly over each other with different
     * textures: the depth test cannot tell such faces apart, and they flicker through each other as the view moves
     * (z-fighting) - a chair's back rail laid over its posts did. Faces over exactly the same rectangle are left out:
     * that is an overlay, drawn as vanilla draws the grass on a grass block's side. Rotated elements are not compared.
     */
    private void noFacesFight(String modelId, String usedBy, JsonArray elements, Map<String, String> textures) {
        for (int i = 0; i < elements.size(); i++) {
            for (int j = i + 1; j < elements.size(); j++) {
                JsonObject a = elements.get(i).getAsJsonObject();
                JsonObject b = elements.get(j).getAsJsonObject();
                if (a.has("rotation") || b.has("rotation")) {
                    continue;
                }
                float[] af = box(a, "from"), at = box(a, "to"), bf = box(b, "from"), bt = box(b, "to");
                for (var face : a.getAsJsonObject("faces").entrySet()) {
                    String side = face.getKey();
                    if (!b.getAsJsonObject("faces").has(side)) {
                        continue;
                    }
                    // the axis the face looks along, whether it is the box's far side, and the two axes it spans
                    int normal = switch (side) { case "east", "west" -> 0; case "up", "down" -> 1; default -> 2; };
                    boolean far = side.equals("east") || side.equals("up") || side.equals("south");
                    float ap = far ? at[normal] : af[normal];
                    float bp = far ? bt[normal] : bf[normal];
                    if (Math.abs(ap - bp) > 0.001F) {
                        continue;
                    }
                    int u = normal == 0 ? 2 : 0;
                    int v = normal == 1 ? 2 : 1;
                    float overlapU = Math.min(at[u], bt[u]) - Math.max(af[u], bf[u]);
                    float overlapV = Math.min(at[v], bt[v]) - Math.max(af[v], bf[v]);
                    if (overlapU <= 0.001F || overlapV <= 0.001F) {
                        continue;
                    }
                    boolean same = Math.abs(af[u] - bf[u]) < 0.001F && Math.abs(at[u] - bt[u]) < 0.001F
                            && Math.abs(af[v] - bf[v]) < 0.001F && Math.abs(at[v] - bt[v]) < 0.001F;
                    String ta = textureOf(face.getValue().getAsJsonObject(), textures);
                    String tb = textureOf(b.getAsJsonObject("faces").getAsJsonObject(side), textures);
                    if (!same && !Objects.equals(ta, tb)) {
                        problems.add(String.format(Locale.ROOT, "%s -> %s: two %s faces lie in one plane over %.1fx%.1f px (%s and %s)",
                                usedBy, modelId, side, overlapU, overlapV, ta, tb));
                    }
                }
            }
        }
    }

    private static float[] box(JsonObject element, String key) {
        var array = element.getAsJsonArray(key);
        float[] out = new float[3];
        for (int k = 0; k < 3; k++) {
            out[k] = array.get(k).getAsFloat();
        }
        return out;
    }

    private static @Nullable String textureOf(JsonObject face, Map<String, String> textures) {
        String ref = face.get("texture").getAsString();
        return ref.startsWith("#") ? resolve(ref.substring(1), textures, new HashSet<>()) : ref;
    }

    /** A PNG's width, from its header; 16 when it cannot be read (a missing one is reported on its own). */
    private static int pngWidth(String path) {
        try (InputStream in = open(path)) {
            if (in == null) {
                return 16;
            }
            byte[] head = in.readNBytes(24);
            return head.length < 24 ? 16
                    : (head[16] & 0xFF) << 24 | (head[17] & 0xFF) << 16 | (head[18] & 0xFF) << 8 | head[19] & 0xFF;
        } catch (IOException e) {
            return 16;
        }
    }

    /** Follows {@code "#other"} chains within a model's own texture map. */
    private static @Nullable String resolve(String key, Map<String, String> textures, Set<String> seen) {
        String value = textures.get(key);
        if (value == null || !seen.add(key)) {
            return null;
        }
        return value.startsWith("#") ? resolve(value.substring(1), textures, seen) : value;
    }

    /**
     * {@code builtin/generated} and {@code builtin/entity} are not files: the game builds those models itself,
     * the first from the item's own layer textures. A parent chain that reaches one simply ends there.
     */
    private static boolean isBuiltin(String modelId) {
        Identifier id = Identifier.tryParse(modelId);
        return (id == null ? modelId : id.getPath()).startsWith("builtin/");
    }

    private static String modelPath(String modelId) {
        Identifier id = Identifier.tryParse(modelId);
        String namespace = id == null ? "minecraft" : id.getNamespace();
        String path = id == null ? modelId : id.getPath();
        return "assets/" + namespace + "/models/" + path + ".json";
    }

    private @Nullable JsonObject readJson(String path, String what) {
        try (InputStream stream = open(path)) {
            if (stream == null) {
                problems.add(what + ": missing " + path);
                return null;
            }
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            problems.add(what + ": could not read " + path + " (" + e + ")");
            return null;
        }
    }

    /** Sanity check: without vanilla assets the audit would pass by seeing nothing. */
    public static boolean vanillaAssetsVisible() {
        return has("assets/minecraft/textures/block/stone.png") && has("assets/minecraft/models/block/cube.json");
    }

    /** Is there a resource at this path, in our jar or in the game's? */
    private static boolean has(String path) {
        if (AssetAudit.class.getResource("/" + path) != null) {
            return true;
        }
        ZipFile jar = gameJar();
        return jar != null && jar.getEntry(path) != null;
    }

    private static @Nullable InputStream open(String path) throws IOException {
        InputStream ours = AssetAudit.class.getResourceAsStream("/" + path);
        if (ours != null) {
            return ours;
        }
        ZipFile jar = gameJar();
        if (jar == null) {
            return null;
        }
        var entry = jar.getEntry(path);
        return entry == null ? null : jar.getInputStream(entry);
    }

    private static @Nullable ZipFile gameJar;
    private static boolean gameJarLooked;

    /**
     * The patched Minecraft jar. It is where the running game code came from, so its assets are the ones this
     * build would actually ship against.
     */
    private static synchronized @Nullable ZipFile gameJar() {
        if (gameJarLooked) {
            return gameJar;
        }
        gameJarLooked = true;
        try {
            var source = Blocks.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path path = Paths.get(source.getLocation().toURI());
                if (Files.isRegularFile(path)) {
                    gameJar = new ZipFile(path.toFile());
                    return gameJar;
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            // fall through to the well-known location
        }
        try (var stream = Files.list(Paths.get("build/moddev/artifacts"))) {
            Path jar = stream.filter(p -> p.getFileName().toString().matches("minecraft-patched-[^-]+[.]jar"))
                    .findFirst().orElse(null);
            if (jar != null) {
                gameJar = new ZipFile(jar.toFile());
            }
        } catch (IOException | RuntimeException e) {
            gameJar = null;
        }
        return gameJar;
    }

    private AssetAudit() {
        // built through run()
    }
}
