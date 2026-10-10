#version 300 es
// StaticFade - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Ben Lucas
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uN_noise_pixels; // = 200.0
uniform float uStatic_luminosity; // = 0.8
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Ben Lucas
// License: MIT




float rnd (vec2 st) {
    return fract(sin(dot(st.xy,
                         vec2(10.5302340293,70.23492931)))*
        12345.5453123);
}

vec4 staticNoise (vec2 st, float offset, float luminosity) {
  float staticR = luminosity * rnd(st * vec2(offset * 2.0, offset * 3.0));
  float staticG = luminosity * rnd(st * vec2(offset * 3.0, offset * 5.0));
  float staticB = luminosity * rnd(st * vec2(offset * 5.0, offset * 7.0));
  return vec4(staticR, staticG, staticB, 1.0);
}

float staticIntensity(float t)
{
  float transitionProgress = abs(2.0*(t-0.5));
  float transformedThreshold =1.2*(1.0 - transitionProgress)-0.1;
  return min(1.0, transformedThreshold);
}
  
vec4 transition(vec2 uv) {

  float baseMix = step(0.5, uProgress);
  vec4 transitionMix = mix(
    getFromColor(uv),
    getToColor(uv),
    baseMix
  );
  
  vec2 uvStatic = floor(uv * uN_noise_pixels)/uN_noise_pixels;
  
  vec4 staticColor = staticNoise(uvStatic, uProgress, uStatic_luminosity);

  float staticThresh = staticIntensity(uProgress);
  float staticMix = step(rnd(uvStatic), staticThresh);

  return mix(transitionMix, staticColor, staticMix);
}
void main() { fragColor = transition(vTexCoord); }
