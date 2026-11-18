#version 150

// A Standing Mirror's glass painted over a shader pack's finished picture (MirrorReflections): as mirror_glass.fsh, but
// with no fog of its own - the pack's picture round it has the pack's.

uniform sampler2D Sampler0;

uniform vec2 ScreenSize;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 colour = texture(Sampler0, vec2(1.0 - uv.x, uv.y)).rgb;
    fragColor = vec4(colour * vec3(0.90, 0.93, 0.95) + vec3(0.015, 0.02, 0.025), 1.0);
}
