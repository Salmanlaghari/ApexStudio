#version 300 es
// pixelize - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform ivec2 uSquaresMin; // = ivec2(20)
uniform int uSteps; // = 50
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT
// forked uFrom https://gist.github.com/benraziel/c528607361d90a072e98

// minimum number of squares (when the effect is at its higher level)

// zero disable the stepping






vec4 transition(vec2 uv) {
  float d = min(uProgress, 1.0 - uProgress);
  float dist = uSteps>0 ? ceil(d * float(uSteps)) / float(uSteps) : d;
  vec2 squareSize = 2.0 * dist / vec2(uSquaresMin);

  vec2 p = dist>0.0 ? (floor(uv / squareSize) + 0.5) * squareSize : uv;
  return mix(getFromColor(p), getToColor(p), uProgress);
}
void main() { fragColor = transition(vTexCoord); }
