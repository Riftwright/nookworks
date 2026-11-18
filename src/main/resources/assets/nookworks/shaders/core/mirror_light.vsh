#version 150

// The whole of a Standing Mirror's light table (MirrorLight), 16 by 16: a square from (0, 0) to (1, 1) laid over the
// target, the place in it handed on (26.3 draws this with its own screen-filling triangle).

in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
    texCoord = Position.xy;
}
