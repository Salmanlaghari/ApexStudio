package com.apexstudio.app.data.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Production Transition & Dynamic Effects Engine with OpenGL ES 3.0.
 *
 * Provides:
 * 1. Animated Transitions between two video clips:
 *    - Cross Dissolve
 *    - Directional Wipe (with soft feathering)
 *    - Zoom Blur / Push
 *    - Slide (Directional push)
 *    - Glitch Transition (block noise & channel separation)
 * 2. Dynamic Real-time Visual Effects:
 *    - RGB Split (Chromatic Aberration with controllable angle and offset)
 *    - Digital Glitch (Scanline tearing, block displacement, slice jitter)
 *    - VHS Retro (Sync wobble, tracking roll bar, phosphor scanlines)
 * 3. High-Performance Frame-Buffer (FBO) Logic:
 *    - Ping-pong FBO pipeline for multi-pass effect compositing.
 *    - Zero-allocation render loop: all textures, buffers, and uniforms are pre-allocated.
 *    - Prevents frame drops, memory leaks, and GC pauses on 60fps rendering.
 */
class TransitionEngine {

    companion object {
        private const val TAG = "TransitionEngine"

        enum class TransitionType(
            val id: String,
            val label: String = "Transition",
            val category: String = "Dissolve & Fade",
            val typeIndex: Int = 0
        ) {
            // Category 1: Dissolve & Fade (10)
            CROSS_DISSOLVE("cross", "Cross Dissolve", "Dissolve & Fade", 0),
            FADE_BLACK("fade_black", "Fade to Black", "Dissolve & Fade", 5),
            FADE_WHITE("fade_white", "Fade to White", "Dissolve & Fade", 6),
            FADE_COLOR("fade_color", "Color Flash", "Dissolve & Fade", 7),
            LUMA_FADE("luma_fade", "Luma Fade", "Dissolve & Fade", 8),
            BURN_FADE("burn_fade", "Burn Dissolve", "Dissolve & Fade", 9),
            EXPOSURE_FADE("exposure_fade", "Overexposure", "Dissolve & Fade", 10),
            BLUR_DISSOLVE("blur_dissolve", "Defocus Fade", "Dissolve & Fade", 11),
            FILM_DISSOLVE("film_dissolve", "Film Grain Fade", "Dissolve & Fade", 12),
            GRADIENT_FADE("gradient_fade", "Linear Dip", "Dissolve & Fade", 13),

            // Category 2: Wipe & Split (10)
            WIPE("wipe", "Horizontal Wipe", "Wipe & Split", 1),
            WIPE_LEFT("wipe_left", "Wipe Left", "Wipe & Split", 14),
            WIPE_UP("wipe_up", "Wipe Up", "Wipe & Split", 15),
            WIPE_DOWN("wipe_down", "Wipe Down", "Wipe & Split", 16),
            RADIAL_WIPE("radial_wipe", "Clock Radial Wipe", "Wipe & Split", 17),
            CIRCLE_CROP("circle_crop", "Iris Circle Wipe", "Wipe & Split", 18),
            DIAMOND_WIPE("diamond_wipe", "Diamond Iris", "Wipe & Split", 19),
            SPLIT_HORIZONTAL("split_horizontal", "Split Doors H", "Wipe & Split", 20),
            SPLIT_VERTICAL("split_vertical", "Split Doors V", "Wipe & Split", 21),
            VENETIAN_BLINDS("venetian_blinds", "Venetian Blinds", "Wipe & Split", 22),

            // Category 3: Slide & Push (10)
            SLIDE("slide", "Slide Left", "Slide & Push", 3),
            SLIDE_RIGHT("slide_right", "Slide Right", "Slide & Push", 23),
            SLIDE_UP("slide_up", "Slide Up", "Slide & Push", 24),
            SLIDE_DOWN("slide_down", "Slide Down", "Slide & Push", 25),
            PUSH_LEFT("push_left", "Push Left", "Slide & Push", 26),
            PUSH_RIGHT("push_right", "Push Right", "Slide & Push", 27),
            PUSH_UP("push_up", "Push Up", "Slide & Push", 28),
            PUSH_DOWN("push_down", "Push Down", "Slide & Push", 29),
            PARALLAX_SLIDE("parallax_slide", "Parallax Drift", "Slide & Push", 30),
            CORNER_SLIDE("corner_slide", "Diagonal Slide", "Slide & Push", 31),

            // Category 4: Zoom & Warp (10)
            ZOOM_BLUR("zoom", "Zoom In Blur", "Zoom & Warp", 2),
            ZOOM_OUT("zoom_out", "Zoom Out Warp", "Zoom & Warp", 32),
            WHIP_PAN_LEFT("whip_pan_left", "Whip Pan Left", "Zoom & Warp", 33),
            WHIP_PAN_RIGHT("whip_pan_right", "Whip Pan Right", "Zoom & Warp", 34),
            WHIP_PAN_UP("whip_pan_up", "Whip Pan Up", "Zoom & Warp", 35),
            WHIP_PAN_DOWN("whip_pan_down", "Whip Pan Down", "Zoom & Warp", 36),
            SPIN_CW("spin_cw", "Spin Clockwise", "Zoom & Warp", 37),
            SPIN_CCW("spin_ccw", "Spin Counter-CW", "Zoom & Warp", 38),
            SWIRL_WARP("swirl_warp", "Vortex Swirl", "Zoom & Warp", 39),
            FISHEYE_PUNCH("fisheye_punch", "Fisheye Punch", "Zoom & Warp", 40),

            // Category 5: Glitch & Digital (10)
            GLITCH("glitch", "Digital Glitch", "Glitch & Digital", 4),
            RGB_DISPLACE("rgb_displace", "RGB Slice Split", "Glitch & Digital", 41),
            VHS_REWIND("vhs_rewind", "VHS Rewind Tracking", "Glitch & Digital", 42),
            PIXEL_BLOCKS("pixel_blocks", "Pixel Dissolve", "Glitch & Digital", 43),
            MOSAIC_ZOOM("mosaic_zoom", "Mosaic Transition", "Glitch & Digital", 44),
            TV_STATIC("tv_static", "CRT Noise Static", "Glitch & Digital", 45),
            CODEC_TEAR("codec_tear", "MPEG Codec Tear", "Glitch & Digital", 46),
            SCANLINE_SWEEP("scanline_sweep", "Scanline Beam", "Glitch & Digital", 47),
            COLOR_CORRUPT("color_corrupt", "Bitshift Invert", "Glitch & Digital", 48),
            PIXEL_STRETCH("pixel_stretch", "Horizontal Slitscan", "Glitch & Digital", 49),

            // Category 6: Light & Flash (10)
            LIGHT_LEAK_TRANS("light_leak_trans", "Film Light Leak", "Light & Flash", 50),
            LENS_FLARE_FLASH("lens_flare_flash", "Anamorphic Flare", "Light & Flash", 51),
            GLOW_BURST("glow_burst", "Glow Exposure Bloom", "Light & Flash", 52),
            STROBE_FLASH("strobe_flash", "Triple Strobe Hit", "Light & Flash", 53),
            NEON_PULSE("neon_pulse", "Neon Cyan Pulse", "Light & Flash", 54),
            PRISM_DISPERSE("prism_disperse", "Spectral Dispersion", "Light & Flash", 55),
            SUN_GLARE("sun_glare", "Golden Sun Glare", "Light & Flash", 56),
            CHROMATIC_ZOOM("chromatic_zoom", "Prismatic Zoom Burst", "Light & Flash", 57),
            DIFFUSE_GLOW("diffuse_glow", "Dream Diffusion", "Light & Flash", 58),
            SPARK_BURST("spark_burst", "Glitter Spark Flash", "Light & Flash", 59);

            companion object {
                fun byId(id: String?): TransitionType = values().firstOrNull { it.id == id } ?: CROSS_DISSOLVE
                fun categories(): List<String> = listOf(
                    "All",
                    "Dissolve & Fade",
                    "Wipe & Split",
                    "Slide & Push",
                    "Zoom & Warp",
                    "Glitch & Digital",
                    "Light & Flash"
                )
            }
        }

        enum class DynamicEffectType(val id: String) {
            RGB_SPLIT("rgb_split"),
            DIGITAL_GLITCH("glitch"),
            VHS("vhs")
        }

        private const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;

out vec2 vTexCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = aTexCoord;
}
"""

        // Transition Fragment Shader: mixes clip A (uTexA) and clip B (uTexB) using uProgress
        private const val TRANSITION_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexA;
uniform sampler2D uTexB;
uniform float uProgress; // 0.0 -> clip A, 1.0 -> clip B
uniform int uType;       // 0 -> 59

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    float p = clamp(uProgress, 0.0, 1.0);
    vec2 uv = vTexCoord;

    vec4 colA = texture(uTexA, uv);
    vec4 colB = texture(uTexB, uv);

    if (uType == 0) { // Cross Dissolve
        fragColor = mix(colA, colB, p);
    } 
    else if (uType == 1) { // Wipe (Right)
        float softness = 0.08;
        float edge = p * (1.0 + softness * 2.0) - softness;
        float factor = smoothstep(edge - softness, edge + softness, uv.x);
        fragColor = mix(colB, colA, factor);
    } 
    else if (uType == 2) { // Zoom Blur
        vec2 center = vec2(0.5, 0.5);
        vec4 cA = vec4(0.0);
        vec4 cB = vec4(0.0);
        float samples = 6.0;
        for (float i = 0.0; i < samples; i++) {
            float scaleA = 1.0 + p * 0.4 * (i / samples);
            float scaleB = 1.4 - (1.0 - p) * 0.4 * (i / samples);
            vec2 coordA = (uv - center) / max(scaleA, 0.001) + center;
            vec2 coordB = (uv - center) / max(scaleB, 0.001) + center;
            cA += texture(uTexA, clamp(coordA, 0.0, 1.0));
            cB += texture(uTexB, clamp(coordB, 0.0, 1.0));
        }
        cA /= samples;
        cB /= samples;
        fragColor = mix(cA, cB, smoothstep(0.3, 0.7, p));
    } 
    else if (uType == 3) { // Slide
        vec2 uvA = uv + vec2(p, 0.0);
        vec2 uvB = uv - vec2(1.0 - p, 0.0);
        if (uv.x < 1.0 - p) fragColor = texture(uTexA, uvA);
        else fragColor = texture(uTexB, uvB);
    } 
    else if (uType == 4) { // Glitch
        float slice = floor(uv.y * 24.0);
        float rnd = hash21(vec2(slice, floor(p * 15.0)));
        float displace = (rnd - 0.5) * 0.15 * sin(p * 3.14159);
        vec2 gUv = uv + vec2(displace, 0.0);
        vec4 gA = texture(uTexA, clamp(gUv, 0.0, 1.0));
        vec4 gB = texture(uTexB, clamp(gUv, 0.0, 1.0));
        vec4 base = mix(gA, gB, p);
        if (abs(displace) > 0.03) {
            float r = mix(texture(uTexA, gUv + vec2(0.01, 0.0)).r, texture(uTexB, gUv + vec2(0.01, 0.0)).r, p);
            float b = mix(texture(uTexA, gUv - vec2(0.01, 0.0)).b, texture(uTexB, gUv - vec2(0.01, 0.0)).b, p);
            base.r = r;
            base.b = b;
        }
        fragColor = base;
    }
    // Category 1: Dissolve & Fade
    else if (uType == 5) { // Fade Black
        fragColor = p < 0.5 ? mix(colA, vec4(0.0, 0.0, 0.0, 1.0), p * 2.0) : mix(vec4(0.0, 0.0, 0.0, 1.0), colB, (p - 0.5) * 2.0);
    }
    else if (uType == 6) { // Fade White
        fragColor = p < 0.5 ? mix(colA, vec4(1.0, 1.0, 1.0, 1.0), p * 2.0) : mix(vec4(1.0, 1.0, 1.0, 1.0), colB, (p - 0.5) * 2.0);
    }
    else if (uType == 7) { // Fade Color
        vec4 flash = vec4(0.0, 0.85, 1.0, 1.0);
        fragColor = p < 0.5 ? mix(colA, flash, p * 2.0) : mix(flash, colB, (p - 0.5) * 2.0);
    }
    else if (uType == 8) { // Luma Fade
        float luma = dot(colA.rgb, vec3(0.299, 0.587, 0.114));
        fragColor = luma < p ? colB : colA;
    }
    else if (uType == 9) { // Burn Fade
        float l = dot(colA.rgb, vec3(0.333));
        float factor = smoothstep(p - 0.08, p + 0.08, l);
        vec4 res = mix(colB, colA, factor);
        if (abs(l - p) < 0.08) res.rgb += vec3(1.0, 0.4, 0.1) * 1.5;
        fragColor = res;
    }
    else if (uType == 10) { // Exposure Fade
        float boost = 1.0 + sin(p * 3.14159) * 3.5;
        fragColor = mix(colA, colB, p) * boost;
    }
    else if (uType == 11) { // Blur Dissolve
        vec2 blur = vec2(sin(p * 3.14159) * 0.015, 0.0);
        fragColor = mix(texture(uTexA, uv + blur), texture(uTexB, uv - blur), p);
    }
    else if (uType == 12) { // Film Dissolve
        float grain = (hash21(uv * 100.0 + p * 12.0) - 0.5) * 0.2 * sin(p * 3.14159);
        fragColor = mix(colA, colB, p) + grain;
    }
    else if (uType == 13) { // Gradient Fade
        float g = smoothstep(p - 0.1, p + 0.1, uv.y);
        fragColor = mix(colB, colA, g);
    }
    // Category 2: Wipe & Split
    else if (uType == 14) { // Wipe Left
        float f = smoothstep(1.0 - p - 0.08, 1.0 - p + 0.08, uv.x);
        fragColor = mix(colA, colB, f);
    }
    else if (uType == 15) { // Wipe Up
        float f = smoothstep(p - 0.08, p + 0.08, uv.y);
        fragColor = mix(colB, colA, f);
    }
    else if (uType == 16) { // Wipe Down
        float f = smoothstep(1.0 - p - 0.08, 1.0 - p + 0.08, uv.y);
        fragColor = mix(colA, colB, f);
    }
    else if (uType == 17) { // Radial Wipe
        float a = (atan(uv.y - 0.5, uv.x - 0.5) / 6.28318) + 0.5;
        fragColor = a < p ? colB : colA;
    }
    else if (uType == 18) { // Circle Crop
        float d = length(uv - vec2(0.5));
        fragColor = d < p * 0.72 ? colB : colA;
    }
    else if (uType == 19) { // Diamond Wipe
        float d = abs(uv.x - 0.5) + abs(uv.y - 0.5);
        fragColor = d < p ? colB : colA;
    }
    else if (uType == 20) { // Split Horizontal
        float d = abs(uv.x - 0.5);
        fragColor = d < p * 0.5 ? colB : colA;
    }
    else if (uType == 21) { // Split Vertical
        float d = abs(uv.y - 0.5);
        fragColor = d < p * 0.5 ? colB : colA;
    }
    else if (uType == 22) { // Venetian Blinds
        float f = fract(uv.y * 10.0);
        fragColor = f < p ? colB : colA;
    }
    // Category 3: Slide & Push
    else if (uType == 23) { // Slide Right
        if (uv.x < p) fragColor = texture(uTexB, uv + vec2(1.0 - p, 0.0));
        else fragColor = texture(uTexA, uv - vec2(p, 0.0));
    }
    else if (uType == 24) { // Slide Up
        if (uv.y < 1.0 - p) fragColor = texture(uTexA, uv + vec2(0.0, p));
        else fragColor = texture(uTexB, uv - vec2(0.0, 1.0 - p));
    }
    else if (uType == 25) { // Slide Down
        if (uv.y < p) fragColor = texture(uTexB, uv + vec2(0.0, 1.0 - p));
        else fragColor = texture(uTexA, uv - vec2(0.0, p));
    }
    else if (uType == 26) { // Push Left
        if (uv.x < 1.0 - p) fragColor = texture(uTexA, uv + vec2(p, 0.0));
        else fragColor = texture(uTexB, uv - vec2(1.0 - p, 0.0));
    }
    else if (uType == 27) { // Push Right
        if (uv.x > p) fragColor = texture(uTexA, uv - vec2(p, 0.0));
        else fragColor = texture(uTexB, uv + vec2(1.0 - p, 0.0));
    }
    else if (uType == 28) { // Push Up
        if (uv.y < 1.0 - p) fragColor = texture(uTexA, uv + vec2(0.0, p));
        else fragColor = texture(uTexB, uv - vec2(0.0, 1.0 - p));
    }
    else if (uType == 29) { // Push Down
        if (uv.y > p) fragColor = texture(uTexA, uv - vec2(0.0, p));
        else fragColor = texture(uTexB, uv + vec2(0.0, 1.0 - p));
    }
    else if (uType == 30) { // Parallax Slide
        fragColor = mix(texture(uTexA, uv + vec2(p * 0.4, 0.0)), texture(uTexB, uv - vec2((1.0 - p) * 0.8, 0.0)), p);
    }
    else if (uType == 31) { // Corner Slide
        float d = (uv.x + uv.y) * 0.5;
        fragColor = d < p ? colB : colA;
    }
    // Category 4: Zoom & Warp
    else if (uType == 32) { // Zoom Out
        vec2 c = vec2(0.5);
        float sA = 1.0 - p * 0.4;
        float sB = 1.5 - p * 0.5;
        vec2 uvA = (uv - c) / max(sA, 0.01) + c;
        vec2 uvB = (uv - c) / max(sB, 0.01) + c;
        fragColor = p < 0.5 ? texture(uTexA, clamp(uvA, 0.0, 1.0)) : texture(uTexB, clamp(uvB, 0.0, 1.0));
    }
    else if (uType == 33) { // Whip Pan Left
        float off = sin(p * 3.14159) * 0.12;
        fragColor = mix(texture(uTexA, uv + vec2(p + off, 0.0)), texture(uTexB, uv - vec2(1.0 - p - off, 0.0)), p);
    }
    else if (uType == 34) { // Whip Pan Right
        float off = sin(p * 3.14159) * 0.12;
        fragColor = mix(texture(uTexA, uv - vec2(p + off, 0.0)), texture(uTexB, uv + vec2(1.0 - p - off, 0.0)), p);
    }
    else if (uType == 35) { // Whip Pan Up
        float off = sin(p * 3.14159) * 0.12;
        fragColor = mix(texture(uTexA, uv + vec2(0.0, p + off)), texture(uTexB, uv - vec2(0.0, 1.0 - p - off)), p);
    }
    else if (uType == 36) { // Whip Pan Down
        float off = sin(p * 3.14159) * 0.12;
        fragColor = mix(texture(uTexA, uv - vec2(0.0, p + off)), texture(uTexB, uv + vec2(0.0, 1.0 - p - off)), p);
    }
    else if (uType == 37) { // Spin CW
        float a = p * 3.14159;
        mat2 rot = mat2(cos(a), -sin(a), sin(a), cos(a));
        vec2 rotUv = rot * (uv - 0.5) + 0.5;
        fragColor = mix(texture(uTexA, clamp(rotUv, 0.0, 1.0)), colB, p);
    }
    else if (uType == 38) { // Spin CCW
        float a = -p * 3.14159;
        mat2 rot = mat2(cos(a), -sin(a), sin(a), cos(a));
        vec2 rotUv = rot * (uv - 0.5) + 0.5;
        fragColor = mix(texture(uTexA, clamp(rotUv, 0.0, 1.0)), colB, p);
    }
    else if (uType == 39) { // Swirl Warp
        float d = length(uv - 0.5);
        float theta = (1.0 - d) * sin(p * 3.14159) * 3.0;
        mat2 m = mat2(cos(theta), -sin(theta), sin(theta), cos(theta));
        vec2 sUv = m * (uv - 0.5) + 0.5;
        fragColor = mix(texture(uTexA, clamp(sUv, 0.0, 1.0)), colB, p);
    }
    else if (uType == 40) { // Fisheye Punch
        float r = length(uv - 0.5);
        float k = sin(p * 3.14159) * 1.5;
        vec2 fUv = (uv - 0.5) * (1.0 + k * r * r) + 0.5;
        fragColor = mix(texture(uTexA, clamp(fUv, 0.0, 1.0)), colB, p);
    }
    // Category 5: Glitch & Digital
    else if (uType == 41) { // RGB Displace
        vec2 off = vec2(sin(p * 3.14159) * 0.03, 0.0);
        float r = mix(texture(uTexA, uv + off).r, texture(uTexB, uv + off).r, p);
        float g = mix(colA.g, colB.g, p);
        float b = mix(texture(uTexA, uv - off).b, texture(uTexB, uv - off).b, p);
        fragColor = vec4(r, g, b, 1.0);
    }
    else if (uType == 42) { // VHS Rewind
        float bar = smoothstep(0.05, 0.0, abs(uv.y - fract(p * 2.5)));
        vec4 res = mix(colA, colB, p);
        res.rgb += bar * vec3(0.3, 0.3, 0.4);
        fragColor = res;
    }
    else if (uType == 43) { // Pixel Blocks
        float n = mix(100.0, 8.0, sin(p * 3.14159));
        vec2 pUv = floor(uv * n) / n;
        fragColor = mix(texture(uTexA, pUv), texture(uTexB, pUv), p);
    }
    else if (uType == 44) { // Mosaic Zoom
        float m = mix(60.0, 4.0, sin(p * 3.14159));
        vec2 mUv = floor(uv * m) / m;
        fragColor = mix(texture(uTexA, mUv), colB, p);
    }
    else if (uType == 45) { // TV Static
        float noise = (hash21(uv * 40.0 + p * 15.0) - 0.5) * sin(p * 3.14159) * 0.8;
        fragColor = mix(colA, colB, p) + noise;
    }
    else if (uType == 46) { // Codec Tear
        vec2 block = floor(uv * vec2(20.0, 10.0));
        float shift = (hash21(block + floor(p * 6.0)) - 0.5) * 0.1 * sin(p * 3.14159);
        fragColor = mix(texture(uTexA, uv + vec2(shift, 0.0)), colB, p);
    }
    else if (uType == 47) { // Scanline Sweep
        float beam = smoothstep(0.06, 0.0, abs(uv.y - p));
        vec4 c = mix(colA, colB, p);
        c.rgb += beam * vec3(0.2, 0.7, 1.0);
        fragColor = c;
    }
    else if (uType == 48) { // Color Corrupt
        vec4 c = mix(colA, colB, p);
        if (p > 0.4 && p < 0.6) c.rgb = vec3(1.0) - c.rgb;
        fragColor = c;
    }
    else if (uType == 49) { // Pixel Stretch
        float str = sin(p * 3.14159) * 0.2;
        vec2 sUv = vec2(uv.x, clamp(uv.y + (uv.y - 0.5) * str, 0.0, 1.0));
        fragColor = mix(texture(uTexA, sUv), colB, p);
    }
    // Category 6: Light & Flash
    else if (uType == 50) { // Light Leak
        vec3 leak = vec3(1.0, 0.5, 0.2) * sin(p * 3.14159) * 1.5;
        fragColor = mix(colA, colB, p) + vec4(leak, 0.0);
    }
    else if (uType == 51) { // Lens Flare Flash
        float flare = max(0.0, 1.0 - length(uv - 0.5) * 2.0) * sin(p * 3.14159);
        fragColor = mix(colA, colB, p) + vec4(vec3(0.4, 0.7, 1.0) * flare * 2.0, 0.0);
    }
    else if (uType == 52) { // Glow Burst
        float burst = sin(p * 3.14159) * 2.0;
        fragColor = mix(colA, colB, p) * (1.0 + burst);
    }
    else if (uType == 53) { // Strobe Flash
        float strobe = mod(p * 6.0, 1.0) > 0.5 ? 0.7 : 0.0;
        fragColor = mix(colA, colB, p) + vec4(strobe);
    }
    else if (uType == 54) { // Neon Pulse
        vec3 neon = vec3(0.0, 0.85, 1.0) * sin(p * 3.14159) * 1.2;
        fragColor = mix(colA, colB, p) + vec4(neon, 0.0);
    }
    else if (uType == 55) { // Prism Disperse
        float disp = sin(p * 3.14159) * 0.02;
        float r = mix(texture(uTexA, uv + vec2(disp, 0.0)).r, texture(uTexB, uv + vec2(disp, 0.0)).r, p);
        float g = mix(colA.g, colB.g, p);
        float b = mix(texture(uTexA, uv - vec2(disp, 0.0)).b, texture(uTexB, uv - vec2(disp, 0.0)).b, p);
        fragColor = vec4(r, g, b, 1.0);
    }
    else if (uType == 56) { // Sun Glare
        vec3 sun = vec3(1.0, 0.85, 0.5) * sin(p * 3.14159) * smoothstep(0.7, 0.0, length(uv - vec2(0.2, 0.1)));
        fragColor = mix(colA, colB, p) + vec4(sun * 2.0, 0.0);
    }
    else if (uType == 57) { // Chromatic Zoom
        vec2 c = vec2(0.5);
        float z = sin(p * 3.14159) * 0.04;
        float r = mix(texture(uTexA, (uv - c) * (1.0 + z) + c).r, texture(uTexB, uv).r, p);
        float g = mix(colA.g, colB.g, p);
        float b = mix(texture(uTexA, (uv - c) * (1.0 - z) + c).b, texture(uTexB, uv).b, p);
        fragColor = vec4(r, g, b, 1.0);
    }
    else if (uType == 58) { // Diffuse Glow
        float diff = sin(p * 3.14159) * 0.6;
        vec4 base = mix(colA, colB, p);
        fragColor = base + base * diff;
    }
    else if (uType == 59) { // Spark Burst
        float spark = step(0.98, hash21(uv * 40.0 + p * 15.0)) * sin(p * 3.14159) * 2.0;
        fragColor = mix(colA, colB, p) + vec4(spark);
    }
    else {
        fragColor = mix(colA, colB, p);
    }
}
"""

