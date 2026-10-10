#version 300 es
// CrazyParametricFun - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): mandubian
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uA; // = 4.0
uniform float uB; // = 1.0
uniform float uAmplitude; // = 120.0
uniform float uSmoothness; // = 0.1
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: mandubian
// License: MIT






vec4 transition(vec2 uv) {
  vec2 p = uv.xy / vec2(1.0).xy;
  vec2 dir = p - vec2(.5);
  float dist = length(dir);
  float x = (uA - uB) * cos(uProgress) + uB * cos(uProgress * ((uA / uB) - 1.) );
  float y = (uA - uB) * sin(uProgress) - uB * sin(uProgress * ((uA / uB) - 1.));
  vec2 offset = dir * vec2(sin(uProgress  * dist * uAmplitude * x), sin(uProgress * dist * uAmplitude * y)) / uSmoothness;
  return mix(getFromColor(p + offset), getToColor(p), smoothstep(0.2, 1.0, uProgress));
}
void main() { fragColor = transition(vTexCoord); }
