#version 300 es
// burn - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
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
// Author: gre
// License: MIT
uniform vec3 color /* = vec3(0.9, 0.4, 0.2) */;
vec4 transition(vec2 uv) {
  return mix(
    getFromColor(uv) + vec4(uProgress*color, 1.0),
    getToColor(uv) + vec4((1.0-uProgress)*color, 1.0),
    uProgress
  );
}
void main() { fragColor = transition(vTexCoord); }
