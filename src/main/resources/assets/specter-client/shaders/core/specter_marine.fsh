#version 330
#extension GL_ARB_separate_shader_objects : require

// Specter Marine Layer: a bank of sea fog lying low over the world. It is thick at its base and thins with height,
// it is patchy and drifts, and it glows where the sun or moon is behind it.
//
// For every pixel the view ray is walked through the layer (a few steps, dithered) adding up how much fog lies
// between the eye and whatever is there - land, water, or nothing but sky, in which case the walk goes on to the
// distance where the bank meets the horizon. Blended over the finished world, before your hand is drawn.

#include <specter-client:specter_fx.glsl>

uniform sampler2D Sampler0;   // tiling noise (r big shapes, g puffy cells, b fine detail)
uniform sampler2D Sampler1;   // the depth of the finished world

layout(location = 0) in vec4 nearPoint;
layout(location = 1) in vec4 farPoint;
layout(location = 2) in vec2 screenUv;

layout(location = 0) out vec4 fragColor;

void main() {
    float strength = FogTune.w;
    if (strength <= 0.001 || Fog.x <= 0.0) discard;

    // Reversed Z in 26.x: flipped back so 0 is the near plane and 1 the far plane, as the maths below expects.
    float z = 1.0 - texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r;
    vec3 rd = normalize(farPoint.xyz / farPoint.w);
    bool sky = z >= 0.999999;
    float dist = FogDrift.w;
    if (!sky) {
        vec4 h = mix(nearPoint, farPoint, z);
        dist = min(length(h.xyz / h.w), FogDrift.w);
    }

    // The stretch of the ray that is inside the layer: from where the fog starts to what the ray hits, cut where
    // the ray is above the top of the layer (four falloffs up, where there is next to nothing left).
    float camY = FxCam.y;
    float top = Fog.y + Fog.z * 4.0;
    float t0 = FogTune.x, t1 = dist;
    if (rd.y > 0.0005) {
        if (camY >= top) discard;
        t1 = min(t1, (top - camY) / rd.y);
    } else if (rd.y < -0.0005) {
        if (camY > top) t0 = max(t0, (top - camY) / rd.y);
    } else if (camY >= top) {
        discard;
    }
    if (t1 <= t0) discard;

    const int STEPS = 14;
    float inverseFalloff = 1.0 / max(Fog.z, 0.5);
    float jitter = fxDither(gl_FragCoord.xy);
    float patchSize = max(FogTune.z, 8.0);
    float depth = 0.0;
    float previous = t0;
    for (int i = 0; i < STEPS; i++) {
        // Steps bunch up near the eye, where the detail of the fog is seen, and stretch out with distance.
        float u = (float(i) + jitter) / float(STEPS);
        float t = mix(t0, t1, u * u);
        float dt = t - previous;
        previous = t;
        float height = camY + rd.y * t - Fog.y;
        float density = exp(-max(height, 0.0) * inverseFalloff);
        // Patches: big slow shapes with smaller ones riding on them, leaning downwind with height so tops look
        // combed. Measured in patches from the wrapped place of the camera, so they never jump as you travel.
        vec2 q = FogCam.xy + (rd.xz * t + vec2(height * 0.8, height * 0.3)) / patchSize + FogDrift.xy;
        float shape = texture(Sampler0, q).r * 0.62 + texture(Sampler0, q * 3.0 + FogDrift.z).g * 0.38;
        density *= mix(1.0, smoothstep(0.30, 0.72, shape) * 1.75, FogTune.y);
        depth += density * dt;
    }
    depth *= Fog.x;

    float cover = (1.0 - exp(-depth)) * Fog.w * strength;
    if (cover <= 0.002) discard;

    // Lit from behind: fog between you and the sun (or the moon, faintly) glows; away from it, it is flat.
    float toSun = max(dot(rd, FxSun.xyz), 0.0);
    float toMoon = max(dot(rd, FxMoon.xyz), 0.0);
    float day = 1.0 - FxSun.w;
    vec3 col = FogColor.rgb * (1.0 + FogColor.a * (pow(toSun, 5.0) * 0.9 * day + pow(toMoon, 8.0) * 0.35 * FxSun.w * FxMoon.w));
    // Thick fog is a touch darker than thin fog: light has further to come through it.
    col *= mix(1.04, 0.90, clamp(depth * 0.35, 0.0, 1.0));
    fragColor = vec4(col, cover);
}
