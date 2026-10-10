#version 300 es
// swap - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
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
uniform float uPerspective; // = 0.2
uniform float uDepth; // = 3.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT
// General parameters



 
const vec4 black = vec4(0.0, 0.0, 0.0, 1.0);
const vec2 boundMin = vec2(0.0, 0.0);
const vec2 boundMax = vec2(1.0, 1.0);
 
bool inBounds (vec2 p) {
  return all(lessThan(boundMin, p)) && all(lessThan(p, boundMax));
}
 
vec2 project (vec2 p) {
  return p * vec2(1.0, -1.2) + vec2(0.0, -0.02);
}
 
vec4 bgColor (vec2 p, vec2 pfr, vec2 pto) {
  vec4 c = black;
  pfr = project(pfr);
  if (inBounds(pfr)) {
    c += mix(black, getFromColor(pfr), uReflection * mix(1.0, 0.0, pfr.y));
  }
  pto = project(pto);
  if (inBounds(pto)) {
    c += mix(black, getToColor(pto), uReflection * mix(1.0, 0.0, pto.y));
  }
  return c;
}
 
vec4 transition(vec2 uv) {
  vec2 pfr, pto = vec2(-1.);
 
  float size = mix(1.0, uDepth, uProgress);
  float persp = uPerspective * uProgress;
  pfr = (uv + vec2(-0.0, -0.5)) * vec2(size/(1.0-uPerspective*uProgress), size/(1.0-size*persp*uv.x)) + vec2(0.0, 0.5);
 
  size = mix(1.0, uDepth, 1.-uProgress);
  persp = uPerspective * (1.-uProgress);
  pto = (uv + vec2(-1.0, -0.5)) * vec2(size/(1.0-uPerspective*(1.0-uProgress)), size/(1.0-size*persp*(0.5-uv.x))) + vec2(1.0, 0.5);

  if (uProgress < 0.5) {
    if (inBounds(pfr)) {
      return getFromColor(pfr);
    }
    if (inBounds(pto)) {
      return getToColor(pto);
    }  
  }
  if (inBounds(pto)) {
    return getToColor(pto);
  }
  if (inBounds(pfr)) {
    return getFromColor(pfr);
  }
  return bgColor(uv, pfr, pto);
}
void main() { fragColor = transition(vTexCoord); }
