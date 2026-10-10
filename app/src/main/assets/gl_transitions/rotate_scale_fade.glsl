#version 300 es
// rotate_scale_fade - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Fernando Kuteken
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec2 uCenter; // = vec2(0.5, 0.5)
uniform float uRotations; // = 1.0
uniform float uScale; // = 8.0
uniform vec4 uBackColor; // = vec4(0.15, 0.15, 0.15, 1.0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Fernando Kuteken
// License: MIT

#define PI 3.14159265359






vec4 transition(vec2 uv) {
  
  vec2 difference = uv - uCenter;
  vec2 dir = normalize(difference);
  float dist = length(difference);
  
  float angle = 2.0 * PI * uRotations * uProgress;
  
  float c = cos(angle);
  float s = sin(angle);
  
  float currentScale = mix(uScale, 1.0, 2.0 * abs(uProgress - 0.5));
  
  vec2 rotatedDir = vec2(dir.x  * c - dir.y * s, dir.x * s + dir.y * c);
  vec2 rotatedUv = uCenter + rotatedDir * dist / currentScale;
  
  if (rotatedUv.x < 0.0 || rotatedUv.x > 1.0 ||
      rotatedUv.y < 0.0 || rotatedUv.y > 1.0)
    return uBackColor;
    
  return mix(getFromColor(rotatedUv), getToColor(rotatedUv), uProgress);
}
void main() { fragColor = transition(vTexCoord); }
