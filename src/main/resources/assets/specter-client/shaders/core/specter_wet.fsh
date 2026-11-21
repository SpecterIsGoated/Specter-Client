#version 330
#extension GL_ARB_separate_shader_objects : require

// Specter Realistic Rain: wet ground.
//
// For every pixel of the finished world: is it an upward-facing surface that rain reaches (the top of its column in
// the sky map, not under a roof or a tree, in a biome where it rains)? If so it is darkened like wet stone, pooled into
// puddles where a slow noise is high, rippled by rings where drops land, and given a mirror reflection found by
// marching the reflected view ray across the screen. Dry pixels are left alone (discarded).

#include <specter-client:specter_fx.glsl>

uniform sampler2D Sampler0;   // a copy of the finished frame (linear, clamped)
uniform sampler2D Sampler1;   // the depth of the finished world
uniform sampler2D Sampler2;   // the sky map: rg = top height + 64, b = kind (0 dry, 0.5 ground, 1 water)

layout(location = 0) in vec4 nearPoint;
layout(location = 1) in vec4 farPoint;
layout(location = 2) in vec2 screenUv;

layout(location = 0) out vec4 fragColor;

#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
float depthToNdc(float d) { return d; }
float ndcToDepth(float z) { return z; }
#else
float depthToNdc(float d) { return d * 2.0 - 1.0; }
float ndcToDepth(float z) { return z * 0.5 + 0.5; }
#endif

// Camera-relative position of what is drawn at screen uv.
vec3 sceneAt(vec2 uv, out bool sky) {
    float d = texture(Sampler1, uv).r;
    sky = d <= 0.000001;   // reversed Z: the far plane (sky) is 0
    vec4 h = InvViewProj * vec4(uv * 2.0 - 1.0, depthToNdc(d), 1.0);
    return h.xyz / h.w;
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = fxHash12(i);
    float b = fxHash12(i + vec2(1.0, 0.0));
    float c = fxHash12(i + vec2(0.0, 1.0));
    float d = fxHash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Puddle shapes: a few octaves of slow noise in world blocks (0..1, high = low ground where water pools).
float puddleField(vec2 w) {
    float n = valueNoise(w * 0.11) * 0.55 + valueNoise(w * 0.27 + 7.3) * 0.30 + valueNoise(w * 0.9 + 3.1) * 0.15;
    return n;
}

// Rain ripples: in each cell a drop lands at a random spot every so often and a ring spreads and fades. Returns the
// tilt of the surface (xz) and a crest brightness (z). 'density' thins the drops out in light rain.
vec3 ripples(vec2 p, float t, float density) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    vec2 tilt = vec2(0.0);
    float crest = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 c = cell + vec2(float(i), float(j));
            vec3 h = fxHash33(vec3(c, 19.0));
            if (h.z > density) continue;
            float period = 0.75 + h.x * 0.7;
            float cycle = t / period + h.y;
            float age = fract(cycle);
            // A new landing spot every cycle.
            vec3 spot = fxHash33(vec3(c, floor(cycle) + 3.0));
            vec2 d = f - (vec2(float(i), float(j)) + 0.15 + spot.xy * 0.7);
            float r = length(d);
            float radius = age * 0.55;
            float x = (r - radius) * 26.0;
            if (abs(x) > 3.14159) continue;
            float fade = (1.0 - age) * (1.0 - age);
            float wave = sin(x) * fade;
            tilt += (d / max(r, 0.0001)) * wave;
            crest += max(cos(x * 0.5), 0.0) * fade * 0.5;
        }
    }
    return vec3(tilt, crest);
}

// 1 if rain reaches a surface at height worldY in this sky-map column (it is the top of the column and it rains in
// that biome), else 0. 'kind' gets the column's kind (0.5 ground, 1 water).
float rainReaches(ivec2 texel, float worldY, out float kind) {
    kind = 0.0;
    int size = int(Wet2.z);
    if (texel.x < 0 || texel.y < 0 || texel.x >= size || texel.y >= size) return 0.0;
    vec4 cell = texelFetch(Sampler2, texel, 0);
    kind = cell.b;
    if (kind < 0.25) return 0.0;
    float top = floor(cell.r * 255.0 + 0.5) + floor(cell.g * 255.0 + 0.5) * 256.0 - 64.0;
    return worldY < top - 1.02 || worldY > top + 0.06 ? 0.0 : 1.0;
}

