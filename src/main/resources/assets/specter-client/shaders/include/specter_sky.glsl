
// Specter sky: what Atmosphere and Nebula Night share. The Java side (com.jfant.specter.sky.SkyRenderer) fills
// SpecterSky once a frame in exactly this order (std140); every pass of the sky reads the same block, so the day
// sky, the clouds, the night sky and the cloud shadows on the ground always agree with each other.

layout(std140) uniform SpecterSky {
    vec4 SunDir;        // xyz toward the sun (world axes), w seconds (wraps every hour)
    vec4 MoonDir;       // xyz toward the moon, w moon brightness (phase)
    vec4 State;         // x Atmosphere sky on, y Nebula Night on, z daylight 0-1, w night 0-1
    vec4 Weather;       // x rain 0-1, y thunder 0-1, z lightning flash 0-1, w cloud quality 0-3
    vec4 CamPos;        // xyz camera (blocks, x/z wrapped), w render distance (blocks)
    vec4 FogTune;       // x horizon blend into the terrain fog, y unused, z exposure, w atmosphere fog 0-1
    vec4 SkyTune;       // x saturation, y brightness, z sun glow, w sunrise/sunset strength (picked for the time)
    vec4 SkyTune2;      // x haze, y custom colours mix, z unused, w morning (1) or evening (0)
    vec4 CustomZenith;  // rgb custom colours (linear)
    vec4 CustomHorizon;
    vec4 CustomSunset;
    vec4 Cloud;         // x coverage 0-1, y opacity 0-1, z base height above the camera (blocks), w thickness (blocks)
    vec4 Cloud2;        // x high cloud amount, y shadow strength, z low clouds on (1) / off (0), w march steps
    vec4 Wind;          // xy low cloud drift (blocks), zw high cloud drift (blocks)
    mat4 StarRot;       // world direction -> sky-sphere direction (turns with the night)
    vec4 Stars;         // x density 0-1, y brightness, z twinkle 0-1, w night sky brightness
    vec4 Nebula;        // x Milky Way, y nebulae, z horizon glow, w aurora
    vec4 NebulaA;       // rgb first nebula colour (linear), a cosmic dust
    vec4 NebulaB;       // rgb second nebula colour (linear), a stars on (1) / off (0)
    vec4 Extras;        // x constellation alpha, y meteors in use, z constellation segments in use, w night quality 0-3
    vec4 Meteors[24];   // 6 meteors: [start dir, progress 0-1+] [end dir, width] [colour linear, brightness] [trail length, fireball 0/1, -, -]
    vec4 Lines[64];     // constellation segments: pairs of sky-sphere directions (xyz), w star brightness at that end
    vec4 CloudLook;     // x edge softness 0-1, y billow detail 0-1.5, z shape scale (0.5, 1 or 2), w how much thick cores tower 0-1
};

// The tiling noise every pass uses (r big shapes, g puffy cells, b fine detail, a variety), mipmapped, repeating.
uniform sampler2D Sampler0;

const float PI = 3.14159265;
const float TAU = 6.28318531;

// ---- colour ----

vec3 toLinear(vec3 c) {
    return pow(max(c, 0.0), vec3(2.2));
}

