#version 300 es
// ButterflyWaveScrawler - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): mandubian
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uAmplitude; // = 1.0
uniform float uWaves; // = 30.0
uniform float uColorSeparation; // = 0.3
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: mandubian
// License: MIT



const float PI = 3.14159265358979323846264;
float compute(vec2 p, float uProgress, vec2 center) {
vec2 o = p*sin(uProgress * uAmplitude)-center;
// horizontal vector
vec2 h = vec2(1., 0.);
// butterfly polar function (don't ask me why this one :))
float theta = acos(dot(o, h)) * uWaves;
float s = sin((2.*theta - PI) / 24.);
float s2 = s * s;
return (exp(cos(theta)) - 2.*cos(4.*theta) + s2 * s2 * s) / 10.;
}
vec4 transition(vec2 uv) {
  if (uProgress <= 0.0) return getFromColor(uv);
  if (uProgress >= 1.0) return getToColor(uv);
  vec2 p = uv;
  float inv = 1. - uProgress;
  float disp = compute(p, uProgress, vec2(0.5, 0.5));
  vec4 texTo = getToColor(p + inv*disp);
  vec4 texFrom = vec4(
    getFromColor(p + uProgress*disp*(1.0 - uColorSeparation)).r,
    getFromColor(p + uProgress*disp).g,
    getFromColor(p + uProgress*disp*(1.0 + uColorSeparation)).b,
    1.0);
  return texTo*uProgress + texFrom*inv;
}
void main() { fragColor = transition(vTexCoord); }
