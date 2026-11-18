#version 330

// Sky Lyrics, vertex stage: places the word quads and hands their texture spot and colour on.
// Minecraft fills these blocks by name; only the first members are needed, laid out as the game writes them.
layout(std140) uniform DynamicTransforms {
    mat4 cameraFromWorld;
    vec4 tint;
};
layout(std140) uniform Projection {
    mat4 screenFromCamera;
};

// Named as the vertex format names them.
in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 glyphUv;
out vec4 shade;

void main() {
    vec4 inCamera = cameraFromWorld * vec4(Position, 1.0);
    gl_Position = screenFromCamera * inCamera;
    glyphUv = UV0;
    shade = Color;
}
