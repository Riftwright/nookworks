package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.port.render.SubmitNodeCollector;

/**
 * Client only: how another mod's shaft meets our machine or Drive Wheel where it runs straight into one (the 1.21.1
 * port's Create part, {@code compat.create.client.ShaftJoins}: Create's shaft is thicker than our axles, so the join is
 * drawn on our side, inside our block). Without such a mod nothing is set and nothing is drawn.
 */
public final class ForeignJoins {
    private ForeignJoins() {}

    /** What the mod draws. */
    public interface Look {
        /**
         * Draws the join for {@code part} - a machine's name ("oil_press"), the quern's "hand_quern_up" or
         * "hand_quern_down", or "drive_wheel" - in the pose of our axle on that side (pointing north, the shaft's side
         * at z = 0; the quern's upright), already turned as far round as the shaft. Whether it stands in for our own
         * axle model there (which is then not drawn).
         */
        boolean draw(String part, PoseStack pose, SubmitNodeCollector collector, int light);
    }

    private static volatile @Nullable Look look;

    /** Set once, at setup, by the mod whose shafts these are. */
    public static void set(Look joins) {
        look = joins;
    }

    static boolean draw(String part, PoseStack pose, SubmitNodeCollector collector, int light) {
        Look l = look;
        return l != null && l.draw(part, pose, collector, light);
    }
}
