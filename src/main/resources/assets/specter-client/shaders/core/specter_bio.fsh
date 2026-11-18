#version 330

// Specter Bioluminescence: water that glows blue-green at night, the way a plankton bloom does - not as a flat tint
// but where the water is stirred: along the shore where small waves break, in drifting patches, as pinpoint sparks,
// and in the wake of whatever moves through it.
//
// Found from two depth pictures of the frame: one taken after the solid world was drawn, one after the water. Where
// the second is nearer than the first, something see-through was drawn; if that something faces up and sits at the
// height a water surface sits at (8/9 of a block), it is water. Its glow is ADDED to the frame (light, not paint),
// so it can never darken anything and fades to exactly nothing by day.
//
// With the camera under water the same pass adds drifting motes of light in the water and a soft moving glow on the
// sea floor.

#moj_import <specter-client:specter_fx.glsl>

uniform sampler2D Sampler0;   // tiling noise (r big shapes, g puffy cells, b fine detail)
uniform sampler2D Sampler1;   // depth of the solid world only (before water was drawn)
uniform sampler2D Sampler2;   // depth of the finished world

in vec4 nearPoint;
in vec4 farPoint;
in vec2 screenUv;

out vec4 fragColor;

vec3 relAt(float z) {
    vec4 h = mix(nearPoint, farPoint, z);
    return h.xyz / h.w;
}

// The glow of a point on the water surface. `p` is relative to the camera; `floorDrop` is how far below the surface
// the bed seen through it lies (large or negative when there is none).
vec3 surfaceGlow(vec3 p, float floorDrop, float dist) {
    float t = FxCam.w;
    vec2 w = FxCam.xz + p.xz;
    float speed = BioTune.y;

    // Plankton patches: two layers of big soft shapes sliding against each other, so the bright areas slowly
    // appear, merge and thin out rather than scroll past.
    float a = texture(Sampler0, w / 128.0 + vec2(t * 0.0031, t * 0.0019) * speed).r;
    float b = texture(Sampler0, w / 32.0 - vec2(t * 0.0052, -t * 0.0037) * speed).g;
    float bloom = smoothstep(0.50, 0.84, a * 0.62 + b * 0.38) * Bio.w;

    // Ripples: fine bright threads that cross the patches, the crests of small waves catching.
    float threads = texture(Sampler0, w / 8.0 + vec2(t * 0.024, t * 0.017) * speed).b;
    float ripple = smoothstep(0.58, 0.92, threads) * (0.30 + 0.70 * bloom);

    // The shore: brightest right at the waterline, falling away over the first block or two of depth, in bands
    // that travel in toward the land like wavelets running up a beach.
    float shore = 0.0;
    if (floorDrop > -0.05 && floorDrop < 6.0) {
        float shallow = exp(-max(floorDrop, 0.0) * 1.5);
        float lap = 0.5 + 0.5 * sin(floorDrop * 8.0 + t * 2.1 * speed + a * 7.0);
        shore = shallow * (0.40 + 0.60 * lap * lap) * Bio.y;
    }

    // Sparks: a grid of cells a third of a block across; a few cells hold a spark, each flaring on its own clock.
    // Rain stirs the surface, so it sparks more.
    vec2 cell = floor(w * 3.0), f = fract(w * 3.0);
    float h = fxHash12(cell);
    float clock = fract(h * 13.7 + t * (0.12 + 0.22 * h) * speed);
    float flare = smoothstep(0.0, 0.06, clock) * (1.0 - smoothstep(0.06, 0.34, clock));
    vec2 at = vec2(fxHash12(cell + 7.1), fxHash12(cell + 3.3)) * 0.6 + 0.2;
    float spark = smoothstep(0.24, 0.0, length(f - at)) * flare * step(0.74 - FxState.y * 0.25, h);
    spark *= Bio.z * (0.35 + 1.5 * bloom + shore) / (1.0 + dist * 0.05);

    // Wakes: each a ring spreading from where the water was stirred, with a bright middle at first.
    float wake = 0.0;
    for (int i = 0; i < 12; i++) {
        vec4 k = Wakes[i];
        if (k.w <= 0.0) continue;
        float d = length(p.xz - k.xy);
        float radius = 0.5 + k.z * 1.7;
        float ring = exp(-pow((d - radius) * 1.5, 2.0)) * 0.8 + exp(-d * d * 0.7) * max(0.0, 1.0 - k.z * 0.9);
        wake += ring * k.w * exp(-k.z * 0.85);
    }
    wake *= BioA.a;

    float glow = bloom * 0.34 + ripple * 0.26 + shore + spark + wake;
    // Dim glow is the deep colour; as it brightens it shifts to the bright colour, and the very brightest whitens.
    vec3 col = mix(BioA.rgb, BioB.rgb, clamp(glow * 0.85, 0.0, 1.0)) * glow;
    col += vec3(0.55, 1.0, 0.95) * max(glow - 1.0, 0.0) * 0.4;
    return col;
}

