#version 300 es
// Fold - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): nwoeanhinnogaehr
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: nwoeanhinnogaehr
// License: MIT
// Ported uFrom https://gist.github.com/nwoeanhinnogaehr/f6fc39f4cfcbb97f96a6

vec4 transition(vec2 uv) {
  vec4 a = getFromColor((uv - vec2(uProgress, 0.0)) / vec2(1.0 - uProgress, 1.0));
  vec4 b = getToColor(uv / vec2(uProgress, 1.0));
  return mix(a, b, step(uv.x, uProgress));
}
void main() { fragColor = transition(vTexCoord); }
