package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.Plating;
import dev.nookworks.cooking.Serving;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Lots of food as they lie on a Plate or a Wooden Tray: one kind of thing, one of it or several ({@link Serving}), each
 * drawn as the food itself ({@link DishModels}) where it has a model. Several of a thing are laid side by side in the
 * room the lot has, in rows, and what does not fit flat lies on top of the others (a fifth cookie on four, doughnuts in
 * a pile) - worked out from the model's own size, so a resource pack's model is laid by its own. No two pieces touch:
 * faces of two pieces in one plane flicker (the user, 2026-11-14: テクスチャやブロックが重なってチカチカしたり).
 * <p>
 * Shared by the plate's and the tray's renderers and by the tray's item, which shows what was on it when it was lifted.
 */
public final class TableLots {
    public static final StandaloneModelKey<BlockStateModelPart> BALL = new StandaloneModelKey<>(() -> Nookworks.MODID + ":dough_ball");
    public static final StandaloneModelKey<BlockStateModelPart> RISEN_BALL = new StandaloneModelKey<>(() -> Nookworks.MODID + ":dough_ball_risen");
    /** A plate under a dish that is served on its own, where no Plate block is (on a tray). */
    public static final StandaloneModelKey<BlockStateModelPart> PLATE = new StandaloneModelKey<>(() -> Nookworks.MODID + ":plate_under_dish");
    private static final int[] NO_TINTS = new int[0];
    /** Between two pieces lying side by side, in pixels. */
    private static final float GAP = 0.15F;
    /** Each piece a hair above the one before: should two ever overlap, their faces are not in one plane. */
    private static final float LIFT = 0.02F;
    /** How much smaller than its lot's scale a thing too wide for its room is drawn, at most. */
    private static final float SMALLEST = 0.78F;
    /** The plate's top above its foot, in pixels: where a dish sits on it. */
    private static final float PLATE_TOP = 1;
    /** A little turn to each lot of a tray, so four loaves do not lie like soldiers. */
    private static final float[] TWIST = {-8, 6, 10, -5};

