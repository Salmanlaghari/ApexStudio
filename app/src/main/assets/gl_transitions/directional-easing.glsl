#version 300 es
// directional-easing - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Max Plotnikov
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec2 uDirection; // = vec2(0.0, 1.0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Max Plotnikov
// License: MIT



vec4 transition(vec2 uv) {
  float easing = sqrt((2.0 - uProgress) * uProgress);
  vec2 p = uv + easing * sign(uDirection);
  vec2 f = fract(p);
  return mix(
    getToColor(f),
    getFromColor(f),
    step(0.0, p.y) * step(p.y, 1.0) * step(0.0, p.x) * step(p.x, 1.0)
  );
}
void main() { fragColor = transition(vTexCoord); }
