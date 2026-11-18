package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.rope.HungBy;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import net.minecraft.client.Minecraft;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.BlockModelRenderState;
import dev.nookworks.port.render.BlockModelResolver;
import dev.nookworks.port.render.BlockStateModelPart;
import dev.nookworks.port.render.BlockDisplayContext;
import dev.nookworks.port.render.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import dev.nookworks.port.render.EntityRenderState;
import dev.nookworks.port.render.ItemModelResolver;
import dev.nookworks.port.render.TrackingItemStackRenderState;
import dev.nookworks.port.render.RenderTypes;
import dev.nookworks.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import dev.nookworks.port.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import dev.nookworks.port.render.SimpleUnbakedStandaloneModel;
import dev.nookworks.port.render.StandaloneModelKey;

/**
 * Draws something hung up: tied on the rope with a few turns of cord (so its string does not seem to sprout from the
 * rope), a bit of cord from the tie down to it, and it tied on by the end a thing is hung by - a bottle by its neck, a
 * fish by its tail, wheat and corn by their stalks, a flower upside down by its stem - and hanging from there as a thing
 * on a string does, side on to the rope (or facing whoever hung it from a rope's end), swaying a little. A Filament
 * Bulb is its block model, flex and all, its flex wound round the rope; a lantern is the lantern block, hanging, hooked
 * over the rope by its own loop, and glow berries the berried cave vine, its strands from the rope; sweet berries are a
 * sprig of the bush, as they hang on a Hook ({@link HungItemEntity#SPRIGS}). On a Thin Chain set swinging it goes with the chain, tie and all.
 */
