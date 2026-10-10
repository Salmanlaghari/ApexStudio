#version 300 es
// BookFlip - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): hong
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
// Author: hong
// License: MIT

vec2 skewRight(vec2 p) {
  float skewX = (p.x - uProgress)/(0.5 - uProgress) * 0.5;
  float skewY =  (p.y - 0.5)/(0.5 + uProgress * (p.x - 0.5) / 0.5)* 0.5  + 0.5;
  return vec2(skewX, skewY);
}

vec2 skewLeft(vec2 p) {
  float skewX = (p.x - 0.5)/(uProgress - 0.5) * 0.5 + 0.5;
  float skewY = (p.y - 0.5) / (0.5 + (1.0 - uProgress ) * (0.5 - p.x) / 0.5) * 0.5  + 0.5;
  return vec2(skewX, skewY);
}

vec4 addShade() {
  float shadeVal  =  max(0.7, abs(uProgress - 0.5) * 2.0);
  return vec4(vec3(shadeVal ), 1.0);
}

vec4 transition(vec2 uv) {
  float pr = step(1.0 - uProgress, uv.x);

  if (uv.x < 0.5) {
    return mix(getFromColor(uv), getToColor(skewLeft(uv)) * addShade(), pr);
  } else {
    return mix(getFromColor(skewRight(uv)) * addShade(), getToColor(uv),   pr);
  }
}
void main() { fragColor = transition(vTexCoord); }
