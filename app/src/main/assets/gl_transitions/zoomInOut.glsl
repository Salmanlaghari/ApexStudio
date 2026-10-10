#version 300 es
// zoomInOut - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): OllyOllyOlly
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
// Author: OllyOllyOlly
// License: MIT

vec2 zoom(vec2 uv, float amount) {
  return 0.5 + ((uv - 0.5) * (1.0 - amount));
}

vec4 transition(vec2 uv) {
  float zoomFrom = smoothstep(0.0, 1.0, uProgress * 2.0);
  float zoomTo = smoothstep(0.0, 1.0, (1.0 - uProgress) * 2.0);
  float crossfade = smoothstep(0.4, 0.6, uProgress);
  return mix(
    getFromColor(zoom(uv, zoomFrom)),
    getToColor(zoom(uv, zoomTo)),
    crossfade
  );
}
void main() { fragColor = transition(vTexCoord); }
