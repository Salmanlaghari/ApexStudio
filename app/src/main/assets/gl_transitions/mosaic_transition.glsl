#version 300 es
// mosaic_transition - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): YueDev
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uMosaicNum; // = 10.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: YueDev
// License: MIT



vec2 getMosaicUV(vec2 uv) {
  float mosaicWidth = 2.0 / uMosaicNum * min(uProgress, 1.0 - uProgress);
  float mX = floor(uv.x / mosaicWidth) + 0.5;
  float mY = floor(uv.y / mosaicWidth) + 0.5;
  return vec2(mX * mosaicWidth, mY * mosaicWidth);
}

vec4 transition(vec2 uv) {
  vec2 mosaicUV = min(uProgress, 1.0 - uProgress) == 0.0 ? uv : getMosaicUV(uv);
  return mix(getFromColor(mosaicUV), getToColor(mosaicUV), uProgress * uProgress);
}
void main() { fragColor = transition(vTexCoord); }
