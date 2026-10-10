#version 300 es
// circle - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Fernando Kuteken
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec2 uCenter; // = vec2(0.5, 0.5)
uniform vec3 uBackColor; // = vec3(0.1, 0.1, 0.1)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Fernando Kuteken
// License: MIT




vec4 transition(vec2 uv) {
  
  float distance = length(uv - uCenter);
  float radius = sqrt(8.0) * abs(uProgress - 0.5);
  
  if (distance > radius) {
    return vec4(uBackColor, 1.0);
  }
  else {
    if (uProgress < 0.5) return getFromColor(uv);
    else return getToColor(uv);
  }
}
void main() { fragColor = transition(vTexCoord); }
