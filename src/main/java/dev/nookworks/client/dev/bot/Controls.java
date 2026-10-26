package dev.nookworks.client.dev.bot;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The walker's hands on the keyboard and mouse: the movement keys held for the next tick, and where it is turning to
 * look. Keys go through the game's own key mappings, so the player moves exactly as for a person at the keys; the view
 * turns at most {@link #turnSpeed} degrees a tick, so it looks round as a person does (and films smoothly: the turn is
 * set after the tick, and the frames between ticks ease from the old look to the new).
 */
final class Controls {
    boolean forward;
    boolean back;
    boolean left;
    boolean right;
    boolean jump;
    boolean sneak;
    boolean sprint;
    /** Degrees a tick the view turns at most. */
    float turnSpeed = 30.0F;
    private @Nullable Float yaw;
    private @Nullable Float pitch;
    /** Whether the keys are the walker's: released once when it lets go, so the keys a person holds are theirs again. */
    private boolean holding;

    void stopMoving() {
        this.forward = false;
        this.back = false;
        this.left = false;
        this.right = false;
        this.jump = false;
        this.sneak = false;
        this.sprint = false;
    }

    void face(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Mth.clamp(pitch, -90.0F, 90.0F);
    }

    /** Turn to look at a point from the eyes. */
    void lookAt(LocalPlayer player, Vec3 at) {
        Vec3 eye = player.getEyePosition();
        double dx = at.x - eye.x;
        double dy = at.y - eye.y;
        double dz = at.z - eye.z;
        this.face((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    /** Turn the body toward a point on the level, the pitch let be (or eased toward level ground ahead). */
    void headFor(LocalPlayer player, double x, double z) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        if (dx * dx + dz * dz < 1e-4) {
            return;
        }
        float pitchNow = this.pitch != null ? this.pitch : player.getXRot();
        this.face((float) Math.toDegrees(Math.atan2(-dx, dz)), pitchNow + Mth.clamp(10.0F - pitchNow, -6.0F, 6.0F));
    }

    void stopTurning() {
        this.yaw = null;
        this.pitch = null;
    }

    /** Whether the view has come round to where it was asked to look, within {@code degrees}. */
    boolean aimed(LocalPlayer player, float degrees) {
        if (this.yaw == null || this.pitch == null) {
            return true;
        }
        return Math.abs(Mth.wrapDegrees(this.yaw - player.getYRot())) <= degrees && Math.abs(this.pitch - player.getXRot()) <= degrees;
    }

    /** Whether the look asked for is round to the point already (within {@code degrees}), turning or not. */
    static boolean looking(LocalPlayer player, Vec3 at, float degrees) {
        Vec3 eye = player.getEyePosition();
        Vec3 want = at.subtract(eye).normalize();
        Vec3 view = player.getViewVector(1.0F);
        return Math.toDegrees(Math.acos(Mth.clamp(want.dot(view), -1.0, 1.0))) <= degrees;
    }

    /** The keys held for the next tick, and a step of the turn (after the tick: see the class note). */
    void apply(Minecraft mc, boolean active) {
        var o = mc.options;
        if (!active) {
            if (this.holding) {
                this.holding = false;
                for (KeyMapping key : new KeyMapping[] {o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint}) {
                    key.setDown(false);
                }
            }
            return;
        }
        this.holding = true;
        o.keyUp.setDown(this.forward);
        o.keyDown.setDown(this.back);
        o.keyLeft.setDown(this.left);
        o.keyRight.setDown(this.right);
        o.keyJump.setDown(this.jump);
        o.keyShift.setDown(this.sneak);
        o.keySprint.setDown(this.sprint);
        LocalPlayer player = mc.player;
        if (player != null && this.yaw != null && this.pitch != null) {
            float dy = Mth.wrapDegrees(this.yaw - player.getYRot());
            float dp = this.pitch - player.getXRot();
            float yawStep = Mth.clamp(dy, -this.turnSpeed, this.turnSpeed);
            float pitchStep = Mth.clamp(dp, -this.turnSpeed, this.turnSpeed);
            // the last few degrees eased, not stopped dead
            if (Math.abs(dy) < this.turnSpeed * 0.6F) {
                yawStep = dy * 0.75F;
                if (Math.abs(dy) < 0.4F) {
                    yawStep = dy;
                }
            }
            if (Math.abs(dp) < this.turnSpeed * 0.6F) {
                pitchStep = dp * 0.75F;
                if (Math.abs(dp) < 0.4F) {
                    pitchStep = dp;
                }
            }
            player.setYRot(player.getYRot() + yawStep);
            player.setXRot(Mth.clamp(player.getXRot() + pitchStep, -90.0F, 90.0F));
            player.setYHeadRot(player.getYRot());
        }
    }
}
