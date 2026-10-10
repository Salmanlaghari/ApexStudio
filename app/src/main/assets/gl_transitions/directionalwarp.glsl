#version 300 es
// directionalwarp - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): pschroen
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uSmoothness; // = 0.1
uniform vec2 uDirection; // = vec2(-1.0, 1.0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: pschroen
// License: MIT




const vec2 center = vec2(0.5, 0.5);

vec4 transition(vec2 uv) {
  vec2 v = normalize(uDirection);
  v /= abs(v.x) + abs(v.y);
  float d = v.x * center.x + v.y * center.y;
  float m = 1.0 - smoothstep(-uSmoothness, 0.0, v.x * uv.x + v.y * uv.y - (d - 0.5 + uProgress * (1.0 + uSmoothness)));
  return mix(getFromColor((uv - 0.5) * (1.0 - m) + 0.5), getToColor((uv - 0.5) * m + 0.5), m);
}
void main() { fragColor = transition(vTexCoord); }
