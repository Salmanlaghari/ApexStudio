#version 300 es
// flyeye - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uSize; // = 0.04
uniform float uZoom; // = 50.0
uniform float uColorSeparation; // = 0.3
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT




vec4 transition(vec2 uv) {
  float inv = 1. - uProgress;
  vec2 disp = uSize*vec2(cos(uZoom*uv.x), sin(uZoom*uv.y));
  vec4 texTo = getToColor(uv + inv*disp);
  vec4 texFrom = vec4(
    getFromColor(uv + uProgress*disp*(1.0 - uColorSeparation)).r,
    getFromColor(uv + uProgress*disp).g,
    getFromColor(uv + uProgress*disp*(1.0 + uColorSeparation)).b,
    1.0);
  return texTo*uProgress + texFrom*inv;
}
void main() { fragColor = transition(vTexCoord); }
