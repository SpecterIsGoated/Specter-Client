#version 330

// Specter sky, the sky itself (drawn in vanilla's sky pass, before the sun, moon and terrain):
//  - Atmosphere's day sky: scattering, sun glow, sunrise and sunset colour, weather, custom colours;
//  - Nebula Night: the baked Milky Way and nebulae (Blender, tools/blender), procedural twinkling stars, cosmic
//    dust, horizon glow, aurora, constellations and shooting stars.
// With Atmosphere off it only darkens vanilla's night sky and adds Nebula Night on top (premultiplied alpha).

#moj_import <specter-client:specter_sky.glsl>

layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

uniform sampler2D Sampler1;   // Milky Way, colour
uniform sampler2D Sampler2;   // nebula masks: r first colour, g second colour, b dust

in vec4 nearPoint;
in vec4 farPoint;

out vec4 fragColor;

// ---- the sky sphere ----

// Direction on the sky sphere -> spot in the equirectangular textures, the way tools/blender/galaxy.py renders
// them: v = 0 straight up, u = 0.5 toward +X.
vec2 equirect(vec3 d) {
    return vec2(0.5 + atan(-d.z, d.x) / TAU, acos(clamp(d.y, -1.0, 1.0)) / PI);
}

vec3 starColor(float t) {
    vec3 c = mix(vec3(0.62, 0.72, 1.00), vec3(0.93, 0.95, 1.00), smoothstep(0.05, 0.30, t));
    c = mix(c, vec3(1.00, 0.93, 0.82), smoothstep(0.55, 0.75, t));
    c = mix(c, vec3(1.00, 0.76, 0.56), smoothstep(0.82, 0.95, t));
    // A breath of Specter violet in every star.
    return mix(c, vec3(0.86, 0.76, 1.0), 0.12);
}

// One layer of stars: one possible star per cell of a grid on the faces of a cube round the sky. `px` is the angular
// size of a screen pixel, so a star is never thinner than a pixel (no shimmer when it is small) and keeps its light
// when it is spread out.
vec3 starLayer(vec3 d, float cells, float chance, float scale, float px, float t, float twinkle, float seed, float horizonBoost) {
    vec3 a = abs(d);
    vec2 uv;
    float face;
    if (a.x >= a.y && a.x >= a.z) {
        uv = d.yz / a.x;
        face = d.x > 0.0 ? 0.0 : 1.0;
    } else if (a.y >= a.z) {
        uv = d.xz / a.y;
        face = d.y > 0.0 ? 2.0 : 3.0;
    } else {
        uv = d.xy / a.z;
        face = d.z > 0.0 ? 4.0 : 5.0;
    }
    vec2 g = (uv * 0.5 + 0.5) * cells;
    vec2 cell = floor(g);
    vec3 h = hash33(vec3(cell, face * 131.0 + seed));
    if (h.x > chance) return vec3(0.0);
    vec3 h2 = hash33(vec3(cell.yx + 17.0, face + seed * 7.0));
    vec2 spot = cell + 0.22 + 0.56 * h2.xy;
    // Grid units -> radians (a cube face is flatter at its middle than its corners).
    float dist = length(g - spot) * (2.0 / cells) / (1.0 + dot(uv, uv));
    float bright = 0.035 + 0.965 * pow(h.y, 7.0);
    float size = px * (0.55 + 1.1 * bright);
    // Never wider than a quarter of the cell: a star is only looked for in its own cell.
    float cellAngle = (2.0 / cells) / (1.0 + dot(uv, uv));
    size = min(size, cellAngle * 0.16);
    float r = clamp(px * 0.8, size, cellAngle * 0.16);
    float core = exp(-dist * dist / (r * r)) * (size * size) / (r * r);
    // The brightest few get a soft glow, kept small.
    float halo = smoothstep(0.55, 1.0, bright) * 0.07 * exp(-dist / min(px * 5.0, cellAngle * 0.05));
    float flicker = 1.0;
    if (twinkle > 0.0) {
        float w = sin(t * (1.7 + 4.3 * h2.z) + h.z * 91.0) * sin(t * (0.9 + 2.9 * h.y) + h2.x * 57.0);
        flicker = 1.0 + twinkle * 0.6 * horizonBoost * w;
    }
    return starColor(h.z) * bright * scale * (core + halo) * flicker;
}

