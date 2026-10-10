#version 300 es
// Overexposure - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Ben Zhang
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uStrength; // = 0.6
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Ben Zhang
// License: MIT


const float PI = 3.141592653589793;

vec4 transition(vec2 uv) {
  vec4 uFrom = getFromColor(uv);
  vec4 uTo = getToColor(uv);

  // Multipliers
  float from_m = 1.0 - uProgress + sin(PI * uProgress) * uStrength;
  float to_m = uProgress + sin(PI * uProgress) * uStrength;
  
  return vec4(
    uFrom.r * uFrom.a * from_m + uTo.r * uTo.a * to_m,
    uFrom.g * uFrom.a * from_m + uTo.g * uTo.a * to_m,
    uFrom.b * uFrom.a * from_m + uTo.b * uTo.a * to_m,
    mix(uFrom.a, uTo.a, uProgress)
  );
}
void main() { fragColor = transition(vTexCoord); }
