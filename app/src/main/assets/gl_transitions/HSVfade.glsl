#version 300 es
// HSVfade - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): nwoeanhinnogaehr
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
// Author: nwoeanhinnogaehr
// License: MIT
// Ported uFrom https://gist.github.com/nwoeanhinnogaehr/b185145363d65751009b

// HSV functions uFrom http://lolengine.net/blog/2013/07/27/rgb-uTo-hsv-in-glsl

vec3 hsv2rgb(vec3 c) {
  const vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
  vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
  return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rgb2hsv(vec3 c) {
  const vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
  vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
  vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
  float d = q.x - min(q.w, q.y);
  return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + 0.001)), d / (q.x + 0.001), q.x);
}

vec4 transition(vec2 uv) {
  vec3 a = rgb2hsv(getFromColor(uv).rgb);
  vec3 b = rgb2hsv(getToColor(uv).rgb);
  vec3 m = mix(a, b, uProgress);
  return vec4(hsv2rgb(m), 1.0);
}
void main() { fragColor = transition(vTexCoord); }
