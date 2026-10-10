#version 300 es
// SimpleZoomOut - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Tianshuo
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uZoom_quickness; // = 0.8
uniform bool uFade; // = true
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Tianshuo
// License: MIT






vec2 zoom(vec2 uv, float amount) {
  return 0.5 + ((uv - 0.5) * (1.0-amount));	
}

vec4 transition(vec2 uv) {
  float nQuick = clamp(uZoom_quickness,0.2,1.0);

  return mix(
    getFromColor(uv),
    getToColor(zoom(uv,1.-smoothstep(1.-nQuick, 1., uProgress))),
   uFade?smoothstep(1.0-nQuick, 1., uProgress):(uProgress<1.0-nQuick?0.0:1.0)
  );
}
void main() { fragColor = transition(vTexCoord); }
