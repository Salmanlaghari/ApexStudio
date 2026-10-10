#version 300 es
// scale-in - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): haiyoucuv
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
// Author: haiyoucuv
// License: MIT

vec4 scale(in vec2 uv){
    uv = 0.5 + (uv - 0.5) * uProgress;
    return getToColor(uv);
}

vec4 transition(vec2 uv) {
  return mix(
    getFromColor(uv),
    scale(uv),
    uProgress
  );
}
void main() { fragColor = transition(vTexCoord); }
