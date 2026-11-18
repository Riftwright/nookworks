#version 150

// What a shader pack painted on a Standing Mirror's measuring board (MirrorLight), read into the table the mirror's
// picture is lit with: block light across, sky light up, as the game's own lightmap has them. The board is darkest at
// its edges and brightest in its middle, each way, so every level is on it four times over, once in each quarter. The
// four are read where they are on the screen and nothing stands before the glass there (by the world's depth: a hand,
// what it holds, a fence, a passer-by is not the board); of those left the brightest and the darkest are dropped when
// there are more than two (what writes no depth - rain, smoke - is not the board either), and the rest averaged.
//
// ModelViewMat takes a place on the board to where it is on the screen, TextureMat the same place on the glass's
// picture, drawn a little before it; ColorModulator holds the gain, the board's dark margin, its bright middle, and how
// much of this reading goes into the table; ModelOffset.x, the strip along the top and the bottom that holds no light
// at all.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform mat4 ModelViewMat;
uniform mat4 TextureMat;
uniform vec4 ColorModulator;
uniform vec3 ModelOffset;

in vec2 texCoord;

out vec4 fragColor;

// how far from the board's middle (0 to 0.5) a level of light (0 to 1) is, well inside the flat parts at the two ends
// (the dark one begins `edge` in from the board's side)
float away(float level, float margin, float peak, float edge) {
    if (level > 0.999) {
        return peak * 0.5;
    }
    if (level < 0.001) {
        return 0.5 - (edge + margin) * 0.5;
    }
    return peak + (1.0 - level) * (0.5 - margin - peak);
}

// the picture at a place on the board, and 1; or all 0, where that place is off the screen or behind something
vec4 seen(vec2 at) {
    vec4 clip = ModelViewMat * vec4(at, 0.0, 1.0);
    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
    if (clip.w <= 0.0 || any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) {
        return vec4(0.0);
    }
    // as the glass's picture is tested when it is drawn: nearer is less, as OpenGL's depth runs (from -1 on the screen)
    vec4 glass = TextureMat * vec4(at, 0.0, 1.0);
    float depth = glass.z / glass.w * 0.5 + 0.5;
    if (texture(Sampler1, uv).r < depth) {
        return vec4(0.0);
    }
    return vec4(texture(Sampler0, uv).rgb, 1.0);
}

float brightness(vec4 colour) {
    return dot(colour.rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    float gain = ColorModulator.r;
    float margin = ColorModulator.g;
    float peak = ColorModulator.b;
    float edge = ModelOffset.x;
    // a texel's middle is a whole level of light: none at the first, 15 at the last
    vec2 level = clamp((texCoord * 16.0 - 0.5) / 15.0, 0.0, 1.0);
    vec2 d = vec2(away(level.x, margin, peak, 0.0), away(level.y, margin, peak, edge));
    vec4 a = seen(vec2(0.5 - d.x, 0.5 - d.y));
    vec4 b = seen(vec2(0.5 + d.x, 0.5 - d.y));
    vec4 c = seen(vec2(0.5 - d.x, 0.5 + d.y));
    vec4 e = seen(vec2(0.5 + d.x, 0.5 + d.y));
    vec4 sum = a + b + c + e;
    if (sum.a < 0.5) {
        fragColor = vec4(0.0); // none of the four seen: this level is left as it was
        return;
    }
    if (sum.a > 2.5) {
        // the brightest and the darkest of those seen left out
        vec4 high = a;
        vec4 low = a.a > 0.5 ? a : vec4(0.0, 0.0, 0.0, -1.0);
        vec4 each[3] = vec4[3](b, c, e);
        for (int i = 0; i < 3; i++) {
            if (each[i].a < 0.5) {
                continue;
            }
            if (high.a < 0.5 || brightness(each[i]) > brightness(high)) {
                high = each[i];
            }
            if (low.a < 0.5 || brightness(each[i]) < brightness(low)) {
                low = each[i];
            }
        }
        sum -= high + low;
    }
    fragColor = vec4(sum.rgb / sum.a * gain, ColorModulator.a);
}
