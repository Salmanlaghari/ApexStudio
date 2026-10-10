#version 300 es
// StarWipe - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Ben Lucas
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uBorder_thickness; // = 0.01
uniform float uStar_rotation; // = 0.75
uniform vec4 uBorder_color; // = vec4(1.0)
uniform vec2 uStar_center; // = vec2(0.5)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Ben Lucas
// License: MIT
#define PI 3.141592653589793
#define STAR_ANGLE 1.2566370614359172






vec2 rotate(vec2 v, float theta) {
    float cosTheta = cos(theta);
    float sinTheta = sin(theta);

    return vec2(
        cosTheta * v.x - sinTheta * v.y,
        sinTheta * v.x + cosTheta * v.y
    );
}

bool inStar(vec2 uv, vec2 center, float radius){
  vec2 uv_centered = uv - center;
  uv_centered = rotate(uv_centered, uStar_rotation * STAR_ANGLE);
  float theta = atan(uv_centered.y, uv_centered.x) + PI;

  vec2 uv_rotated = rotate(uv_centered, -STAR_ANGLE * (floor(theta / STAR_ANGLE) + 0.5));

  float slope = 0.3;
  if(uv_rotated.y > 0.0){
      return (radius + uv_rotated.x * slope > uv_rotated.y);
  } else {
     return (-radius - uv_rotated.x * slope < uv_rotated.y);
  }
}

vec4 transition(vec2 uv) {
  float progressScaled = (2.0 * uBorder_thickness + 1.0) * uProgress - uBorder_thickness;
  if(inStar(uv, uStar_center, progressScaled)){
    return getToColor(uv);
  } else if(inStar(uv, uStar_center, progressScaled+uBorder_thickness)){
    return uBorder_color;
  } else {
    return getFromColor(uv);
  }
}
void main() { fragColor = transition(vTexCoord); }
