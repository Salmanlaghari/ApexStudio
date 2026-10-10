#version 300 es
// Rectangle - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): martiniti
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec4 uBgcolor; // = vec4(0.0, 0.0, 0.0, 1.0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: martiniti
// License: MIT





vec4 transition(vec2 uv) {
  float s = pow(2.0 * abs(uProgress - 0.5), 3.0);

  
   vec2 sq = uv.xy / vec2(1.0).xy;
   
    // bottom-left
    vec2 bl = step(vec2(abs(1. - 2.*uProgress)), sq + .25);
    float dist = bl.x * bl.y;

    // top-right
    vec2 tr = step(vec2(abs(1. - 2.*uProgress)), 1.25-sq);
    dist *= 1. * tr.x * tr.y;
  
  return mix(
    uProgress < 0.5 ? getFromColor(uv) : getToColor(uv),
    uBgcolor,
    step(s, dist)
  );
  
}
void main() { fragColor = transition(vTexCoord); }
