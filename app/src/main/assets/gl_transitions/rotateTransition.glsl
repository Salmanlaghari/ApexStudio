#version 300 es
// rotateTransition - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): haiyoucuv
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
// Author: haiyoucuv
// License: MIT

#define PI 3.1415926

vec2 rotate2D(in vec2 uv, in float angle){
  
  return uv * mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
}
vec4 transition(vec2 uv) {
  
  vec2 p = fract(rotate2D(uv - 0.5, uProgress * PI * 2.0) + 0.5);

  return mix(
    getFromColor(p),
    getToColor(p),
    uProgress
  );
}
void main() { fragColor = transition(vTexCoord); }
