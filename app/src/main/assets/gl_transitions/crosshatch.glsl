#version 300 es
// crosshatch - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): pthrasher
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec2 uCenter; // = vec2(0.5)
uniform float uThreshold; // = 3.0
uniform float uFadeEdge; // = 0.1
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// License: MIT
// Author: pthrasher
// adapted by gre uFrom https://gist.github.com/pthrasher/04fd9a7de4012cbb03f6





float rand(vec2 co) {
  return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}
vec4 transition(vec2 uv) {
  float dist = distance(uCenter, uv) / uThreshold;
  float r = uProgress - min(rand(vec2(uv.y, 0.0)), rand(vec2(0.0, uv.x)));
  return mix(getFromColor(uv), getToColor(uv), mix(0.0, mix(step(dist, r), 1.0, smoothstep(1.0-uFadeEdge, 1.0, uProgress)), smoothstep(0.0, uFadeEdge, uProgress)));    
}
void main() { fragColor = transition(vTexCoord); }
