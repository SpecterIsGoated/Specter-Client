#version 330
#extension GL_ARB_separate_shader_objects : require

// Specter world effects, vertex stage for every full-screen pass: one triangle covering the screen, and under each
// corner the points on the near and far planes (homogeneous, so they interpolate exactly). With a depth value the
// fragment stage turns those into a position in the world.

#include <specter-client:specter_fx.glsl>

layout(location = 0) in vec3 Position;

layout(location = 0) out vec4 nearPoint;
layout(location = 1) out vec4 farPoint;
layout(location = 2) out vec2 screenUv;

// 26.x renders with reversed Z: the near plane is at NDC z = 1, the far plane at 0 (zero-to-one depth) or -1.
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
const float FAR_NDC = 0.0;
#else
const float FAR_NDC = -1.0;
#endif

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    nearPoint = InvViewProj * vec4(Position.xy, 1.0, 1.0);
    farPoint = InvViewProj * vec4(Position.xy, FAR_NDC, 1.0);
    screenUv = Position.xy * 0.5 + 0.5;
}
