#version 150

// A new light table begun as the game's own lightmap, texel for texel (MirrorLight): what a level not yet read is lit by.

uniform sampler2D Sampler0;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord);
}
