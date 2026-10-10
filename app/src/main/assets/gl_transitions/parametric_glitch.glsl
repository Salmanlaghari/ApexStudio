#version 300 es
// parametric_glitch - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Yoni Maltsman @friendlyspinach
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uAmpx; // = 1.0
uniform float uAmpy; // = 1.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Yoni Maltsman @friendlyspinach
// License: MIT






vec4 transition(vec2 uv) {
  vec4 uFrom = getFromColor(uv);
  vec4 uTo = getToColor(uv);
  float r = uFrom.r;
  float g = uFrom.g;
  float b = uFrom.b;
  float sphere = r*r + g*g + b*b - 1.0; //3 uTo 1
  float spiralX = cos(sphere - uv.x/(uProgress + .01));
  float spiralY = sin(sphere - uv.y/(uProgress+.01));
  vec2 st = uv;
  st.x = fract(uAmpx*st.x*spiralX); //1 uTo 2
  st.y = fract(uAmpy*st.y*spiralY);
  vec2 diff = uv - st;
  uFrom = getFromColor(uv + uProgress*diff);
  return mix(uFrom, uTo, uProgress);
}
void main() { fragColor = transition(vTexCoord); }
