#version 300 es
// CircleCrop - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): fkuteken
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform vec4 uBgcolor; // = vec4(0.0, 0.0, 0.0, 1.0)
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// License: MIT
// Author: fkuteken
// ported by gre uFrom https://gist.github.com/fkuteken/f63e3009c1143950dee9063c3b83fb88






vec4 transition(vec2 uv) {
  vec2 ratio2 = vec2(1.0, 1.0 / uRatio);
  float s = pow(2.0 * abs(uProgress - 0.5), 3.0);

  float dist = length((vec2(uv) - 0.5) * ratio2);
  return mix(
    uProgress < 0.5 ? getFromColor(uv) : getToColor(uv), // branching is ok here as we statically depend on uProgress uniform (branching won't change over pixels)
    uBgcolor,
    step(s, dist)
  );
}
void main() { fragColor = transition(vTexCoord); }
