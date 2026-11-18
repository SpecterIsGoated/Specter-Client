#version 330

// Sky Lyrics, fragment stage. Sampler1 is a copy of the depth buffer taken after the solid terrain and entities
// were drawn but before water and clouds, so only solid things can hide the words: where something nearer than the
// words was already there, the pixel is dropped; water and clouds, drawn since, are simply written over.
layout(std140) uniform DynamicTransforms {
    mat4 cameraFromWorld;
    vec4 tint;
};

uniform sampler2D Sampler0; // the line's words
uniform sampler2D Sampler1; // depth of the solid world

in vec2 glyphUv;
in vec4 shade;

out vec4 pixel;

void main() {
    float solidDepth = texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r;
    bool hidden = gl_FragCoord.z > solidDepth;

    vec4 ink = texture(Sampler0, glyphUv) * shade * tint;
    if (hidden || ink.a <= 0.0) {
        discard;
    }
    pixel = ink;
}
