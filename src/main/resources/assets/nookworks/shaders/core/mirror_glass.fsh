#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#ifdef MIRROR_FOG
#include <minecraft:fog.glsl>
#endif

// A Standing Mirror's glass: its own part of the picture drawn from behind it (MirrorReflections), which comes out
// flipped left to right, read back so; a little cool and dim, as silvered glass is. Without a shader pack, fogged as a
// block there is by the world's fog between the eye and it (under water, the water's); under a pack (no MIRROR_FOG)
// it is painted over the pack's finished picture, which has its own.
uniform sampler2D Sampler0;

#ifdef MIRROR_FOG
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 colour = texture(Sampler0, vec2(1.0 - uv.x, uv.y)).rgb;
    vec4 glass = vec4(colour * vec3(0.90, 0.93, 0.95) + vec3(0.015, 0.02, 0.025), 1.0);
#ifdef MIRROR_FOG
    glass = apply_fog(glass, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
    fragColor = glass;
}