void main() {
    float wetness = Wet0.x;
    if (wetness <= 0.002 || Wet2.w < 0.5) discard;

    float zRaw = texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r;
    float z = 1.0 - zRaw;
    vec4 h = mix(nearPoint, farPoint, z);
    vec3 p = h.xyz / h.w;            // relative to the camera
    // Screen derivatives must be taken while every pixel of the 2x2 block is still running (before any discard).
    vec3 n = normalize(cross(dFdx(p), dFdy(p)));
    if (zRaw <= 0.000001) discard;   // sky
    float dist = length(p);
    if (dist > 70.0) discard;

    // Only surfaces facing up.
    if (n.y < 0.0) n = -n;
    if (n.y < 0.80) discard;

    // ...that rain reaches: the top of their column in the sky map. A sheltered spot right next to a rained-on block at
    // the same height (under the edge of a roof or a tree, the border of a dry biome) gets wet too, fading out half a
    // block in, so the wet does not stop dead on the block line.
    vec2 local = p.xz - Wet2.xy;
    ivec2 texel = ivec2(floor(local));
    vec2 inCell = local - vec2(texel);
    float worldY = p.y + FxCam.y;
    float kind = 0.0;
    float mask = rainReaches(texel, worldY, kind);
    bool water = mask > 0.5 && kind > 0.75;
    if (mask < 0.5) {
        // A slightly wobbly edge so the spill is not a ruler-straight line.
        float wobble = (valueNoise((p.xz + FxCam.xz) * 3.0) - 0.5) * 0.18;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                float k;
                if (rainReaches(texel + ivec2(dx, dz), worldY, k) < 0.5) continue;
                // Distance from this point to that neighbour's square.
                float ex = dx > 0 ? 1.0 - inCell.x : (dx < 0 ? inCell.x : 0.0);
                float ez = dz > 0 ? 1.0 - inCell.y : (dz < 0 ? inCell.y : 0.0);
                float d = length(vec2(ex, ez)) + wobble;
                mask = max(mask, 1.0 - smoothstep(0.15, 0.5, d));
            }
        }
    }
    if (mask <= 0.002) discard;

    // Fade out toward the edge of the map / the effect's reach.
    float edge = min(min(local.x, local.y), min(Wet2.z - local.x, Wet2.z - local.y));
    float reach = smoothstep(0.0, 8.0, edge) * (1.0 - smoothstep(50.0, 70.0, dist));

    vec2 world = p.xz + FxCam.xz;
    float t = FxCam.w;

    // Wet film everywhere, puddles where the noise is high. As it dries the puddle threshold rises, so the film goes
    // first and puddles shrink to their deepest middles before they disappear.
    float film = smoothstep(0.0, 0.35, wetness);
    float pud = 0.0;
    if (!water) {
        float field = puddleField(world);
        float threshold = 1.0 - Wet0.w * 1.05 * smoothstep(0.15, 1.0, wetness);
        pud = smoothstep(threshold, threshold + 0.05, field);
    } else {
        pud = 1.0;
    }

    // Ripples (only while rain is falling): two sizes of rings.
    vec3 ripple = vec3(0.0);
    float rain = Wet0.z;
    if (rain > 0.01) {
        float density = 0.25 + 0.6 * rain;
        vec3 a = ripples(world * 1.7, t, density);
        vec3 b = ripples(world * 3.1 + 11.0, t * 1.15, density * 0.8);
        float where = water ? 1.0 : mix(0.35, 1.0, pud);
        ripple = (a + b * 0.6) * where;
    }

    vec3 base = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0).rgb;
    vec3 v = normalize(p);
    vec3 nr = normalize(vec3(-ripple.x * 0.22, 1.0, -ripple.y * 0.22));

    // Mirror reflection: march the reflected ray across the screen and pick up what it meets.
    vec3 refl = Wet1.rgb;
    float found = 0.0;
    float strength = Wet0.y;
    if (strength > 0.001) {
        vec3 rd = reflect(v, nr);
        if (rd.y < 0.02) rd.y = 0.02;
        rd = normalize(rd);
        float jitter = fxDither(gl_FragCoord.xy);
        float stepLen = 0.18 + dist * 0.012;
        vec3 pos = p + n * 0.03 + rd * stepLen * jitter;
        vec2 hitUv = vec2(-1.0);
        for (int i = 0; i < 28; i++) {
            pos += rd * stepLen;
            stepLen *= 1.16;
            vec4 clip = FxViewProj * vec4(pos, 1.0);
            if (clip.w <= 0.0) break;
            vec3 ndc = clip.xyz / clip.w;
            vec2 uv = ndc.xy * 0.5 + 0.5;
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;
            bool skyHere;
            vec3 scene = sceneAt(uv, skyHere);
            if (skyHere) continue;
            float rayDist = length(pos);
            float sceneDist = length(scene);
            float behind = rayDist - sceneDist;
            if (behind > 0.0 && behind < stepLen * 2.5 + 0.4) {
                // Refine between the last two points.
                vec3 lo = pos - rd * stepLen / 1.16;
                vec3 hi = pos;
                for (int k = 0; k < 5; k++) {
                    vec3 mid = (lo + hi) * 0.5;
                    vec4 mc = FxViewProj * vec4(mid, 1.0);
                    vec2 muv = (mc.xy / mc.w) * 0.5 + 0.5;
                    bool s2;
                    vec3 sc = sceneAt(muv, s2);
                    if (!s2 && length(mid) > length(sc)) hi = mid; else lo = mid;
                }
                vec4 hc = FxViewProj * vec4(hi, 1.0);
                hitUv = (hc.xy / hc.w) * 0.5 + 0.5;
                break;
            }
        }
        if (hitUv.x >= 0.0) {
            vec2 border = min(hitUv, 1.0 - hitUv);
            found = smoothstep(0.0, 0.08, min(border.x, border.y));
            refl = mix(Wet1.rgb, texture(Sampler0, hitUv).rgb, found);
        } else {
            // Left the screen: show the sky, a touch brighter toward the horizon.
            refl = Wet1.rgb * mix(1.1, 0.85, clamp(rd.y, 0.0, 1.0));
        }
    }

    // Fresnel: grazing views mirror much more than looking straight down.
    float cosTheta = clamp(dot(-v, nr), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - cosTheta, 5.0);
    // Puddles are near-mirrors even looking down (exaggerated, as the eye reads wet streets); the thin film only
    // shines at grazing angles.
    float mirror = strength * (water ? 0.40 * mix(0.45, 1.0, fresnel) : film * 0.30 * mix(0.25, 1.0, fresnel) + pud * mix(0.55, 0.85, fresnel));

    vec3 wetBase = water ? base : base * (1.0 - Wet1.a * film);
    vec3 color = mix(wetBase, refl, clamp(mirror, 0.0, 0.85));
    // Ring crests catch the light a little.
    color += vec3(ripple.z) * 0.32 * (Wet1.rgb + 0.2);

    float amount = wetness * reach * mask;
    fragColor = vec4(mix(base, color, amount), 1.0);
}
