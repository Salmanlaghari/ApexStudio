#version 300 es
// ColourDistance - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): P-Seebauer
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uPower; // = 5.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// License: MIT
// Author: P-Seebauer
// ported by gre uFrom https://gist.github.com/P-Seebauer/2a5fa2f77c883dd661f9



vec4 transition(vec2 uv) {
  vec4 fTex = getFromColor(uv);
  vec4 tTex = getToColor(uv);
  float m = step(distance(fTex, tTex), uProgress);
  return mix(
    mix(fTex, tTex, m),
    tTex,
    pow(uProgress, uPower)
  );
}
void main() { fragColor = transition(vTexCoord); }
