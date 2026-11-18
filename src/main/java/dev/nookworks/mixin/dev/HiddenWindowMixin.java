package dev.nookworks.mixin.dev;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;

/**
 * Development run only ({@link DevMixins}): with {@code -Dnookworks.agentHidden=true} the game's window is made
 * hidden, and not to take the focus when shown, so the agent's client never comes up over what the user is doing.
 * 1.21.1 makes its window before any mod code runs, so the wish has to be in the window hints.
 */
@Mixin(Window.class)
abstract class HiddenWindowMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V", remap = false))
    private void nookworks$startHidden(Operation<Void> defaults) {
        defaults.call();
        if (Boolean.getBoolean("nookworks.agentHidden")) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
    }
}
