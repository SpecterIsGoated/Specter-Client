#version 330

// Specter world effects, vertex stage for every full-screen pass: one triangle covering the screen, and under each
// corner the points on the near and far planes (homogeneous, so they interpolate exactly). With a depth value the
// fragment stage turns those into a position in the world.

#moj_import <specter-client:specter_fx.glsl>

in vec3 Position;

out vec4 nearPoint;
out vec4 farPoint;
out vec2 screenUv;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    nearPoint = InvViewProj * vec4(Position.xy, -1.0, 1.0);
    farPoint = InvViewProj * vec4(Position.xy, 1.0, 1.0);
    screenUv = Position.xy * 0.5 + 0.5;
}
