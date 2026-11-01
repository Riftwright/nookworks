package dev.nookworks.client.render;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;

import dev.nookworks.Nookworks;
import dev.nookworks.fluid.LineContent;
import dev.nookworks.fluid.WindowFluid;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Another mod's liquid in a window - a Copper Tank's, a Sight Glass's - in its own colour (nookTechnology's session,
 * 2026-11-01: its coal tar, black, looked like an empty glass in the plain grey every such liquid was drawn in): the
 * window's light grey, tint 0, multiplied by the liquid's colour - its still texture's average, through its own tint,
 * as the game draws it. The block's client is told the liquid ({@link WindowFluid}). A liquid it cannot read stays the
 * grey it was. Water in a Sight Glass is tinted as the water where it stands, as before.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class WindowTint {
    /** The grey another mod's liquid was drawn in before (224 of the window's 255): for one whose colour is not to be had. */
    private static final int UNKNOWN = 0xFFE0E0E0;

    /** Each liquid's colour, by the sprite it was worked out from (a resource reload makes new sprites). */
    private record Known(TextureAtlasSprite sprite, int colour) {}

    private static final Map<Fluid, Known> COLOURS = new ConcurrentHashMap<>();

    private WindowTint() {}

    @SubscribeEvent
    static void blocks(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return UNKNOWN;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return inWindow(level, pos);
            }
        }), ModBlocks.COPPER_TANK.get());
        BlockTintSource water = BlockTintSources.water();
        event.register(List.of(new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return water.color(state);
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return state.hasProperty(LineContent.PROPERTY) && state.getValue(LineContent.PROPERTY) == LineContent.OTHER
                        ? inWindow(level, pos) : water.colorInWorld(state, level, pos);
            }
        }), ModBlocks.SIGHT_GLASS.get());
    }

    /** The colour of the liquid the block at {@code pos} shows. */
    private static int inWindow(BlockAndTintGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WindowFluid window ? colour(window.windowFluid()) : UNKNOWN;
    }

    /** {@code fluid}'s colour as the game draws it: its still texture's average, through its tint; or the plain grey. */
    static int colour(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return UNKNOWN;
        }
        try {
            FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            TextureAtlasSprite sprite = model.stillMaterial().sprite();
            Known known = COLOURS.get(fluid);
            if (known != null && known.sprite() == sprite) {
                return known.colour();
            }
            int average = average(sprite);
            int tint = model.fluidTintSource() != null ? model.fluidTintSource().color(fluid.defaultFluidState()) : -1;
            int colour = average == 0 ? UNKNOWN : 0xFF000000 | ARGB.multiply(average, tint);
            COLOURS.put(fluid, new Known(sprite, colour));
            return colour;
        } catch (RuntimeException unreadable) {
            return UNKNOWN; // another mod's model in a shape not looked for: the grey, as before
        }
    }

    /** The average of {@code sprite}'s first frame, of its pixels that show (0 for none, or none to read). */
    private static int average(TextureAtlasSprite sprite) {
        NativeImage image = sprite.contents().getOriginalImage();
        if (image == null || image.isClosed()) {
            return 0;
        }
        int w = Math.min(sprite.contents().width(), image.getWidth());
        int h = Math.min(sprite.contents().height(), image.getHeight());
        long r = 0;
        long g = 0;
        long b = 0;
        long n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getPixel(x, y);
                if (ARGB.alpha(argb) < 16) {
                    continue;
                }
                r += ARGB.red(argb);
                g += ARGB.green(argb);
                b += ARGB.blue(argb);
                n++;
            }
        }
        return n == 0 ? 0 : ARGB.color(255, (int) (r / n), (int) (g / n), (int) (b / n));
    }
}
