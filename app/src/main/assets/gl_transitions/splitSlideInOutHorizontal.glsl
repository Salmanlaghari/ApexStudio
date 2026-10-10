#version 300 es
// splitSlideInOutHorizontal - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): OllyOllyOlly
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform bool uReverse; // = false
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: OllyOllyOlly
// License: MIT



const vec2 boundMin = vec2(0.0, 0.0);
const vec2 boundMax = vec2(1.0, 1.0);

bool inBounds (vec2 p) {
  return all(lessThan(boundMin, p)) && all(lessThan(p, boundMax));
}

vec4 transition(vec2 uv) {
  float modifier = (uv.y > 0.5 ? 1.0 : -1.0) * (uReverse ? -1.0 : 1.0) ;
  vec2 fromP = vec2(uv.x + (uProgress * modifier), uv.y);
  vec2 toP = vec2((uv.x + (uProgress * modifier)) - modifier, uv.y);

  return inBounds(fromP) ? getFromColor(fromP) : getToColor(toP);
}
void main() { fragColor = transition(vTexCoord); }