void main() {
    float strength = Bio.x;
    if (strength <= 0.001) discard;

    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float zSolid = texelFetch(Sampler1, pixel, 0).r;
    float zAll = texelFetch(Sampler2, pixel, 0).r;
    bool under = FxState.x > 0.5;
    float reach = BioTune.x;
    vec3 col = vec3(0.0);

    // ---- the water surface (from above, or from below when the camera is under it) ----
    if (zAll < zSolid - 1e-7 && zAll < 0.999999) {
        vec3 p = relAt(zAll);
        float dist = length(p);
        if (dist < reach) {
            // Facing up or down, and at the height water lies at: a flat face 8/9 of the way up a block.
            vec3 n = normalize(cross(dFdx(p), dFdy(p)));
            float level = fract(FxCam.y + p.y);
            float tolerance = 0.035 + dist * 0.0012;
            if (abs(n.y) > 0.82 && abs(level - 0.8889) < tolerance) {
                float floorDrop = 99.0;
                if (zSolid < 0.999999) floorDrop = p.y - relAt(zSolid).y;
                float fade = 1.0 - smoothstep(reach * 0.55, reach, dist);
                col += surfaceGlow(p, under ? 99.0 : floorDrop, dist) * fade * (under ? 0.38 : 1.0);
            }
        }
    }

    // ---- under water: motes drifting in the water, and light playing on the bed ----
    if (under) {
        float t = FxCam.w;
        vec3 rd = normalize(farPoint.xyz / farPoint.w);
        float sceneDist = zAll >= 0.999999 ? 48.0 : length(relAt(zAll));
        vec3 cam = FxCam.xyz;

        // Motes: real points in the water (so near ones slide past faster than far ones). The ray is sampled at a
        // few distances; at each, the mote living in that cell of space shows if the ray passes close to it, and
        // fades in and out as it nears and leaves the sampled distance so none ever pops.
        float motes = 0.0;
        if (BioB.a > 0.0) {
            for (int i = 0; i < 9; i++) {
                float tk = 1.5 + float(i) * 1.2;
                if (tk > sceneDist) break;
                vec3 q = cam + rd * tk;
                // They rise slowly and wander.
                q.y -= t * 0.045;
                q.xz += vec2(sin(t * 0.17 + q.y), cos(t * 0.13 + q.y * 1.3)) * 0.12;
                vec3 grid = q * 1.5;
                vec3 id = floor(grid);
                vec3 rnd = fxHash33(id);
                vec3 off = (rnd - 0.5) * 0.7 - (fract(grid) - 0.5);
                float along = dot(off, rd);
                float across = length(off - rd * along);
                float size = 0.035 + 0.045 * rnd.x;
                float seen = smoothstep(size, 0.0, across) * (1.0 - smoothstep(0.22, 0.48, abs(along)));
                float twinkle = 0.45 + 0.55 * sin(t * (0.9 + 1.8 * rnd.y) + rnd.z * 6.283);
                motes += seen * twinkle * step(0.42, rnd.z) / (1.0 + tk * 0.10);
            }
        }
        col += BioB.rgb * motes * BioB.a * 1.6;

        // The bed: slow nets of light crawling over upward faces near you, like the glow of the bloom above.
        if (BioTune.w > 0.0 && zAll >= zSolid - 1e-7 && zSolid < 0.999999 && sceneDist < 28.0) {
            vec3 p = relAt(zSolid);
            vec3 n = normalize(cross(dFdx(p), dFdy(p)));
            vec2 w = FxCam.xz + p.xz;
            float n1 = texture(Sampler0, w / 16.0 + vec2(t * 0.011, t * 0.007)).g;
            float n2 = texture(Sampler0, w / 16.0 - vec2(t * 0.009, -t * 0.012) + 0.37).g;
            float net = pow(1.0 - abs(n1 - n2), 14.0);
            col += mix(BioA.rgb, BioB.rgb, 0.5) * net * BioTune.w * 0.34 * smoothstep(0.3, 0.9, abs(n.y)) * exp(-sceneDist * 0.11);
        }
    }

    col *= strength;
    if (max(col.r, max(col.g, col.b)) <= 0.0015) discard;
    // Back to display space for adding onto the frame.
    fragColor = vec4(pow(col, vec3(1.0 / 2.2)), 1.0);
}
