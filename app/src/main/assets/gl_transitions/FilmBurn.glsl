#version 300 es
// FilmBurn - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Anastasia Dunbar
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uSeed; // = 2.31
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Anastasia Dunbar
// License: MIT

float sigmoid(float x, float a) {
    float b = pow(x*2.,a)/2.;
    if (x > .5) {
        b = 1.-pow(2.-(x*2.),a)/2.;
    }
	return b;
}
float rand(float co){
    return fract(sin((co*24.9898)+uSeed)*43758.5453);
}
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}
float apow(float a,float b) { return pow(abs(a),b)*sign(b); }
vec3 pow3(vec3 a,vec3 b) { return vec3(apow(a.r,b.r),apow(a.g,b.g),apow(a.b,b.b)); }
float smooth_mix(float a,float b,float c) { return mix(a,b,sigmoid(c,2.)); }
float random(vec2 co, float shft){
    co += 10.;
    return smooth_mix(fract(sin(dot(co.xy ,vec2(12.9898+(floor(shft)*.5),78.233+uSeed))) * 43758.5453),fract(sin(dot(co.xy ,vec2(12.9898+(floor(shft+1.)*.5),78.233+uSeed))) * 43758.5453),fract(shft));
}
float smooth_random(vec2 co, float shft) {
	return smooth_mix(smooth_mix(random(floor(co),shft),random(floor(co+vec2(1.,0.)),shft),fract(co.x)),smooth_mix(random(floor(co+vec2(0.,1.)),shft),random(floor(co+vec2(1.,1.)),shft),fract(co.x)),fract(co.y));
}
vec4 burnTexture(vec2 p) {
    return mix(getFromColor(p), getToColor(p), sigmoid(uProgress,10.));
}
#define pi 3.14159265358979323
#define clamps(x) clamp(x,0.,1.)

vec4 transition(vec2 uv) {
  vec3 f = vec3(0.);
  for (float i = 0.; i < 13.; i++) {
    f += sin(((uv.x*rand(i)*6.)+(uProgress*8.))+rand(i+1.43))*sin(((uv.y*rand(i+4.4)*6.)+(uProgress*6.))+rand(i+2.4));
    f += 1.-clamps(length(uv-vec2(smooth_random(vec2(uProgress*1.3),i+1.),smooth_random(vec2(uProgress*.5),i+6.25)))*mix(20.,70.,rand(i)));
  }
  f += 4.;
  f /= 11.;
  f = pow3(f*vec3(1.,0.7,0.6),vec3(1.,2.-sin(uProgress*pi),1.3));
  f *= sin(uProgress*pi);
  
  uv -= .5;
  uv *= 1.+(smooth_random(vec2(uProgress*5.),6.3)*sin(uProgress*pi)*.05);
  uv += .5;
  
  vec4 blurred_image = vec4(0.);
  float bluramount = sin(uProgress*pi)*.03;
  #define repeats  50.
  for (float i = 0.; i < repeats; i++) { 
      vec2 q = vec2(cos(degrees((i/repeats)*360.)),sin(degrees((i/repeats)*360.))) *  (rand(vec2(i,uv.x+uv.y))+bluramount); 
      vec2 uv2 = uv+(q*bluramount);
      blurred_image += burnTexture(uv2);
  }
  blurred_image /= repeats;
  
  return blurred_image+vec4(f,0.);
}
void main() { fragColor = transition(vTexCoord); }