public class HungItemRenderer extends EntityRenderer<HungItemEntity, HungItemRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> BULB = new StandaloneModelKey<>(() -> Nookworks.MODID + ":filament_bulb");
    /** The bulb switched off, its coil cold. */
    public static final StandaloneModelKey<BlockStateModelPart> BULB_OFF = new StandaloneModelKey<>(() -> Nookworks.MODID + ":filament_bulb_off");
    private static final int[] NO_TINTS = new int[0];
    /** How big a hung thing is drawn, as a share of a block. */
    private static final float SIZE = 0.45F;
    /** The tie: along the rope, and round it (the rope is a pixel thick). */
    static final float TIE_LENGTH = 1.5F / 16;
    static final float TIE_THICKNESS = 1.6F / 16;
    /** The tie is the string's cord, a shade darker than the rope; a bulb's, its black flex. */
    static final int TWINE = 0xFFC9BFAE;
    private static final int FLEX = 0xFF2C2A2E;
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();
    /**
     * A lantern's loop, or a vine's strands, reach to the rope's top, the rope half a pixel over the line it is drawn
     * along.
     */
    private static final float LANTERN_LIFT = 0.5F / 16;

    public static void registerModels(dev.nookworks.port.render.ModelEvent.RegisterStandalone event) {
        event.register(BULB, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/filament_bulb")));
        event.register(BULB_OFF, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/filament_bulb_off")));
        // PORT: 1.21.1 has no item model component to swap; each sprig's model (models/item/<name>) is loaded on its own
        for (ResourceLocation sprig : HungItemEntity.SPRIGS.values()) {
            event.register(new StandaloneModelKey<>(sprig::toString), SimpleUnbakedStandaloneModel.blockStateModel(sprig.withPrefix("item/")));
        }
    }

    /**
     * Where the cord ties on, for each way of hanging and each model a thing is drawn with: worked out once from the
     * model's outline, not every frame for every thing on every rope. A resource reload brings new models, so new keys;
     * the old ones are let go when there are many.
     */
    private static final Map<Object, float[]> TIES = new HashMap<>();

    public static final class State extends EntityRenderState {
        /** A tracking one, whose model identity keys {@link #TIES}; made afresh each frame, as it only ever adds to that. */
        TrackingItemStackRenderState item = new TrackingItemStackRenderState();
        Vec3 hang = Vec3.ZERO;
        float yaw;
        float sway;
        /** On a chain swinging: the way the chain has moved where it hangs, and the chain's angle there, in degrees. */
        Vec3 swungBy = Vec3.ZERO;
        float swing;
        boolean bulb;
        /** A bulb switched off: its coil cold, lit as the place it hangs in is. */
        boolean off;
        /** A mob's head or the dragon egg: drawn whole, as an item frame shows it, from a cord tied to its top. */
        boolean solid;
        /**
         * A lantern or glow berries: drawn as a block, hanging from the rope ({@link HungItemEntity#drawnBlock}); a vine
         * grown on as its pieces one under another, the tip last, each lit as the light where it hangs is.
         */
        boolean block;
        int pieces;
        final BlockModelRenderState[] blockModels = new BlockModelRenderState[HungItemEntity.VINE_MAX];
        final int[] pieceLight = new int[HungItemEntity.VINE_MAX];

        State() {
            for (int i = 0; i < this.blockModels.length; i++) {
                this.blockModels[i] = new BlockModelRenderState();
            }
        }
        /** How big the picture is drawn, a block across: a sprig of berries bigger than the rest. */
        float size = SIZE;
        boolean onSpan;
        /** Where the cord ties on, in the item's own frame (a block across, before it is scaled), and its turn. */
        float tieX;
        float tieY;
        float turn;
    }

    private final ItemModelResolver itemModels;
    private final BlockModelResolver blockModels;

    public HungItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModels = dev.nookworks.port.render.ItemModelResolver.get();
        this.blockModels = dev.nookworks.port.render.BlockModelResolver.get();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HungItemEntity hung, State state, float partialTicks) {
        super.extractRenderState(hung, state, partialTicks);
        state.hang = hung.hangPoint().subtract(hung.position());
        state.yaw = hung.yaw();
        state.sway = Mth.sin((hung.tickCount + partialTicks) * 0.06F + hung.getId() * 1.7F) * 4.0F;
        state.bulb = hung.isBulb();
        state.off = hung.switchedOff();
        state.solid = hung.isSolid();
        BlockState block = HungItemEntity.drawnBlock(hung.item());
        state.block = block != null;
        state.pieces = 0;
        if (block != null && HungItemEntity.isVine(hung.item())) {
            // the berries hung and what has grown under them: vanilla's vine body, its tip at the end
            state.pieces = hung.vineLength();
            Vec3 hang = hung.hangPoint();
            for (int piece = 0; piece < state.pieces; piece++) {
                BlockState drawn = (piece == state.pieces - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
                        .setValue(CaveVines.BERRIES, hung.vineBerries(piece));
                this.blockModels.update(state.blockModels[piece], drawn, BLOCK_DISPLAY_CONTEXT);
                state.pieceLight[piece] = piece == 0 ? state.lightCoords
                        : LightCoordsUtil.getLightCoords(hung.level(), BlockPos.containing(hang.x, hang.y - 0.5 - piece, hang.z));
            }
        } else if (block != null) {
            state.pieces = 1;
            this.blockModels.update(state.blockModels[0], block, BLOCK_DISPLAY_CONTEXT);
            state.pieceLight[0] = state.lightCoords;
        }
        state.size = (float) HungItemEntity.drawn(hung.item());
        state.onSpan = hung.onSpan();
        state.swungBy = Vec3.ZERO;
        state.swing = 0.0F;
        if (state.onSpan) {
            RopeKnotEntity.Swung swung = RopeKnotEntity.swungAt(hung.level(), hung.spanFromSeen(), hung.spanToSeen(), hung.hangPoint(), partialTicks);
            if (swung != null) {
                state.swungBy = swung.shift();
                state.swing = swing(swung, state.yaw);
            }
        }
        state.item = new TrackingItemStackRenderState();
        this.itemModels.updateForNonLiving(state.item, hung.item(), ItemDisplayContext.FIXED, hung);
        ResourceLocation sprig = drawnAs(hung.item());
        if (sprig != null) {
            state.item.setModelOverride(sprig.withPrefix("item/")); // PORT: in place of 26.3's swapped item model
        }
        if (!state.bulb && !state.solid && !state.block) {
            HungBy by = HungBy.of(hung.item());
            Object key = List.of(by, state.item.getModelIdentity());
            float[] tie = TIES.get(key);
            if (tie == null) {
                // Its outline, off its model: a flat item's edge faces trace its opaque pixels, so the tie follows
                // whatever picture it has, a resource pack's or another mod's. The picture's square adds only its corners.
                List<Vector3f> outline = new ArrayList<>();
                state.item.visitExtents(v -> {
                    if (Math.abs(v.x()) < 0.499F || Math.abs(v.y()) < 0.499F) {
                        outline.add(new Vector3f(v));
                    }
                });
                tie = by.tieOn(outline);
                if (TIES.size() > 1024) {
                    TIES.clear();
                }
                TIES.put(key, tie);
            }
            state.tieX = tie[0];
            state.tieY = tie[1];
            state.turn = tie[2];
        }
    }

    /**
     * The chain's angle, in degrees about the way a thing facing {@code yaw} is drawn along the rope (its x): the chain's
     * own, or turned back where the two run opposite ways.
     */
    static float swing(RopeKnotEntity.Swung swung, float yaw) {
        double along = Math.cos(Math.toRadians(yaw)) * swung.along().x + Math.sin(Math.toRadians(yaw)) * swung.along().z;
        return (float) Math.toDegrees(along < 0 ? -swung.angle() : swung.angle());
    }

    /**
     * What a hung thing is drawn as: berries as a sprig of their plant, unless the stack carries a model of its own.
     * PORT: 26.3 swaps the stack's {@code minecraft:item_model} for the sprig's; 1.21.1 has no such component, so this
     * gives the sprig's model id (null for the stack's own look) and the item is drawn with that model.
     */
    private static @org.jspecify.annotations.Nullable ResourceLocation drawnAs(ItemStack stack) {
        return HungItemEntity.SPRIGS.get(stack.getItem());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(state.hang.x + state.swungBy.x, state.hang.y + state.swungBy.y, state.hang.z + state.swungBy.z);
        dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(-state.yaw)); // x along the rope
        int tieLight = state.lightCoords;
        int tieColour = state.bulb ? FLEX : TWINE;
        boolean upright = !state.onSpan;
        if (!state.block) { // a lantern's own loop goes over the rope, a vine's strands round it: no cord
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(RopeDrawing.TEXTURE),
                    (p, buffer) -> RopeDrawing.tie(p, buffer, TIE_LENGTH, TIE_THICKNESS, upright, tieLight, tieColour));
        }
        if (state.swing != 0.0F) {
            dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(state.swing)); // swung out with the chain, about the way it runs
        }
        if (state.block) {
            // swaying about its top, a little, as a bulb sways about its flex; the block's sides along the rope
            dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(state.sway * 0.5F));
            pose.translate(-0.5F, LANTERN_LIFT - 1.0F, -0.5F); // the model's top, a loop or the strands, on the rope's top
            // lit by its own light as the block is (a lantern's flame, the berries' glow; none put out), or by the light
            // where it hangs if that is more
            for (int piece = 0; piece < state.pieces; piece++) {
                int light = LightCoordsUtil.max(state.pieceLight[piece], state.blockModels[piece].blockLightCoords);
                state.blockModels[piece].submitMultiLayer(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
                pose.translate(0.0F, -1.0F, 0.0F); // the next piece a block under it
            }
            pose.popPose();
            return;
        }
        dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(state.yaw));
        if (state.bulb) {
            BlockStateModelPart bulb = dev.nookworks.port.render.PortModelManager.of(Minecraft.getInstance().getModelManager()).getStandaloneModel(state.off ? BULB_OFF : BULB);
            if (bulb != null) {
                dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(state.sway * 0.5F));
                pose.translate(-0.5F, -1.0F, -0.5F); // the model's top, where its flex ends, at the rope
                // lit by itself: the coil glows, and the glass and the cap round it with it; off, as the place is lit
                collector.submitMultiLayerBlockModel(pose, List.of(bulb), false, NO_TINTS,
                        state.off ? state.lightCoords : LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            }
            pose.popPose();
            return;
        }
        dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(-state.yaw));
        dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(state.sway));
        int light = state.lightCoords;
        Vec3[] string = {Vec3.ZERO, new Vec3(0, -HungItemEntity.STRING, 0)};
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(RopeDrawing.TEXTURE),
                (p, buffer) -> RopeDrawing.cord(p, buffer, string, new int[] {light, light}));
        pose.translate(0, -HungItemEntity.STRING, 0); // the cord's end, where it is tied on
        if (state.solid) {
            // as an item frame shows it - half a block about its middle, a head's face out along -z, here the way it
            // faces - its top (a head's crown, the egg's tip) at the cord's end
            pose.translate(0, -HungItemEntity.SOLID_SIZE / 2, 0);
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
            return;
        }
        dev.nookworks.port.render.Poses.rotate(pose, Axis.ZP.rotation(state.turn));
        pose.scale(state.size, state.size, state.size);
        pose.translate(-state.tieX, -state.tieY, 0);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
