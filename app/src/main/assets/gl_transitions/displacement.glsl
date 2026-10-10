#version 300 es
// displacement - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Travis Fischer
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform sampler2D uDisplacementMap;
uniform float uStrength; // = 0.5
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Travis Fischer
// License: MIT
//
// Adapted uFrom a Codrops article by Robin Delaporte
// https://tympanus.net/Development/DistortionHoverEffect





vec4 transition(vec2 uv) {
  float displacement = texture(uDisplacementMap, uv).r * uStrength;

  vec2 uvFrom = vec2(uv.x + uProgress * displacement, uv.y);
  vec2 uvTo = vec2(uv.x - (1.0 - uProgress) * displacement, uv.y);

  return mix(
    getFromColor(uvFrom),
    getToColor(uvTo),
    uProgress
  );
}
void main() { fragColor = transition(vTexCoord); }
