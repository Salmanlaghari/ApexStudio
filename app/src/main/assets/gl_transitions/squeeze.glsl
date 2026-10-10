#version 300 es
// squeeze - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uColorSeparation; // = 0.04
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT
 

 
vec4 transition(vec2 uv) {
  float y = 0.5 + (uv.y-0.5) / (1.0-uProgress);
  if (y < 0.0 || y > 1.0) {
     return getToColor(uv);
  }
  else {
    vec2 fp = vec2(uv.x, y);
    vec2 off = uProgress * vec2(0.0, uColorSeparation);
    vec4 c = getFromColor(fp);
    vec4 cn = getFromColor(fp - off);
    vec4 cp = getFromColor(fp + off);
    return vec4(cn.r, c.g, cp.b, c.a);
  }
}
void main() { fragColor = transition(vTexCoord); }
