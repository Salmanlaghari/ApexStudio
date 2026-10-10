#version 300 es
// chessboard - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): lql
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uGrid_num; // = 10.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: lql
// License: MIT



vec4 transition(vec2 uv) {
    vec2 st = uv * uGrid_num;
    vec2 idx = floor(st);
    vec2 grid = fract(st);

    vec4 a = getFromColor(uv);
    vec4 b = getToColor(uv);

    float checker = mod(idx.x + idx.y, 2.0);
    float mixFactor;

    if (uProgress <= 0.5) {
        mixFactor = (checker > 0.5) ? step(grid.x, uProgress * 2.0) : 0.0;
    } else {
        mixFactor = (checker < 0.5) ? step(grid.x, (uProgress - 0.5) * 2.0) : 1.0;
    }

    return mix(a, b, mixFactor);
}
void main() { fragColor = transition(vTexCoord); }
