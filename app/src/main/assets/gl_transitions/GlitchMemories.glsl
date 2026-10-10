#version 300 es
// GlitchMemories - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Gunnar Roth
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
// Author: Gunnar Roth
// based on work uFrom natewave
// License: MIT
vec4 transition(vec2 uv) {
  vec2 block = floor(uv.xy / vec2(16));
  vec2 uv_noise = block / vec2(64);
  uv_noise += floor(vec2(uProgress) * vec2(1200.0, 3500.0)) / vec2(64);
  vec2 dist = uProgress > 0.0 ? (fract(uv_noise) - 0.5) * 0.3 *(1.0 -uProgress) : vec2(0.0);
  vec2 red = uv + dist * 0.2;
  vec2 green = uv + dist * .3;
  vec2 blue = uv + dist * .5;

  return vec4(mix(getFromColor(red), getToColor(red), uProgress).r,mix(getFromColor(green), getToColor(green), uProgress).g,mix(getFromColor(blue), getToColor(blue), uProgress).b,1.0);
}
void main() { fragColor = transition(vTexCoord); }
