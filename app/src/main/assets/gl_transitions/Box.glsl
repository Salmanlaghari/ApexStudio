#version 300 es
// Box - ported from gl-transitions (https://github.com/gl-transitions/gl-transitions), MIT licensed.
// Original author(s): lql
// Adapted for Android GLSL ES 3.00 (fragment shader).
precision highp float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uFrom;
uniform sampler2D uTo;
uniform float uProgress;
uniform float uRatio;
uniform int uRectIn; // = 1
uniform int uLocation; // = 0
vec4 getFromColor(vec2 uv) { return texture(uFrom, uv); }
vec4 getToColor(vec2 uv) { return texture(uTo, uv); }
// Author: lql
// License: MIT

// center:0, left_top:1, left_bottom:2, right_top:3, right_bottom:4


vec4 transition(vec2 uv) {
    float p = uRectIn == 1 ? 1.0 - uProgress : uProgress;
    float x1, y1, x2, y2;

    // Determine rectangle coordinates based on uLocation
    if (uLocation == 0) {
        x1 = y1 = 0.5 * (1.0 - p);
        x2 = y2 = 1.0 - x1;
    } else {
        // Calculate the x and y coordinates based on the uLocation
        x1 = (uLocation == 1 || uLocation == 2) ? 0.0 : 1.0 - p;
        y1 = (uLocation == 1 || uLocation == 3) ? 1.0 - p : 0.0;
        x2 = (uLocation == 1 || uLocation == 2) ? p : 1.0;
        y2 = (uLocation == 1 || uLocation == 3) ? 1.0 : p;
    }

    // Determine if the point is inside the rectangle
    float in_rect = step(x1, uv.x) * step(uv.x, x2) * step(y1, uv.y) * step(uv.y, y2);
    in_rect = uRectIn == 1 ? 1.0 - in_rect : in_rect;

    // Mix colors based on the in_rect value
    return mix(getFromColor(uv), getToColor(uv), in_rect);
}
void main() { fragColor = transition(vTexCoord); }
