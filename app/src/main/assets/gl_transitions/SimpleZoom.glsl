#version 300 es
// SimpleZoom - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): 0gust1
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uZoom_quickness; // = 0.8
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: 0gust1
// License: MIT




vec2 zoom(vec2 uv, float amount) {
  return 0.5 + ((uv - 0.5) * (1.0-amount));	
}

vec4 transition(vec2 uv) {
  float nQuick = clamp(uZoom_quickness,0.2,1.0);

  return mix(
    getFromColor(zoom(uv, smoothstep(0.0, nQuick, uProgress))),
    getToColor(uv),
   smoothstep(nQuick-0.2, 1.0, uProgress)
  );
}
void main() { fragColor = transition(vTexCoord); }
