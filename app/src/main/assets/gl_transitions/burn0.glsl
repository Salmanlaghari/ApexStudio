#version 300 es
// burn0 - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): liubailin2020@gmail.com
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec3 uBurnColor; // = vec3(1.0, 0.5, 0.0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: liubailin2020@gmail.com
// License: MIT



float random (in vec2 st) {
    return fract(sin(dot(st.xy,
                         vec2(12.9898,78.233)))*
        43758.5453123);
}

// Based on Morgan McGuire @morgan3d
// https://www.shadertoy.com/view/4dS3Wd
float noise (in vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x) +
            (c - a)* u.y * (1.0 - u.x) +
            (d - b) * u.x * u.y;
}

#define OCTAVES 4
float fbm (in vec2 st) {
    float value = 0.0;
    float amplitude = .5;
    for (int i = 0; i < OCTAVES; i++) {
        value += amplitude * noise(st);
        st *= 2.;
        amplitude *= .5;
    }
    return value;
}

vec4 transition(vec2 uv) {
    if (uProgress <= 0.0) return getFromColor(uv);
    if (uProgress >= 1.0) return getToColor(uv);
    vec4 uFrom = getFromColor(uv);
    vec4 uTo = getToColor(uv);
    float n = fbm(uv * 4.);
    float l = smoothstep(uProgress, uProgress + 0.05, n);
    float edge = (1.0 - l) * l * 5.0;
    return mix(uTo, uFrom, l) + vec4(uBurnColor, 0.0) * edge;
}
void main() { fragColor = transition(vTexCoord); }
