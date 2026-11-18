#version 330
#extension GL_ARB_separate_shader_objects : require

// Specter sky, vertex stage for every full-screen sky pass: one triangle covering the screen, plus the points on the
// near and far planes under each corner (homogeneous, so they interpolate exactly) for the fragment stage to turn
// into a view ray or, with the depth buffer, a position in the world.

#include <minecraft:dynamictransforms.glsl>

#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;

layout(location = 0) out vec4 nearPoint;
layout(location = 1) out vec4 farPoint;

// 26.x renders with reversed Z: the near plane is at NDC z = 1, the far plane at 0 (zero-to-one depth) or -1.
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
const float FAR_NDC = 0.0;
#else
const float FAR_NDC = -1.0;
#endif

void main() {
    // On the far plane: depth tests against "nothing drawn here yet" pass, anything nearer wins.
    gl_Position = vec4(Position.xy, FAR_NDC, 1.0);
    mat4 back = inverse(ProjMat * ModelViewMat);
    nearPoint = back * vec4(Position.xy, 1.0, 1.0);
    farPoint = back * vec4(Position.xy, FAR_NDC, 1.0);
}
