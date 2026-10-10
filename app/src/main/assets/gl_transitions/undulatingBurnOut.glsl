#version 300 es
// undulatingBurnOut - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): pthrasher
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uSmoothness; // = 0.03
uniform vec2 uCenter; // = vec2(0.5)
uniform vec3 uColor; // = vec3(0.0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// License: MIT
// Author: pthrasher
// adapted by gre uFrom https://gist.github.com/pthrasher/8e6226b215548ba12734





const float M_PI = 3.14159265358979323846;

float quadraticInOut(float t) {
  float p = 2.0 * t * t;
  return t < 0.5 ? p : -p + (4.0 * t) - 1.0;
}

float getGradient(float r, float dist) {
  float d = r - dist;
  return mix(
    smoothstep(-uSmoothness, 0.0, r - dist * (1.0 + uSmoothness)),
    -1.0 - step(0.005, d),
    step(-0.005, d) * step(d, 0.01)
  );
}

float getWave(vec2 p){
  vec2 _p = p - uCenter; // offset uFrom uCenter
  float rads = atan(_p.y, _p.x);
  float degs = degrees(rads) + 180.0;
  vec2 range = vec2(0.0, M_PI * 30.0);
  vec2 domain = vec2(0.0, 360.0);
  float uRatio = (M_PI * 30.0) / 360.0;
  degs = degs * uRatio;
  float x = uProgress;
  float magnitude = mix(0.02, 0.09, smoothstep(0.0, 1.0, x));
  float offset = mix(40.0, 30.0, smoothstep(0.0, 1.0, x));
  float ease_degs = quadraticInOut(sin(degs));
  float deg_wave_pos = (ease_degs * magnitude) * sin(x * offset);
  return x + deg_wave_pos;
}

vec4 transition(vec2 uv) {
  float dist = distance(uCenter, uv);
  float m = getGradient(getWave(uv), dist);
  vec4 cfrom = getFromColor(uv);
  vec4 cto = getToColor(uv);
  return mix(mix(cfrom, cto, m), mix(cfrom, vec4(uColor, 1.0), 0.75), step(m, -2.0));
}
void main() { fragColor = transition(vTexCoord); }
