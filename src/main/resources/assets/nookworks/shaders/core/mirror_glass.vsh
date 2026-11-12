#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <minecraft:dynamictransforms.glsl>
#ifdef MIRROR_FOG
#include <minecraft:fog.glsl>
#endif

layout(location = 0) in vec3 Position;

#ifdef MIRROR_FOG
// how far the glass is from the eye, for the world's fog (its positions are the camera's own, as an entity's are)
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
#ifdef MIRROR_FOG
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
#endif
}
