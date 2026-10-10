#version 300 es
// windowslice - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uCount; // = 10.0
uniform float uSmoothness; // = 0.5
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT




vec4 transition(vec2 uv) {
  float pr = smoothstep(-uSmoothness, 0.0, uv.x - uProgress * (1.0 + uSmoothness));
  float s = step(pr, fract(uCount * uv.x));
  return mix(getFromColor(uv), getToColor(uv), s);
}
void main() { fragColor = transition(vTexCoord); }
