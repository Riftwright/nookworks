package dev.nookworks.port.render;

/** 26.3's {@code LevelRenderState}, as far as the mod reads it: the camera of the frame being drawn. */
public class LevelRenderState {
    public CameraRenderState cameraRenderState = new CameraRenderState();

    /** The frame being drawn now. */
    public static LevelRenderState current() {
        LevelRenderState state = new LevelRenderState();
        state.cameraRenderState = CameraRenderState.current();
        return state;
    }
}
