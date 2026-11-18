#version 330
#extension GL_ARB_separate_shader_objects : require

// Specter sky, cloud shadows: for every solid pixel, find where the sunlight that reaches it crossed the cloud layer
// and darken it by how much cloud is there. Multiplied over the frame (blend DST_COLOR x source), right after the
// solid terrain, using the copy of the depth taken at that moment.

#include <specter-client:specter_sky.glsl>

uniform sampler2D Sampler1;   // the solid-only depth copy

layout(location = 0) in vec4 nearPoint;
layout(location = 1) in vec4 farPoint;

layout(location = 0) out vec4 fragColor;

void main() {
    // Reversed Z in 26.x: flipped back so 0 is the near plane and 1 the far plane, as the maths below expects.
    float z = 1.0 - texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r;
    if (z >= 0.999999) discard;
    vec3 sun = SunDir.xyz;
    float strength = Cloud2.y * Cloud.y * State.z * smoothstep(0.04, 0.25, sun.y);
    if (strength <= 0.001) discard;

    // The depth value picks the point between the near and far plane under this pixel.
    vec4 h = mix(nearPoint, farPoint, z);
    vec3 rel = h.xyz / h.w;
    float dist = length(rel);
    strength *= 1.0 - smoothstep(CamPos.w * 0.55, CamPos.w * 0.95, dist);
    if (strength <= 0.001) discard;

    vec3 world = vec3(CamPos.x, 0.0, CamPos.z) + rel;
    // Height of the point under the clouds' middle.
    float up = Cloud.z + Cloud.w * 0.3 - rel.y;
    if (up <= 0.0) discard;
    vec2 q = world.xz + Wind.xy + sun.xz / sun.y * up;
    float dens = cloudMap(q, Cloud.x);
    // Soft edges, as the sun is not a point.
    float shade = smoothstep(0.02, 0.45, dens);
    fragColor = vec4(vec3(1.0 - shade * strength * 0.6), 1.0);
}