vec3 toDisplay(vec3 c) {
    return pow(max(c, 0.0), vec3(1.0 / 2.2));
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 saturateColor(vec3 c, float s) {
    float l = luma(c);
    return max(mix(vec3(l), c, s), 0.0);
}

// ACES fit: keeps a bright sunset glowing instead of clipping to flat white.
vec3 tonemap(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// ---- hashing ----

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

// Per-pixel dither so a few march steps don't band.
float interleavedGradient(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

// ---- atmosphere ----
// Single scattering, done cheaply: Rayleigh (blue sky), Mie (haze and the bright ring round the sun) and ozone (the
// violet-blue left in the sky after sunset), with Kasten-Young air mass for the light's path. Tuned for looks.
// SkyModel.java mirrors these functions to colour the terrain's fog, so keep the two in step.

const vec3 RAYLEIGH = vec3(0.0464, 0.108, 0.265);   // optical depth straight up
const vec3 OZONE = vec3(0.0083, 0.0241, 0.0011);
const float MIE = 0.0252;

float airMass(float mu) {
    float elevation = degrees(asin(clamp(mu, -1.0, 1.0)));
    return 1.0 / (max(mu, 0.0) + 0.50572 * pow(max(elevation + 6.07995, 0.05), -1.6364));
}

vec3 opticalDepth(float haze) {
    return RAYLEIGH + vec3(MIE * haze) + OZONE;
}

float rayleighPhase(float c) {
    return 0.0596831 * (1.0 + c * c);
}

float miePhase(float c, float g) {
    float g2 = g * g;
    return 0.0795775 * (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * c, 1e-4), 1.5);
}

// Light from a body at `light` after its trip through the air: white high up, deep orange low.
vec3 sunlightColor(vec3 light, float haze) {
    vec3 t = exp(-opticalDepth(haze) * airMass(light.y));
    return t * smoothstep(-0.10, 0.03, light.y);
}

// In-scattered light seen along `dir` from a body at `light` (linear, before exposure).
vec3 scatter(vec3 dir, vec3 light, float haze) {
    float c = dot(dir, light);
    vec3 tau = opticalDepth(haze);
    float mie = MIE * haze;
    vec3 lit = sunlightColor(light, haze);
    // The high air stays lit a little after sunset: the pink-violet after-glow over the dark side.
    float twilight = smoothstep(-0.28, -0.02, light.y) * (1.0 - smoothstep(-0.02, 0.12, light.y));
    lit += vec3(0.80, 0.42, 0.70) * 0.05 * twilight;
    vec3 phase = (RAYLEIGH * rayleighPhase(c) + vec3(mie * miePhase(c, 0.76))) / tau;
    // Soft cap on the air mass: single scattering alone makes the horizon far too bright and white.
    float mass = airMass(max(dir.y, 0.0));
    mass = mass / (1.0 + mass * 0.16);
    vec3 inScatter = phase * (1.0 - exp(-tau * mass));
    return inScatter * lit * 14.0;
}

// The eye opens up as the light fades: dim sunsets still glow, noon is not blown out.
float autoExposure(float sunY) {
    float dusk = (1.0 - smoothstep(-0.04, 0.30, sunY)) * smoothstep(-0.32, -0.04, sunY);
    return 1.15 + 1.0 * dusk;
}

// ---- clouds ----
// Low clouds: a slab `thickness` blocks deep at `height` above the camera, on a curved shell so far ones sink to the
// horizon like real ones. Shapes come from the noise texture: every octave period divides 10240 blocks, which is
// where the camera position and the drift wrap, so nothing ever jumps.

const float PLANET = 60000.0;

// Distance along `dir` to the shell `h` blocks above the camera.
float shellDistance(vec3 dir, float h) {
    float r = PLANET + h;
    float b = PLANET * dir.y;
    return -b + sqrt(max(b * b + r * r - PLANET * PLANET, 0.0));
}

float remap(float v, float a, float b, float c, float d) {
    return c + (v - a) * (d - c) / max(b - a, 1e-4);
}

// Coverage map only (big shapes): for shadows and the cheapest clouds. The ramp from "no cloud" to "solid cloud"
// is narrow, so clouds have full, solid cores rather than being thin all the way through; CloudLook.x widens it
// for soft-edged sheets.
float cloudMap(vec2 xz, float coverage) {
    xz *= CloudLook.z;
    float big = texture(Sampler0, xz * (1.0 / 5120.0)).r;
    float mid = texture(Sampler0, xz * (1.0 / 1280.0) + vec2(0.37, 0.71)).g;
    float shape = big * 0.66 + mid * 0.34;
    float edge = 1.0 - coverage;
    float ramp = mix(0.16, 0.50, CloudLook.x);
    return clamp((shape - edge) / ramp, 0.0, 1.0);
}

// Density at a point `h01` of the way up the slab. Flat bottoms; the top stands as high as the cloud is thick
// there, so thin edges are low and thick cores tower. With `detail`, billows are carved out of the sides and tops
// (never the core), and they slowly churn so a cloud changes shape as it drifts rather than sliding past frozen.
float cloudDensity(vec3 p, float h01, float coverage, bool detail) {
    float base = cloudMap(p.xz, coverage);
    if (base <= 0.0) return 0.0;
    float top = mix(0.30, 1.0, pow(base, mix(1.0, 0.40, CloudLook.w)));
    float d = base * smoothstep(0.0, 0.09, h01) * (1.0 - smoothstep(top * 0.50, top, h01));
    if (detail && d > 0.0) {
        vec2 q = p.xz * CloudLook.z;
        // Four turns an hour, so it joins up exactly where the seconds wrap.
        float turn = SunDir.w * (TAU / 3600.0) * 4.0;
        vec2 churn = vec2(sin(turn), cos(turn)) * 0.06;
        float billow = texture(Sampler0, q * (1.0 / 640.0) + vec2(h01 * 0.17, h01 * 0.11) + churn).g;
        float wisp = texture(Sampler0, q * (1.0 / 160.0) + vec2(h01 * 0.31, -h01 * 0.23) - churn * 2.0).b;
        float carve = (billow * 0.64 + wisp * 0.36) * CloudLook.y;
        d = d - carve * 0.62 * (1.0 - d * 0.55) * (0.45 + 0.75 * h01);
        d *= 1.3;
    }
    return clamp(d, 0.0, 1.0);
}
