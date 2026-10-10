#version 300 es
// Dreamy - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): mikolalysenko
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
// Author: mikolalysenko
// License: MIT

vec2 offset(float uProgress, float x, float theta) {
  float phase = uProgress*uProgress + uProgress + theta;
  float shifty = 0.03*uProgress*cos(10.0*(uProgress+x));
  return vec2(0, shifty);
}
vec4 transition(vec2 uv) {
  return mix(getFromColor(uv + offset(uProgress, uv.x, 0.0)), getToColor(uv + offset(1.0-uProgress, uv.x, 3.14)), uProgress);
}
void main() { fragColor = transition(vTexCoord); }
