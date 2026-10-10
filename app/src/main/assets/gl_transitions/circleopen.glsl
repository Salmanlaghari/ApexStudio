#version 300 es
// circleopen - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uSmoothness; // = 0.3
uniform bool uOpening; // = true
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT



const vec2 center = vec2(0.5, 0.5);
const float SQRT_2 = 1.414213562373;

vec4 transition(vec2 uv) {
  float x = uOpening ? uProgress : 1.-uProgress;
  float m = smoothstep(-uSmoothness, 0.0, SQRT_2*distance(center, uv) - x*(1.+uSmoothness));
  return mix(getFromColor(uv), getToColor(uv), uOpening ? 1.-m : m);
}
void main() { fragColor = transition(vTexCoord); }
