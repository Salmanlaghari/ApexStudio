#version 300 es
// Bounce - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Adrian Purser
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec4 uShadow_colour; // = vec4(0.,0.,0.,.6)
uniform float uShadow_height; // = 0.075
uniform float uBounces; // = 3.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Adrian Purser
// License: MIT





const float PI = 3.14159265358;

vec4 transition(vec2 uv) {
  float time = uProgress;
  float stime = sin(time * PI / 2.);
  float phase = time * PI * uBounces;
  float y = (abs(cos(phase))) * (1.0 - stime);
  float d = uv.y - y;
  return mix(
    mix(
      getToColor(uv),
      uShadow_colour,
      step(d, uShadow_height) * (1. - mix(
        ((d / uShadow_height) * uShadow_colour.a) + (1.0 - uShadow_colour.a),
        1.0,
        smoothstep(0.95, 1., uProgress) // fade-out the shadow at the end
      ))
    ),
    getFromColor(vec2(uv.x, uv.y + (1.0 - y))),
    step(d, 0.0)
  );
}
void main() { fragColor = transition(vTexCoord); }
