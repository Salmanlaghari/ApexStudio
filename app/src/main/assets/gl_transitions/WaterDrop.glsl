#version 300 es
// WaterDrop - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Paweł Płóciennik
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uAmplitude; // = 30.0
uniform float uSpeed; // = 30.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Paweł Płóciennik
// License: MIT



vec4 transition(vec2 uv) {
  vec2 dir = uv - vec2(.5);
  float dist = length(dir);

  if (dist > uProgress) {
    return mix(getFromColor( uv), getToColor( uv), uProgress);
  } else {
    vec2 offset = dir * sin(dist * uAmplitude - uProgress * uSpeed);
    return mix(getFromColor( uv + offset), getToColor( uv), uProgress);
  }
}
void main() { fragColor = transition(vTexCoord); }