// ---- night pieces ----

vec3 horizonGlow(vec3 d, float t) {
    float az = atan(d.x, d.z);
    vec3 tint = mix(vec3(0.42, 0.18, 0.95), vec3(0.16, 0.34, 1.0), 0.5 + 0.5 * sin(az * 2.0 + t * 0.012));
    float band = exp(-max(d.y, 0.0) * 9.0) * smoothstep(-0.08, 0.0, d.y);
    return tint * band * 0.035;
}

// Curtains of light over the northern sky (-Z is north in Minecraft), made of thin slices at rising heights.
vec3 aurora(vec3 d, float t, int steps, vec2 pixel) {
    if (d.y < 0.015) return vec3(0.0);
    float north = smoothstep(-0.55, 0.35, -d.z / max(length(d.xz), 1e-3));
    if (north <= 0.0) return vec3(0.0);
    vec3 acc = vec3(0.0);
    // Random (not patterned) offset per pixel: slices blur into one curtain instead of showing as lines.
    float jitter = hash12(pixel);
    for (int i = 0; i < steps; i++) {
        float k = (float(i) + jitter) / float(steps);
        float h = 1.0 + k * 0.85;
        // Every slice reads the same ribbon field on a plane at its own height, so a ribbon on the ground plan
        // stacks up into a hanging curtain.
        vec2 p = d.xz * (h / (d.y + 0.05)) * 0.11 + vec2(0.0, -0.35);
        vec2 warp = vec2(texture(Sampler0, p * 0.55 + vec2(t * 0.0009, t * 0.0005)).a, texture(Sampler0, p * 0.45 - vec2(t * 0.0006, 0.0)).g) - 0.5;
        float field = texture(Sampler0, p * 0.32 + warp * 0.10 + vec2(t * 0.00025, 0.0)).r;
        // Thin contour lines of the field are the ribbons; a second, fainter set gives the wide glow round them.
        float line = abs(field - 0.5);
        float curtain = (1.0 - smoothstep(0.0, 0.022, line)) + (1.0 - smoothstep(0.0, 0.09, line)) * 0.25;
        // Vertical rays: brightness that changes along the curtain but not up it.
        float rays = 0.45 + 1.1 * texture(Sampler0, vec2(field * 9.0 + warp.x * 0.3 + t * 0.0012, 0.37)).b;
        float weight = pow(1.0 - k, 1.6) * smoothstep(0.0, 0.10, k);
        vec3 color = mix(vec3(0.10, 1.00, 0.62), vec3(0.20, 0.55, 1.00), smoothstep(0.10, 0.45, k));
        color = mix(color, vec3(0.62, 0.22, 1.00), smoothstep(0.40, 0.85, k));
        acc += color * curtain * rays * weight;
    }
    return acc * (1.3 / float(steps)) * north * smoothstep(0.015, 0.12, d.y);
}

// Thin lines between the stars of a few real constellations, on the turning sky sphere.
vec3 constellations(vec3 sd, float px) {
    int count = int(Extras.z + 0.5);
    float lines = 0.0;
    float stars = 0.0;
    for (int i = 0; i < 32; i++) {
        if (i >= count) break;
        vec3 a = Lines[i * 2].xyz;
        vec3 b = Lines[i * 2 + 1].xyz;
        vec3 n = cross(a, b);
        float ln = length(n);
        if (ln < 1e-5) continue;
        n /= ln;
        float off = abs(dot(sd, n));
        // Inside the arc from a to b, and kept clear of the stars at its ends.
        float inside = step(0.0, dot(cross(a, sd), n)) * step(0.0, dot(cross(sd, b), n));
        float gap = smoothstep(0.010, 0.016, acos(clamp(dot(sd, a), -1.0, 1.0))) * smoothstep(0.010, 0.016, acos(clamp(dot(sd, b), -1.0, 1.0)));
        lines = max(lines, inside * gap * (1.0 - smoothstep(px * 0.35, px * 1.25, off)));
        float da = acos(clamp(dot(sd, a), -1.0, 1.0));
        float db = acos(clamp(dot(sd, b), -1.0, 1.0));
        float r = px * 1.3;
        stars = max(stars, max(Lines[i * 2].w * exp(-da * da / (r * r)), Lines[i * 2 + 1].w * exp(-db * db / (r * r))));
    }
    return vec3(0.62, 0.52, 1.0) * lines * 0.20 + vec3(0.92, 0.90, 1.0) * stars * 1.4;
}

