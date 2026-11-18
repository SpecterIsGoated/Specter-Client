#version 330

// Specter sky, Atmosphere's clouds. Drawn after the solid terrain with a depth test at the far plane, so only sky
// pixels pay for them (in front of the sun and moon, behind everything solid, under water and glass drawn later).
//  - Low clouds: ray-marched through a curved slab with light marched toward the sun or moon (self-shadowing),
//    a forward-scattering silver lining, a powder darkening on their sunny faces and aerial haze far away.
//  - High clouds: thin cirrus streaks on a higher shell.
// Output is premultiplied: light and coverage.

#moj_import <specter-client:specter_sky.glsl>

in vec4 nearPoint;
in vec4 farPoint;

out vec4 fragColor;

float hgPhase(float c, float g) {
    return miePhase(c, g);
}

void main() {
    vec3 nearPos = nearPoint.xyz / nearPoint.w;
    vec3 farPos = farPoint.xyz / farPoint.w;
    vec3 d = normalize(farPos - nearPos);
    if (d.y < -0.02) discard;
    d.y = max(d.y, 0.0005);

    float rain = Weather.x;
    float storm = clamp(rain * 0.7 + Weather.y * 0.5, 0.0, 1.0);
    float haze = SkyTune2.x * (1.0 + rain * 2.5);
    vec3 sun = SunDir.xyz;
    vec3 moon = MoonDir.xyz;
    // The cloud lighting follows whichever is up: the sun, or the moon once the sun has set.
    bool byMoon = sun.y < -0.06;
    vec3 L = byMoon ? moon : sun;
    vec3 lightColor = byMoon
        ? sunlightColor(moon, haze) * 0.030 * MoonDir.w * vec3(0.70, 0.80, 1.15)
        : sunlightColor(normalize(sun + vec3(0.0, 0.07, 0.0)), haze);
    // Light falling on the clouds from the whole sky: blue by day, pink-violet in twilight, deep purple at night.
    vec3 ambient = scatter(vec3(0.0, 1.0, 0.0), sun, haze) * 2.1 + scatter(normalize(vec3(sun.x, 0.25, sun.z)), sun, haze) * 0.5;
    ambient += vec3(0.016, 0.013, 0.034) + scatter(vec3(0.0, 1.0, 0.0), moon, haze) * MoonDir.w * 0.03;
    // Storm clouds: heavy and dark, the sun hardly getting through and their bellies near black.
    lightColor *= 1.0 - storm * 0.82;
    ambient *= 1.0 - storm * 0.62;
    float albedo = mix(1.0, 0.42, storm);
    vec3 flash = vec3(0.60, 0.64, 0.95) * Weather.z * 1.6;

    float c = dot(d, L);
    // Bright forward lobe (silver lining when looking toward the sun) plus a softer back lobe.
    float phase = mix(hgPhase(c, 0.62), hgPhase(c, -0.22), 0.28) * 11.0 + 0.10;
    float coverage = Cloud.x;
    float opacity = Cloud.y;
    float exposure = FogTune.z * autoExposure(SunDir.y);
    float quality = Weather.w;

    vec3 light = vec3(0.0);
    float transmittance = 1.0;
    float firstHit = 0.0;

    if (Cloud2.z > 0.5 && Cloud.z > 1.0) {
        float h0 = Cloud.z;
        float thick = Cloud.w;
        float t0 = shellDistance(d, h0);
        float t1 = shellDistance(d, h0 + thick);
        firstHit = t0;
        if (t0 < 26000.0) {
            // At grazing angles the slab is long: cap the stretch marched, the far part is lost in the haze anyway.
            t1 = min(t1, t0 + thick * 5.0);
            // Long, grazing paths near the horizon get more steps (up to double) so they do not stipple.
            int steps = int(Cloud2.w * clamp((t1 - t0) / (thick * 2.0), 1.0, 2.0) + 0.5);
            vec2 origin = CamPos.xz + Wind.xy;
            if (steps <= 1) {
                // Cheapest: one look at the coverage at mid-height, shaded by how thick the cloud is there.
                float tm = (t0 + t1) * 0.5;
                vec2 xz = origin + d.xz * tm;
                float dens = cloudDensity(vec3(xz.x, 0.0, xz.y), 0.3, coverage, false);
                float toward = cloudMap(xz + L.xz / max(L.y, 0.12) * thick * 0.5, coverage);
                float alpha = 1.0 - exp(-dens * 4.5);
                vec3 lit = (lightColor * phase * exp(-toward * 2.2) + ambient * (0.75 + 0.25 * (1.0 - dens)) * (1.0 - storm * 0.5 * dens) + flash * dens) * albedo;
                light = lit * alpha;
                transmittance = 1.0 - alpha;
            } else {
                int lightSteps = quality >= 3.0 ? 3 : (quality >= 2.0 ? 2 : 1);
                bool detail = quality >= 1.0;
                float dt = (t1 - t0) / float(steps);
                float jitter = 0.2 + 0.6 * interleavedGradient(gl_FragCoord.xy);
                vec2 toLight = L.xz / max(L.y, 0.10);
                for (int i = 0; i < 128; i++) {
                    if (i >= steps) break;
                    float t = t0 + (float(i) + jitter) * dt;
                    float height = sqrt(t * t + 2.0 * t * PLANET * d.y + PLANET * PLANET) - PLANET;
                    float h01 = clamp((height - h0) / thick, 0.0, 1.0);
                    vec2 xz = origin + d.xz * t;
                    float dens = cloudDensity(vec3(xz.x, 0.0, xz.y), h01, coverage, detail && t < 9000.0);
                    if (dens <= 0.002) continue;
                    // How much cloud lies between here and the light.
                    float toward = 0.0;
                    for (int j = 0; j < 3; j++) {
                        if (j >= lightSteps) break;
                        float s = (float(j) + 0.5) / float(lightSteps);
                        float lh = h01 + (1.0 - h01) * s;
                        vec2 q = xz + toLight * thick * (lh - h01);
                        toward += cloudDensity(vec3(q.x, 0.0, q.y), lh, coverage, false);
                    }
                    toward *= thick * (1.0 - h01) / float(lightSteps);
                    float sigma = dens * 0.052 * (1.0 + storm * 0.9);
                    float lightT = exp(-toward * 0.040 * 0.9) * 0.82 + exp(-toward * 0.040 * 0.15) * 0.18;
                    float powder = 1.0 - exp(-dens * 3.0);
                    vec3 scattered = lightColor * phase * lightT * mix(1.0, powder, 0.55)
                        + ambient * mix(0.34 + 0.66 * h01, 0.10 + 0.90 * h01 * h01, storm) * (1.0 - 0.42 * dens * (1.0 - h01))
                        + flash * dens;
                    scattered *= albedo;
                    float stepT = exp(-sigma * dt);
                    light += transmittance * scattered * (1.0 - stepT);
                    transmittance *= stepT;
                    if (transmittance < 0.02) break;
                }
            }
            // Far clouds fade into the haze over the horizon.
            float far = 1.0 - exp(-t0 / (14000.0 / haze));
            vec3 horizon = scatter(normalize(vec3(d.x, 0.02, d.z)), sun, haze) + vec3(0.006, 0.004, 0.015);
            horizon = mix(horizon, vec3(luma(horizon)), storm * 0.75) * (1.0 - storm * 0.55);
            float alpha = 1.0 - transmittance;
            light = mix(light, horizon * alpha, far * 0.85);
            float fade = 1.0 - smoothstep(14000.0, 26000.0, t0);
            light *= fade;
            transmittance = 1.0 - alpha * fade;
        }
    }

    // High clouds: thin streaks drawn behind the low ones.
    vec3 highLight = vec3(0.0);
    float highAlpha = 0.0;
    if (Cloud2.x > 0.0) {
        float hh = Cloud.z * 2.5 + 1600.0;
        float tc = shellDistance(d, hh);
        if (tc < 60000.0) {
            vec2 q = CamPos.xz * 0.6 + Wind.zw + d.xz * tc;
            // Patches of high cloud, each made of fine fibres combed along the wind, gently bent by a slow warp.
            float patches = smoothstep(0.42, 0.78, texture(Sampler0, q * (1.0 / 20480.0) + 0.17).r);
            vec2 warp = (texture(Sampler0, q * (1.0 / 7680.0)).ag - 0.5) * 0.06;
            float fibres = texture(Sampler0, vec2(q.x * (1.0 / 2560.0), q.y * (1.0 / 7680.0)) + warp).b;
            fibres = fibres * 0.7 + texture(Sampler0, vec2(q.x * (1.0 / 1280.0), q.y * (1.0 / 3840.0)) + warp * 2.0).r * 0.3;
            float wisp = patches * smoothstep(0.38, 0.85, fibres) * Cloud2.x * (1.0 - smoothstep(25000.0, 60000.0, tc));
            // Thin high cloud barely shows at night: a faint veil, not dark streaks across the stars.
            highAlpha = wisp * 0.6 * (1.0 - storm * 0.6) * mix(0.25, 1.0, smoothstep(-0.25, 0.05, sun.y));
            highLight = (lightColor * (0.55 + hgPhase(c, 0.75) * 4.0) * 1.3 + ambient * 1.3) * highAlpha;
        }
    }

    vec3 col = light + highLight * transmittance;
    float alpha = (1.0 - transmittance) + highAlpha * transmittance;
    alpha *= opacity;
    col *= opacity;
    if (alpha <= 0.001) discard;
    // Into display space the way the sky is, keeping the premultiplied coverage.
    vec3 unpremult = col / alpha;
    vec3 shown = toDisplay(tonemap(unpremult * exposure));
    shown = saturateColor(shown, SkyTune.x) * SkyTune.y;
    // Clouds right at the horizon melt into the terrain's fog colour like the sky does.
    fragColor = vec4(shown * alpha, alpha * smoothstep(-0.02, 0.03, d.y));
    fragColor.rgb *= smoothstep(-0.02, 0.03, d.y);
}