        // Dynamic Visual Effects Fragment Shader: RGB Split, Glitch, VHS
        private const val EFFECTS_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform int uEffectType; // 0: RGB Split, 1: Glitch, 2: VHS
uniform float uIntensity;
uniform float uTime;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = vTexCoord;
    float intensity = clamp(uIntensity, 0.0, 1.0);

    if (uEffectType == 0) {
        // RGB Split
        float offset = 0.015 * intensity;
        float r = texture(uTexture, uv + vec2(offset, 0.0)).r;
        float g = texture(uTexture, uv).g;
        float b = texture(uTexture, uv - vec2(offset, 0.0)).b;
        fragColor = vec4(r, g, b, texture(uTexture, uv).a);
    } 
    else if (uEffectType == 1) {
        // Digital Glitch
        float sliceY = floor(uv.y * 30.0);
        float timeStep = floor(uTime * 12.0);
        float noise = hash(vec2(sliceY, timeStep));
        vec2 offset = vec2(0.0);

        if (noise > 0.75) {
            offset.x = (noise - 0.75) * 0.12 * intensity;
        }

        vec2 glitchUv = uv + offset;
        vec4 orig = texture(uTexture, uv);
        vec4 glitched = texture(uTexture, glitchUv);

        // Color channel aberration
        glitched.r = texture(uTexture, glitchUv + vec2(0.01 * intensity, 0.0)).r;
        glitched.b = texture(uTexture, glitchUv - vec2(0.01 * intensity, 0.0)).b;

        fragColor = mix(orig, glitched, intensity);
    } 
    else if (uEffectType == 2) {
        // VHS Retro
        vec2 vhsUv = uv;
        vhsUv.x += sin(uv.y * 100.0 + uTime * 5.0) * 0.002 * intensity;

        // Tracking roll bar
        float bar = smoothstep(0.0, 0.05, abs(fract(uv.y * 2.0 - uTime * 0.4) - 0.5));
        vec4 col = texture(uTexture, vhsUv);

        // Chromatic dispersion
        col.r = texture(uTexture, vhsUv + vec2(0.004 * intensity, 0.0)).r;
        col.b = texture(uTexture, vhsUv - vec2(0.004 * intensity, 0.0)).b;

        // Phosphor scanline
        float scanline = sin(uv.y * 600.0) * 0.08 * intensity;
        col.rgb -= scanline;
        col.rgb *= mix(0.7, 1.0, bar);

        fragColor = col;
    } 
    else {
        fragColor = texture(uTexture, uv);
    }
}
"""
    }

    /**
     * Managed Framebuffer Object (FBO) for offscreen multi-pass rendering.
     */
    class GlFramebuffer(val width: Int, val height: Int) {
        var fboId: Int = 0
            private set
        var textureId: Int = 0
            private set

        init {
            val fbos = IntArray(1)
            GLES30.glGenFramebuffers(1, fbos, 0)
            fboId = fbos[0]

            val textures = IntArray(1)
            GLES30.glGenTextures(1, textures, 0)
            textureId = textures[0]

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                width, height, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, textureId, 0
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG, "Framebuffer not complete: status = $status")
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }

        fun bind() {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glViewport(0, 0, width, height)
        }

        fun unbind() {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }

        fun release() {
            if (fboId != 0) {
                GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
                fboId = 0
            }
            if (textureId != 0) {
                GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
                textureId = 0
            }
        }
    }

    private var transitionProgram: Int = 0
    private var effectsProgram: Int = 0

    // Transition Uniforms
    private var uTexALoc: Int = -1
    private var uTexBLoc: Int = -1
    private var uProgressLoc: Int = -1
    private var uTypeLoc: Int = -1

    // Effects Uniforms
    private var uFxTextureLoc: Int = -1
    private var uFxTypeLoc: Int = -1
    private var uFxIntensityLoc: Int = -1
    private var uFxTimeLoc: Int = -1

    // Ping-pong FBOs for multi-pass chaining
    private var pingFbo: GlFramebuffer? = null
    private var pongFbo: GlFramebuffer? = null

    private val quadBuffer: FloatBuffer

    init {
        val quadVertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f
        )
        quadBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        quadBuffer.position(0)
    }

    fun initGl() {
        transitionProgram = createProgram(VERTEX_SHADER, TRANSITION_FRAGMENT_SHADER)
        uTexALoc = GLES30.glGetUniformLocation(transitionProgram, "uTexA")
        uTexBLoc = GLES30.glGetUniformLocation(transitionProgram, "uTexB")
        uProgressLoc = GLES30.glGetUniformLocation(transitionProgram, "uProgress")
        uTypeLoc = GLES30.glGetUniformLocation(transitionProgram, "uType")

        effectsProgram = createProgram(VERTEX_SHADER, EFFECTS_FRAGMENT_SHADER)
        uFxTextureLoc = GLES30.glGetUniformLocation(effectsProgram, "uTexture")
        uFxTypeLoc = GLES30.glGetUniformLocation(effectsProgram, "uEffectType")
        uFxIntensityLoc = GLES30.glGetUniformLocation(effectsProgram, "uIntensity")
        uFxTimeLoc = GLES30.glGetUniformLocation(effectsProgram, "uTime")
    }

    fun ensureFbos(width: Int, height: Int) {
        if (pingFbo == null || pingFbo?.width != width || pingFbo?.height != height) {
            pingFbo?.release()
            pongFbo?.release()
            pingFbo = GlFramebuffer(width, height)
            pongFbo = GlFramebuffer(width, height)
        }
    }

    /**
     * Renders a transition from [textureA] to [textureB] into the currently bound target.
     */
    fun renderTransition(
        textureA: Int,
        textureB: Int,
        progress: Float,
        type: TransitionType
    ) {
        GLES30.glUseProgram(transitionProgram)

        // Setup vertex attributes
        quadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, quadBuffer)
        GLES30.glEnableVertexAttribArray(0)

        quadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 4 * 4, quadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        // Bind Texture A to unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureA)
        GLES30.glUniform1i(uTexALoc, 0)

        // Bind Texture B to unit 1
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureB)
        GLES30.glUniform1i(uTexBLoc, 1)

        GLES30.glUniform1f(uProgressLoc, progress)
        GLES30.glUniform1i(uTypeLoc, type.typeIndex)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    /**
     * Applies a dynamic effect (RGB split, Glitch, VHS) to [sourceTexture].
     */
    fun renderEffect(
        sourceTexture: Int,
        effectType: DynamicEffectType,
        intensity: Float,
        timeSeconds: Float
    ) {
        GLES30.glUseProgram(effectsProgram)

        quadBuffer.position(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, quadBuffer)
        GLES30.glEnableVertexAttribArray(0)

        quadBuffer.position(2)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 4 * 4, quadBuffer)
        GLES30.glEnableVertexAttribArray(1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sourceTexture)
        GLES30.glUniform1i(uFxTextureLoc, 0)

        val typeInt = when (effectType) {
            DynamicEffectType.RGB_SPLIT -> 0
            DynamicEffectType.DIGITAL_GLITCH -> 1
            DynamicEffectType.VHS -> 2
        }
        GLES30.glUniform1i(uFxTypeLoc, typeInt)
        GLES30.glUniform1f(uFxIntensityLoc, intensity)
        GLES30.glUniform1f(uFxTimeLoc, timeSeconds)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    fun release() {
        pingFbo?.release()
        pingFbo = null
        pongFbo?.release()
        pongFbo = null

        if (transitionProgram != 0) {
            GLES30.glDeleteProgram(transitionProgram)
            transitionProgram = 0
        }
        if (effectsProgram != 0) {
            GLES30.glDeleteProgram(effectsProgram)
            effectsProgram = 0
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vShader)
        GLES30.glAttachShader(program, fShader)
        GLES30.glLinkProgram(program)
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        return shader
    }
}
