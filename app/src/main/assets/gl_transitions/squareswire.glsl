#version 300 es
// squareswire - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform ivec2 uSquares; // = ivec2(10,10)
uniform vec2 uDirection; // = vec2(1.0, -0.5)
uniform float uSmoothness; // = 1.6
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT
 




const vec2 center = vec2(0.5, 0.5);
vec4 transition(vec2 uv) {
  vec2 v = normalize(uDirection);
  v /= abs(v.x)+abs(v.y);
  float d = v.x * center.x + v.y * center.y;
  float offset = uSmoothness;
  float pr = smoothstep(-offset, 0.0, v.x * uv.x + v.y * uv.y - (d-0.5+uProgress*(1.+offset)));
  vec2 squarep = fract(uv*vec2(uSquares));
  vec2 squaremin = vec2(pr/2.0);
  vec2 squaremax = vec2(1.0 - pr/2.0);
  float a = (1.0 - step(uProgress, 0.0)) * step(squaremin.x, squarep.x) * step(squaremin.y, squarep.y) * step(squarep.x, squaremax.x) * step(squarep.y, squaremax.y);
  return mix(getFromColor(uv), getToColor(uv), a);
}
void main() { fragColor = transition(vTexCoord); }
