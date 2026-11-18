#version 330
#extension GL_ARB_separate_shader_objects : require

// Sky Lyrics, fragment stage. Sampler1 is a copy of the depth buffer taken after the solid terrain and entities
// were drawn but before water and clouds, so only solid things can hide the words: where something nearer than the
// words was already there, the pixel is dropped; water and clouds, drawn since, are simply written over.
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0; // the line's words
uniform sampler2D Sampler1; // depth of the solid world

layout(location = 0) in vec2 glyphUv;
layout(location = 1) in vec4 shade;

layout(location = 0) out vec4 pixel;

void main() {
    float solidDepth = texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r;
    // Reversed Z in 26.x: larger depth is nearer, so the words are hidden where they are farther than the solid world.
    bool hidden = gl_FragCoord.z < solidDepth;

    vec4 ink = texture(Sampler0, glyphUv) * shade * ColorModulator;
    if (hidden || ink.a <= 0.0) {
        discard;
    }
    pixel = ink;
}
