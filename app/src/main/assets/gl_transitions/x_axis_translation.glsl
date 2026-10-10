#version 300 es
// x_axis_translation - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): lizhongjian
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
// Author: lizhongjian
// License: MIT

vec4 transition(vec2 uv) {
  vec2 newUV = uv;
  newUV.x -= uProgress;
  if(uv.x >= uProgress)
  {
    return getFromColor(newUV);
  }

  
  return mix(
    getFromColor(uv),
    getToColor(uv),
    uProgress
  );
}
void main() { fragColor = transition(vTexCoord); }
