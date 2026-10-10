#version 300 es
// DreamyZoom - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Zeh Fernando
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uRotation; // = 6.0
uniform float uScale; // = 1.2
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Zeh Fernando
// License: MIT

// Definitions --------
#define DEG2RAD 0.03926990816987241548078304229099 // 1/180*PI


// Transition parameters --------

// In degrees


// Multiplier



// The code proper --------

vec4 transition(vec2 uv) {
  // Massage parameters
  float phase = uProgress < 0.5 ? uProgress * 2.0 : (uProgress - 0.5) * 2.0;
  float angleOffset = uProgress < 0.5 ? mix(0.0, uRotation * DEG2RAD, phase) : mix(-uRotation * DEG2RAD, 0.0, phase);
  float newScale = uProgress < 0.5 ? mix(1.0, uScale, phase) : mix(uScale, 1.0, phase);
  
  vec2 center = vec2(0, 0);

  // Calculate the source point
  vec2 assumedCenter = vec2(0.5, 0.5);
  vec2 p = (uv.xy - vec2(0.5, 0.5)) / newScale * vec2(uRatio, 1.0);

  // This can probably be optimized (with distance())
  float angle = atan(p.y, p.x) + angleOffset;
  float dist = distance(center, p);
  p.x = cos(angle) * dist / uRatio + 0.5;
  p.y = sin(angle) * dist + 0.5;
  vec4 c = uProgress < 0.5 ? getFromColor(p) : getToColor(p);

  // Finally, apply the color
  return c + (uProgress < 0.5 ? mix(0.0, 1.0, phase) : mix(1.0, 0.0, phase));
}
void main() { fragColor = transition(vTexCoord); }
