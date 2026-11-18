#version 330

// Specter world effects: what Bioluminescence, Marine Layer and Color Grading share. The Java side
// (com.jfant.specter.fx.WorldFx) fills SpecterFx once a frame in exactly this order (std140).

layout(std140) uniform SpecterFx {
    mat4 InvViewProj;        // clip space -> position relative to the camera, world axes
    vec4 FxCam;              // xyz camera position (x and z wrapped every 4096 blocks, y as it is), w seconds (wraps hourly)
    vec4 FxSun;              // xyz toward the sun, w night 0-1
    vec4 FxMoon;             // xyz toward the moon, w moon brightness (phase)
    vec4 FxState;            // x camera under water (1) or not (0), y rain 0-1, z render distance (blocks), w unused
    vec4 Bio;                // x strength (night and fade already in), y shore glow, z sparkles, w plankton patches
    vec4 BioA;               // rgb the deep colour (linear), a wake strength
    vec4 BioB;               // rgb the bright colour (linear), a drifting motes under water
    vec4 BioTune;            // x reach (blocks), y wave speed, z unused, w sea-floor glow under water
    vec4 Wakes[12];          // xy where something stirred the water (x and z relative to the camera), z age (s), w strength
    vec4 Fog;                // x density at the base (per block), y base height (world y), z falloff (blocks), w most it may cover 0-1
    vec4 FogColor;           // rgb (display space, lit for the time of day), a how much it glows toward the sun
    vec4 FogTune;            // x distance it starts at, y patchiness 0-1, z patch size (blocks), w strength 0-1 (the fade in and out)
    vec4 FogDrift;           // xy drift so far (in patches), z churn, w how far it is followed where no land ends the view (blocks)
    vec4 FogCam;             // xy camera x and z in patches (wrapped to 0-1), zw unused
    vec4 Grade0;             // x exposure (stops), y brightness, z contrast, w saturation
    vec4 Grade1;             // x temperature, y tint, z gamma, w vibrance
    vec4 Grade2;             // x highlights, y shadows, z vignette, w grain
    vec4 Grade3;             // x fade (lifted blacks), y mix 0-1, z seconds for the grain, w aspect (width / height)
    vec4 GradeShadowTint;    // rgb, a amount
    vec4 GradeHighlightTint; // rgb, a amount
};

float fxLuma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float fxHash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 fxHash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

// Per-pixel dither so a few march steps do not band.
float fxDither(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}
