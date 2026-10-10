#version 300 es
// TVStatic - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Brandon Anzaldi
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uOffset; // = 0.05
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Brandon Anzaldi
// License: MIT


// Pseudo-random noise function
// http://byteblacksmith.com/improvements-uTo-the-canonical-one-liner-glsl-rand-for-opengl-es-2-0/
highp float noise(vec2 co)
{
    highp float a = 12.9898;
    highp float b = 78.233;
    highp float c = 43758.5453;
    highp float dt= dot(co.xy * uProgress, vec2(a, b));
    highp float sn= mod(dt,3.14);
    return fract(sin(sn) * c);
}

vec4 transition(vec2 uv) {
  if (uProgress < uOffset) {
    return getFromColor(uv);
  } else if (uProgress > (1.0 - uOffset)) {
    return getToColor(uv);
  } else {
    return vec4(vec3(noise(uv)), 1.0);
  }
}
void main() { fragColor = transition(vTexCoord); }
