#version 300 es
// DirectionalScaled - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Thibaut Foussard
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec2 uDirection; // = vec2(0.0, 1.0)
uniform float uScale; // = .7
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Thibaut Foussard
// based on Directional transition by Gaëtan Renaudeau
// https://gl-transitions.com/editor/Directional
// License: MIT

#define PI acos(-1.0)




float parabola(float x) {
  float y = pow(sin(x * PI), 1.);
  return y;
}

vec4 transition(vec2 uv) {
  float easedProgress = pow(sin(uProgress  * PI / 2.), 3.);
  vec2 p = uv + easedProgress * sign(uDirection);
  vec2 f = fract(p);
  
  float s = 1. - (1. - (1. / uScale)) * parabola(uProgress);
  f = (f - 0.5) * s  + 0.5;
  
  float mixer = step(0.0, p.y) * step(p.y, 1.0) * step(0.0, p.x) * step(p.x, 1.0);
  vec4 col = mix(getToColor(f), getFromColor(f), mixer);
  
  float border = step(0., f.x) * step(0., (1. - f.x)) * step(0., f.y) * step(0., 1. - f.y);
  col *= border;
  
  return col;
}
void main() { fragColor = transition(vTexCoord); }
