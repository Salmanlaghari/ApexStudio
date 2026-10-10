#version 300 es
// fadecolor - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec3 uColor; // = vec3(0.0)
uniform float uColorPhase; // = 0.4
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT


vec4 transition(vec2 uv) {
  return mix(
    mix(vec4(uColor, 1.0), getFromColor(uv), smoothstep(1.0-uColorPhase, 0.0, uProgress)),
    mix(vec4(uColor, 1.0), getToColor(uv), smoothstep(    uColorPhase, 1.0, uProgress)),
    uProgress);
}
void main() { fragColor = transition(vTexCoord); }
