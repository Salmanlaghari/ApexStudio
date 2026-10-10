#version 300 es
// AdvancedMosaic - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Sergey Kosarevsky
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uPixelSize; // = 50.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Sergey Kosarevsky
// License: MIT
// Ported uFrom https://gist.github.com/corporateshark/21d2fdd24c706952dc8c



vec4 transition(vec2 uv) {
  float T = uProgress;
  float half_ = 0.5;
  float size = (T < half_) ? mix(1.0, uPixelSize, T / half_) : mix(uPixelSize, 1.0, (T - half_) / half_);
  float D = size * 0.005;
  // Remap UV uTo center the mosaic pattern
  vec2 UV = (uv - 0.5) / D;
  vec2 coord = clamp(D * (ceil(UV - 0.5)) + 0.5, 0.0, 1.0);
  vec4 C0 = getFromColor(coord);
  vec4 C1 = getToColor(coord);
  return mix(C0, C1, T);
}
void main() { fragColor = transition(vTexCoord); }
