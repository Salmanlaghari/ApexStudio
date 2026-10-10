#version 300 es
// BowTieWithParameter - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): KMojek
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uAdjust; // = 0.5
uniform bool uReverse; // = false
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: KMojek
// License: MIT




float check(vec2 p1, vec2 p2, vec2 p3)
{
  return (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y);
}

bool pointInTriangle(vec2 pt, vec2 p1, vec2 p2, vec2 p3)
{

    bool b1 = check(pt, p1, p2) < 0.0;
    bool b2 = check(pt, p2, p3) < 0.0;
    bool b3 = check(pt, p3, p1) < 0.0;
    return b1 == b2 && b2 == b3;
}

const float height = 0.5;

vec4 transition_firstHalf( vec2 uv, float prog )
{
  if ( uv.y < 0.5 )
  {
    vec2 botLeft = vec2( -0., prog-height );
    vec2 botRight = vec2( 1., prog-height );
    vec2 tip = vec2( uAdjust, prog );
    if ( pointInTriangle( uv, botLeft, botRight, tip ) )
        return getToColor(uv);
    }
  else
  {
    vec2 topLeft = vec2( -0., 1.-prog+height );
    vec2 topRight = vec2( 1., 1.-prog+height );
    vec2 tip = vec2( uAdjust, 1.-prog );
    if ( pointInTriangle( uv, topLeft, topRight, tip ) )
      return getToColor( uv );
  }
  return getFromColor( uv );
}

vec4 transition_secondHalf( vec2 uv, float prog )
{
  if ( uv.x > uAdjust )
  {
    vec2 top = vec2( prog + height,  1. );
    vec2 bot = vec2( prog + height, -0. );
    vec2 tip = vec2( mix( uAdjust, 1.0, 2.0 * (prog - 0.5) ), 0.5 );
    if ( pointInTriangle( uv, top, bot, tip) )
      return getFromColor( uv );
  }
  else
  {
    vec2 top = vec2( 1.0-prog - height,  1. );
    vec2 bot = vec2( 1.0-prog - height, -0. );
    vec2 tip = vec2( mix( uAdjust, 0.0, 2.0 * (prog - 0.5)  ), 0.5 );
    if ( pointInTriangle( uv, top, bot, tip) )
      return getFromColor( uv );
  }
  return getToColor( uv );
}

vec4 transition(vec2 uv) {
  if ( uReverse )
    return ( uProgress < 0.5 ) ? transition_secondHalf( uv, 1.-uProgress ) : transition_firstHalf( uv, 1.-uProgress );
  else
    return ( uProgress < 0.5 ) ? transition_firstHalf( uv, uProgress ) : transition_secondHalf( uv, uProgress );
}
void main() { fragColor = transition(vTexCoord); }
