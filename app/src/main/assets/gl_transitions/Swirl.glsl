#version 300 es
// Swirl - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): Sergey Kosarevsky
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// License: MIT
// Author: Sergey Kosarevsky
// ( http://www.linderdaum.com )
// ported by gre uFrom https://gist.github.com/corporateshark/cacfedb8cca0f5ce3f7c

vec4 transition(vec2 uv) {
	float Radius = 1.0;

	float T = uProgress;

	uv -= vec2( 0.5, 0.5 );

	float Dist = length(uv);

	if ( Dist < Radius )
	{
		float Percent = (Radius - Dist) / Radius;
		float A = ( T <= 0.5 ) ? mix( 0.0, 1.0, T/0.5 ) : mix( 1.0, 0.0, (T-0.5)/0.5 );
		float Theta = Percent * Percent * A * 8.0 * 3.14159;
		float S = sin( Theta );
		float C = cos( Theta );
		uv = vec2( dot(uv, vec2(C, -S)), dot(uv, vec2(S, C)) );
	}
	uv += vec2( 0.5, 0.5 );

	vec4 C0 = getFromColor(uv);
	vec4 C1 = getToColor(uv);

	return mix( C0, C1, T );
}
void main() { fragColor = transition(vTexCoord); }
