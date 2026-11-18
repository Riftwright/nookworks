#version 150

#moj_import <minecraft:fog.glsl>

// A Standing Mirror's glass: its own part of the picture drawn from behind it (MirrorReflections), which comes out
// flipped left to right, read back so; a little cool and dim, as silvered glass is. Fogged as a block there is by the
// world's fog between the eye and it (under water, the water's). Under a shader pack the glass is painted over the
// pack's finished picture instead, which has its own fog (mirror_glass_over_shaders.fsh).

uniform sampler2D Sampler0;

uniform vec2 ScreenSize;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 colour = texture(Sampler0, vec2(1.0 - uv.x, uv.y)).rgb;
    vec4 glass = vec4(colour * vec3(0.90, 0.93, 0.95) + vec3(0.015, 0.02, 0.025), 1.0);
    fragColor = linear_fog(glass, vertexDistance, FogStart, FogEnd, FogColor);
}
