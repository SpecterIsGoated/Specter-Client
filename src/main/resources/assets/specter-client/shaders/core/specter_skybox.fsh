#version 330
#extension GL_ARB_separate_shader_objects : require

// Specter Skybox: a 360 degree equirectangular picture looked up by view direction. ModelOffset.x turns it (radians),
// ColorModulator.rgb is the brightness. Drawn on the far plane, so only sky pixels get it.

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec4 nearPoint;
layout(location = 1) in vec4 farPoint;

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 d = normalize(farPoint.xyz / farPoint.w - nearPoint.xyz / nearPoint.w);
    // north (-z) is the middle of the picture; turning right (east) moves right across it
    float phi = atan(d.x, -d.z) + ModelOffset.x;
    float u = fract(phi * 0.15915494 + 0.5);
    float v = 0.5 - asin(clamp(d.y, -1.0, 1.0)) * 0.31830989;
    // one level, no mipmaps: the picture is never shrunk on screen, and a mip pick would show a seam where u wraps
    vec3 c = textureLod(Sampler0, vec2(u, v), 0.0).rgb * ColorModulator.rgb;
    fragColor = vec4(c, 1.0);
}
