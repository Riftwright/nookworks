package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PotRecipe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Draws what is in an Iron Pot or a Frying Pan (trial of the cooking review, 2026-11-14; it drew every thing in the pot
 * as its item's picture floating on a pool that was always full and always blue):
 * <ul>
 * <li>the water or oil at the height of how much there is, the water taking the colour of what cooks in it (the
 * recipe's {@code color}: a stew's white, a soup's amber), or of milk once milk is in;</li>
 * <li>the ingredients as small models of themselves ({@link DishModels.Place#POT}, {@link DishModels.Place#PAN}), up to
 * three of a kind, rocking on the surface while it simmers. What dissolves or is poured ({@code #nookworks:dissolves_in_pot}:
 * flour, sugar, salt, milk) is not drawn; anything else with no model lies there as its item, as before;</li>
 * <li>the dish waiting: one ladled out by the bowl as the pot's whole surface (the soup itself, lower with each
 * serving), one taken by hand as its pieces in what is left of the oil.</li>
 * </ul>
 */
public class IronPotRenderer implements BlockEntityRenderer<IronPotBlockEntity, IronPotRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> POT_WATER = key("pot_water");
    public static final StandaloneModelKey<BlockStateModelPart> POT_OIL = key("pot_oil");
    public static final StandaloneModelKey<BlockStateModelPart> POT_BROTH = key("pot_broth");
    public static final StandaloneModelKey<BlockStateModelPart> PAN_OIL = key("pan_oil");
    /** What goes into the liquid and is seen no more: flour, sugar, salt, milk. */
    public static final TagKey<Item> DISSOLVES = TagKey.create(Registries.ITEM, Nookworks.id("dissolves_in_pot"));
    private static final int WATER = 0xFF3F76E4;
    private static final int MILK = 0xFFF1EFE6;
    /** The most pieces drawn: one in the middle and a ring of six. */
    private static final int MOST = 7;

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> Nookworks.MODID + ":" + name);
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(POT_WATER, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/pot_water")));
        event.register(POT_OIL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/pot_oil")));
        event.register(POT_BROTH, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/pot_broth")));
        event.register(PAN_OIL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/pan_oil")));
    }

    /** One thing lying in the vessel: its model, or with none its item. */
    record Piece(@Nullable StandaloneModelKey<BlockStateModelPart> model, @Nullable ItemStackRenderState item) {}

    public static final class State extends BlockEntityRenderState {
        final List<Piece> pieces = new ArrayList<>();
        /** A dish that fills the vessel, drawn once. */
        @Nullable StandaloneModelKey<BlockStateModelPart> whole;
        @Nullable StandaloneModelKey<BlockStateModelPart> liquid;
        int[] tints = new int[] {-1};
        boolean pan;
        /** Where the middle of the vessel is, from the block's corner, in blocks. */
        float x = 0.5F;
        float z = 0.5F;
        /** The vessel's floor, and the top of what stands in it. */
        float floor;
        float level;
        boolean wet;
        boolean simmering;
        double ticks;
    }

    private final ItemModelResolver itemModels;

    public IronPotRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(IronPotBlockEntity pot, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pot, state, partialTicks, camera, breakProgress);
        state.pieces.clear();
        state.whole = null;
        state.liquid = null;
        var block = pot.getBlockState();
        Level level = pot.getLevel();
        boolean onGrill = block.hasProperty(IronPotBlock.ON_GRILL) && block.getValue(IronPotBlock.ON_GRILL);
        state.pan = pot.pan();
        state.simmering = block.hasProperty(IronPotBlock.COOKING) && block.getValue(IronPotBlock.COOKING);
        // the bubbling's periods are no whole number of ticks, so no cycle of the time wraps cleanly: the angle is worked
        // out from the whole time, in doubles, and only the angle is a float
        long now = level == null ? 0 : level.getGameTime();
        state.ticks = now + (double) partialTicks;
        // the pan's bowl is a pixel off the block's middle, away from its handle
        Direction facing = block.hasProperty(IronPotBlock.FACING) ? block.getValue(IronPotBlock.FACING) : Direction.NORTH;
        state.x = 0.5F - (state.pan ? facing.getStepX() / 16.0F : 0);
        state.z = 0.5F - (state.pan ? facing.getStepZ() / 16.0F : 0);
        state.floor = ((state.pan ? 1 : 2) + (onGrill ? 1 : 0)) / 16.0F;
        DishModels.Place place = state.pan ? DishModels.Place.PAN : DishModels.Place.POT;
        int seed = (int) pot.getBlockPos().asLong();
        ItemStack dish = pot.dish();
        PotRecipe.Liquid liquid = pot.liquid();
        int amount = pot.amount();
        state.level = state.floor;
        state.wet = false;
        if (liquid != null) {
            state.wet = true;
            if (state.pan) {
                state.liquid = PAN_OIL; // a film over the pan's floor
                state.level = state.floor + 0.15F / 16;
            } else {
                state.liquid = liquid == PotRecipe.Liquid.WATER ? POT_WATER : POT_OIL;
                state.level = state.floor + (0.5F + 3.5F * Math.min(1.0F, amount / (float) IronPotBlockEntity.CAPACITY)) / 16;
            }
            state.tints[0] = WATER;
        }
        if (!dish.isEmpty()) {
            pot.cookingSeen = -1;
            var model = DishModels.of(dish, place);
            if (pot.container() != null) {
                // ladled out by the bowl: the soup is the pot's surface, a little lower with each serving
                PotRecipe made = level == null ? null : PotRecipe.making(level, dish, state.pan);
                if (!state.pan) {
                    state.level = state.floor + Math.min(4.0F, 0.8F + 0.45F * dish.getCount()) / 16;
                    state.wet = true;
                }
                if (model != null) {
                    state.whole = model;
                    state.liquid = null;
                } else {
                    this.add(state, null, dish, level, seed);
                    if (!state.pan && made != null && made.color().isPresent()) {
                        state.liquid = POT_BROTH;
                        state.tints[0] = 0xFF000000 | made.color().get();
                    }
                }
            } else {
                for (int i = 0; i < Math.min(dish.getCount(), MOST - 2); i++) {
                    this.add(state, model, dish, level, seed + i);
                }
            }
            // the dishes done behind it, and what still cooks, lie beside it
            for (IronPotBlockEntity.Waiting waiting : pot.later()) {
                ItemStack done = waiting.dish();
                var doneModel = DishModels.of(done, place);
                for (int i = 0; i < Math.min(done.getCount(), 2); i++) {
                    this.add(state, doneModel, done, level, seed + 31 * (i + 1));
                }
            }
            this.addContents(state, pot.contents(), place, level, seed);
            return;
        }
        List<ItemStack> contents = pot.contents();
        boolean milk = this.addContents(state, contents, place, level, seed);
        if (state.pan || liquid != PotRecipe.Liquid.WATER) {
            pot.cookingSeen = -1;
            return;
        }
        // the water takes the colour of what cooks in it, over ten seconds from when it starts; milk whitens it at once
        int base = milk ? MILK : WATER;
        var cooking = !state.simmering || level == null ? null : pot.recipeNow(level);
        if (cooking != null && cooking.value().color().isPresent()) {
            if (pot.cookingSeen < 0) {
                pot.cookingSeen = now;
            }
            float t = Mth.clamp((float) (state.ticks - pot.cookingSeen) / 200.0F, 0.0F, 1.0F);
            state.liquid = POT_BROTH;
            state.tints[0] = ARGB.srgbLerp(t, base, 0xFF000000 | cooking.value().color().get());
        } else {
            pot.cookingSeen = -1;
            if (milk) {
                state.liquid = POT_BROTH;
                state.tints[0] = MILK;
            }
        }
    }

    /** The things in the vessel as pieces, up to three of a kind and {@link #MOST} in all; whether milk is among them. */
    private boolean addContents(State state, List<ItemStack> contents, DishModels.Place place, @Nullable Level level, int seed) {
        boolean milk = false;
        for (int slot = 0; slot < contents.size(); slot++) {
            ItemStack stack = contents.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            milk |= stack.is(Items.MILK_BUCKET);
            var model = DishModels.of(stack, place);
            if (model == null && stack.is(DISSOLVES)) {
                continue;
            }
            for (int i = 0; i < Math.min(stack.getCount(), 3) && state.pieces.size() < MOST; i++) {
                this.add(state, model, stack, level, seed + slot * 8 + i);
            }
        }
        return milk;
    }

    private void add(State state, @Nullable StandaloneModelKey<BlockStateModelPart> model, ItemStack stack, @Nullable Level level, int seed) {
        if (model != null) {
            state.pieces.add(new Piece(model, null));
            return;
        }
        ItemStackRenderState item = new ItemStackRenderState();
        this.itemModels.updateForTopItem(item, stack, ItemDisplayContext.FIXED, level, null, seed);
        state.pieces.add(new Piece(null, item));
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.liquid != null) {
            pose.pushPose();
            pose.translate(state.x, state.level, state.z);
            DishModels.draw(state.liquid, 1.0F, state.tints, state.liquid == POT_WATER || state.liquid == PAN_OIL, state.lightCoords, pose, collector);
            pose.popPose();
        }
        if (state.whole != null) {
            pose.pushPose();
            pose.translate(state.x, state.pan ? state.floor : state.level, state.z);
            DishModels.draw(state.whole, 1.0F, state.lightCoords, pose, collector);
            pose.popPose();
        }
        int n = state.pieces.size();
        float reach = (n <= 2 ? 2.6F : state.pan ? 3.0F : 2.8F) / 16;
        for (int i = 0; i < n; i++) {
            Piece piece = state.pieces.get(i);
            // one alone lies in the middle; two side by side; up to six make a ring; the seventh goes in the middle of it
            boolean middle = n == 1 || i == 6;
            int ring = Math.min(n, 6);
            float angle = (float) (i * Math.PI * 2 / ring) + (n == 2 ? 0 : 0.4F);
            float r = middle ? 0 : reach;
            float bob = state.simmering && state.wet && !state.pan ? (float) Math.sin(state.ticks * 0.15 + i * 1.7) * 0.2F / 16 : 0;
            pose.pushPose();
            // each a hair higher than the last: where two lie across each other, their tops are not in one plane
            pose.translate(state.x + Mth.cos(angle) * r, state.level + bob + i * 0.03F / 16, state.z + Mth.sin(angle) * r);
            pose.rotate(Axis.YP.rotationDegrees(n == 2 ? i * 180.0F : i * 67.0F + 15.0F));
            if (piece.model() != null) {
                if (state.wet && !state.pan) {
                    pose.translate(0, -0.6F / 16, 0); // it floats low: a little of it under the surface
                    pose.rotate(Axis.XP.rotationDegrees(state.simmering ? (float) Math.sin(state.ticks * 0.11 + i) * 5.0F : 0));
                }
                DishModels.draw(piece.model(), 1.0F, state.lightCoords, pose, collector);
            } else if (piece.item() != null) {
                pose.translate(0, 0.02F + i * 0.001F, 0);
                pose.rotate(Axis.XP.rotationDegrees(90.0F));
                pose.scale(0.22F, 0.22F, 0.22F);
                piece.item().submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            }
            pose.popPose();
        }
    }
}
