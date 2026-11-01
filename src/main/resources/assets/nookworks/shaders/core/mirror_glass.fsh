#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// A Standing Mirror's glass: its own part of the picture drawn from behind it (MirrorReflections), which comes out
// flipped left to right, read back so; a little cool and dim, as silvered glass is.
uniform sampler2D Sampler0;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 colour = texture(Sampler0, vec2(1.0 - uv.x, uv.y)).rgb;
    fragColor = vec4(colour * vec3(0.90, 0.93, 0.95) + vec3(0.015, 0.02, 0.025), 1.0);
}
