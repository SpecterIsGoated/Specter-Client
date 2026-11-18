#version 330

// Specter sky, vertex stage for every full-screen sky pass: one triangle covering the screen, plus the points on the
// near and far planes under each corner (homogeneous, so they interpolate exactly) for the fragment stage to turn
// into a view ray or, with the depth buffer, a position in the world.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;

out vec4 nearPoint;
out vec4 farPoint;

void main() {
    // On the far plane: depth tests against "nothing drawn here yet" pass, anything nearer wins.
    gl_Position = vec4(Position.xy, 1.0, 1.0);
    mat4 back = inverse(ProjMat * ModelViewMat);
    nearPoint = back * vec4(Position.xy, -1.0, 1.0);
    farPoint = back * vec4(Position.xy, 1.0, 1.0);
}