// Shooting stars: each is a short great-circle path; the head glows, the trail fades out behind it, and fireballs
// leave a glowing train that lingers after the head has burnt out (progress past 1).
vec3 meteors(vec3 d, float px) {
    vec3 acc = vec3(0.0);
    int count = int(Extras.y + 0.5);
    for (int i = 0; i < 6; i++) {
        if (i >= count) break;
        vec4 m0 = Meteors[i * 4];
        vec4 m1 = Meteors[i * 4 + 1];
        vec4 m2 = Meteors[i * 4 + 2];
        vec4 m3 = Meteors[i * 4 + 3];
        vec3 a = m0.xyz;
        vec3 b = m1.xyz;
        float progress = m0.w;
        float width = max(m1.w, px * 0.75);
        vec3 n = cross(a, b);
        float span = asin(clamp(length(n), 0.0, 1.0));
        if (span < 1e-4) continue;
        n = normalize(n);
        float off = dot(d, n);
        if (abs(off) > width * 16.0 + 0.03) continue;
        // Angle along the path to this pixel's nearest point on it.
        vec3 onPlane = normalize(d - n * off);
        float along = atan(dot(cross(a, onPlane), n), dot(a, onPlane));
        float head = span * min(progress, 1.0);
        float behind = head - along;
        float alive = smoothstep(0.0, 0.06, progress) * (1.0 - smoothstep(0.85, 1.0, progress));
        vec3 color = m2.rgb;
        vec3 light = vec3(0.0);
        if (alive > 0.0) {
            // Trail: brightest right behind the head, thinning and fading along its length.
            float trailLength = max(span * m3.x, 1e-3);
            if (behind > 0.0 && along > 0.0) {
                float f = behind / trailLength;
                float w = width * (1.0 - 0.65 * min(f, 1.0));
                light += color * exp(-off * off / (w * w)) * exp(-f * 3.0) * 0.9;
            }
            // Head: hot white core and a soft bloom in the meteor's colour.
            vec3 hp = normalize(cos(head) * a + sin(head) * cross(n, a));
            float dh = acos(clamp(dot(d, hp), -1.0, 1.0));
            light += mix(color, vec3(1.0), 0.7) * exp(-dh * dh / (width * width * 1.5)) * 1.7;
            light += color * exp(-dh / (width * 5.0)) * 0.2;
            light *= alive;
        }
        // Fireballs leave a train of glowing gas along their whole path that fades over a few seconds.
        if (m3.y > 0.5) {
            float age = max(progress - 0.5, 0.0);
            float train = exp(-off * off / (width * width * 2.5)) * step(0.0, along) * step(along, head);
            light += vec3(0.55, 0.38, 1.0) * train * exp(-age * 1.4) * smoothstep(0.0, 0.3, progress) * 0.22;
        }
        acc += light * m2.w;
    }
    return acc;
}

