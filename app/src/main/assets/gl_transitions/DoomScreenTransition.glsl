#version 300 es
// DoomScreenTransition - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Zeh Fernando
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform int uBars; // = 30
uniform float uAmplitude; // = 2.0
uniform float uNoise; // = 0.1
uniform float uFrequency; // = 0.5
uniform float uDripScale; // = 0.5
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Zeh Fernando
// License: MIT


// Transition parameters --------

// Number of total uBars/columns


// Multiplier for speed uRatio. 0 = no variation when going down, higher = some elements go much faster


// Further variations in speed. 0 = no uNoise, 1 = super noisy (ignore uFrequency)


// Speed variation horizontally. the bigger the value, the shorter the waves


// How much the uBars seem uTo "run" uFrom the middle of the screen first (sticking uTo the sides). 0 = no drip, 1 = curved drip



// The code proper --------

float rand(int num) {
  return fract(mod(float(num) * 67123.313, 12.0) * sin(float(num) * 10.3) * cos(float(num)));
}

float wave(int num) {
  float fn = float(num) * uFrequency * 0.1 * float(uBars);
  return cos(fn * 0.5) * cos(fn * 0.13) * sin((fn+10.0) * 0.3) / 2.0 + 0.5;
}

float drip(int num) {
  return sin(float(num) / float(uBars - 1) * 3.141592) * uDripScale;
}

float pos(int num) {
  return (uNoise == 0.0 ? wave(num) : mix(wave(num), rand(num), uNoise)) + (uDripScale == 0.0 ? 0.0 : drip(num));
}

vec4 transition(vec2 uv) {
  int bar = int(uv.x * (float(uBars)));
  float scale = 1.0 + pos(bar) * uAmplitude;
  float phase = uProgress * scale;
  float posY = uv.y / vec2(1.0).y;
  vec2 p;
  vec4 c;
  if (phase + posY < 1.0) {
    p = vec2(uv.x, uv.y + mix(0.0, vec2(1.0).y, phase)) / vec2(1.0).xy;
    c = getFromColor(p);
  } else {
    p = uv.xy / vec2(1.0).xy;
    c = getToColor(p);
  }

  // Finally, apply the color
  return c;
}
void main() { fragColor = transition(vTexCoord); }
