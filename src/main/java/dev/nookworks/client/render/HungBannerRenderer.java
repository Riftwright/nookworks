package dev.nookworks.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.nookworks.rope.HungBannerEntity;
import dev.nookworks.rope.RopeKnotEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a banner hung on Thin Rope as vanilla draws a banner on a wall: its bar and its flag, the flag in its patterns
 * and waving ({@link BannerRenderer#submitPatterns}). On a rope strung between posts the bar is lashed under the rope
 * at both ends and the cloth hangs straight down, leaning with the rope as it slopes across the banner - its top edge
 * along the rope, its sides upright, as cloth hangs from a sloping line. From a rope's end a cord runs from both ends
 * of the bar up to the rope, as a hanging scroll hangs. On a Thin Chain set swinging it swings with the chain.
 */
public class HungBannerRenderer extends EntityRenderer<HungBannerEntity, HungBannerRenderer.State> {
    private static final float SCALE = (float) HungBannerEntity.SCALE;
    /** The top of the wall banner model's bar, in the middle of its thickness: the point it is hung by. */
    private static final float BAR_TOP = -20.5F / 16;
    private static final float BAR_MIDDLE = 10.5F / 16;
    private static final float BAR = (float) HungBannerEntity.BAR;
    private static final float TIE_X = (float) HungBannerEntity.TIE_X;
    private static final float ROPE = (float) (2 * HungBannerEntity.ROPE_RADIUS);
    private static final float BRIDLE = (float) HungBannerEntity.BRIDLE;
    /** A lashing: a pixel and a half across, standing a third of a pixel out from what it is wound round. */
    private static final float LASH_HALF = 0.75F / 16;
    private static final float LASH = 0.3F / 16;
    /** The cord from each end of the bar up to a rope's end. */
    private static final Vec3[] LEFT_CORD = {new Vec3(0, BRIDLE, 0), new Vec3(-TIE_X, 0, 0)};
    private static final Vec3[] RIGHT_CORD = {new Vec3(0, BRIDLE, 0), new Vec3(TIE_X, 0, 0)};

    public static final class State extends EntityRenderState {
        DyeColor base = DyeColor.WHITE;
        BannerPatternLayers patterns = BannerPatternLayers.EMPTY;
        float yaw;
        float slope;
        float phase;
        boolean onSpan;
        /** On a chain swinging: the way the chain has moved over the bar, and its angle there, in degrees. */
        Vec3 swungBy = Vec3.ZERO;
        float swing;
    }

    private final SpriteGetter sprites;
    private final BannerModel bar;
    private final BannerFlagModel flag;
    /**
     * Its turn and its lean (height rising with width as the rope rises), kept rather than made each frame: the pose
     * takes them in as they are given.
     */
    private final Quaternionf turn = new Quaternionf();
    private final Matrix4f lean = new Matrix4f();

    public HungBannerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.sprites = context.getSprites();
        // the wall banner's: its bar, without the pole a standing one has, and its flag hanging from the bar's top
        this.bar = new BannerModel(context.bakeLayer(ModelLayers.WALL_BANNER));
        this.flag = new BannerFlagModel(context.bakeLayer(ModelLayers.WALL_BANNER_FLAG));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HungBannerEntity banner, State state, float partialTicks) {
        super.extractRenderState(banner, state, partialTicks);
        ItemStack item = banner.item();
        state.base = item.getItem() instanceof BannerItem bannerItem ? bannerItem.getColor() : DyeColor.WHITE;
        state.patterns = item.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        state.yaw = banner.yaw();
        state.slope = banner.slope();
        state.onSpan = banner.onSpan();
        state.swungBy = Vec3.ZERO;
        state.swing = 0.0F;
        if (state.onSpan) {
            Vec3 bar = banner.position().add(0, HungBannerEntity.HEIGHT + HungBannerEntity.ROPE_RADIUS, 0);
            RopeKnotEntity.Swung swung = RopeKnotEntity.swungAt(banner.level(), banner.spanFromSeen(), banner.spanToSeen(), bar, partialTicks);
            if (swung != null) {
                state.swungBy = swung.shift();
                state.swing = HungItemRenderer.swing(swung, state.yaw);
            }
        }
        // vanilla's wave, five seconds a sway, set off by where it hangs - its exact place, where vanilla's goes by the
        // block - so along a rope each sways a little after the one before
        Vec3 at = banner.position();
        state.phase = (float) (Mth.positiveModulo(at.x * 7 + at.y * 9 + at.z * 13 + banner.level().getGameTime() + partialTicks, 100.0) / 100.0);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(state.swungBy.x, HungBannerEntity.HEIGHT + state.swungBy.y, state.swungBy.z); // the top of the bar, in its middle
        pose.rotate(this.turn.rotationY(-state.yaw * Mth.DEG_TO_RAD)); // x along the rope, the banner's face to z
        if (state.swing != 0.0F) {
            pose.rotate(Axis.XP.rotationDegrees(state.swing)); // swung out with the chain it hangs on
        }
        if (state.slope != 0.0F) {
            pose.mulPose(this.lean.identity().m01(state.slope));
        }
        int light = state.lightCoords;
        boolean onSpan = state.onSpan;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(RopeDrawing.TEXTURE), (p, buffer) -> ties(p, buffer, onSpan, light));
        pose.scale(SCALE, -SCALE, -SCALE); // as vanilla draws one
        pose.translate(0, -BAR_TOP, -BAR_MIDDLE);
        collector.submitModel(this.bar, Unit.INSTANCE, pose, light, OverlayTexture.NO_OVERLAY, -1, Sheets.BANNER_BASE, this.sprites,
                state.outlineColor);
        collector.submitModel(this.flag, state.phase, pose, light, OverlayTexture.NO_OVERLAY, -1, Sheets.BANNER_BASE, this.sprites,
                state.outlineColor);
        BannerRenderer.submitPatterns(this.sprites, pose, collector, light, OverlayTexture.NO_OVERLAY, this.flag, state.phase, true,
                state.base, state.patterns);
        pose.popPose();
    }

    /**
     * What ties it on, about the top of the bar: at each end of the bar a lashing round it and the top of the cloth in
     * front of it - on a span, round the rope over it too; from a rope's end, the cord from there up to the rope, and a
     * tie round the rope's end where the two meet.
     */
    private static void ties(PoseStack.Pose pose, VertexConsumer buffer, boolean onSpan, int light) {
        float top = onSpan ? ROPE + LASH : LASH;
        lash(pose, buffer, -TIE_X, top, light);
        lash(pose, buffer, TIE_X, top, light);
        if (!onSpan) {
            RopeDrawing.cord(pose, buffer, LEFT_CORD, new int[] {light, light});
            RopeDrawing.cord(pose, buffer, RIGHT_CORD, new int[] {light, light});
            float r = HungItemRenderer.TIE_THICKNESS / 2;
            RopeDrawing.lashing(pose, buffer, -r, BRIDLE - LASH, -r, r, BRIDLE + HungItemRenderer.TIE_LENGTH, r, light,
                    HungItemRenderer.TWINE);
        }
    }

    /** A lashing at {@code x} along the bar, from under the bar up to {@code top}, round the cloth's top in front of it. */
    private static void lash(PoseStack.Pose pose, VertexConsumer buffer, float x, float top, int light) {
        RopeDrawing.lashing(pose, buffer, x - LASH_HALF, -BAR - LASH, -BAR / 2 - LASH, x + LASH_HALF, top, BAR + LASH, light,
                HungItemRenderer.TWINE);
    }
}
