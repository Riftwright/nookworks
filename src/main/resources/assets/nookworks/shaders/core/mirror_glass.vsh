#version 150

#moj_import <minecraft:fog.glsl>

// A Standing Mirror's glass (MirrorReflections), its corners where the block entity or the pass over a shader pack's
// picture put them: relative to the camera, as an entity's are, so the distance to the eye is the length of Position.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

// how far the glass is from the eye, for the world's fog
out float vertexDistance;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(Position, FogShape);
}
