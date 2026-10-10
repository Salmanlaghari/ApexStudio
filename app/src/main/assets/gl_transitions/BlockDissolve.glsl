#version 300 es
// BlockDissolve - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): nwoeanhinnogaehr
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uBlocksize; // = 0.02
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: nwoeanhinnogaehr
// License: MIT
// Ported uFrom https://gist.github.com/nwoeanhinnogaehr/b93818de23d4511fde10



float rand(vec2 co) {
  return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 transition(vec2 uv) {
  return mix(getFromColor(uv), getToColor(uv), step(rand(floor(uv / uBlocksize)), uProgress));
}
void main() { fragColor = transition(vTexCoord); }
