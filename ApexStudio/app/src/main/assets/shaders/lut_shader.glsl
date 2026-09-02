#version 300 es
// ApexStudio – 3D LUT Fragment Shader
// Maps RGB pixel colors against a 512×512 (8×8 grid of 64×64 tiles)
// horizontal-strip LUT texture with smooth bilinear interpolation.

precision highp float;

// ── uniforms ──────────────────────────────────────────────────
uniform sampler2D uTexture;      // source video frame  (GL_TEXTURE0)
uniform sampler2D uLutTexture;   // 512×512 LUT image   (GL_TEXTURE1)
uniform float      uIntensity;   // 0.0 … 1.0 filter opacity
uniform vec2       uTexelSize;   // 1.0 / frame dimensions

// ── varying (interpolated from vertex shader) ─────────────────
in  vec2 vTexCoord;
out vec4 fragColor;

// ─────────────────────────────────────────────────────────────
// Given an RGB value in [0,1], compute the UV into the
// horizontal-strip 512×512 LUT image.
//
// The LUT image is laid out as 8 horizontal strips of 64×64 tiles.
// Each strip corresponds to one blue slice (b_index 0-7).
// Within a strip, rows = green index, columns = red index.
//
//  lutSize = 512, sliceCount = 8, sliceSize = 64
// ─────────────────────────────────────────────────────────────
vec2 lutUV(vec3 rgb) {
    const float lutSize  = 512.0;
    const float sliceCount = 8.0;
    const float sliceSize  = lutSize / sliceCount; // 64

    // Scale RGB to LUT coordinate space (offset by 0.5 texel)
    float blueIndex  = rgb.b * (sliceCount - 1.0);
    float blueFloor  = floor(blueIndex);
    float blueFrac   = blueIndex - blueFloor;

    float greenIdx = rgb.g * (sliceSize - 1.0);
    float redIdx   = rgb.r * (sliceSize - 1.0);

    // Pixel coords within the LUT image
    float x0 = (blueFloor * sliceSize + redIdx + 0.5) / lutSize;
    float y0 = (greenIdx + 0.5) / lutSize;

    float x1 = ((blueFloor + 1.0) * sliceSize + redIdx + 0.5) / lutSize;
    float y1 = y0;

    return vec2(x0, y0);
}

vec2 lutUV2(vec3 rgb, float sliceOffset) {
    const float lutSize  = 512.0;
    const float sliceCount = 8.0;
    const float sliceSize  = lutSize / sliceCount;

    float blueIndex = rgb.b * (sliceCount - 1.0);
    float blueFloor = floor(blueIndex) + sliceOffset;
    blueFloor = clamp(blueFloor, 0.0, sliceCount - 1.0);

    float greenIdx = rgb.g * (sliceSize - 1.0);
    float redIdx   = rgb.r * (sliceSize - 1.0);

    float x = (blueFloor * sliceSize + redIdx + 0.5) / lutSize;
    float y = (greenIdx + 0.5) / lutSize;

    return vec2(x, y);
}

// ─────────────────────────────────────────────────────────────
void main() {
    vec4 original = texture(uTexture, vTexCoord);
    vec3 color    = original.rgb;

    const float lutSize  = 512.0;
    const float sliceCount = 8.0;
    const float sliceSize  = lutSize / sliceCount;

    // Blue slice interpolation
    float blueIndex = color.b * (sliceCount - 1.0);
    float blueFloor = floor(blueIndex);
    float blueCeil  = min(blueFloor + 1.0, sliceCount - 1.0);
    float blueFrac  = blueIndex - blueFloor;

    // Green & red indices (offset by 0.5 texel for center sampling)
    float greenIdx = color.g * (sliceSize - 1.0);
    float redIdx   = color.r * (sliceSize - 1.0);

    // Four corner samples from two adjacent blue slices
    vec2 uv00 = vec2(
        (blueFloor * sliceSize + redIdx + 0.5) / lutSize,
        (greenIdx + 0.5) / lutSize
    );
    vec2 uv10 = vec2(
        (blueFloor * sliceSize + redIdx + 1.0 + 0.5) / lutSize,
        (greenIdx + 0.5) / lutSize
    );
    vec2 uv01 = vec2(
        (blueFloor * sliceSize + redIdx + 0.5) / lutSize,
        (greenIdx + 1.0 + 0.5) / lutSize
    );
    vec2 uv11 = vec2(
        (blueFloor * sliceSize + redIdx + 1.0 + 0.5) / lutSize,
        (greenIdx + 1.0 + 0.5) / lutSize
    );

    vec3 slice0Color0 = texture(uLutTexture, uv00).rgb;
    vec3 slice0Color1 = texture(uLutTexture, uv10).rgb;
    vec3 slice0Color2 = texture(uLutTexture, uv01).rgb;
    vec3 slice0Color3 = texture(uLutTexture, uv11).rgb;

    vec3 slice1Color0 = texture(uLutTexture, vec2(
        (blueCeil * sliceSize + redIdx + 0.5) / lutSize,
        (greenIdx + 0.5) / lutSize
    )).rgb;
    vec3 slice1Color1 = texture(uLutTexture, vec2(
        (blueCeil * sliceSize + redIdx + 1.0 + 0.5) / lutSize,
        (greenIdx + 0.5) / lutSize
    )).rgb;
    vec3 slice1Color2 = texture(uLutTexture, vec2(
        (blueCeil * sliceSize + redIdx + 0.5) / lutSize,
        (greenIdx + 1.0 + 0.5) / lutSize
    )).rgb;
    vec3 slice1Color3 = texture(uLutTexture, vec2(
        (blueCeil * sliceSize + redIdx + 1.0 + 0.5) / lutSize,
        (greenIdx + 1.0 + 0.5) / lutSize
    )).rgb;

    // Bilinear interpolation within each slice
    vec2 f = vec2(fract(redIdx), fract(greenIdx));

    vec3 slice0 = mix(
        mix(slice0Color0, slice0Color1, f.x),
        mix(slice0Color2, slice0Color3, f.x),
        f.y
    );

    vec3 slice1 = mix(
        mix(slice1Color0, slice1Color1, f.x),
        mix(slice1Color2, slice1Color3, f.x),
        f.y
    );

    // Trilinear interpolation between the two blue slices
    vec3 lutColor = mix(slice0, slice1, blueFrac);

    // Blend original ↔ LUT color by intensity
    vec3 result = mix(color, lutColor, uIntensity);

    fragColor = vec4(result, original.a);
}
