package dev.nookworks.port.render;

import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;

/**
 * 26.3's {@code ItemStackRenderState}: an item as it is to be drawn - which stack, where ({@link ItemDisplayContext}), in
 * what world, held by whom, and the seed its model is picked with ({@link ItemModelResolver} fills it). 1.21.1 draws an
 * item from exactly these with {@code ItemRenderer.renderStatic}, which is what {@link #submit} does: the item's model,
 * its display transform and its glint, as 26.3 draws it.
 */
public class ItemStackRenderState {
    private static final Direction[] SIDES = Direction.values();
    ItemStack stack = ItemStack.EMPTY;
    ItemDisplayContext displayContext = ItemDisplayContext.NONE;
    @Nullable Level level;
    @Nullable LivingEntity owner;
    int seed;
    /** A model of its own to draw the stack with, loaded on its own; null for the item's. */
    @Nullable ResourceLocation modelOverride;

    public void clear() {
        this.modelOverride = null;
        this.stack = ItemStack.EMPTY;
        this.displayContext = ItemDisplayContext.NONE;
        this.level = null;
        this.owner = null;
        this.seed = 0;
    }

    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    /** Whether the item is lit as a block is (a block's item) rather than flat (a sprite). */
    public boolean usesBlockLight() {
        return !this.isEmpty() && this.model().usesBlockLight();
    }

    /**
     * PORT: draws the stack with the model {@code modelId} (registered to be loaded on its own) instead of its item's - what
     * 26.3 does by setting the stack's {@code minecraft:item_model}, which 1.21.1 has no component for.
     */
    public void setModelOverride(@Nullable ResourceLocation modelId) {
        this.modelOverride = modelId;
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, int outlineColor) {
        if (this.isEmpty()) {
            return;
        }
        if (this.modelOverride != null) {
            Minecraft.getInstance().getItemRenderer().render(this.stack, this.displayContext, leftHand(this.displayContext), poseStack,
                    submitNodeCollector.buffers(), lightCoords, overlayCoords, this.model());
            return;
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(this.owner, this.stack, this.displayContext, leftHand(this.displayContext),
                poseStack, submitNodeCollector.buffers(), this.level, lightCoords, overlayCoords, this.seed);
    }

    /** The model drawn: the item's, with its overrides worked out for this stack. */
    BakedModel model() {
        if (this.modelOverride != null) {
            BakedModel own = Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(this.modelOverride));
            if (own != Minecraft.getInstance().getModelManager().getMissingModel()) {
                return own.getOverrides().resolve(own, this.stack, this.level instanceof net.minecraft.client.multiplayer.ClientLevel client
                        ? client : null, this.owner, this.seed);
            }
        }
        return Minecraft.getInstance().getItemRenderer().getModel(this.stack, this.level, this.owner, this.seed);
    }

    /**
     * Each corner of the item's quads where it is drawn - through its display transform, about its middle - as 26.3
     * gives a model's extents. An item drawn by code (a {@code builtin/entity} model) has none.
     */
    public void visitExtents(Consumer<Vector3fc> output) {
        if (this.isEmpty()) {
            return;
        }
        PoseStack pose = new PoseStack();
        BakedModel model = this.model().applyTransform(this.displayContext, pose, leftHand(this.displayContext));
        pose.translate(-0.5F, -0.5F, -0.5F);
        Vector3f scratch = new Vector3f();
        RandomSource random = RandomSource.create(42L);
        for (BakedModel pass : model.getRenderPasses(this.stack, true)) {
            for (int side = -1; side < SIDES.length; side++) {
                random.setSeed(42L);
                for (BakedQuad quad : pass.getQuads(null, side < 0 ? null : SIDES[side], random)) {
                    int[] data = quad.getVertices();
                    int stride = data.length / 4;
                    for (int v = 0; v < 4; v++) {
                        scratch.set(Float.intBitsToFloat(data[v * stride]), Float.intBitsToFloat(data[v * stride + 1]),
                                Float.intBitsToFloat(data[v * stride + 2]));
                        output.accept(scratch.mulPosition(pose.last().pose()));
                    }
                }
            }
        }
    }

    static boolean leftHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }
}
