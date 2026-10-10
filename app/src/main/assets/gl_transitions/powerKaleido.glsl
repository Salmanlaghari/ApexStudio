#version 300 es
// powerKaleido - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Boundless
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uScale; // = 2.0
uniform float uZ; // = 1.5
uniform float uSpeed; // = 5.
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Name: Power Kaleido
// Author: Boundless
// License: MIT
#define PI 3.14159265358979
const float rad = 120.; // change this value uTo get different mirror effects
const float deg = rad / 180. * PI;




vec2 refl(vec2 p,vec2 o,vec2 n)
{
	return 2.0*o+2.0*n*dot(p-o,n)-p;
}

vec2 rot(vec2 p, vec2 o, float a)
{
    float s = sin(a);
    float c = cos(a);
	return o + mat2(c, -s, s, c) * (p - o);
}

vec4 mainImage(vec2 uv)
{
  float dist = uScale / 10.;

  vec2 uv0 = uv;
	uv -= 0.5;
  uv.x *= uRatio;
  uv *= uZ;
  uv = rot(uv, vec2(0.0), uProgress*uSpeed);
  // uv.x = fract(uv.x/l/3.0)*l*3.0;
	float theta = uProgress*6.+PI/.5;
	for(int iter = 0; iter < 10; iter++) {
    for(float i = 0.; i < 2. * PI; i+=deg) {
	    float ts = sign(asin(cos(i))) == 1.0 ? 1.0 : 0.0;
      if(((ts == 1.0) && (uv.y-dist*cos(i) > tan(i)*(uv.x+dist*+sin(i)))) || ((ts == 0.0) && (uv.y-dist*cos(i) < tan(i)*(uv.x+dist*+sin(i))))) {
        uv = refl(vec2(uv.x+sin(i)*dist*2.,uv.y-cos(i)*dist*2.), vec2(0.,0.), vec2(cos(i),sin(i)));
      }
    }
  }
  uv += 0.5;
  uv = rot(uv, vec2(0.5), uProgress*-uSpeed);
  uv -= 0.5;
  uv.x /= uRatio;
  uv += 0.5;
  uv = 2.*abs(uv/2.-floor(uv/2.+0.5));
  vec2 uvMix = mix(uv,uv0,cos(uProgress*PI*2.)/2.+0.5);
  vec4 color = mix(getFromColor(uvMix),getToColor(uvMix),cos((uProgress-1.)*PI)/2.+0.5);
	return color;
    
}
vec4 transition(vec2 uv) {
  vec4 color = mainImage(uv);
  return color;
}
void main() { fragColor = transition(vTexCoord); }
