#version 330

// Specter Color Grading: the finished picture of the world (terrain, sky, entities, your hand - not the HUD) put
// through a small colour pipeline, in the order a photo editor would: exposure and white balance on the light itself,
// then tone (shadows, highlights, contrast), then colour (saturation, vibrance, split toning), then finish (fade,
// vignette, grain). Grade3.y cross-fades between the untouched picture and the graded one.

#moj_import <specter-client:specter_fx.glsl>

uniform sampler2D Sampler0;   // a copy of the frame

in vec4 nearPoint;
in vec4 farPoint;
in vec2 screenUv;

out vec4 fragColor;

void main() {
    vec3 original = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0).rgb;
    float amount = Grade3.y;
    if (amount <= 0.001) {
        fragColor = vec4(original, 1.0);
        return;
    }

    // ---- light: in linear, where doubling a number doubles the light ----
    vec3 lin = pow(max(original, 0.0), vec3(2.2));
    lin *= exp2(Grade0.x);
    // White balance: temperature slides blue <-> orange, tint slides green <-> magenta; brightness is kept.
    float temp = Grade1.x, tint = Grade1.y;
    vec3 balance = vec3(1.0 + 0.32 * temp + 0.10 * tint, 1.0 - 0.18 * tint, 1.0 - 0.32 * temp + 0.10 * tint);
    balance /= fxLuma(balance);
    lin *= balance;
    vec3 c = pow(max(lin, 0.0), vec3(1.0 / 2.2));

    // ---- tone ----
    float l = clamp(fxLuma(c), 0.0, 1.0);
    float inShadow = 1.0 - smoothstep(0.0, 0.55, l);
    float inHighlight = smoothstep(0.45, 1.0, l);
    // Shadows: a gamma curve that only bites where it is dark (up lifts detail out of the dark, down crushes it).
    c = pow(max(c, 0.0), vec3(exp2(-Grade2.y * inShadow * 1.2)));
    // Highlights: the same from the bright end (up pushes them brighter, down pulls skies and snow back).
    c = 1.0 - pow(max(1.0 - c, 0.0), vec3(exp2(Grade2.x * inHighlight * 1.2)));
    // Contrast pivots on mid grey, brightness is a plain lift.
    c = (c - 0.5) * Grade0.z + 0.5 + Grade0.y;
    c = pow(max(c, 0.0), vec3(1.0 / max(Grade1.z, 0.05)));

    // ---- colour ----
    l = fxLuma(c);
    c = mix(vec3(l), c, Grade0.w);
    // Vibrance: saturation that leaves already vivid colours alone, so grass does not go neon before skin warms up.
    float vivid = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(vec3(l), c, 1.0 + Grade1.w * (1.0 - clamp(vivid, 0.0, 1.0)));
    // Split toning: one colour washed into the shadows, another into the highlights.
    c += (GradeShadowTint.rgb - 0.5) * GradeShadowTint.a * inShadow * 0.6;
    c += (GradeHighlightTint.rgb - 0.5) * GradeHighlightTint.a * inHighlight * 0.6;

    // ---- finish ----
    // Fade: blacks lifted and whites pulled in, the washed look of old film.
    c = mix(c, c * 0.86 + 0.07, Grade3.x);
    // Vignette: corners darkened, measured in a circle whatever the shape of the window.
    vec2 fromCentre = (screenUv - 0.5) * vec2(min(Grade3.w, 1.6), 1.0);
    c *= 1.0 - Grade2.z * smoothstep(0.28, 0.95, length(fromCentre) * 1.25);
    // Grain: finest in the mid tones, none in pure black or white, new every frame.
    float noise = fxHash12(gl_FragCoord.xy + fract(Grade3.z * 7.31) * 911.0) - 0.5;
    c += noise * Grade2.w * 0.14 * (1.0 - abs(2.0 * clamp(l, 0.0, 1.0) - 1.0) * 0.7);

    fragColor = vec4(mix(original, clamp(c, 0.0, 1.0), amount), 1.0);
}
