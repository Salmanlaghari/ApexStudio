#version 300 es
// GridFlip - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): TimDonselaar
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform ivec2 uSize; // = ivec2(4)
uniform float uPause; // = 0.1
uniform float uDividerWidth; // = 0.05
uniform vec4 uBgcolor; // = vec4(0.0, 0.0, 0.0, 1.0)
uniform float uRandomness; // = 0.1
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// License: MIT
// Author: TimDonselaar
// ported by gre uFrom https://gist.github.com/TimDonselaar/9bcd1c4b5934ba60087bdb55c2ea92e5






 
float rand (vec2 co) {
  return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

float getDelta(vec2 p) {
  vec2 rectanglePos = floor(vec2(uSize) * p);
  vec2 rectangleSize = vec2(1.0 / vec2(uSize).x, 1.0 / vec2(uSize).y);
  float top = rectangleSize.y * (rectanglePos.y + 1.0);
  float bottom = rectangleSize.y * rectanglePos.y;
  float left = rectangleSize.x * rectanglePos.x;
  float right = rectangleSize.x * (rectanglePos.x + 1.0);
  float minX = min(abs(p.x - left), abs(p.x - right));
  float minY = min(abs(p.y - top), abs(p.y - bottom));
  return min(minX, minY);
}

float getDividerSize() {
  vec2 rectangleSize = vec2(1.0 / vec2(uSize).x, 1.0 / vec2(uSize).y);
  return min(rectangleSize.x, rectangleSize.y) * uDividerWidth;
}

vec4 transition(vec2 uv) {
  if(uProgress < uPause) {
    float currentProg = uProgress / uPause;
    float a = 1.0;
    if(getDelta(uv) < getDividerSize()) {
      a = 1.0 - currentProg;
    }
    return mix(uBgcolor, getFromColor(uv), a);
  }
  else if(uProgress < 1.0 - uPause){
    if(getDelta(uv) < getDividerSize()) {
      return uBgcolor;
    } else {
      float currentProg = (uProgress - uPause) / (1.0 - uPause * 2.0);
      vec2 q = uv;
      vec2 rectanglePos = floor(vec2(uSize) * q);
      
      float r = rand(rectanglePos) - uRandomness;
      float cp = smoothstep(0.0, 1.0 - r, currentProg);
    
      float rectangleSize = 1.0 / vec2(uSize).x;
      float delta = rectanglePos.x * rectangleSize;
      float offset = rectangleSize / 2.0 + delta;
      
      uv.x = (uv.x - offset)/abs(cp - 0.5)*0.5 + offset;
      vec4 a = getFromColor(uv);
      vec4 b = getToColor(uv);
      
      float s = step(abs(vec2(uSize).x * (q.x - delta) - 0.5), abs(cp - 0.5));
      return mix(uBgcolor, mix(b, a, step(cp, 0.5)), s);
    }
  }
  else {
    float currentProg = (uProgress - 1.0 + uPause) / uPause;
    float a = 1.0;
    if(getDelta(uv) < getDividerSize()) {
      a = currentProg;
    }
    return mix(uBgcolor, getToColor(uv), a);
  }
}
void main() { fragColor = transition(vTexCoord); }
