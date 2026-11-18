package dev.nookworks.port.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Buffers that draw everything put in them at full light, whatever light it was given: for something 1.21.1's own
 * renderer draws lit by the world where 26.3 lets the caller give it the light (a painting in a glowing frame).
 */
public final class FullBrightBuffers implements MultiBufferSource {
    private final MultiBufferSource buffers;

    private FullBrightBuffers(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public static MultiBufferSource wrap(MultiBufferSource buffers) {
        return new FullBrightBuffers(buffers);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new Lit(this.buffers.getBuffer(type));
    }

    /** Passes every vertex on, its light (the second texture coordinates) made full. */
    private record Lit(VertexConsumer buffer) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.buffer.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.buffer.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.buffer.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.buffer.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.buffer.setUv2(240, 240);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.buffer.setNormal(x, y, z);
            return this;
        }
    }
}