    private TableLots() {}

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(BALL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/dough_ball")));
        event.register(RISEN_BALL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/dough_ball_risen")));
        event.register(PLATE, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/plate")));
    }

    enum Kind { ITEM, DOUGH, RISEN, DISH, PLATE }

    /** How much smaller than the plate itself food is drawn on it, and the room on a plate, in pixels at the plate's own size. */
    public static final float ON_PLATE = 0.85F;
    public static final float PLATE_ROOM = 10.6F;

    /** One lot, as it is drawn. */
    public static final class Lot {
        final ItemStackRenderState item = new ItemStackRenderState();
        @Nullable StandaloneModelKey<BlockStateModelPart> dish;
        Kind kind = Kind.ITEM;
        int count;
        float rise;
        /** On its own plate: on a tray the plate is drawn under it. */
        boolean plated;
        /** Several of it on a Plate lean one on the next ({@link Serving#leaning}). */
        boolean leaning;
        /** For a Plate set on a tray: what lies on it, or null for an empty plate. */
        @Nullable Lot on;
        Serving.Room room = Serving.Room.QUARTER;
    }

    /** The look of {@code stack} as a lot; {@code rise} is how far a piece of dough has risen, 0 to 1. */
    public static Lot lot(ItemStack stack, float rise, ItemModelResolver itemModels, @Nullable Level level, int seed) {
        Lot lot = new Lot();
        if (Plating.isPlate(stack)) {
            // a plate with its food, as one lot: the plate, and the food as a lot on it
            ItemStack food = Plating.onPlate(stack);
            lot.kind = Kind.PLATE;
            lot.count = 1;
            lot.on = food.isEmpty() ? null : lot(food, 0, itemModels, level, seed);
            return lot;
        }
        lot.count = stack.getCount();
        lot.rise = rise;
        lot.dish = DishModels.of(stack);
        lot.plated = Plating.plated(stack);
        Serving serving = Serving.of(stack);
        lot.room = serving.room();
        lot.leaning = serving.leaning();
        lot.kind = stack.is(ModKitchen.BREAD_DOUGH.get()) ? Kind.DOUGH : stack.is(ModKitchen.RISEN_DOUGH.get()) ? Kind.RISEN
                : lot.dish != null ? Kind.DISH : Kind.ITEM;
        if (lot.kind == Kind.ITEM) {
            itemModels.updateForTopItem(lot.item, stack, ItemDisplayContext.FIXED, level, null, seed);
        }
        return lot;
    }

    /**
     * Where {@code count} pieces lie in a room {@code roomW} across and {@code roomD} deep, each {@code w} by {@code d}
     * and {@code h} high (all in pixels, as drawn): {x, y, z} from the middle of the room, at its floor. As many rows
     * across as fit, the last row centred; what does not fit flat goes on top, laid the same way.
     */
    public static float[][] places(int count, float w, float d, float h, float roomW, float roomD) {
        float[][] at = new float[count][];
        int across = Math.max(1, (int) ((roomW + GAP) / (w + GAP)));
        int deep = Math.max(1, (int) ((roomD + GAP) / (d + GAP)));
        int flat = across * deep;
        int done = 0;
        for (int layer = 0; done < count; layer++) {
            int n = Math.min(count - done, flat);
            int rows = (n + across - 1) / across;
            int perRow = (n + rows - 1) / rows;
            for (int i = 0; i < n; i++) {
                int row = i / perRow;
                int inRow = Math.min(perRow, n - row * perRow);
                int col = i - row * perRow;
                float x = (col - (inRow - 1) / 2F) * (w + GAP);
                float z = (row - (rows - 1) / 2F) * (d + GAP);
                at[done + i] = new float[] {x, layer * h + (done + i) * LIFT, z};
            }
            done += n;
        }
        return at;
    }

    /**
     * Draws {@code lot} with the pose at the middle of its room, on the surface it lies on, facing the front: at
     * {@code scale}, its pieces within {@code roomW} by {@code roomD} pixels. {@code turn} is the lot's own little
     * twist, in degrees. {@code onPlate}: on a Plate, where several of a food that leans are leant one on the next.
     */
    public static void draw(Lot lot, float scale, float roomW, float roomD, float turn, boolean onPlate, int light, PoseStack pose,
            SubmitNodeCollector collector) {
        switch (lot.kind) {
            case DOUGH, RISEN -> ball(lot, scale, turn, light, pose, collector);
            case ITEM -> item(lot, scale, turn, light, pose, collector);
            case PLATE -> {
                // the plate as large as its room lets it be, and its food on it as on a plate on the table
                float plate = plateScale(scale, roomW, roomD);
                pose.pushPose();
                pose.rotate(Axis.YP.rotationDegrees(turn));
                DishModels.draw(PLATE, plate, light, pose, collector);
                if (lot.on != null) {
                    pose.translate(0, PLATE_TOP * plate / 16, 0);
                    draw(lot.on, plate * (lot.on.kind == Kind.ITEM ? 1 : ON_PLATE), PLATE_ROOM * plate, PLATE_ROOM * plate, 0, true, light, pose, collector);
                }
                pose.popPose();
            }
            case DISH -> {
                pose.pushPose();
                if (lot.plated) {
                    // a plate of its own under it: a tray has none (on a Plate block the block is its plate)
                    float plate = plateScale(scale, roomW, roomD);
                    DishModels.draw(PLATE, plate, light, pose, collector);
                    pose.translate(0, PLATE_TOP * plate / 16, 0);
                    scale = plate * ON_PLATE;
                    roomW = roomD = PLATE_ROOM * plate;
                }
                float[] size = DishModels.size(lot.dish);
                if (lot.count == 1) {
                    // one thing alone takes the lot's twist, and where it is wider than its room it is drawn a little
                    // smaller, to keep clear of what lies beside it (a loaf, turned, in a quarter of a tray)
                    double t = Math.toRadians(Math.abs(turn));
                    float across = (float) (size[0] * Math.cos(t) + size[1] * Math.sin(t)) * scale;
                    float deep = (float) (size[1] * Math.cos(t) + size[0] * Math.sin(t)) * scale;
                    float fit = Math.clamp(Math.min((roomW - GAP) / across, (roomD - GAP) / deep), SMALLEST, 1);
                    pose.rotate(Axis.YP.rotationDegrees(turn));
                    DishModels.draw(lot.dish, scale * fit, light, pose, collector);
                } else if (onPlate && lot.leaning) {
                    leaning(lot, size, scale, light, pose, collector);
                } else {
                    // several lie square to one another, each about the middle of its own place; what lies on top is turned
                    float[][] places = places(lot.count, size[0] * scale, size[1] * scale, size[2] * scale, roomW, roomD);
                    for (int i = 0; i < places.length; i++) {
                        boolean onTop = places[i][1] >= size[2] * scale;
                        pose.pushPose();
                        // what lies on top is set down a little off the one below, as a hand stacks a tower
                        pose.translate((places[i][0] + (onTop ? (i * 7 % 3 - 1) * 0.15F : 0)) / 16, places[i][1] / 16,
                                (places[i][2] + (onTop ? (i * 5 % 3 - 1) * 0.15F : 0)) / 16);
                        pose.rotate(Axis.YP.rotationDegrees(onTop ? 14 * (i % 2 == 0 ? 1 : -1) : 0));
                        pose.translate(-size[3] * scale / 16, 0, -size[4] * scale / 16);
                        DishModels.draw(lot.dish, scale, light, pose, collector);
                        pose.popPose();
                    }
                }
                pose.popPose();
            }
        }
    }

    /** A plate's size in a room: the lot's own scale, or smaller where the plate would not fit (a quarter of a tray). */
    private static float plateScale(float scale, float roomW, float roomD) {
        return Math.min(scale, (Math.min(roomW, roomD) - GAP) / DishModels.size(PLATE)[0]);
    }

    /** How far a leaning piece is tipped, in degrees, and the air left between it and what it leans on, in pixels. */
    private static final float LEAN = 18;
    private static final float CLEAR = 0.06F;
    /** Leaning pieces go this many to a row, the rows this far apart, and each row before the last a little to the right. */
    private static final int LEAN_ROW = 3;
    private static final float ROW_GAP = 0.8F;
    private static final float ROW_SHIFT = 0.7F;
    /** Each piece of a row a little nearer or further than the one before, in pixels: none of their sides in one plane. */
    private static final float[] STAGGER = {0, 0.3F, -0.25F};

    /**
     * Several of a flat thing as they are set out on a plate (the user's photograph of three cookies, 2026-11-14): the
     * first lies flat at the left, the next stands on its edge beside it and leans on it, the third leans on the second,
     * each showing most of its face. More than three make a second row before the first, of the two that are over. The
     * places are worked out from the piece's own width and thickness so that each rests on the last with a hair of air
     * between: none runs into another, the rows stand clear of each other, and no two faces lie in one plane - where
     * they did, they would flicker.
     */
    private static void leaning(Lot lot, float[] size, float scale, int light, PoseStack pose, SubmitNodeCollector collector) {
        float w = size[0] * scale;
        float d = size[1] * scale;
        float t = size[2] * scale;
        double tip = Math.toRadians(LEAN);
        float first = (float) (t / Math.tan(tip)) + CLEAR; // from the flat one's edge to where the next one's edge stands
        float pitch = (float) (t / Math.sin(tip)) + CLEAR; // and from each leaning one's foot to the next one's
        int rows = (lot.count + LEAN_ROW - 1) / LEAN_ROW;
        int done = 0;
        for (int row = 0; row < rows; row++) {
            int n = (lot.count - done + rows - row - 1) / (rows - row); // the back row the fuller: 3 and 2 of five
            float span = w + (n > 1 ? first + (n - 2) * pitch + (float) (t * Math.sin(tip)) : 0);
            float left = -span / 2 + (rows - 1 - row) * -ROW_SHIFT / 2 + row * ROW_SHIFT / 2;
            float back = (row - (rows - 1) / 2F) * (d + ROW_GAP);
            for (int i = 0; i < n; i++) {
                pose.pushPose();
                float z = back + STAGGER[i % STAGGER.length] * scale;
                if (i == 0) {
                    pose.translate((left + w / 2) / 16, 0, z / 16);
                } else {
                    pose.translate((left + w + first + (i - 1) * pitch) / 16, 0, z / 16); // the edge it stands on
                    pose.rotate(Axis.ZP.rotationDegrees(-LEAN));
                    pose.translate(-w / 2 / 16, 0, 0);
                }
                pose.translate(-size[3] * scale / 16, 0, -size[4] * scale / 16);
                DishModels.draw(lot.dish, scale, light, pose, collector);
                pose.popPose();
            }
            done += n;
        }
    }

    /** A ball of dough, swelling towards the risen ball as it proofs. */
    private static void ball(Lot lot, float scale, float turn, int light, PoseStack pose, SubmitNodeCollector collector) {
        boolean risen = lot.kind == Kind.RISEN;
        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(risen ? RISEN_BALL : BALL);
        if (model == null) {
            return;
        }
        float t = risen ? 0 : lot.rise;
        // the raw ball is 3 px high and 6 wide, the risen one 5 and 7: it goes up more than it spreads
        pose.pushPose();
        pose.rotate(Axis.YP.rotationDegrees(turn * 2));
        pose.scale(scale * (1 + 0.2F * t), scale * (1 + 0.6F * t), scale * (1 + 0.2F * t));
        pose.translate(-0.5F, 0, -0.5F);
        collector.submitMultiLayerBlockModel(pose, List.of(model), false, NO_TINTS, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** Food with no model lies flat as its item's picture, its top away from the front; a block's item stands. */
    private static void item(Lot lot, float scale, float turn, int light, PoseStack pose, SubmitNodeCollector collector) {
        float size = 0.55F * scale;
        pose.pushPose();
        pose.rotate(Axis.YP.rotationDegrees(turn));
        if (lot.item.usesBlockLight()) {
            pose.translate(0, 0.25F * size, 0); // a block's item model is half a block across, centred
            pose.scale(size, size, size);
        } else {
            pose.translate(0, 0.02F, 0);
            pose.rotate(Axis.XP.rotationDegrees(-90)); // face up, its top to the back
            pose.scale(size, size, size);
        }
        lot.item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** A tray's board, where things sit: its top, and the room inside its rim, in pixels. */
    private static final float TRAY_TOP = 1;
    private static final float BOARD = 12;

    /**
     * Draws what lies on a tray: {@code lots} slot by slot (null for an empty slot) and {@code places} from
     * {@link Serving#trayPlaces} for the same slots, the pose at the tray block's corner with its front to the south.
     * One lot alone is drawn full size in the middle of the board; sharing it, each a little smaller in its own room -
     * a quarter of the board, or half of it across (the user, 2026-10-31, of dishes sharing a tray).
     */
    public static void drawTray(List<@Nullable Lot> lots, float @Nullable [][] places, int light, PoseStack pose, SubmitNodeCollector collector) {
        int n = (int) lots.stream().filter(lot -> lot != null).count();
        if (n == 0 || places == null) {
            return;
        }
        for (int slot = 0; slot < lots.size(); slot++) {
            Lot lot = lots.get(slot);
            if (lot == null || places[slot] == null) {
                continue;
            }
            pose.pushPose();
            pose.translate(places[slot][0] / 16, TRAY_TOP / 16, places[slot][1] / 16);
            boolean ball = lot.kind == Kind.DOUGH || lot.kind == Kind.RISEN;
            boolean half = lot.room == Serving.Room.HALF;
            // a long thing lies square across the board: turned, it would have to be drawn smaller to stay in its half
            float turn = lot.room == Serving.Room.QUARTER ? TWIST[slot % TWIST.length] : 0;
            if (n == 1) {
                // balls cut from one dough: larger the fewer of them, as they were
                draw(lot, ball ? 1.1F : 1.0F, BOARD, BOARD, turn, false, light, pose, collector);
            } else {
                draw(lot, ball && n == 2 ? 0.85F : 0.72F, half ? BOARD : BOARD / 2, BOARD / 2, turn, false, light, pose, collector);
            }
            pose.popPose();
        }
    }

    /** The lots of a tray's slots, for {@link #drawTray}. */
    public static List<@Nullable Lot> lots(List<ItemStack> slots, java.util.function.IntFunction<Float> rise, ItemModelResolver itemModels,
            @Nullable Level level, int seed) {
        List<@Nullable Lot> lots = new ArrayList<>(slots.size());
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            lots.add(stack.isEmpty() ? null : lot(stack, rise.apply(slot), itemModels, level, seed + slot));
        }
        return lots;
    }
}