// Everything Nebula Night adds, linear light, before the night fade.
vec3 nightSky(vec3 d, float px, vec2 pixel) {
    float t = SunDir.w;
    vec3 sd = normalize((StarRot * vec4(d, 0.0)).xyz);
    int quality = int(Extras.w + 0.5);
    // Thicker air near the horizon: dimmer, and the stars twinkle more.
    float lowAir = exp(-0.11 * airMass(max(d.y, 0.0)));
    float twinkleAir = 1.0 + 1.6 * (1.0 - smoothstep(0.0, 0.5, d.y));

    vec2 uv = equirect(sd);
    vec3 milkyWay = toLinear(texture(Sampler1, uv).rgb);
    float bandLight = luma(milkyWay);
    // The nebulae drift very slowly, each colour layer its own way, so they feel deep rather than painted on.
    vec2 flowA = (texture(Sampler0, uv * vec2(6.0, 3.0) + vec2(t * 0.00031, 0.0)).ba - 0.5) * 0.0026;
    vec2 flowB = (texture(Sampler0, uv * vec2(5.0, 2.5) - vec2(0.0, t * 0.00027)).ag - 0.5) * 0.0026;
    float maskA = texture(Sampler2, uv + flowA).r;
    float maskB = texture(Sampler2, uv + flowB).g;
    float dust = texture(Sampler2, uv).b;
    vec3 nebula = (pow(maskA, 1.6) * NebulaA.rgb + pow(maskB, 1.6) * NebulaB.rgb) * Nebula.y;
    nebula *= 1.0 - 0.55 * dust;
    vec3 col = milkyWay * Nebula.x * 0.42 + nebula * 0.24;

    if (NebulaB.a > 0.5) {
        float density = Stars.x;
        float bright = Stars.y;
        float tw = Stars.z;
        vec3 stars = starLayer(sd, 64.0, 0.020 * density, 1.0, px, t, tw, 1.0, twinkleAir);
        stars += starLayer(sd, 150.0, 0.030 * density, 0.5, px, t, tw, 2.0, twinkleAir);
        if (quality >= 1) stars += starLayer(sd, 330.0, density * clamp(0.012 + bandLight * 1.2, 0.0, 0.25), 0.26, px, t, tw * 0.5, 3.0, twinkleAir);
        if (quality >= 2) stars += starLayer(sd, 680.0, density * clamp(bandLight * 2.0 - 0.01, 0.0, 0.35), 0.16, px, t, 0.0, 4.0, 1.0);
        // The galaxy's dust lanes hide the stars behind them, as they do in the real sky.
        col += stars * bright * (1.0 - 0.75 * dust);
    }

    // Cosmic dust: faint violet motes on a sphere that turns a little faster than the stars - a hint of depth.
    if (NebulaA.a > 0.0 && quality >= 1) {
        float s = t * 0.0035;
        vec3 dd = vec3(sd.x * cos(s) - sd.z * sin(s), sd.y, sd.x * sin(s) + sd.z * cos(s));
        col += starLayer(dd, 240.0, 0.02, 0.10, px * 1.6, t, 0.8, 9.0, 1.0) * vec3(0.75, 0.55, 1.0) * NebulaA.a;
    }

    col *= lowAir;
    if (Extras.x > 0.0) col += constellations(sd, px) * Extras.x * lowAir;
    if (Nebula.w > 0.0 && quality >= 1) {
        int steps = quality >= 3 ? 48 : (quality == 2 ? 32 : 20);
        col += aurora(d, t, steps, pixel) * Nebula.w;
    }
    col += horizonGlow(d, t) * Nebula.z;
    col *= Stars.w;
    // Meteors burn high in the air: not dimmed by the sky brightness setting, only by the air near the horizon.
    col += meteors(d, px) * mix(0.25, 1.0, lowAir);
    return col;
}

// ---- day sky (Atmosphere) ----

// Specter's deep midnight purple, what a clear night sky settles to.
vec3 nightBase(vec3 d) {
    float up = sqrt(max(d.y, 0.0));
    return mix(vec3(0.0034, 0.0021, 0.0085), vec3(0.0007, 0.0005, 0.0021), up);
}

