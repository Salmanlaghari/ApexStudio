#version 300 es
// Radial - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Xaychru
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uSmoothness; // = 1.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// License: MIT
// Author: Xaychru
// ported by gre uFrom https://gist.github.com/Xaychru/ce1d48f0ce00bb379750



const float PI = 3.141592653589;

vec4 transition(vec2 uv) {
  vec2 rp = uv*2.-1.;
  return mix(
    getToColor(uv),
    getFromColor(uv),
    smoothstep(0., uSmoothness, atan(rp.y,rp.x) - (uProgress-.5) * PI * 2.5)
  );
}
void main() { fragColor = transition(vTexCoord); }
