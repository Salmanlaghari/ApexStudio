#version 300 es
// Slides - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
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
uniform bool uIn; // = false
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: Mark Craig
// mrmcsoftware on github and youtube ( http://www.youtube.com/MrMcSoftware )
// License: MIT

// Slides Transition by Mark Craig (Copyright © 2022)



// uType: slide uTo/uFrom which edge, which corner, or center
// uIn: if true slide new image in, otherwise slide old image out

#define rad2 rad / 2.0

vec4 transition(vec2 uv) {
vec2 uv0 = uv;
float rad = uIn ? uProgress : 1.0 - uProgress;
float xc1, yc1;
// I used if/else instead of switch in case it's an old GPU
if (uType == 0) { xc1 = .5 - rad2; yc1 = 0.0; }
else if (uType == 1) { xc1 = 1.0 - rad; yc1 = .5 - rad2; }
else if (uType == 2) { xc1 = .5 - rad2; yc1 = 1.0 - rad; }
else if (uType == 3) { xc1 = 0.0; yc1 = .5 - rad2; }
else if (uType == 4) { xc1 = 1.0 - rad; yc1 = 0.0; }
else if (uType == 5) { xc1 = 1.0 - rad; yc1 = 1.0 - rad; }
else if (uType == 6) { xc1 = 0.0; yc1 = 1.0 - rad; }
else if (uType == 7) { xc1 = 0.0; yc1 = 0.0; }
else if (uType == 8) { xc1 = .5 - rad2; yc1 = .5 - rad2; }
uv.y = 1.0 - uv.y;
vec2 uv2;
if ((uv.x >= xc1) && (uv.x <= xc1 + rad) && (uv.y >= yc1) && (uv.y <= yc1 + rad))
	{
	uv2 = vec2((uv.x - xc1) / rad, 1.0 - (uv.y - yc1) / rad);
	return(uIn ? getToColor(uv2) : getFromColor(uv2));
	}
return(uIn ? getFromColor(uv0) : getToColor(uv0));
}
void main() { fragColor = transition(vTexCoord); }
