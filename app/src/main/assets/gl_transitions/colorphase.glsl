#version 300 es
// colorphase - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec4 uFromStep; // = vec4(0.0, 0.2, 0.4, 0.0)
uniform vec4 uToStep; // = vec4(0.6, 0.8, 1.0, 1.0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT

// Usage: uFromStep and uToStep must be in [0.0, 1.0] range 
// and all(uFromStep) must be < all(uToStep)




vec4 transition(vec2 uv) {
  vec4 a = getFromColor(uv);
  vec4 b = getToColor(uv);
  return mix(a, b, smoothstep(uFromStep, uToStep, vec4(uProgress)));
}
void main() { fragColor = transition(vTexCoord); }
