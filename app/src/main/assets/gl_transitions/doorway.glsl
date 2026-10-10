#version 300 es
// doorway - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uReflection; // = 0.4
uniform float uPerspective; // = 0.4
uniform float uDepth; // = 3.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT




const vec4 black = vec4(0.0, 0.0, 0.0, 1.0);
const vec2 boundMin = vec2(0.0, 0.0);
const vec2 boundMax = vec2(1.0, 1.0);

bool inBounds (vec2 p) {
  return all(lessThan(boundMin, p)) && all(lessThan(p, boundMax));
}

vec2 project (vec2 p) {
  return p * vec2(1.0, -1.2) + vec2(0.0, -0.02);
}

vec4 bgColor (vec2 p, vec2 pto) {
  vec4 c = black;
  pto = project(pto);
  if (inBounds(pto)) {
    c += mix(black, getToColor(pto), uReflection * mix(1.0, 0.0, pto.y));
  }
  return c;
}


vec4 transition(vec2 uv) {
  vec2 pfr = vec2(-1.), pto = vec2(-1.);
  float middleSlit = 2.0 * abs(uv.x-0.5) - uProgress;
  if (middleSlit > 0.0) {
    pfr = uv + (uv.x > 0.5 ? -1.0 : 1.0) * vec2(0.5*uProgress, 0.0);
    float d = 1.0/(1.0+uPerspective*uProgress*(1.0-middleSlit));
    pfr.y -= d/2.;
    pfr.y *= d;
    pfr.y += d/2.;
  }
  float size = mix(1.0, uDepth, 1.-uProgress);
  pto = (uv + vec2(-0.5, -0.5)) * vec2(size, size) + vec2(0.5, 0.5);
  if (inBounds(pfr)) {
    return getFromColor(pfr);
  }
  else if (inBounds(pto)) {
    return getToColor(pto);
  }
  else {
    return bgColor(uv, pto);
  }
}
void main() { fragColor = transition(vTexCoord); }
