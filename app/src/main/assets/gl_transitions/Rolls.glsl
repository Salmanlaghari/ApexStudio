#version 300 es
// Rolls - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Mark Craig
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform int uType; // = 0
uniform bool uRotDown; // = false
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Mark Craig
// mrmcsoftware on github and youtube ( http://www.youtube.com/MrMcSoftware )
// License: MIT

// Rolls Transition by Mark Craig (Copyright © 2022)



// uType (0-3): Rotate/Roll uFrom which corner
// uRotDown: if true rotate old image down, otherwise rotate old image up

#define M_PI 3.14159265358979323846

vec4 transition(vec2 uv) {
float theta, c1, s1;
vec2 iResolution = vec2(uRatio, 1.0);
vec2 uvi;
// I used if/else instead of switch in case it's an old GPU
if (uType == 0) { theta = (uRotDown ? M_PI : -M_PI) / 2.0 * uProgress; uvi.x = 1.0 - uv.x; uvi.y = uv.y; }
else if (uType == 1) { theta = (uRotDown ? M_PI : -M_PI) / 2.0 * uProgress; uvi = uv; }
else if (uType == 2) { theta = (uRotDown ? -M_PI : M_PI) / 2.0 * uProgress; uvi.x = uv.x; uvi.y = 1.0 - uv.y; }
else if (uType == 3) { theta = (uRotDown ? -M_PI : M_PI) / 2.0 * uProgress; uvi = 1.0 - uv; }
c1 = cos(theta); s1 = sin(theta);
vec2 uv2;
uv2.x = (uvi.x * iResolution.x * c1 - uvi.y * iResolution.y * s1);
uv2.y = (uvi.x * iResolution.x * s1 + uvi.y * iResolution.y * c1);
if ((uv2.x >= 0.0) && (uv2.x <= iResolution.x) && (uv2.y >= 0.0) && (uv2.y <= iResolution.y))
	{
	uv2 /= iResolution;
	if (uType == 0) { uv2.x = 1.0 - uv2.x; }
	else if (uType == 2) { uv2.y = 1.0 - uv2.y; }
	else if (uType == 3) { uv2 = 1.0 - uv2; }
	return(getFromColor(uv2));
	}
return(getToColor(uv));
}
void main() { fragColor = transition(vTexCoord); }
