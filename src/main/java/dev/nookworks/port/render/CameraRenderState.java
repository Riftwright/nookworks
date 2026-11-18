package dev.nookworks.port.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 26.3's {@code CameraRenderState}: where the camera is this frame, as the renderers are handed it. 1.21.1 hands them
 * nothing of the kind, so it is read off the game renderer's main camera when a renderer is called ({@link #current}).
 */
public class CameraRenderState {
    public BlockPos blockPos = BlockPos.ZERO;
    public Vec3 pos = Vec3.ZERO;
    public float xRot;
    public float yRot;
    public boolean initialized;
    public boolean isFirstPerson;
    public Quaternionf orientation = new Quaternionf();
    public Matrix4f projectionMatrix = new Matrix4f();
    public Matrix4f viewRotationMatrix = new Matrix4f();

    /** The main camera as it stands now. */
    public static CameraRenderState current() {
        CameraRenderState state = new CameraRenderState();
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            state.initialized = true;
            state.pos = camera.getPosition();
            state.blockPos = camera.getBlockPosition();
            state.xRot = camera.getXRot();
            state.yRot = camera.getYRot();
            state.isFirstPerson = !camera.isDetached();
            state.orientation = new Quaternionf(camera.rotation());
            state.viewRotationMatrix = new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate());
        }
        return state;
    }
}