// The sky in linear light, before exposure, with weather and the custom colours mixed in.
vec3 atmosphereSky(vec3 d) {
    float haze = SkyTune2.x * (1.0 + Weather.x * 2.5);
    vec3 dir = normalize(vec3(d.x, max(d.y, 0.0) + 0.0005, d.z));
    vec3 sun = SunDir.xyz;
    vec3 moon = MoonDir.xyz;
    float c = dot(dir, sun);

    vec3 col = scatter(dir, sun, haze);
    // Moonlight scatters too: a faint blue-grey dome round the moon on bright nights.
    col += scatter(dir, moon, haze) * MoonDir.w * 0.004 * vec3(0.70, 0.82, 1.20);
    col += nightBase(dir);

    // Sun glow: a tight bright ring and a wide soft one, both in the sun's own (sunset-tinted) colour.
    vec3 sunLight = sunlightColor(sun, haze);
    col += sunLight * (miePhase(c, 0.97) * 0.020 + miePhase(c, 0.80) * 0.010) * SkyTune.z;

    // Sunrise and sunset: gold by the sun, through pink, to violet across the sky, lying low along the horizon.
    float low = smoothstep(-0.20, -0.02, sun.y) * (1.0 - smoothstep(0.02, 0.42, sun.y));
    if (low > 0.0 && SkyTune.w > 0.0) {
        vec2 hz = normalize(dir.xz + vec2(1e-5));
        vec2 sz = normalize(sun.xz + vec2(1e-5));
        float toward = 0.5 + 0.5 * dot(hz, sz);
        float band = pow(1.0 - dir.y, 4.0);
        vec3 warm = mix(vec3(0.95, 0.36, 0.55), vec3(1.0, 0.55, 0.16), smoothstep(0.55, 1.0, toward));
        warm = mix(vec3(0.42, 0.24, 0.80), warm, smoothstep(0.15, 0.6, toward));
        float strength = band * (0.25 + 0.75 * toward * toward) * low;
        col += warm * warm * strength * 0.30 * SkyTune.w;
        // The warm light also reaches part-way up the sky.
        col += warm * pow(1.0 - dir.y, 1.5) * toward * low * 0.06 * SkyTune.w;
    }

    // Custom colours: a gradient of the player's own, mixed over the model.
    if (SkyTune2.y > 0.0) {
        float day = State.z;
        vec3 custom = mix(CustomHorizon.rgb, CustomZenith.rgb, pow(dir.y, 0.6)) * mix(0.05, 1.0, day);
        float near = pow(max(c, 0.0), 3.0) * low;
        custom = mix(custom, CustomSunset.rgb, clamp(near + low * pow(1.0 - dir.y, 3.0) * 0.6, 0.0, 1.0));
        col = mix(col, custom * 0.9, SkyTune2.y);
    }

    // Weather: rain greys and darkens it, thunder more so; a lightning flash lights the whole sky.
    float storm = clamp(Weather.x * 0.75 + Weather.y * 0.25, 0.0, 1.0);
    col = mix(col, vec3(luma(col)) * vec3(0.92, 0.95, 1.0), storm * 0.75) * (1.0 - storm * 0.55 - Weather.y * 0.2);
    col += vec3(0.55, 0.58, 0.75) * Weather.z * 0.6;
    return col;
}

void main() {
    vec3 nearPos = nearPoint.xyz / nearPoint.w;
    vec3 farPos = farPoint.xyz / farPoint.w;
    vec3 d = normalize(farPos - nearPos);
    // Angular size of this pixel, for crisp stars and lines at any resolution and field of view.
    float px = max(length(fwidth(d)), 1e-5);
    vec2 pixel = gl_FragCoord.xy;
    float night = State.w;
    float exposure = FogTune.z * autoExposure(SunDir.y);

    vec3 emission = vec3(0.0);
    if (State.y > 0.5 && night > 0.001) {
        emission = nightSky(d, px, pixel) * night * (1.0 - Weather.x * 0.92);
        emission *= smoothstep(-0.06, 0.0, d.y);
    }

    if (State.x > 0.5) {
        vec3 col = atmosphereSky(d) + emission;
        col = toDisplay(tonemap(col * exposure));
        col = saturateColor(col, SkyTune.x) * SkyTune.y;
        // Meet the terrain's fog at the horizon, so far-off land melts into the sky without a seam.
        float blend = FogTune.x * (1.0 - smoothstep(-0.02, 0.10, d.y));
        col = mix(col, FogColor.rgb, clamp(blend, 0.0, 1.0));
        fragColor = vec4(col, 1.0);
    } else {
        // Vanilla's sky stays; at night it is pulled down to deep space and the night sky goes on top.
        float cover = night * 0.88 * (1.0 - Weather.x * 0.7) * smoothstep(-0.05, 0.12, d.y);
        vec3 base = toDisplay(nightBase(d) * exposure * 1.4);
        vec3 glow = toDisplay(tonemap(emission * exposure)) - toDisplay(vec3(0.0));
        fragColor = vec4(base * cover + glow, cover);
    }
}
