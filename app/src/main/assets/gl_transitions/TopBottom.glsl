#version 300 es
// TopBottom - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): zhmy
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
// Author: zhmy
// License: MIT

const vec4 black = vec4(0.0, 0.0, 0.0, 1.0);
const vec2 boundMin = vec2(0.0, 0.0);
const vec2 boundMax = vec2(1.0, 1.0);

bool inBounds (vec2 p) {
    return all(lessThan(boundMin, p)) && all(lessThan(p, boundMax));
}

vec4 transition(vec2 uv) {
    vec2 spfr,spto = vec2(-1.);
    float size = mix(1.0, 3.0, uProgress*0.2);
    spto = (uv + vec2(-0.5,-0.5))*vec2(size,size)+vec2(0.5,0.5);
    spfr = (uv + vec2(0.0, 1.0 - uProgress));
    if(inBounds(spfr)){
        return getToColor(spfr);
    } else if(inBounds(spto)){
        return getFromColor(spto) * (1.0 - uProgress);
    } else{
        return black;
    }
}
void main() { fragColor = transition(vTexCoord); }
