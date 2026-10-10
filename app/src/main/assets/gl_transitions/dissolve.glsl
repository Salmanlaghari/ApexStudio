#version 300 es
// dissolve - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): hjm1fb
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uLineWidth; // = 0.1
uniform vec3 uSpreadClr; // = vec3(1.0, 0.0, 0.0)
uniform vec3 uHotClr; // = vec3(0.9, 0.9, 0.2)
uniform float uPow; // = 5.0
uniform float uIntensity; // = 1.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: hjm1fb
// License: MIT

#ifdef GL_ES
precision mediump float;
#endif







vec2 hash(vec2 p)  // replace this by something better
{
  p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
  return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float noise(in vec2 p) {
  const float K1 = 0.366025404;  // (sqrt(3)-1)/2;
  const float K2 = 0.211324865;  // (3-sqrt(3))/6;

  vec2 i = floor(p + (p.x + p.y) * K1);
  vec2 a = p - i + (i.x + i.y) * K2;
  float m = step(a.y, a.x);
  vec2 o = vec2(m, 1.0 - m);
  vec2 b = a - o + K2;
  vec2 c = a - 1.0 + 2.0 * K2;
  vec3 h = max(0.5 - vec3(dot(a, a), dot(b, b), dot(c, c)), 0.0);
  vec3 n = h * h * h * h * vec3(dot(a, hash(i + 0.0)), dot(b, hash(i + o)), dot(c, hash(i + 1.0)));
  return dot(n, vec3(70.0));
}

vec4 transition(vec2 uv) {
  vec4 uFrom = getFromColor(uv);
  vec4 uTo = getToColor(uv);
  vec4 outColor;
  float burn;
  burn = 0.5 + 0.5 * (0.299 * uFrom.r + 0.587 * uFrom.g + 0.114 * uFrom.b);

  float show = burn - uProgress;
  if (show < 0.001) {
    outColor = uTo;
  } else {
    float factor = 1.0 - smoothstep(0.0, uLineWidth, show);
    vec3 burnColor = mix(uSpreadClr, uHotClr, factor);
    burnColor = pow(burnColor, vec3(uPow)) * uIntensity;
    vec3 finalRGB = mix(uFrom.rgb, burnColor, factor * step(0.0001, uProgress));
    outColor = vec4(finalRGB * uFrom.a, uFrom.a);
  }
  return outColor;
}
void main() { fragColor = transition(vTexCoord); }
