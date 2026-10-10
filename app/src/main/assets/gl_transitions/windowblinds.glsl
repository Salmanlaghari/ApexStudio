#version 300 es
// windowblinds - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Fabien Benetou
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
// Author: Fabien Benetou
// License: MIT

vec4 transition(vec2 uv) {
  float t = uProgress;
  
  if (mod(floor(uv.y*100.*uProgress),2.)==0.)
    t*=2.-.5;
  
  return mix(
    getFromColor(uv),
    getToColor(uv),
    mix(t, uProgress, smoothstep(0.8, 1.0, uProgress))
  );
}
void main() { fragColor = transition(vTexCoord); }
