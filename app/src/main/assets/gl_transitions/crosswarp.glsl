#version 300 es
// crosswarp - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Eke Péter <peterekepeter@gmail.com>
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
// Author: Eke Péter <peterekepeter@gmail.com>
// License: MIT
vec4 transition(vec2 uv) {
  float x = uProgress;
  x=smoothstep(.0,1.0,(x*2.0+uv.x-1.0));
  return mix(getFromColor((uv-.5)*(1.-x)+.5), getToColor((uv-.5)*x+.5), x);
}
void main() { fragColor = transition(vTexCoord); }
