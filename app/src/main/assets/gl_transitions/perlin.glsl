#version 300 es
// perlin - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Rich Harris
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uScale; // = 4.0
uniform float uSmoothness; // = 0.01
uniform float uSeed; // = 12.9898
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Rich Harris
// License: MIT

#ifdef GL_ES
precision highp float;
#endif






// http://byteblacksmith.com/improvements-uTo-the-canonical-one-liner-glsl-rand-for-opengl-es-2-0/
float random(vec2 co)
{
    float a = uSeed;
    float b = 78.233;
    float c = 43758.5453;
    float dt= dot(co.xy ,vec2(a,b));
    float sn= mod(dt,3.14);
    return fract(sin(sn) * c);
}

// 2D Noise based on Morgan McGuire @morgan3d
// https://www.shadertoy.com/view/4dS3Wd
float noise (in vec2 st) {
    vec2 i = floor(st);
    vec2 f = fract(st);

    // Four corners in 2D of a tile
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    // Smooth Interpolation

    // Cubic Hermine Curve.  Same as SmoothStep()
    vec2 u = f*f*(3.0-2.0*f);
    // u = smoothstep(0.,1.,f);

    // Mix 4 coorners porcentages
    return mix(a, b, u.x) +
            (c - a)* u.y * (1.0 - u.x) +
            (d - b) * u.x * u.y;
}

vec4 transition(vec2 uv) {
  vec4 uFrom = getFromColor(uv);
  vec4 uTo = getToColor(uv);
  float n = noise(uv * uScale);

  float p = mix(-uSmoothness, 1.0 + uSmoothness, uProgress);
  float lower = p - uSmoothness;
  float higher = p + uSmoothness;

  float q = smoothstep(lower, higher, n);

  return mix(
    uFrom,
    uTo,
    1.0 - q
  );
}
void main() { fragColor = transition(vTexCoord); }
