#version 300 es
// PolkaDotsCurtain - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): bobylito
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uDots; // = 20.0
uniform vec2 uCenter; // = vec2(0, 0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: bobylito
// License: MIT
const float SQRT_2 = 1.414213562373;



vec4 transition(vec2 uv) {
  bool nextImage = distance(fract(uv * uDots), vec2(0.5, 0.5)) < ( uProgress / distance(uv, uCenter));
  return nextImage ? getToColor(uv) : getFromColor(uv);
}
void main() { fragColor = transition(vTexCoord); }
