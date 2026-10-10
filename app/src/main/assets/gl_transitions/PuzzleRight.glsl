#version 300 es
// PuzzleRight - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): JustKirillS
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform ivec2 uSize; // = ivec2(4, 4)
uniform float uPause; // = 0.1
uniform float uDividerWidth; // = 0.005
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: JustKirillS
// License: MIT
// Ported uFrom https://gist.github.com/JustKirillS/714f095318834f4d2375de872c53af1e





float rand(vec2 co) {
  return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

float getDelta(vec2 p) {
  vec2 rectangleSize = 1.0 / vec2(uSize);
  vec2 rectanglePos = floor(vec2(uSize) * p);
  float top = rectangleSize.y * (rectanglePos.y + 1.0);
  float bottom = rectangleSize.y * rectanglePos.y;
  float left = rectangleSize.x * rectanglePos.x;
  float right = rectangleSize.x * (rectanglePos.x + 1.0);
  float minX = min(abs(p.x - left), abs(p.x - right));
  float minY = min(abs(p.y - top), abs(p.y - bottom));
  return min(minX, minY);
}

vec4 transition(vec2 uv) {
  if (uProgress < uPause) {
    float currentProg = uProgress / uPause;
    float a = 1.0;
    if (getDelta(uv) < uDividerWidth) { a = 1.0 - currentProg; }
    return mix(vec4(0.0, 0.0, 0.0, 1.0), getFromColor(uv), a);
  } else if (uProgress < 1.0 - uPause) {
    if (getDelta(uv) < uDividerWidth) {
      return vec4(0.0, 0.0, 0.0, 1.0);
    }
    float currentProg = (uProgress - uPause) / (1.0 - uPause * 2.0);
    vec2 rectanglePos = floor(vec2(uSize) * uv);
    float r = rand(rectanglePos) - 0.1;
    float cp = smoothstep(0.0, 1.0 - r, currentProg);
    float rectangleSize = 1.0 / float(uSize.x);
    float delta = rectanglePos.x * rectangleSize;
    float offset = rectangleSize / 2.0 + delta;
    vec2 p = uv;
    p.x = (p.x - offset) / abs(cp - 0.5) * 0.5 + offset;
    vec4 a = getFromColor(p);
    vec4 b = getToColor(p);
    float s = step(abs(float(uSize.x) * (uv.x - delta) - 0.5), abs(cp - 0.5));
    return vec4(mix(b, a, step(cp, 0.5)).rgb * s, 1.0);
  } else {
    float currentProg = (uProgress - 1.0 + uPause) / uPause;
    float a = 1.0;
    if (getDelta(uv) < uDividerWidth) { a = currentProg; }
    return mix(vec4(0.0, 0.0, 0.0, 1.0), getToColor(uv), a);
  }
}
void main() { fragColor = transition(vTexCoord); }
