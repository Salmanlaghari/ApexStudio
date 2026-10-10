#version 300 es
// static_wipe - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Ben Lucas
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform bool u_transitionUpToDown; // = true
uniform float u_max_static_span; // = 0.5
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Ben Lucas
// License: MIT

#define PI 3.14159265359

float rnd (vec2 st) {
    return fract(sin(dot(st.xy,
                         vec2(10,70)))*
        12345.5453123);
}




vec4 transition(vec2 uv) {
  

  float span = u_max_static_span*pow(sin(PI*uProgress),0.5);
  
  float transitionEdge = u_transitionUpToDown ? 1.0-uv.y : uv.y;
  float mixRatio = 1.0 - step(uProgress, transitionEdge);

  vec4 transitionMix = mix(
    getFromColor(uv),
    getToColor(uv),
    mixRatio
  );
  
  float noiseEnvelope = smoothstep(uProgress-span, uProgress, transitionEdge) * (1.0 - smoothstep(uProgress, uProgress + span, transitionEdge));
  vec4 noise = vec4(vec3(rnd(uv*(1.0+uProgress))), 1.0);
  

  return mix(transitionMix, noise, noiseEnvelope);
}
void main() { fragColor = transition(vTexCoord); }
