#version 300 es
// RotateScaleVanish - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Mark Craig
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform bool uFadeInSecond; // = true
uniform bool uReverseEffect; // = false
uniform bool uReverseRotation; // = false
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Mark Craig
// mrmcsoftware on github and youtube ( http://www.youtube.com/MrMcSoftware )
// License: MIT

// RotateScaleVanish Transition by Mark Craig (Copyright © 2022)





#define M_PI 3.14159265358979323846
#define _TWOPI 6.283185307179586476925286766559

vec4 transition(vec2 uv) {
vec2 iResolution = vec2(uRatio, 1.0);
float t = uReverseEffect ? 1.0 - uProgress : uProgress;
float theta = uReverseRotation ? _TWOPI * t : -_TWOPI * t;
float c1 = cos(theta);
float s1 = sin(theta);
float rad = max(0.00001, 1.0 - t);
float xc1 = (uv.x - 0.5) * iResolution.x;
float yc1 = (uv.y - 0.5) * iResolution.y;
float xc2 = (xc1 * c1 - yc1 * s1) / rad;
float yc2 = (xc1 * s1 + yc1 * c1) / rad;
vec2 uv2 = vec2(xc2 + iResolution.x / 2.0, yc2 + iResolution.y / 2.0);
vec4 col3;
vec4 ColorTo = uReverseEffect ? getFromColor(uv) : getToColor(uv);
if ((uv2.x >= 0.0) && (uv2.x <= iResolution.x) && (uv2.y >= 0.0) && (uv2.y <= iResolution.y))
	{
	uv2 /= iResolution;
	col3 = uReverseEffect ? getToColor(uv2) : getFromColor(uv2);
	}
else { col3 = uFadeInSecond ? vec4(0.0, 0.0, 0.0, 1.0) : ColorTo; }
return((1.0 - t) * col3 + t * ColorTo); // could have used mix
}
void main() { fragColor = transition(vTexCoord); }
