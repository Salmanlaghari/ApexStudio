#version 300 es
// morph - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): paniq
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uStrength; // = 0.1
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: paniq
// License: MIT


vec4 transition(vec2 uv) {
  vec4 ca = getFromColor(uv);
  vec4 cb = getToColor(uv);
  
  vec2 oa = (((ca.rg+ca.b)*0.5)*2.0-1.0);
  vec2 ob = (((cb.rg+cb.b)*0.5)*2.0-1.0);
  vec2 oc = mix(oa,ob,0.5)*uStrength;
  
  float w0 = uProgress;
  float w1 = 1.0-w0;
  return mix(getFromColor(uv+oc*w0), getToColor(uv-oc*w1), uProgress);
}
void main() { fragColor = transition(vTexCoord); }
