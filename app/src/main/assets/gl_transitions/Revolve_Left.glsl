#version 300 es
// Revolve_Left - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): bread
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec2 uCenter; // = vec2(0.46, 0.52)
uniform float uDirection; // = -1.0
uniform float uMaxRotation; // = 1.95
uniform float uPeakZoom; // = 2.22
uniform float uSwirl; // = 2.85
uniform float uBarrel; // = 0.38
uniform float uMotionBlur; // = 1.0
uniform float uSwitchStart; // = 0.30
uniform float uSwitchEnd; // = 0.50
uniform float uShadow; // = 0.16
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: bread
// License: MIT
// gl-transitions v1 compatible












float sat(float x) {
  return clamp(x, 0.0, 1.0);
}

float ease(float x) {
  x = sat(x);
  return x * x * (3.0 - 2.0 * x);
}

float revolveEnvelope(float t) {
  float rise = ease((t - 0.10) / 0.33);
  float fall = 1.0 - ease((t - 0.43) / 0.29);
  return rise * fall;
}

vec2 rotate2(vec2 p, float a) {
  float s = sin(a);
  float c = cos(a);
  return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

vec2 warpUv(vec2 uv, float t) {
  float e = revolveEnvelope(t);

  vec2 p = uv - uCenter;
  p.x *= uRatio;

  float r = length(p);
  float edgeSpin = uMaxRotation * e;
  float coreSpin = uSwirl * e * pow(1.0 - sat(r / 0.96), 1.55);
  float visibleAngle = uDirection * (edgeSpin + coreSpin);

  p = rotate2(p, -visibleAngle);

  float sc = 1.0 + (uPeakZoom - 1.0) * pow(e, 0.85);
  p /= sc;

  float rr = length(p);
  p *= 1.0 + uBarrel * e * rr * rr * 2.8;

  p.x /= uRatio;
  return clamp(p + uCenter, vec2(0.001), vec2(0.999));
}

vec4 sampleRevolve(vec2 uv, float t) {
  vec2 p = warpUv(uv, t);
  float reveal = smoothstep(uSwitchStart, uSwitchEnd, t);
  return mix(getFromColor(p), getToColor(p), reveal);
}

vec4 transition(vec2 uv) {
  if (uProgress <= 0.0) return getFromColor(uv);
  if (uProgress >= 1.0) return getToColor(uv);

  float e = revolveEnvelope(uProgress);
  float span = 0.060 * uMotionBlur * e;

  vec4 color = vec4(0.0);
  float total = 0.0;

  for (int i = -8; i <= 8; i++) {
    float x = float(i) / 8.0;
    float w = 1.0 - abs(x);
    w = w * w + 0.01;

    float t = sat(uProgress + x * span);
    color += sampleRevolve(uv, t) * w;
    total += w;
  }

  color /= total;

  vec2 q = uv - vec2(0.5);
  q.x *= uRatio;
  float vignette = 1.0 - uShadow * e * smoothstep(0.35, 0.95, length(q));
  color.rgb *= vignette;

  return color;
}
void main() { fragColor = transition(vTexCoord); }
