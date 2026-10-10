#version 300 es
// directionalwipe - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec2 uDirection; // = vec2(1.0, -1.0)
uniform float uSmoothness; // = 0.5
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT



 
const vec2 center = vec2(0.5, 0.5);
 
vec4 transition(vec2 uv) {
  vec2 v = normalize(uDirection);
  v /= abs(v.x)+abs(v.y);
  float d = v.x * center.x + v.y * center.y;
  float m =
    (1.0-step(uProgress, 0.0)) * // there is something wrong with our formula that makes m not equals 0.0 with uProgress is 0.0
    (1.0 - smoothstep(-uSmoothness, 0.0, v.x * uv.x + v.y * uv.y - (d-0.5+uProgress*(1.+uSmoothness))));
  return mix(getFromColor(uv), getToColor(uv), m);
}
void main() { fragColor = transition(vTexCoord); }
