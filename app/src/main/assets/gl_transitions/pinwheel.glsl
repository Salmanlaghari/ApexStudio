#version 300 es
// pinwheel - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Mr Speaker
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uSpeed; // = 2.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Mr Speaker
// License: MIT



vec4 transition(vec2 uv) {
  
  vec2 p = uv.xy / vec2(1.0).xy;
  
  float circPos = atan(p.y - 0.5, p.x - 0.5) + uProgress * uSpeed;
  float modPos = mod(circPos, 3.1415 / 4.);
  float signed = sign(uProgress - modPos);
  
  return mix(getToColor(p), getFromColor(p), step(signed, 0.5));
  
}
void main() { fragColor = transition(vTexCoord); }
