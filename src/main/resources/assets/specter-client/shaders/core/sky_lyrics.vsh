#version 330
#extension GL_ARB_separate_shader_objects : require

// Sky Lyrics, vertex stage: places the word quads and hands their texture spot and colour on.
// Minecraft fills these blocks by name; only the first members are needed, laid out as the game writes them.
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// Named as the vertex format names them.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 glyphUv;
layout(location = 1) out vec4 shade;

void main() {
    vec4 inCamera = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * inCamera;
    glyphUv = UV0;
    shade = Color;
}
