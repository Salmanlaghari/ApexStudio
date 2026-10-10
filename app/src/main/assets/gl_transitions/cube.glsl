#version 300 es
// cube - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): gre
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform float uPersp; // = 0.7
uniform float uUnzoom; // = 0.3
uniform float uReflection; // = 0.4
uniform float uFloating; // = 3.0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: gre
// License: MIT





vec2 project (vec2 p) {
  return p * vec2(1.0, -1.2) + vec2(0.0, -uFloating/100.);
}

bool inBounds (vec2 p) {
  return all(lessThan(vec2(0.0), p)) && all(lessThan(p, vec2(1.0)));
}

vec4 bgColor (vec2 p, vec2 pfr, vec2 pto) {
  vec4 c = vec4(0.0, 0.0, 0.0, 1.0);
  pfr = project(pfr);
  // FIXME avoid branching might help perf!
  if (inBounds(pfr)) {
    c += mix(vec4(0.0), getFromColor(pfr), uReflection * mix(1.0, 0.0, pfr.y));
  }
  pto = project(pto);
  if (inBounds(pto)) {
    c += mix(vec4(0.0), getToColor(pto), uReflection * mix(1.0, 0.0, pto.y));
  }
  return c;
}

// p : the position
// uPersp : the perspective in [ 0, 1 ]
// center : the xcenter in [0, 1] \ 0.5 excluded
vec2 xskew (vec2 p, float uPersp, float center) {
  float x = mix(p.x, 1.0-p.x, center);
  return (
    (
      vec2( x, (p.y - 0.5*(1.0-uPersp) * x) / (1.0+(uPersp-1.0)*x) )
      - vec2(0.5-distance(center, 0.5), 0.0)
    )
    * vec2(0.5 / distance(center, 0.5) * (center<0.5 ? 1.0 : -1.0), 1.0)
    + vec2(center<0.5 ? 0.0 : 1.0, 0.0)
  );
}

vec4 transition(vec2 uv) {
  float uz = uUnzoom * 2.0*(0.5-distance(0.5, uProgress));
  vec2 p = -uz*0.5+(1.0+uz) * uv;
  vec2 fromP = xskew(
    (p - vec2(uProgress, 0.0)) / vec2(1.0-uProgress, 1.0),
    1.0-mix(uProgress, 0.0, uPersp),
    0.0
  );
  vec2 toP = xskew(
    p / vec2(uProgress, 1.0),
    mix(pow(uProgress, 2.0), 1.0, uPersp),
    1.0
  );
  // FIXME avoid branching might help perf!
  if (inBounds(fromP)) {
    return getFromColor(fromP);
  }
  else if (inBounds(toP)) {
    return getToColor(toP);
  }
  return bgColor(uv, fromP, toP);
}
void main() { fragColor = transition(vTexCoord); }
